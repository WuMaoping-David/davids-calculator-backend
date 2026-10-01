package cn.calculator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Small Java HTTP API. Frontend assets are deliberately hosted by a separate server. */
public final class CalculatorApplication {
  private static final int MAX_BODY_BYTES = 16384;
  private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
  private final HistoryRepository repository;
  private final ExpressionCalculator calculator = new ExpressionCalculator();
  private final Set<String> allowedOrigins;

  private CalculatorApplication(HistoryRepository repository, Set<String> allowedOrigins) {
    this.repository = repository;
    this.allowedOrigins = allowedOrigins;
  }

  public static void main(String[] args) throws Exception {
    String address = environment("BIND_ADDRESS", "127.0.0.1");
    int port = Integer.parseInt(environment("PORT", "8080"));
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("PORT must be between 1 and 65535.");
    }
    Set<String> origins = new HashSet<>();
    for (String origin : environment("CORS_ORIGINS",
        "http://localhost:5173,http://127.0.0.1:5173").split(",")) {
      if (!origin.trim().isEmpty() && !"*".equals(origin.trim())) {
        origins.add(origin.trim());
      }
    }
    System.setProperty("sun.net.httpserver.maxReqTime", "30");
    System.setProperty("sun.net.httpserver.maxRspTime", "30");
    System.setProperty("sun.net.httpserver.idleInterval", "30");
    final HistoryRepository repository = new HistoryRepository(
        environment("DB_PATH", "./data/calculator"));
    final HttpServer server;
    try {
      server = HttpServer.create(new InetSocketAddress(address, port), 64);
    } catch (IOException exception) {
      repository.close();
      throw exception;
    }
    final ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(64), new ThreadPoolExecutor.CallerRunsPolicy());
    CalculatorApplication application = new CalculatorApplication(repository, origins);
    server.createContext("/", application::handle);
    server.setExecutor(executor);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      server.stop(1);
      executor.shutdown();
      try {
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
      try {
        repository.close();
      } catch (SQLException exception) {
        System.err.println("Database close failed: " + exception.getMessage());
      }
    }, "calculator-shutdown"));
    server.start();
    System.out.println("David's calculator API listening on http://" + address + ":" + port);
    System.out.println("Health check: http://" + address + ":" + port + "/api/health");
  }

  private void handle(HttpExchange exchange) throws IOException {
    try {
      exchange.getResponseHeaders().set("Cache-Control", "no-store");
      exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
      exchange.getResponseHeaders().set("Vary", "Origin");
      String origin = exchange.getRequestHeaders().getFirst("Origin");
      if (origin != null && !allowedOrigins.contains(origin)) {
        throw new ApiException(403, "ORIGIN_NOT_ALLOWED", "This website origin is not allowed. Check the API CORS configuration.");
      }
      if (origin != null) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
      }
      if ("OPTIONS".equals(exchange.getRequestMethod())) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "600");
        exchange.sendResponseHeaders(204, -1);
        return;
      }
      route(exchange);
    } catch (CalculationException exception) {
      sendError(exchange, 400, exception.getCode(), exception.getMessage());
    } catch (ApiException exception) {
      sendError(exchange, exception.status, exception.code, exception.getMessage());
    } catch (SQLException exception) {
      System.err.println("Database request failed: " + exception.getMessage());
      sendError(exchange, 500, "DATABASE_ERROR", "Unable to save or load data. Please try again later.");
    } catch (RuntimeException exception) {
      exception.printStackTrace(System.err);
      sendError(exchange, 500, "INTERNAL_ERROR", "The service is temporarily unavailable. Please try again later.");
    } finally {
      exchange.close();
    }
  }

  private void route(HttpExchange exchange) throws SQLException, IOException {
    String path = exchange.getRequestURI().getPath();
    if ("/api/health".equals(path)) {
      requireMethod(exchange, "GET");
      if (!repository.isHealthy()) {
        throw new SQLException("Database health check failed");
      }
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("status", "ok");
      sendJson(exchange, 200, success(data));
    } else if ("/api/calculate".equals(path)) {
      requireMethod(exchange, "POST");
      JsonObject request = readRequest(exchange);
      String expression = requireString(request, "expression");
      String angleMode = request.has("angleMode") ? requireString(request, "angleMode") : "deg";
      String result = calculator.calculate(expression, angleMode);
      JsonObject parameters = new JsonObject();
      parameters.addProperty("expression", expression);
      parameters.addProperty("angleMode", angleMode);
      CalculationRecord record = repository.save(expression.trim(), result, "calculation", parameters);
      sendJson(exchange, 201, success(record));
    } else if ("/api/history".equals(path)) {
      if ("DELETE".equals(exchange.getRequestMethod())) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deletedCount", repository.deleteAll());
        sendJson(exchange, 200, success(data));
      } else {
        if (!"GET".equals(exchange.getRequestMethod())) {
          exchange.getResponseHeaders().set("Allow", "GET, DELETE, OPTIONS");
          throw new ApiException(405, "METHOD_NOT_ALLOWED", "Use GET or DELETE for history.");
        }
        sendJson(exchange, 200, success(repository.findAll()));
      }
    } else if (path.startsWith("/api/history/")) {
      requireMethod(exchange, "DELETE");
      long id = parseId(path.substring("/api/history/".length()));
      if (!repository.delete(id)) {
        throw new ApiException(404, "HISTORY_NOT_FOUND", "This history record no longer exists.");
      }
      sendJson(exchange, 200, success(null));
    } else {
      throw new ApiException(404, "NOT_FOUND", "The requested API endpoint does not exist.");
    }
  }

  private JsonObject readRequest(HttpExchange exchange) throws IOException {
    String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
    if (contentType == null || !"application/json".equalsIgnoreCase(contentType.split(";", 2)[0].trim())) {
      throw new ApiException(415, "UNSUPPORTED_MEDIA_TYPE", "The request content type must be application/json.");
    }
    String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
    if (contentLength != null) {
      try {
        if (Long.parseLong(contentLength) > MAX_BODY_BYTES) {
          throw new ApiException(413, "BODY_TOO_LARGE", "The request body is too large.");
        }
      } catch (NumberFormatException exception) {
        throw new ApiException(400, "INVALID_CONTENT_LENGTH", "The request content length is invalid.");
      }
    }
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    try (InputStream input = exchange.getRequestBody()) {
      byte[] buffer = new byte[2048];
      int count;
      while ((count = input.read(buffer)) != -1) {
        if (body.size() + count > MAX_BODY_BYTES) {
          throw new ApiException(413, "BODY_TOO_LARGE", "The request body is too large.");
        }
        body.write(buffer, 0, count);
      }
    }
    JsonElement parsed;
    try {
      parsed = GSON.fromJson(new String(body.toByteArray(), StandardCharsets.UTF_8), JsonElement.class);
    } catch (JsonParseException exception) {
      throw new ApiException(400, "INVALID_JSON", "The request body is not valid JSON.");
    }
    if (parsed == null || !parsed.isJsonObject()) {
      throw new ApiException(400, "INVALID_REQUEST", "The request body must be a JSON object.");
    }
    return parsed.getAsJsonObject();
  }

  private static String requireString(JsonObject request, String name) {
    JsonElement value = request.get(name);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new ApiException(400, "INVALID_REQUEST", "The '" + name + "' field must be a string.");
    }
    return value.getAsString();
  }

  private static long parseId(String value) {
    if (!value.matches("[1-9][0-9]{0,18}")) {
      throw new ApiException(400, "INVALID_ID", "The history record ID is invalid.");
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new ApiException(400, "INVALID_ID", "The history record ID is invalid.");
    }
  }

  private static void requireMethod(HttpExchange exchange, String method) {
    if (!method.equals(exchange.getRequestMethod())) {
      exchange.getResponseHeaders().set("Allow", method + ", OPTIONS");
      throw new ApiException(405, "METHOD_NOT_ALLOWED", "This API endpoint does not support the request method.");
    }
  }

  private static Map<String, Object> success(Object data) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("success", true);
    if (data != null) {
      response.put("data", data);
    }
    return response;
  }

  private static void sendError(HttpExchange exchange, int status, String code, String message)
      throws IOException {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("success", false);
    response.put("message", message);
    response.put("code", code);
    sendJson(exchange, status, response);
  }

  private static void sendJson(HttpExchange exchange, int status, Object response) throws IOException {
    byte[] bytes = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  private static String environment(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
  }

  private static final class ApiException extends RuntimeException {
    final int status;
    final String code;

    ApiException(int status, String code, String message) {
      super(message);
      this.status = status;
      this.code = code;
    }
  }
}
