# Public deployment of David's calculator

The frontend and Java backend are deployed independently. Keep a persistent disk for the H2 database. A public source repository alone is not a running service.

## Backend on Render

Create a Web Service from the backend repository. Select Docker with `Dockerfile` at the repository root. Leave the Docker command unset. Set the health check path to `/api/health`.

Choose a paid service that supports persistent disks. Review the actual recurring service and storage price in the dashboard before creating it. Mount a persistent disk at `/var/data` and set `DB_PATH=/var/data/calculator`. The Dockerfile sets `BIND_ADDRESS=0.0.0.0`; Render supplies `PORT`.

Set `CORS_ORIGINS` to the exact frontend HTTPS origin once the static site address is known. Do not include a trailing slash. Before that address exists, use `https://example.invalid` to avoid allowing an unintended browser origin. Copy the actual backend HTTPS origin after the deploy is live.

## Frontend on Render

Create a Static Site from the frontend repository. Use `node build-render.mjs` as the build command and `dist` as the publish directory. Set `API_BASE_URL` to the actual backend HTTPS origin, without `/api` or another path. The build generates production `config.js`; the local configuration remains unchanged.

Set the backend `CORS_ORIGINS` to the actual static-site origin and redeploy the backend. Open the frontend HTTPS URL on another network.

## Verification

Check the public backend `/api/health`. Calculate `0.1+0.2` and `(2+3)*4`, then check scientific DEG/RAD functions. Reload the frontend and confirm history remains. Delete one disposable entry. Clear only disposable demonstration history. Save another entry, restart the backend, and confirm its ID, expression, result, and parameters persist. Keep the service available throughout grading.

This release has shared history rather than separate user accounts. All visitors see the same saved records and can use the history deletion features. Do not enter private information in expressions.

## Publication record

Record actual verified repository and service URLs here after publication. Do not represent example or planned URLs as working services. Update the assignment blog with those verified URLs and deployment screenshots.

## Official documentation

- https://render.com/docs/docker
- https://render.com/docs/static-sites
- https://render.com/docs/disks
