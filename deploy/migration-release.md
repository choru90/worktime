# V5 → V7 migration/controller candidate

See [expected bootstrap contract diff and required administrator capabilities](bootstrap-contract.patch.txt). This is a structural review aid, not a generated or installed host candidate. Actual deployment also requires a per-writer inventory and stopped/disabled/restart-inhibited evidence from every external writer owner; the profile boolean alone does not supply that evidence.

This PR implements the reviewed item17 execution plan. It does not authorize its own merge, install a controller, mutate live DB/manifest, deploy, or change GitHub environment/SSH approval rules. Push/PR runs tests only. Historical V4→V5 reconciliation must not be repurposed.

## Separate trust/bootstrap approval

The controller is managed outside this repo. No automated trusted bootstrap path was found. After PR merge **and separate confirmation of the expanded controller trust boundary**, an existing authorized host administrator must install the reviewed candidate. If that admin path is unavailable, remain blocked; never use an uploaded release artifact to overwrite trusted code.

`bootstrap_migration.py` accepts only original SHA256 `5ffbf53db65ada79f002f1d825c403870cca047179ea0dd7a57b1b63c126a6ae` and the inspected AST contract. On an approved private review machine/host:

```
python3 deploy/bootstrap_migration.py --controller <private-original-copy> --output <unused-private-candidate-path>
```

This command never imports, executes, installs or overwrites the controller. It emits a review candidate/hash. Review its complete diff privately: guard immediately after the original exclusive `deploy.lock`; prepare immediately before the **unchanged** fingerprint comparison; completion only after normal smoke and manifest activation. Exact-main/artifact validation, original backup/activation/rollback remain. Actual generation against the pinned server file remains an operator preflight; representative fixture tests do not prove the server's unredacted literal contract.

Under the existing lock, the administrator must verify ownership/permissions of `/opt/worktime` and every parent, retain private verified backups of original controller/manifest/versions/config, and install candidate plus `migration_release.py`, `migration_runtime.py`, `migration_resume.py` as root-owned private regular files (`0600`, single hard link). Preserve the existing lock inode. No installation workflow is supplied or run by this PR. Create root-owned `0600` `migration-plan.json` from the example after verifying installed code hashes.

The plan pins exact **post-merge main** SHA, archive/migration/image digests, old backend and baseline, patched controller/modules, source UUID/database, all writer IDs, independent restore MySQL UUID/container and two fresh empty schemas. The pre-PR main artifact lacks MigrationCli and must not be substituted. An existing deployment approval does not approve this bootstrap or new PR merge.

## Required environment, never inferred/provisioned

Review all local/remote/external writers and maintenance authorization. Disable writer auto-restart through the reviewed maintenance procedure before migration; adapter requires restart policy `no` and the exact old image. It rejects additional local backend containers. `external_writers_frozen=true` is an explicit operator assertion, not proof that Docker inventory sees remote writers. Independent restore MySQL/schema creation, storage/retention, real restore capacity/time and DB privileges are prerequisites, not automatic provisioning.

Root-owned `0600` credential files directly under the trusted root contain the existing approved credentials, never values in Git/Actions/chat:

```
WORKTIME_MIGRATION_URL=jdbc:mysql://127.0.0.1:3306/<approved-schema>?serverTimezone=UTC&characterEncoding=UTF-8
WORKTIME_MIGRATION_USER=<existing-user>
WORKTIME_MIGRATION_PASSWORD=<existing-secret>
WORKTIME_MIGRATION_SERVER_UUID=<approved-server-uuid>
WORKTIME_MIGRATION_DATABASE=<approved-schema>
```

Source, restore-V5 and restore-V7 each have a separately pinned file name/hash. Docker runs the exact image ID with `--network container:<approved-MySQL-ID>`, no port publication and the migration-only PropertiesLauncher main. JDBC verifies UUID/database **before DDL**; no Spring/HTTP/JPA/push worker starts. Backup/restore resolves root credentials inside approved MySQL containers from existing `MYSQL_ROOT_PASSWORD_FILE` or `MYSQL_ROOT_PASSWORD`, never exports them. State/dumps/snapshots contain customer values, remain private `0600` and must not become workflow artifacts.

## Ordered execution under the existing flock

Normal exact-main/archive validation precedes the hook. Unchanged migration-fingerprint releases need no migration plan. A changed-fingerprint backend must match the trusted plan and installed code digests.

1. Verify source UUID/database and exact applied V1–V5 checksums against this artifact's resolved migrations. Refuse unknown/failed/additional history. Write/fsync the migration journal before freezing every reviewed writer; recheck inventory and source after freeze.
2. Dump InnoDB using single-transaction/quick/hex-blob/routines/triggers/events, GTID OFF/no-tablespaces/column-statistics0. Never overwrite a prior backup. Verify bytes/hash, restore into the approved **separate MySQL instance's empty V5 schema**, compare complete history/columns/indexes/business snapshot (normalizing only expected target identity), recheck source unchanged.
3. Persist `migrating6` before DDL, apply target6 using the packaged project Flyway, verify original business values/schema and exact history, empty push tables/index/FK contract, persist `verified6`. Repeat target7 and `verified7`, checking nullable varchar500 memo and existing schedule memoNULL. No clean/repair/baseline/checksum alteration/ignore migration override. MySQL DDL is never assumed transactional.
4. Prove V7 dump/restore in the second approved empty restore schema. Recheck frozen writers, source and manifest. Atomically advance **only** the approved fingerprint, preserve runtime image/other fields, persist `approved`. A crash between approval/checkpoint writes intentionally requires review.
5. Return to the original fingerprint comparison, then original controller backup/load/activate/smoke/rollback. Only healthy target manifest activation archives the private migration receipt and removes its blocker. The original controller journal still protects activation. No nested flock or lock handoff gap.

All failures stop downstream deployment and retain a durable blocker. Both backend and web are blocked while migration is pending. Normal image rollback does not remove migration evidence or roll schema down; it may restart writers, so review any writes and re-establish freeze before a resume.

## Explicit resume/recovery

Only `verified6`, `verified7`, `approved` permit an explicit **identical-plan, exact-target** resume: identity/history/full snapshot, original V5 proof/backup hash, unchanged manifest, frozen writers must match; approved also needs V7 proof. Existing exact-current-main rule still applies. From the installed trusted directory, after explicit resume authorization:

```
python3 /opt/worktime/migration_resume.py --target-sha <same-approved-sha> --acknowledgement reviewed-frozen-exact-target-resume
```

The acknowledgement prevents accidents and is not authorization. It calls the original deploy() lock instead of holding a second lock descriptor. `freezing`, `verified5`, `migrating6`, `migrating7`, partial restore, checksum/row drift or ambiguous DDL require operator recovery review and refuse automatically. Do not delete/reset journals, relabel phases, overwrite backups, repair Flyway, wipe populated restore targets or set manifest hashes to bypass refusal. Changing plans/code or resolving the controller's existing deployment journal is a separate reviewed recovery operation.

Prefer forward fix or compatible old image after writes resume. Restore V5 only under separately reviewed recovery while writers remain frozen and post-backup writes cannot be lost; no automatic schema downgrade/destructive restore. Reinstalling the original controller is not an automatic fallback.

## Evidence and remaining preflights

Python tests cover state/identity/checksum/backup failure/resume/bootstrap guard contracts. `gradlew test bootJar migrationReleaseTestClasspath` packages the migration CLI and test-only V5 fixture. `test_release_mysql.py` requires explicit loopback test port/password and fresh `worktime_release_test_*` schemas, refuses existing schemas and never cleans DBs. Existing staging CI now runs these actual MySQL dump/restore/corrupt-restore/wrong-identity/checksum-drift/checkpoint-resume cases before deployment is eligible.

Actual local MySQL uses the production coordinator and packaged CLI with a local adapter modelling synthetic writer freeze. This does not prove production Docker transport, all remote writers, real secrets/permissions, restore scale/time, or bootstrap. Snapshots are memory-based with one-million-row-per-table/256MiB output budgets; review actual capacity before use and refuse/extend through reviewed change if exceeded.

After all backend instances match, verify synthetic memo save/fresh read/acceptance; only then deploy web and actual browser smoke. Pre-deploy read-only smoke is not post-deploy proof. App/store/APNs paths are separate.

References: [MySQL8 dump](https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html), [Spring Boot3.2 PropertiesLauncher](https://docs.spring.io/spring-boot/docs/3.2.0/reference/html/executable-jar.html#appendix.executable-jar.property-launcher), [Flyway migrate](https://documentation.red-gate.com/fd/migrate-277578887.html). Uses project's pinned libraries, no dependency upgrade.
