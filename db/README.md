# db/

CNPG Postgres **+ PostGIS** Cluster, one per environment (`db/dev`, `db/prod`),
provisioned by `.github/workflows/db.yml`. CNPG creates the database
`std_dive_logger` owned by role `std_dive_logger`, generates Secret
`std-dive-logger-db-app`, and (via `postInitApplicationSQL`) pre-creates the
`postgis`, `pg_trgm` and `fuzzystrmatch` extensions.

## Replaying the legacy DB

Dump the legacy PostGIS DB (public schema only — skips the `tiger` / `topology`
geocoder cruft the image auto-installed; `spatial_ref_sys` data comes free with
the fresh postgis extension):

```bash
ssh strato "sudo docker exec std-dive-logger-std_dive_logger_postgresql_db_dev-1 \
  pg_dump -U std_dive_logger -d std_dive_logger -n public \
  --no-owner --no-privileges --exclude-table-data=spatial_ref_sys" \
  | gzip > ~/db_dumps/std-dive-logger-dev.sql.gz
```

Restore **with `SET ROLE`** so every object is owned by `std_dive_logger`, not
the superuser (otherwise the app gets `permission denied` on its own tables):

```bash
{ echo "SET ROLE std_dive_logger;"; gunzip -c ~/db_dumps/std-dive-logger-dev.sql.gz; } \
  | ssh strato "sudo k3s kubectl -n std-dive-logger-dev exec -i std-dive-logger-db-1 -- \
      psql -U postgres -d std_dive_logger -v ON_ERROR_STOP=0"
```

`CREATE EXTENSION IF NOT EXISTS` lines in the dump no-op (already created by
`postInitApplicationSQL`). Verify:

```bash
ssh strato "sudo k3s kubectl -n std-dive-logger-dev exec std-dive-logger-db-1 -- \
  psql -U postgres -d std_dive_logger -c \
  \"SELECT tableowner, count(*) FROM pg_tables WHERE schemaname='public' GROUP BY 1\""
# -> std_dive_logger | <n>
```

If a restore was already done as the superuser, re-own with the loop in the
migration hand-off (skip linked SERIAL sequences; ALTER TABLE cascades to
indexes/TOAST/owned sequences).
