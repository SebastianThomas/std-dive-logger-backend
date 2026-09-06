# Test suites

`mvn test` (and the CI `install` build) runs the fast default suite: unit/parser
checks, PostgreSQL migrations, HTTP observability and the analytics dashboard.
The 39 other Spring application integration classes are tagged `slow` and are
disabled by default. They remain compiled, so API changes still surface.

Run the larger suite explicitly with `mvn -Pfull-tests test`. To run one opted-out
regression, use `mvn -Pfull-tests -pl <module> -am test -Dtest=<TestClass>
-Dsurefire.failIfNoSpecifiedTests=false` (as a single command).

Surefire uses one fork with a 1 GiB heap, caches at most two Spring contexts and
does not retry failures. The immutable timezone geometry is loaded once per JVM,
not once per Spring context. This addresses the repeated geometry allocations
seen in CI run 34040472933. The opt-in suite takes longer; its complete runtime
and memory behavior have not been remeasured as part of this change.
