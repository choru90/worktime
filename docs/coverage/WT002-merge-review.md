# Coverage follow-up and merge review

The user authorized coverage remediation and merging on 2026-11-14. Deployment
and coverage exclusions were not authorized. Load/concurrency campaign expansion
and Android implementation are deferred. Existing regressions remain enabled.

## Safety before merging

Both repositories previously deployed staging automatically on main pushes.
Independent guard PRs change deployment packaging/upload/job to explicit
`workflow_dispatch` on main with `deploy_staging: true` (default false). CI still
runs for ordinary pushes/PRs. Guard changes are merged before functional PRs and
are explicitly synchronized into the pending branches; main push deployment must
remain skipped. No production/staging runtime is changed as part of this work.

## Actual correctness and maintainability fixes

- Bulk business-hours replacement used `!closed || before || closed`, an always
  true expression. Use the existing single-day `BusinessHours.updateHours` rule:
  closed days accept display times, open days require start < end. Validate the
  entire list before deleting persisted hours. Eight real JPA integration cases
  cover open/closed x increasing/equal/reversed, explicit empty replacement and
  repeated replacement of the same weekday.
- The same-weekday replacement test first reproduced a unique-key violation.
  Flush deletion before insertion to preserve the store/day uniqueness constraint.
  Invalid input keeps the previously stored configuration.
- Delete the unused private ZonedDateTime extension shadowed by the JDK method.
  No caller/behavior changes. Require repository locking/refresh methods as
  abstract interface contracts; both real JPA adapters already implement them.
  This removes unsafe silent no-op defaults rather than inventing dummy tests.
- Remove the duplicate time-order guard in ScheduleService because immutable
  ScheduleUpdateRequest already enforces it in its constructor. Constructor/API
  rejection and domain checks remain tested; no authorization check is removed.

These are concrete bug fixes and dead-code/contract cleanup, not JaCoCo excludes
or blanket filters. The original WT-001 report and unapplied proposal remain
historical evidence; the business-hours proposal is now incorporated into the
current implementation by this follow-up.

## Honest residual boundary

The earlier 9 missed lines/40 branches included these reachable/unused issues.
Remaining misses are retained in the raw XML/inventory. They include the
ScheduleService and WorkLogService catch paths whose validated domain operations
cannot throw under their locked preconditions, exception nonnull fallback paths,
Kotlin Nothing instructions after an always-throwing authorization helper,
nonempty group/map invariants and the migration's nonnull safe navigation.
No mock violates these contracts merely to make a counter green. Existing domain
validation/defensive exception mapping is preserved. Raw 100% is not certified.

Full JUnit suite now requires >=274 cases, including eight business-hours
replacement tests, the existing 14 retirement cases and payroll/correction suites.
Measured CI results and merge SHAs are recorded in choru90/ai-company#17.
