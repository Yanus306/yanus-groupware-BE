#!/usr/bin/env bash
set -euo pipefail
[[ $(id -u) == 0 ]] || { echo 'Run as root' >&2; exit 1; }
[[ $(uname -m) == x86_64 ]] || { echo 'This pinned release requires amd64' >&2; exit 1; }
source_directory=$(cd -- "$(dirname -- "$0")" && pwd)
[[ -s /etc/alertmanager/slack-webhook ]] || { echo 'Protected Slack webhook file required' >&2; exit 1; }
[[ $(stat -c %a /etc/alertmanager/slack-webhook) == 640 && $(stat -c %U /etc/alertmanager/slack-webhook) == root ]] || { echo 'Webhook requires root ownership and mode 0640' >&2; exit 1; }
id alertmanager >/dev/null 2>&1 || useradd --system --home /var/lib/alertmanager --shell /usr/sbin/nologin alertmanager
chgrp alertmanager /etc/alertmanager/slack-webhook
install -d -o root -g alertmanager -m 0750 /etc/alertmanager
install -d -o alertmanager -g alertmanager -m 0750 /var/lib/alertmanager
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT
curl --fail --silent --show-error --location --connect-timeout 10 --max-time 120 https://github.com/prometheus/alertmanager/releases/download/v0.28.1/alertmanager-0.28.1.linux-amd64.tar.gz -o "$temporary/release.tar.gz"
(cd "$temporary" && printf '5ac7ab5e4b8ee5ce4d8fb0988f9cb275efcc3f181b4b408179fafee121693311  release.tar.gz\n' | sha256sum -c -)
tar -xzf "$temporary/release.tar.gz" -C "$temporary"
binary_directory="$temporary/alertmanager-0.28.1.linux-amd64"
"$binary_directory/amtool" check-config "$source_directory/alertmanager.yml"
"$binary_directory/amtool" template render --template.glob="$source_directory/yanus-slack.tmpl" --template.text='{{ template "yanus.title" . }}' >/dev/null
backup="/var/backups/yanus-alertmanager/$(date -u +%Y%m%dT%H%M%SZ)"
install -d -m 0700 "$backup"
had_service=0
systemctl is-active --quiet alertmanager && had_service=1
for file in /etc/alertmanager/alertmanager.yml /etc/alertmanager/yanus-slack.tmpl /etc/systemd/system/alertmanager.service /usr/local/bin/alertmanager /usr/local/bin/amtool; do
  [[ ! -e $file ]] || cp --parents -a "$file" "$backup"
done
rollback() {
  trap - ERR
  systemctl stop alertmanager || true
  for file in /etc/alertmanager/alertmanager.yml /etc/alertmanager/yanus-slack.tmpl /etc/systemd/system/alertmanager.service /usr/local/bin/alertmanager /usr/local/bin/amtool; do
    if [[ -e $backup$file ]]; then cp -a "$backup$file" "$file"; else rm -f -- "$file"; fi
  done
  systemctl daemon-reload
  if (( had_service )); then systemctl start alertmanager; else systemctl disable alertmanager || true; fi
  echo 'Alertmanager activation failed; previous files restored' >&2
  exit 1
}
trap rollback ERR
install -m 0755 "$binary_directory/alertmanager" /usr/local/bin/alertmanager
install -m 0755 "$binary_directory/amtool" /usr/local/bin/amtool
install -o root -g alertmanager -m 0640 "$source_directory/alertmanager.yml" /etc/alertmanager/alertmanager.yml
install -o root -g alertmanager -m 0640 "$source_directory/yanus-slack.tmpl" /etc/alertmanager/yanus-slack.tmpl
install -m 0644 "$source_directory/alertmanager.service" /etc/systemd/system/alertmanager.service
amtool check-config /etc/alertmanager/alertmanager.yml
systemctl daemon-reload
systemctl enable --now alertmanager
systemctl restart alertmanager
curl --fail --silent --show-error --max-time 10 --retry 3 --retry-connrefused http://127.0.0.1:9093/-/ready
trap - ERR
echo "Alertmanager verified; rollback snapshot: $backup"
