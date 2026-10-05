#!/usr/bin/env bash
set -euo pipefail
[[ $(id -u) == 0 ]] || { echo 'Run reviewed helper installation as root' >&2; exit 1; }
source_root=$(cd -- "$(dirname -- "$0")/.." && pwd)
node --version
for command in flock psql unzip jq timeout; do command -v "$command" >/dev/null; done
node -e 'if (Number(process.versions.node.split(".")[0]) < 22) process.exit(1)'
[[ -f /etc/yanus-deploy/baseline.json ]] || { echo 'Review source/schema/compatibility and prepare protected baseline first' >&2; exit 1; }
install -d -o root -g root -m 0755 /usr/local/lib/yanus-deploy/deploy /usr/local/lib/yanus-deploy/observability
for file in deploy.sh create-manifest.mjs jar-metadata.mjs preflight.mjs verify-release.mjs verify-public.mjs notify.mjs yanus-native.conf; do
  install -o root -g root -m 0644 "$source_root/deploy/$file" "/usr/local/lib/yanus-deploy/deploy/$file"
done
chmod 0755 /usr/local/lib/yanus-deploy/deploy/deploy.sh
install -o root -g root -m 0644 "$source_root/observability/public-probe.mjs" /usr/local/lib/yanus-deploy/observability/public-probe.mjs
install -o root -g root -m 0644 "$source_root/observability/application-observability.properties" /usr/local/lib/yanus-deploy/observability/application-observability.properties
install -d -o yanus -g yanus -m 0750 /home/yanus/incoming
temporary=$(mktemp)
trap 'rm -f "$temporary"' EXIT
printf 'yanus ALL=(root) NOPASSWD: /usr/local/lib/yanus-deploy/deploy/deploy.sh /home/yanus/incoming/*\n' > "$temporary"
visudo -cf "$temporary"
install -o root -g root -m 0440 "$temporary" /etc/sudoers.d/yanus-deploy
echo 'Helper installed; it does not activate a candidate or enable Actions'
