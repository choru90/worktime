# Staging deployment

Push/PR runs tests and image build. Deployment to https://dev.work-time.co.kr requires an explicitly approved manual staging.yml dispatch on main with deploy_staging=true after CI succeeds. Push and PR events never deploy. No production deployment.

Server trust/config: /opt/worktime/deploy.py and compose.yml, deployment-wide flock, exact SHA artifact checksum, migration-change gate, backup, health verification, rollback and interrupted-deployment journal.

GitHub staging Variables: DEPLOY_HOST, DEPLOY_USER, DEPLOY_PORT. Secrets: SSH_PRIVATE_KEY, SSH_KNOWN_HOSTS. Never commit keys or runtime app/database secrets. Do not infer deployment approval from a push or CI success. Actions use immutable commit pins, artifacts expire after one day.

Migration changes require a reviewed release. For the diagnosed WT-002 stale approval baseline, see [manifest reconciliation review and prerequisites](manifest-recovery.md); its recovery tool is not invoked automatically. Both repos must preserve backward API compatibility. SSH network access and bootstrap are managed separately; do not provision them from this workflow. The initial server bootstrap must contain both accepted images and a manifest.
