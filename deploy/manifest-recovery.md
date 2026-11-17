# WT-002 staging manifest reconciliation — review candidate

This tool is NOT wired to a deployment job. A merge only runs the existing read-only inspection when its workflow changes. It does not run `--apply`. CEO approval for a diagnostic or a merge is not permission to change the manifest.

## Diagnosed state (2026-11-17)

Read-only evidence: [inspection run 44049822186](https://github.com/choru90/worktime/actions/runs/44049822186/job/133924528090).

- DB Flyway V1–V5 successful; two correction columns and correction table present.
- `manifest.json` still contains V4 migration fingerprint `bfb996c0c85bf871f029616e5d74c0a6fb7e14b1f59e8c176fff660929c48fda`.
- Running backend image: `5fe566ceb5a65a0a8d9bfa081500f8013031fa3d`.
- Incoming approved release `7466b0e35d9546731e024dd7b02e267701a04f19`: archive SHA256 `f560ae792f273ec4efab4ab4e0c10986acedb9d95c4943d4b83f5da655ee9017`, V5 migration fingerprint `c276840a0438395c0f32e9844dcda29137c03a194df7212bb48462d34a14316d`.
- Controller SHA256 `5ffbf53db65ada79f002f1d825c403870cca047179ea0dd7a57b1b63c126a6ae`.

## Required review before server execution

The controller's precise manifest comparison and actual deployment-wide lock have NOT yet been reviewed. The extended read-only inspection emits the redacted migration gate expression, lock calls and lock path literals. It does not export configuration documents or credentials. A path literal alone does not prove which lock is held; trace the controller entry point and the file descriptor passed to `flock` before choosing the lock.

Confirm that the single `migration_sha256` field really is the deployment-wide compatibility baseline, and that the controller validates incoming identity/checksum, backs up, verifies health and handles failures normally. Do not use this tool if the field is actually the schema of a retained rollback image: advancing it would incorrectly mark that image compatible. Do not infer this contract from a matching source hash or from the field name.

The current release is V5 already. This is a proposed correction of stale approval metadata, not permission to skip a migration gate. If controller review shows different semantics, revise the recovery implementation and tests before approval/execution.

## Proposed execution after review and explicit CEO authorization

1. Ensure no active deployment and identify the **existing controller lock file**. No guessed lock path or newly created lock is accepted.
2. Transfer the reviewed script through the existing authenticated deployment channel, without changing SSH trust or environment branch policies. Record the exact script commit/hash. Do not pass untrusted shell fragments or credentials as arguments.
3. Run `python3 reconcile_manifest.py --reviewed-lock-file /opt/worktime/ACTUAL_REVIEWED_LOCK` in validation mode first. `ACTUAL_REVIEWED_LOCK` is a placeholder, not a usable lock path.
4. Only after separate authorization for metadata reconciliation, use the same command with `--apply --acknowledgement WT-002-reconcile-only-reviewed-V5`. This literal acknowledgement is an accident-prevention switch, NOT evidence of CEO approval.
5. The script holds a nonblocking exclusive lock, validates exact controller/image/DB/artifact state, requires exactly one old approval field, and saves a private byte-for-byte manifest backup plus SHA256. It revalidates live state after backup, changes only the hash bytes with atomic replacement, preserves owner/mode, fsyncs and verifies the result. Other manifest fields, DB and controller remain untouched. Any changed precondition refuses execution. Second execution also refuses instead of treating a previous repair as a fresh success.
6. Verify the read-only inspection reports V5 baseline. **No image deployment happens in this script.** Run the normal controller only after its stale-release rules and exact target have been reviewed. `main` moved after diagnostic merges; do not silently substitute a different revision for approved backend `7466b0e...`. Confirm source/migration equality and disclose the actual target if a new artifact is required.
7. Verify actual running backend SHA and health before the already approved web `cb1657eba33aa288ea886ecc9486abb42ea76421` deployment. GitHub workflow success or `skipped-stale` alone is not release evidence.

## Failure and recovery

Before atomic replacement, failures preserve the manifest (a verified backup may remain). If filesystem syncing or post-write verification fails after replacement, treat the result as unknown and inspect the manifest and private backup while holding the same lock. Do not retry blindly or restore the V4 approval baseline without reviewing current DB and runtime state. This operation does not back up/restore application data because it does not modify it. The subsequent normal deployment must retain the controller's existing database backup and health/rollback procedure.

## Validation scope

Local synthetic tests cover dry-run/no-write, exact byte/owner/mode preservation, verified backup, missing acknowledgement, changed live state, busy lock, duplicate/ambiguous/already-V5 manifest, symlink, backup/atomic-replacement failure, repeated invocation, and live-precondition rejection. They do not demonstrate the unreviewed remote lock/manifest contract or server-side recovery. No physical Android test, production mutation test, new load campaign or coverage exclusion is included.
