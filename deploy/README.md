# deploy/ + db/

K3s manifests for the std-dive-logger backend. Self-contained — `homelab-infra`
only supplies the shared `app-deployer` identity (`KUBE_TOKEN`).

| | dev | prod |
|---|---|---|
| namespace | `std-dive-logger-dev` | `std-dive-logger-prod` |
| ws | `std-dive-logger-dev.sthomas.ch` | `std-dive-logger.sthomas.ch` |
| import-ws | `importer.std-dive-logger-dev.sthomas.ch` | `importer.std-dive-logger.sthomas.ch` |
| autocomplete | `autocomplete.std-dive-logger-dev.sthomas.ch` | `autocomplete.std-dive-logger.sthomas.ch` |
| analytics | internal only (no route) | internal only |
| frontend | `std-dive-logger-web-dev.sthomas.ch` (separate repo) | `std-dive-logger-web.sthomas.ch` |

## Config model

The jib images set `SPRING_CONFIG_LOCATION=/config/` but do **not** bake a config
file (the `-Dconfig.*` build args are dead — nothing in the poms reads them).
Legacy docker-compose mounted `config/<svc>/application.properties`; here that
file is a **ConfigMap** (`deploy/base/config/<svc>.properties`, copied verbatim
from `std-dive-logger-<svc>/conf/dev/…-dev.properties`) mounted at
`/config/application.properties`. Re-copy those when the `conf/dev` files change.

The mounted file is fully templatized — every env-specific / secret value is
`${...}`, resolved from **environment variables** at container start:

- DB: `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` ← CNPG's `std-dive-logger-db-app`
- secrets: `std-dive-logger-secrets` (kube-secret, from GH repo/env secrets) —
  JWT ×2, R2 account/access/secret, email password
- non-secret env-specific: `std-dive-logger-env` ConfigMap (per overlay) —
  frontend URL, CORS, R2 bucket + base-url, email address + host

The same image serves dev and prod — only the env-var values differ.

## Workflows

- **`db.yml`** (manual) — provisions the CNPG Postgres+PostGIS Cluster for one
  env. Run once per env before the first deploy. The legacy DB is then replayed
  from a `pg_dump -n public` (see the migration hand-off / repo history).
- **`deploy.yml`** — auto-deploys **dev** after *Build and push to Image
  Registry* succeeds for a `v*` tag reachable from `main`; manual dispatch
  (`target` + `tag`) for prod or a redeploy.
- **`mvn_docker_image.yml`** (existing) — builds the 4 Jib images on a `v*` tag.
