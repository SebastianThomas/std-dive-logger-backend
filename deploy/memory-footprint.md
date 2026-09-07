# Service memory investigation

Read-only measurements from dev pods on 2026-09-06, before the memory changes:

| Service | Process RSS | Sampled used heap | Committed class metadata | Used compiled code | Threads |
| --- | ---: | ---: | ---: | ---: | ---: |
| autocomplete | 465 MiB | 117 MiB | 119 MiB | 27 MiB | 46 |
| import-ws | 466 MiB | 136 MiB | 120 MiB | 29 MiB | 47 |
| ws | 467 MiB | 147 MiB | 126 MiB | 29 MiB | 47 |

Measured using `/proc/1/status`, `jcmd 1 GC.heap_info`, `VM.metaspace basic` and
`Compiler.codecache`. Heap samples include uncollected garbage; these are not
post-GC live-set measurements. No forced GC, heap dumps or workload restarts were
performed. Native memory tracking was disabled, so the remaining RSS cannot be
precisely attributed. The large reserved class/code address spaces are not their
resident-memory usage. Kubernetes working-set samples were about 440–442 MiB.

## Changes

Autocomplete now scans only its own components, shared HTTP logging and exception
advice. Its four searches use bound JDBC queries; it no longer boots Hibernate,
the complete entity/repository model, write services or their dependency graph.
Pagination, fuzzy matching, site coordinates/water/timezone, and private-tag
visibility are covered by populated-database tests. Flyway, security, health and
Prometheus remain enabled. The larger libraries still exist on the transitive
classpath, but are no longer initialized as autocomplete's application stack.

WS/import-ws reuse a site's saved zone on reads. Previously every entity load
called the offline resolver, even for a populated `zone_id`, potentially loading
all world timezone geometry during a home/search request. Missing zones still
resolve, and writes recompute the zone so coordinate changes remain correct.
The shared index remains necessary when performing those lookups.

For WS, import-ws and autocomplete:

- Hikari retains one idle connection and retires excess idle connections after
  60 seconds; existing maximum pool sizes are preserved.
- Tomcat keeps two spare request threads; maximum request concurrency is unchanged.
- Java starts with a 32 MiB heap, uses compact object headers and G1, and allows
  periodic concurrent collection after two minutes without a collection. Lower
  free-heap ratios let it return unused committed heap after bursts. Periodic GC
  can add background CPU; evaluate request latency and GC time after deployment.
- Import-ws retains its 70% heap ceiling for parsing bursts; WS retains 60%, and
  autocomplete uses 60%. Existing container limits are retained in this change.

G1 periodic collection is intended for this idle-memory case; see the
[Java 25 G1 guide](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-first-g1-garbage-collector1.html).
These are source/manifest changes, not a measured post-deployment RSS reduction.

## Validation and follow-up measurement

Seven autocomplete integration cases and the timezone listener test passed in a
22-second targeted reactor run. The real UDDF import and HTTP observability
regressions passed in 27 seconds with G1 and compact headers enabled. Both
Kustomize overlays render; Java 25 accepts the resulting JVM options.

After deployment, compare idle working set, heap used/committed, non-heap memory,
loaded classes, thread count and Hikari idle connections in the existing Grafana
dashboard. Allow at least two minutes after startup/import traffic for the idle
collection. Compare request P95/P99 and GC time as well; do not lower container
limits solely on the basis of the new settings before observing import bursts.
