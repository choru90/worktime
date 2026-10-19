# MySQL V4 upgrade and acceptance

Scope: align the existing V1–V3 schema with the current application. V1–V3 are immutable. V4 is a Java-based Flyway migration discovered under `db.migration`; its checksum is fixed and must not be edited after application. Future corrections require V5.

## Before applying to existing data

Stop application writes and background jobs. Take a consistent schema/data backup and prove restoration in an isolated database first. Rehearse V4 against that restored copy, run Hibernate `validate`, compare IDs/row counts and sample payroll/time values. Schedule an approved maintenance window. This implementation has only been exercised on disposable local synthetic data; no deployment is included.

V4 checks enum values, duplicate worker accounts and wage start dates, wage numeric bounds and legacy schedule time conversion before issuing DDL. A schedule uses its store's timezone, then the worker's store timezone. When neither exists, review the records and explicitly provide `WORKTIME_LEGACY_SCHEDULE_TIMEZONE` with the verified IANA timezone. Never guess it. Invalid zones, ambiguous/nonexistent DST times, and schedules with end not after start require data review before retrying. Overnight legacy schedules require manual date clarification.

Existing wage effective dates become inclusive start dates; each contract ends the day before the next begins. The latest remains open. Legacy columns and records are retained; old required columns become nullable to permit current application writes. Legacy local schedule times are converted to UTC with microseconds retained. Old work-log TIMESTAMP values are read/copied under UTC. Hibernate JDBC timezone is explicitly UTC. Status aliases are mapped to the current domain values.

MySQL DDL can commit partially. On any migration failure, keep writes stopped, inspect history and schema and use the rehearsed restore procedure. Do not blindly run Flyway repair, rerun partially applied DDL, remove history rows, or drop legacy columns. Rollback is restoration of the verified backup with its compatible application version.

## Local checks

The opt-in `MysqlMigrationIntegrationTest` cleans a disposable schema before and after each test. It requires `WORKTIME_MYSQL_MIGRATION_TEST_URL` starting exactly with `jdbc:mysql://127.0.0.1:13307/worktime_migration_test?`. It uses a dedicated local root account with a blank password; never use this configuration for a shared database. With that isolated instance running, run the normal Gradle test task with the environment variable set. The six checks cover empty schema, upgrade with preserved IDs/time values, missing timezone, DST ambiguity, explicit fallback and duplicate wage starts.

The real HTTP/browser acceptance harness is supplied in the review package. It creates synthetic owner, store, worker and wage data, invites and signs up a worker using the mobile shared API client, clocks in/out, and checks approval and payroll through the real server and MySQL. To avoid waiting four hours, it adjusts only the synthetic work-log start timestamp after real clock-out. Native Android/iOS UI, store packaging, push notifications and actual payroll payment are outside that test.

References: [Flyway Java migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/java-based-migrations), [MySQL datetime behavior](https://dev.mysql.com/doc/refman/8.4/en/datetime.html).
