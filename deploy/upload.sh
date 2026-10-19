#!/usr/bin/env bash
set -euo pipefail
[[ "$SERVICE" == backend || "$SERVICE" == web ]]
[[ "$GITHUB_SHA" =~ ^[a-f0-9]{40}$ ]]
[[ "$DEPLOY_HOST" =~ ^[a-zA-Z0-9.-]+$ ]]
[[ "$DEPLOY_USER" =~ ^[a-z_][a-z0-9_-]*$ ]]
[[ "$DEPLOY_PORT" =~ ^[0-9]+$ ]]
key_dir=$(mktemp -d)
trap 'rm -rf "$key_dir"' EXIT
chmod 700 "$key_dir"
printf '%s\n' "$SSH_PRIVATE_KEY" > "$key_dir/key"
printf '%s\n' "$SSH_KNOWN_HOSTS" > "$key_dir/known_hosts"
chmod 600 "$key_dir/key" "$key_dir/known_hosts"
options=(-i "$key_dir/key" -o BatchMode=yes -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$key_dir/known_hosts")
remote="$DEPLOY_USER@$DEPLOY_HOST"
path="/opt/worktime/incoming/$SERVICE/$GITHUB_SHA"
ssh "${options[@]}" -p "$DEPLOY_PORT" "$remote" "umask 077; mkdir -p '$path'"
scp "${options[@]}" -P "$DEPLOY_PORT" image.tar.gz metadata.json "$remote:$path/"
ssh "${options[@]}" -p "$DEPLOY_PORT" "$remote" "python3 /opt/worktime/deploy.py '$SERVICE' '$GITHUB_SHA'"
