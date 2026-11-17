# WT-002 staging manifest reconciliation — review candidate

The manual `reconcile-staging-manifest.yml` workflow defaults to validation only. Push, PR and merge events never execute this workflow. With separate CEO authorization, a main dispatch with `reconcile_manifest=true` performs validation followed by the reviewed correction. This workflow never deploys an image. A diagnostic or merge approval alone is not permission to enable the correction.

## Diagnosed state (2026-11-17)

Read-only evidence: [inspection run 44049822186](https://github.com/choru90/worktime/actions/runs/44049822186/job/133924528090).

- DB Flyway V1–V5 successful; two correction columns and correction table present.
- `manifest.json` still contains V4 migration fingerprint `bfb996c0c85bf871f029616e5d74c0a6fb7e14b1f59e8c176fff660929c48fda`.
- Running backend image: `5fe566ceb5a65a0a8d9bfa081500f8013031fa3d`.
- Incoming approved release `7466b0e35d9546731e024dd7b02e267701a04f19`: archive SHA256 `f560ae792f273ec4efab4ab4e0c10986acedb9d95c4943d4b83f5da655ee9017`, V5 migration fingerprint `c276840a0438395c0f32e9844dcda29137c03a194df7212bb48462d34a14316d`.
- Controller SHA256 `5ffbf53db65ada79f002f1d825c403870cca047179ea0dd7a57b1b63c126a6ae`.

## Controller review completed (PR #20)

[Read-only inspection 44079990138](https://github.com/choru90/worktime/actions/runs/44079990138/job/134027747745) verified the pinned controller's complete redacted control flow:

- `old` loads from `root / 'manifest.json'`. Its `backend` value matches running V5 image `5fe566...`; only the root migration fingerprint remains V4.
- `deploy` opens `root / 'deploy.lock'` and holds exclusive flock across state checks, backup, activation and manifest update. The repair uses that same existing lock.
- Incoming backend migration hash is compared with `old['migration_sha256']`. New manifest is copied from old, so the historical exceptional V5 activation left the old hash in place. Repairing this root field does not relabel an older recorded backend image: the recorded image equals the running V5 image, which is required again at execution.
- Normal deployment requires the requested SHA to equal the service repository's current main SHA. The older incoming `7466...` can validate historical evidence but will now be skipped as stale if passed to normal deployment. Use the newly approved main only after proving `src/main` and migrations equal approved `7466...`; do not report a skip as a deployment.
- An interrupted-deployment journal blocks normal deployment. The repair parses its exact static path from the pinned controller without executing it and refuses an existing journal, including a dangling symlink. Unknown path structure also refuses.
- Normal controller validates identity/archive, creates backup, loads image, updates versions, performs smoke checks, writes manifest and attempts rollback to old versions on error. The redacted trace establishes ordering and data flow; this is not a claim that backup restore/rollback has been tested.

## Proposed execution after review and explicit CEO authorization

1. Verify the pinned controller and exact diagnosed state remain unchanged; use the existing `/opt/worktime/deploy.lock`. No newly created lock is accepted.
2. Use the reviewed main-only manual recovery workflow. It streams the checked-out script over the existing strict SSH channel to `python3 -`, logs its SHA256 and does not install a script file on the server. Existing staging secrets/branch policy stay unchanged.
3. Dispatch with `reconcile_manifest=false` for validation only, or let an explicitly approved `true` dispatch run validation before correction. Both use `python3 - --reviewed-lock-file /opt/worktime/deploy.lock`. Runtime source/migrations must still match approved 7466...; unit tests run before SSH.
4. Only after separate authorization for metadata reconciliation, dispatch with `reconcile_manifest=true`; the workflow adds `--apply --acknowledgement WT-002-reconcile-only-reviewed-V5`. This literal acknowledgement is an accident-prevention switch, NOT evidence of CEO approval.
5. The script holds the same existing nonblocking exclusive lock, rejects interrupted deployment journals, requires the manifest backend to equal the reviewed running V5 image, validates exact controller/image/DB/artifact state, requires exactly one old approval field, and saves a private byte-for-byte manifest backup plus SHA256. It revalidates live state after backup, changes only the hash bytes with atomic replacement, preserves owner/mode, fsyncs and verifies the result. Other manifest fields, DB and controller remain untouched. Any changed precondition refuses execution. Second execution also refuses instead of treating a previous repair as a fresh success.
6. Verify the read-only inspection reports V5 baseline. **No image deployment happens in this script.** Run the normal controller only after its stale-release rules and exact target have been reviewed. `main` moved after diagnostic merges; do not silently substitute a different revision for approved backend `7466b0e...`. Confirm source/migration equality and disclose the actual target if a new artifact is required.
7. Verify actual running backend SHA and health before the already approved web `cb1657eba33aa288ea886ecc9486abb42ea76421` deployment. GitHub workflow success or `skipped-stale` alone is not release evidence.

## Failure and recovery

Before atomic replacement, failures preserve the manifest (a verified backup may remain). If filesystem syncing or post-write verification fails after replacement, treat the result as unknown and inspect the manifest and private backup while holding the same lock. Do not retry blindly or restore the V4 approval baseline without reviewing current DB and runtime state. This operation does not back up/restore application data because it does not modify it. The subsequent normal deployment must retain the controller's existing database backup and health/rollback procedure.

## Validation scope

Local synthetic tests cover dry-run/no-write, exact byte/owner/mode preservation, verified backup, missing acknowledgement, changed live state, busy lock, duplicate/ambiguous/already-V5 manifest, symlink, backup/atomic-replacement failure, repeated invocation, and live-precondition rejection. The remote lock/manifest contract was separately inspected as noted above. The new manual workflow and actual reconciliation remain NOT RUN until explicitly authorized; unit tests do not demonstrate server-side recovery. No physical Android test, production mutation test, new load campaign or coverage exclusion is included.
