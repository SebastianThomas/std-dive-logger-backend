# deploy/ + db/

K3s manifests for the std-dive-logger backend. Self-contained — `homelab-infra`
only supplies the shared `app-deployer` identity (`KUBE_TOKEN`).

| | dev | prod |
|---|---|---|
| namespace | `std-dive-logger-dev` | `std-dive-logger-prod` |
| ws | `std-dive-logger-dev.sthomas.ch` | `std-dive-logger.sthomas.ch` |
| import-ws | `std-dive-logger-importer-dev.sthomas.ch` | `std-dive-logger-importer.sthomas.ch` |
| autocomplete | `std-dive-logger-autocomplete-dev.sthomas.ch` | `std-dive-logger-autocomplete.sthomas.ch` |
| analytics | internal only (no route) | internal only |
| frontend | `std-dive-logger-web-dev.sthomas.ch` (separate repo) | `std-dive-logger-web.sthomas.ch` |

All hostnames are one label deep, so the cluster `*.sthomas.ch` wildcard cert
covers them — no per-service cert or acme-dns delegation. The dev overlay
patches the base (prod) hostnames; `analytics` and every Deployment also gets a
`wait-for-db` initContainer so a fresh namespace doesn't crash-loop while CNPG
initialises.

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
- `std-dive-logger-secrets` (kube-secret, from GH env secrets + variables) —
  JWT ×2, R2 account/access/secret, email password, VAPID public/private key +
  subject (web push; `ws` only gets the public key, `analytics` gets all three).
  Only the private key is a real GH *secret* (masked); the public key and
  subject aren't sensitive, so they're GH *variables* instead — still not
  committed anywhere, but visible/inspectable and rotatable without a release.
- non-secret env-specific: `std-dive-logger-env` ConfigMap (per overlay) —
  frontend URL, CORS, R2 bucket + base-url, email address + host

The same image serves dev and prod — only the env-var values differ. Most of
these are explicit `${VAR}` placeholders in the baked properties file; the
VAPID trio is the one exception — it relies on Spring's relaxed env-var
binding straight onto `ch.sthomas.stddivelogger.push.vapid.*` (env var
`CH_STHOMAS_STDDIVELOGGER_PUSH_VAPID_...`), same as the feign autocomplete URL
— so it needs no properties-file entry at all.

## Workflows

- **`db.yml`** (manual) — provisions the CNPG Postgres+PostGIS Cluster for one
  env. Run once per env before the first deploy. The legacy DB is then replayed
  from a `pg_dump -n public` (see the migration hand-off / repo history).
- **`deploy.yml`** — auto-deploys **dev** after *Build and push to Image
  Registry* succeeds for a `v*` tag reachable from `main`; manual dispatch
  (`target` + `tag`) for prod or a redeploy.
- **`mvn_docker_image.yml`** (existing) — builds the 4 Jib images on a `v*` tag.
