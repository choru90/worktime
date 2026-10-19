# Staging deployment

Push/PR runs tests and image build. Only main push deploys to https://dev.work-time.co.kr after CI succeeds. No production deployment.

Server trust/config: /opt/worktime/deploy.py and compose.yml, deployment-wide flock, exact SHA artifact checksum, migration-change gate, backup, health verification, rollback and interrupted-deployment journal.

GitHub staging Variables: DEPLOY_HOST, DEPLOY_USER, DEPLOY_PORT. Secrets: SSH_PRIVATE_KEY, SSH_KNOWN_HOSTS. Never commit keys or runtime app/database secrets. No manual approval for normal staging main push. Actions use immutable commit pins, artifacts expire after one day.

Migration changes require a reviewed release. Both repos must preserve backward API compatibility. SSH network access and bootstrap are managed separately; do not provision them from this workflow. The initial server bootstrap must contain both accepted images and a manifest.
