package cn.calculator;

import com.google.gson.JsonObject;

/** JSON response model; strings preserve decimal precision and timestamp format. */
public final class CalculationRecord {
  public final long id;
  public final String expression;
  public final String result;
  public final String createdAt;
  public final String type;
  public final JsonObject parameters;

  public CalculationRecord(long id, String expression, String result, String createdAt) {
    this(id, expression, result, createdAt, "calculation", defaultParameters(expression));
  }

  public CalculationRecord(long id, String expression, String result, String createdAt,
      String type, JsonObject parameters) {
    this.id = id;
    this.expression = expression;
    this.result = result;
    this.createdAt = createdAt;
    this.type = type;
    this.parameters = parameters.deepCopy();
  }

  static JsonObject defaultParameters(String expression) {
    JsonObject parameters = new JsonObject();
    parameters.addProperty("expression", expression);
    parameters.addProperty("angleMode", "deg");
    return parameters;
  }
}
