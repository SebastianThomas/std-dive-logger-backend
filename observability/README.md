# HTTP observability

All four apps share Zalando Logbook 4.1.0. The starter registers its servlet/security filters.
Each exchange produces a request event and a completion event. Completion levels are INFO for
1xx–3xx, WARN for 4xx, ERROR for 5xx. Fields include `log_type=http_access`, a generated
`correlation_id`, method, MVC route template, status, and `duration_ms`. Requests that abort before
a response still have a request event; Spring reports failures through its normal exception logs
and HTTP metrics. Spring records HTTP status/outcome and duration independently of logging.

## Safe defaults

Only declared-length JSON requests up to 2048 bytes are captured. Multipart, binary, compressed,
chunked, missing-length and larger bodies are not buffered. Previews only expose numeric/boolean
`page`, `size`, `count`, `limit`, `offset`, `enabled`, and `success`; strings, unknown fields,
nested objects and malformed JSON are omitted. No headers, cookies, query strings, client
addresses or raw path parameters are logged. Unmatched routes are `UNKNOWN`.

Response bodies are never buffered: Logbook decides whether to buffer before their size/type
is known. Native `max-body-size` truncates output after buffering, and
`body-only-if-status-at-least` still captures bodies. Neither protects streamed downloads from
buffering. The small custom strategy is retained for that reason. The custom sink supplies typed
status/duration and status-dependent levels, rather than Logbook's default TRACE output.
Do not use `DO_NOT_OBFUSCATE` overrides: those disable protection rather than enable it.
These restrictions cover access logs; application code must also avoid secrets in its own messages.

## Existing dashboard

The supplied `17175_spring_boot_observability_victorialogs.json` needs no metrics-panel changes.
All metrics have `application=${spring.application.name}`; deployments already set this to
`std-dive-logger-ws`, etc. HTTP timers publish `http_server_requests_seconds_bucket`, `_count`,
and `_sum` with `uri`, `status`, and `outcome`. Existing P95/P99 histogram_quantile queries and
`outcome="SERVER_ERROR"` / `status=~"5.*"` queries use these directly. Extra client-side percentile
series are unnecessary. The integration test verifies a real 503 response, its tags and buckets.

The two log panels still use Docker Compose's `compose_service` and an old text layout.
Kubernetes output is single-line Logstash JSON with `app_name`, `level`, `message`, `@timestamp`
and the access fields. Configure VictoriaLogs ingestion to parse JSON into fields, use `message`
as the message field and `@timestamp` as timestamp. Ingest stdout only, not the rotated file too.
Do not use correlation IDs as stream labels.

`existing-dashboard-log-panels.patch.json` is an RFC 6902 patch against the supplied dashboard
JSON, not the Grafana API wrapper. It changes only the two existing log-panel queries and level
legend to use `app_name` and `level`. Apply to that export and update the same dashboard, retaining
its live UID/data source selections. No new dashboard is needed. Both log panels continue showing
all Spring Boot apps. The keyword input can also filter `status:500` or `log_type:http_access`.

The live dashboard/collector were not changed: its API required authentication and browser access
was unavailable. Data appears after deployment, traffic and scrapes. The existing `[1m]` histogram
windows need at least two scrapes; with a scrape interval over 30s, use `$__rate_interval` instead.
