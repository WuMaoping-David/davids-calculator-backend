# David's calculator — Java Backend

## Public deployment

Verified public calculator: https://davids-calculator-frontend.onrender.com

Verified API health: https://davids-calculator-backend.onrender.com/api/health

Both services were live on October 2, 2026. Eleven cloud checks passed and saved history survived a Render restart.

Use a Render Free Docker Web Service and a Neon Free PostgreSQL database, without a Render disk. Set `/api/health` as the health check and configure the remote database environment variables below. The Dockerfile uses Java 17, binds to `0.0.0.0`, and accepts the platform `PORT`. See [PUBLIC_DEPLOYMENT.md](PUBLIC_DEPLOYMENT.md) for the complete free deployment procedure. Local startup uses H2 when no remote URL is configured. Source publication alone does not establish public service availability.

## Project introduction

This repository provides the independent Java HTTP API for David's calculator. It evaluates standard and scientific expressions, saves successful results in local H2 or remote PostgreSQL, retrieves history, deletes individual records, and clears all history on request. The browser interface is hosted separately and performs no core mathematical evaluation.

The current version focuses on scientific calculation. Full inverse-function names are `arcsin`, `arccos`, and `arctan`. Number-base and unit-conversion services have been removed; their former endpoints return 404. Existing conversion history is retained as archived data rather than deleted during an upgrade.

## Technology stack

| Component | Technology |
|---|---|
| Language and server | Java, with JDK `HttpServer` |
| Expression evaluation | Dedicated recursive-descent parser; no script engine or arbitrary-code execution |
| JSON | Gson **2.13.2** |
| Persistence | JDBC, embedded H2 **2.2.224** locally, and PostgreSQL JDBC **42.7.13** for cloud persistence |
| Primary build workflow | PowerShell scripts invoking `javac` and `java` |
| Optional build workflow | Maven, using `pom.xml` |

## Runtime environment

- A full **JDK 8 or newer**, with both `java` and `javac` on `PATH`. The project has been exercised with JDK 8. A JRE alone cannot run the source-build workflow.
- **Windows with PowerShell 5.1 or newer** for the supplied scripts. Maven offers an alternative build route on other operating systems.
- Internet access to Maven Central on the first script build to download the three pinned dependency JARs. Later builds reuse checksum-verified files in `lib/`.
- A writable database directory and a free backend port, default **8080**.
- For full browser use, the separate frontend, normally served on port **5173**.

The local H2 setup requires no separate database server, H2 console, Node.js installation, or application server. Cloud deployment uses a separately provisioned PostgreSQL database. Python 3 is only needed for the optional HTTP integration suite included in the combined workspace.

## Installation method

1. Obtain the backend repository with Git or download and extract its source ZIP. Open PowerShell in the directory containing `build.ps1`, `run.ps1`, and `pom.xml`. In the combined workspace this is `calculator-backend`.
2. Verify the JDK:

```powershell
java -version
javac -version
```

3. Download the pinned dependencies and compile:

```powershell
.\build.ps1
```

`build.ps1` creates `lib/` and `target/classes/`, downloads H2 2.2.224, Gson 2.13.2, and PostgreSQL JDBC 42.7.13 if missing, verifies their SHA-256 hashes, and compiles UTF-8 Java sources. A checksum mismatch stops the build. The dependency versions and expected hashes are recorded in the script; do not substitute a differently versioned JAR without updating and validating the project.

The build itself does not initialize the database. Database initialization occurs when the application starts.

## Startup method

From this repository's root:

```powershell
.\run.ps1
```

The script builds before launching `cn.calculator.CalculatorApplication`. After a successful build, `./run.ps1 -SkipBuild` can skip compilation. Keep the terminal open; press **Ctrl+C** to stop the backend.

Check the service from a second PowerShell terminal:

```powershell
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/health'
```

Expected JSON: `{"success":true,"data":{"status":"ok"}}`. The health check also verifies database availability. The backend does not serve the frontend page at its root URL.

### Optional Maven startup

From the backend repository, with Maven installed:

```powershell
mvn package
java -Dfile.encoding=UTF-8 -cp "target/classes;target/dependency/*" cn.calculator.CalculatorApplication
```

For a POSIX shell on Linux or macOS, use `:` in the classpath:

```sh
mvn package
java -Dfile.encoding=UTF-8 -cp 'target/classes:target/dependency/*' cn.calculator.CalculatorApplication
```

Keep the backend repository as the working directory so the default relative database path stays consistent. Maven copies runtime dependencies into `target/dependency`; it does not create a self-contained executable JAR. The custom Java tests have `main` methods and are not automatically executed by Maven's default test discovery.

## Configuration instructions

Set these environment variables in the terminal **before** running `run.ps1`:

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | Backend TCP port, between 1 and 65535 |
| `BIND_ADDRESS` | `127.0.0.1` | Listening interface; default access is local only |
| `DB_PATH` | `./data/calculator` | Database file prefix; omit the `.mv.db` suffix and do not include a semicolon |
| `CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Comma-separated allowed frontend origins, without trailing slashes |
| `DATABASE_URL` | Unset | PostgreSQL JDBC URL starting with `jdbc:postgresql://`; when set, overrides H2 |
| `DATABASE_USER` | Unset | PostgreSQL role; backend configuration only |
| `DATABASE_PASSWORD` | Unset | PostgreSQL role password; never put it in frontend files or GitHub |
| `REQUIRE_REMOTE_DATABASE` | Unset | Set `true` in cloud deployment to reject startup without `DATABASE_URL` |

A custom local setup, using frontend port 5174 and backend port 8081:

```powershell
$env:PORT = '8081'
$env:BIND_ADDRESS = '127.0.0.1'
$env:DB_PATH = './data/calculator'
$env:CORS_ORIGINS = 'http://localhost:5174,http://127.0.0.1:5174'
.\run.ps1
```

For this example, set the frontend's `apiBaseUrl` to `http://localhost:8081` and start its preview with `./serve.ps1 -Port 5174`. Restart the backend after changing environment variables. `run.ps1` sets the working directory to this repository, so relative `DB_PATH` values resolve here.

The combined workspace's root `start.ps1` deliberately applies fixed local defaults (8080, 5173, loopback, and `./data/calculator`), then restores its caller's environment. Use the independent startup scripts for custom configuration.

## Database initialization method

With no `DATABASE_URL`, the first application startup automatically:

1. Creates the parent directory for `DB_PATH` if necessary.
2. Opens or creates the H2 file database.
3. Creates `calculation_history` if it does not exist.
4. Adds missing metadata columns from older schemas and initializes legacy calculation parameters.

No manual `CREATE DATABASE`, SQL import, or seed data is required. The default file is **`data/calculator.mv.db`** inside this repository. The embedded connection uses the application's internal `sa` user with an empty password; database credentials are never configured in the frontend, and no database network listener or console is started.

```sql
CREATE TABLE IF NOT EXISTS calculation_history (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  expression VARCHAR(512) NOT NULL,
  result VARCHAR(1000) NOT NULL,
  created_at VARCHAR(40) NOT NULL,
  record_type VARCHAR(20) DEFAULT 'calculation' NOT NULL,
  parameters VARCHAR(4096)
);
```

For PostgreSQL, first create a database through Neon or another provider and configure the three database variables. Startup connects over JDBC and creates the same table and missing columns automatically; no seed data is needed. Use certificate-verified TLS as described in PUBLIC_DEPLOYMENT.md. PostgreSQL data persists in the remote database independently of the Render filesystem. Local H2 data is not automatically imported.

The schema is shown for reference; startup executes it automatically. IDs identify records, results are strings, timestamps are UTC, and `parameters` stores the original expression and angle mode. Existing records survive initialization and migration.

Keep the same `DB_PATH` across restarts. Changing it selects a different database and can make history appear empty. Stop the backend before copying the database file for backup. Do not run two application instances against the same embedded database file. Keep `data/` out of the source repository. Both single deletion and Clear all permanently modify this database; neither is a browser-only operation.

## Frontend/backend connection method

Start the backend, then serve the separate frontend over HTTP. The default frontend configuration is:

```javascript
window.CALCULATOR_CONFIG = Object.freeze({
  apiBaseUrl: 'http://localhost:8080',
});
```

The browser sends JSON requests to the backend. `CORS_ORIGINS` must include the browser page's exact scheme, hostname, and port. `localhost` and `127.0.0.1` are different origins. Wildcard origins are not supported. Command-line clients without an `Origin` header are allowed; CORS is not authentication.

For a direct smoke test on the default backend:

```powershell
$payload = @{ expression = 'arcsin(0.5)'; angleMode = 'deg' } | ConvertTo-Json
Invoke-RestMethod -Uri 'http://localhost:8080/api/calculate' -Method Post -ContentType 'application/json' -Body $payload
Invoke-RestMethod -Uri 'http://localhost:8080/api/history'
```

The result should be the string `30`. This request creates a real saved history entry. In the browser, the same expression should show `30` and appear in history after refresh.

## API

Requests and responses use UTF-8 JSON. Successful calculation requests return HTTP **201** with a saved history record. Failed operations return an error and do not create history.

### Standard and scientific calculation

`POST /api/calculate`, with `Content-Type: application/json`:

```json
{"expression":"sin(30) + 2^3","angleMode":"deg"}
```

`expression` must be a string. `angleMode` is optional and defaults to `deg`; the only accepted values are `deg` and `rad`. This setting controls both trigonometric inputs and inverse-trigonometric outputs.

```json
{
  "success": true,
  "data": {
    "id": 1,
    "expression": "sin(30) + 2^3",
    "result": "8.5",
    "createdAt": "2026-10-01T04:00:00Z",
    "type": "calculation",
    "parameters": {"expression":"sin(30) + 2^3","angleMode":"deg"}
  }
}
```

### Read and delete history

- `GET /api/history` returns HTTP 200 and `{"success":true,"data":[...]}`, newest first. An empty database returns an empty array.
- `DELETE /api/history/1` returns HTTP 200 and `{"success":true}` after deleting that row from the database. A missing row returns 404.
- `DELETE /api/history` permanently deletes all rows and returns HTTP 200 with `{"success":true,"data":{"deletedCount":N}}`. Clearing an empty database succeeds with count 0.
- `GET /api/health` checks the database and returns HTTP 200 with `{"success":true,"data":{"status":"ok"}}` when healthy.

Every history record includes `id`, `expression`, `result`, `createdAt`, `type`, and `parameters`. New records have type `calculation`. Existing `base` and `unit` records remain readable and deletable for backward compatibility; their calculation features have been removed. The removed conversion routes return 404. Scientific history retains its angle mode. The database is shared across visitors and the history API currently returns all records without pagination.

### Error responses

```json
{"success":false,"message":"The divisor must not be zero.","code":"DIVISION_BY_ZERO"}
```

| HTTP status | Meaning |
|---|---|
| `400` | Invalid JSON, field type, expression, domain, or record ID |
| `403` | Origin is not in the configured allowlist |
| `404` | Endpoint or history record does not exist |
| `405` | Unsupported HTTP method; includes an `Allow` header |
| `413` | Request body exceeds 16 KiB |
| `415` | Request content type is not `application/json` |
| `500` | Database or internal error |

All user-facing errors are in English. Internal details stay in server logs. `OPTIONS` requests receive CORS preflight headers.

## Scientific expression rules

The safe recursive-descent grammar supports:

- Decimal numbers, parentheses, spaces, unary `+` and `-`, and `+ - * /` operations. `×` and `÷` are also accepted.
- Right-associative exponentiation: `2^3^2 = 512`.
- Powers bind more tightly than a leading sign: `-2^2 = -4`, `(-2)^2 = 4`, `2^-2 = 0.25`.
- Postfix factorial for integers from 0 through 170. It binds before powers, so `2^3! = 64`. Repeated `!` applies factorial repeatedly: `3!! = 720`; it is not double-factorial notation.
- Constants `pi` (also `π`) and `e`.
- Functions `sin`, `cos`, `tan`, `arcsin`, `arccos`, `arctan`, `sqrt`, `abs`, `ln`, `log`, and `exp`. Names are case-insensitive and require parentheses. `log` is base 10; `ln` is natural logarithm.

Legacy spellings `asin`, `acos`, and `atan` remain accepted by the parser for old saved expressions. The interface uses the full arc names.

Implicit multiplication such as `2pi` or `2(3+4)`, scientific-notation literals such as `1e3`, variables, and arbitrary function calls are not supported. No script engine, `eval`, or executable-code evaluation is used.

Addition, subtraction, multiplication, absolute value, factorial, and nonnegative integer powers use exact decimal arithmetic within the documented size limits. Division and negative integer powers use DECIMAL128. Exponents are limited to an absolute value of 1000; negative bases require integer exponents. This calculator follows the integer-power convention `0^0 = 1`.

Transcendental functions, square root, noninteger powers, and constants use Java `Math` double precision. Function outputs are rounded to approximately **15 significant digits**, so they are approximations and should not be treated as arbitrary-precision scientific results. Exact degree quadrants are handled directly. Undefined tangent, non-real domains, non-finite output, and unrepresentably small scientific values produce errors. Trigonometric angle magnitude is limited to `10^12`. See the [Java Math API](https://docs.oracle.com/javase/8/docs/api/java/lang/Math.html) for floating-point behavior.

Resource limits: 512 expression characters, 128 digits per numeric literal, 64 combined nesting levels, 1000 characters for intermediate and final decimal values, and 16 KiB per HTTP request. Power operations check intermediate products to prevent oversized computations. Server threads and their pending-work queue are bounded.

## Source, tests, and verification

```text
src/main/java/cn/calculator/
  CalculatorApplication.java  HTTP routes, JSON validation, errors, and CORS
  ExpressionCalculator.java  Safe standard/scientific expression parser
  CalculationException.java  User-facing mathematical errors
  HistoryRepository.java     JDBC persistence and schema migration
  CalculationRecord.java     Typed history response model
src/test/java/cn/calculator/
  ExpressionCalculatorTest.java
  HistoryRepositoryTest.java
```

Run all Java test programs with:

```powershell
.\build.ps1 -Test
```

The suites cover precedence, scientific domains, angle modes, precision, expression limits, full arc function names, durable single-record and bulk deletion, reusable parameters, and legacy database migration. Tests use explicit assertions rather than Java's optional `-ea` assertions. Database tests create and clean up temporary databases without touching application history. The workspace also includes an HTTP integration test script for running the API against an isolated temporary database.

The current verification recorded 104 parser checks, 26 database checks, and 911 HTTP integration assertions passing. These are check counts, not distinct user-scenario counts.

Local H2 checks are complete. PostgreSQL and public hosting require separate cloud acceptance checks; local test results do not prove a cloud deployment works. The public source repositories are linked in PUBLIC_DEPLOYMENT.md. Public deployment should configure HTTPS and appropriate frontend origins, and decide whether per-user history and authentication are needed.

## Troubleshooting and other operating information

| Problem | Resolution |
|---|---|
| `javac` is not recognized | Install a full JDK and ensure its `bin` directory is on `PATH` |
| PowerShell blocks a downloaded script | Review it and follow the machine's approved script policy; managed policies may require administrator assistance |
| Dependency download fails | Check access to `repo.maven.apache.org`, then rerun `build.ps1` |
| Dependency checksum mismatch | Replace the affected local JAR with the exact dependency obtained by the build script; do not ignore the mismatch |
| Port is already occupied | Stop the other process or change `PORT` and the frontend API URL |
| Database is locked | Stop the other instance using the same `DB_PATH` |
| Database access fails | Check write permissions, free disk space, and the configured path |
| History appears empty after restarting | Check that the working directory and `DB_PATH` still select the original file |
| Browser requests fail although health succeeds | Check `CORS_ORIGINS`, the frontend URL, and HTTP/HTTPS consistency |
| Unknown conversion endpoint | Conversion functionality was removed; use `/api/calculate` for supported scientific expressions |

When started with `run.ps1`, logs appear in that terminal. The combined launcher instead writes logs under its root `.runtime/` directory. Stop the service before changing dependency versions or performing file-level database maintenance.

For public deployment, configure a suitable interface binding, HTTPS through a hosting platform or reverse proxy, the actual frontend origin, and a remote PostgreSQL database for durable cloud history. The frontend API URL must be reachable by visitors. The verified public service addresses are listed at the beginning of this README.

History is shared: there are no accounts or per-user access controls. A deployment requiring private history must add authentication and authorization. A permitted client can clear the shared database history. Stop/restart persistence is tested; it does not guarantee recovery from every hardware or power failure.

Follow [the backend coding conventions](codestyle.md). Publish `src/`, the build/start scripts, `pom.xml`, `README.md`, and `codestyle.md`; exclude generated `target/`, downloaded `lib/`, local `data/`, and logs. The build recreates generated files from source.
