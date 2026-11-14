# WT-001: coverage evidence and decision boundary

Tracking: https://github.com/choru90/ai-company/issues/16

The goal is raw JaCoCo LINE and BRANCH 100%. No production exclusions or custom
JaCoCo filters have been applied. A passing workflow does **not** certify 100%.
`scripts/coverage_audit.py --require-100` fails whenever raw misses remain.

## Reproduction

```sh
# Coverage workflow provisions an isolated MySQL on this fixed local port.
export WORKTIME_MYSQL_MIGRATION_TEST_URL='jdbc:mysql://127.0.0.1:13307/worktime_migration_test?serverTimezone=UTC'
./gradlew test jacocoTestReport --no-daemon
python3 scripts/coverage_audit.py
python3 scripts/coverage_inventory.py
python3 scripts/test_results_audit.py
python3 scripts/coverage_audit.py --require-100
```

The MySQL integration tests refuse other hosts/ports/schema names. The synthetic
root account has an empty password only inside the ephemeral CI service. No
production credentials are used. Coverage CI runs all opt-in migration tests and
fails its evidence audit on absent suites, failures, errors, skipped tests, or a
testcase count mismatch. The normal backend CI separately runs MySQL schedule and
work-log correction concurrency acceptance and the Flyway application startup.

Artifacts contain raw XML, HTML, JUnit XML, the complete uncovered-class audit,
and JSON with **every** package/class/method counter and source lines containing
missed instructions or branches. A partially covered source line is retained in
the JSON even when JaCoCo's LINE counter treats that line as covered.

## Independent review

Independent review checked arithmetic, timezone boundaries, ownership checks,
real hashing/JWT, transaction fixtures, and the XML evidence. Its findings led to:

- A shift at `2024-03-01T00:30+09:00` whose UTC date is February 29, so wrong date
  normalization changes the asserted wage.
- Exact generated dates/workers/statuses instead of positive-count assertions.
- Correct documentation that `EntityManager.flush()` is **not** a commit.
- Regression tests that first reproduced HTTP 200 for another owner's private
  store and wage records where HTTP 404 was required.
- Store, shift, and wage ownership checks, plus worker-account wage read/write
  compatibility tests. Legitimate linked workers retain read access; mutations
  require OWNER. A contract must belong to the worker in its path.

Mock repositories are limited to service unit-test boundaries. API tests use real
Spring method security, JWT and JPA. Migration tests use real MySQL. Test-only
subclasses/reflection do not replace production business methods; reflection in
the action sorting fixture sets equal creation timestamps for a tie case.

## Proven obstacles to honest raw 100%

These observations are **not approved exclusions**. Keep the raw denominator.

1. `BusinessHoursService.setBusinessHours` checks
   `!isClosed || (openTime.isBefore(closeTime) || isClosed)`. With immutable `C`,
   `!C || (T || C)` is true for all four `(C,T)` combinations. The require failure
   and one short-circuit outcome are unreachable. The intended rule needs a CEO
   decision: allow arbitrary times on closed days and require start < end on open
   days, or preserve current behavior and explicitly review the scope exception.
   Correcting that rule changes accepted business input; it has not been changed
   merely to improve coverage. The adjacent `business-hours-rule-proposal.patch`
   is an unapplied review proposal, not a production change.
2. `ScheduleUpdateRequest` rejects non-increasing times in its constructor.
   The same failure condition in `ScheduleService.updateScheduleTime` cannot be
   reached by a valid request. `confirmSchedule` checks the pending status before
   calling `Schedule.confirm()` under the worker lock; its matching domain-failure
   catch cannot be reached without violating those preconditions.
3. Business exceptions in this repository take nonnull messages, and domain
   `require` failures supply fixed messages. Corresponding `message ?: fallback`
   null outcomes in the error handlers/services cannot be reached by these real
   exception paths. No mock was made to invent a null message.
4. `WorktimeAccess.missing(): Nothing` always throws ResourceNotFoundException.
   Kotlin generates `KotlinNothingValueException` instructions after its calls;
   those instructions are unreachable. Nullable navigation through nonnull IDs
   also creates outcomes outside the domain model contract. Exceptions are
   asserted, but a JaCoCo probe after an always-throwing call cannot record a
   fictitious normal return.
5. Dashboard grouping and mapping guarantee nonempty groups and an existing
   contracts-map entry for each source worker. Ranking cannot have a first item
   while its sorted source is empty. Pending actions currently all have priority
   10, so their comparator cannot observe a priority difference. Completed-log
   persistence queries guarantee clock-out is nonnull; inventing a contradictory
   repository result is not a production integration test.
6. `DashboardDateRange.kt` contains a private extension named
   `ZonedDateTime.toOffsetDateTime`, shadowed by the JDK member. Public calls resolve
   to the JDK method. It has no caller. It was not invoked reflectively just to
   change a counter.
7. The output-port default `WorkerRepository.lockForWrite` and
   `ScheduleRepository.refreshForWrite` bodies are no-ops. The real Spring adapters
   override them and are tested, including real concurrency CI. A fake adapter
   executing an empty body supplies no meaningful behavioral assertion.

Inspect bytecode without installing another JDK:

```sh
java -m jdk.jdeps/com.sun.tools.javap.Main -c -p -l \
  build/classes/kotlin/main/com/worktime/application/service/WorktimeAccess.class
java -m jdk.jdeps/com.sun.tools.javap.Main -c -p -l \
  build/classes/kotlin/main/com/worktime/application/service/BusinessHoursService.class
```

Removing unused code, changing business rules, or approving measurement
exceptions are separate decisions. No entire business, authorization, API or
infrastructure class should be silently excluded. No adjusted 100% is reported.
The tracking issue records exact measured totals, remaining misses, CI/artifact
links and the continuation point. Merge and deployment still require CEO approval.
