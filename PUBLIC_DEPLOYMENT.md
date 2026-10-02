# Free public deployment of David's calculator

Deploy the static frontend and Java backend separately on Render, and store cloud history in Neon PostgreSQL. Select Free plans only. No Render disk is needed. Local startup continues to use embedded H2. Publishing source on GitHub does not itself run the application.

## Neon database

Use the Calculator project on the Neon Free plan. Obtain the database name, role, password, and pooled endpoint from Connect. Do not reset the password or publish credentials. The backend initializes its table automatically. Local H2 records are not automatically copied into PostgreSQL.

## Render backend

Create a Free Docker Web Service from `WuMaoping-David/davids-calculator-backend`, branch `main`, with the root Dockerfile. Leave the Docker command unset and set health check path `/api/health`. The image uses Java 17 and binds to `0.0.0.0`; Render supplies `PORT`.

| Backend environment variable | Value |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://YOUR_NEON_HOST/neondb?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&connectTimeout=15&socketTimeout=30` |
| `DATABASE_USER` | The role shown by Neon |
| `DATABASE_PASSWORD` | The role password, saved only in the backend environment |
| `REQUIRE_REMOTE_DATABASE` | `true`, preventing accidental fallback to ephemeral H2 |
| `CORS_ORIGINS` | The exact frontend HTTPS origin, without a trailing slash |

Replace the host and database name with actual values. A raw `postgresql://` URI is not a JDBC URL. Keep credentials out of GitHub, frontend files, screenshots, and the blog. TLS verifies the server certificate and hostname using the Java trust store. Before the frontend address exists, set CORS to `https://example.invalid`. Deploy and record the actual backend origin. The database role must be allowed to create and modify the application table. History lives in Neon independently of Render restarts and redeploys.

## Render frontend

Create a free Static Site from `WuMaoping-David/davids-calculator-frontend`, branch `main`. Set build command `node build-render.mjs`, publish directory `dist`, and `API_BASE_URL` to the backend HTTPS origin, without `/api` or another path. The build generates production `config.js` with a 120-second timeout. Local configuration remains unchanged with a ten-second timeout. No npm dependencies are needed.

Update backend `CORS_ORIGINS` to the actual frontend origin and apply the change. Visitors open the frontend URL rather than the backend root or localhost.

## Verification and operation

Check `/api/health`, including database access. Verify decimals, parentheses, DEG/RAD functions, and English errors. Refresh and confirm history remains. Delete only disposable demonstration entries. Save an entry, restart the backend, and verify its ID, expression, result, and parameters persist in PostgreSQL. Report cloud verification separately from local H2 tests.

Free services have usage quotas and may suspend when idle. The first request can take longer while services wake. A timeout does not prove a calculation was not saved: refresh history before retrying. Check current quotas in provider dashboards before grading; do not select paid upgrades or disks without approval.

History is shared, with no user accounts. All visitors can read and delete records. CORS is not authentication. Use non-private assignment expressions.

## Troubleshooting

| Symptom | Action |
|---|---|
| Offline after an idle period | Allow the backend to wake and retry the health check |
| Backend startup fails | Check JDBC URL, role/password, TLS, and Neon availability in Render logs |
| Healthy API but browser requests fail | Match `API_BASE_URL` and the exact CORS origin |
| History missing after deployment | Confirm remote database settings; do not use ephemeral H2 |
| Free quota warning | Inspect usage and applicable resets; do not upgrade automatically |

## Official documentation

- [Render Docker deployments](https://render.com/docs/docker)
- [Render static sites](https://render.com/docs/static-sites)
- [Render free services](https://render.com/docs/free)
- [Neon connections](https://neon.com/docs/connect/connect-from-any-app)
- [PostgreSQL JDBC TLS](https://jdbc.postgresql.org/documentation/ssl/)
