#!/usr/bin/env bash
set -Eeuo pipefail

runner_user="${1:-preflight-runner}"

fail() {
  echo "error: $*" >&2
  exit 1
}

[[ "${EUID:-$(id -u)}" -eq 0 ]] || fail "run this command as root"
[[ "$runner_user" =~ ^[a-z_][a-z0-9_-]*$ ]] || fail "invalid runner user: $runner_user"
id "$runner_user" >/dev/null 2>&1 || fail "runner user does not exist: $runner_user"

runner_home="$(getent passwd "$runner_user" | cut -d: -f6)"
runner_dir="$runner_home/actions-runner"
service_file="$runner_dir/.service"
[[ -s "$service_file" ]] || fail "runner service metadata is missing: $service_file"

service_name="$(tr -d '\r\n' < "$service_file")"
[[ "$service_name" == actions.runner.*.service ]] || fail "unexpected runner service name: $service_name"

runner_uid="$(id -u "$runner_user")"
runtime_dir="/run/user/$runner_uid"
install -d -m 0700 -o "$runner_user" -g "$runner_user" "$runtime_dir"

if command -v loginctl >/dev/null 2>&1; then
  loginctl enable-linger "$runner_user" || true
fi
systemctl start "user@${runner_uid}.service"

user_dropin="/etc/systemd/system/user@${runner_uid}.service.d"
runner_dropin="/etc/systemd/system/${service_name}.d"
install -d -m 0755 "$user_dropin" "$runner_dropin"

cat > "$user_dropin/50-starsector-preflight-delegate.conf" <<'EOF_USER'
[Service]
Delegate=cpu memory pids
EOF_USER

cat > "$runner_dropin/50-starsector-preflight-rootless-podman.conf" <<EOF_RUNNER
[Service]
Environment=XDG_RUNTIME_DIR=$runtime_dir
Environment=DBUS_SESSION_BUS_ADDRESS=unix:path=$runtime_dir/bus
Delegate=cpu memory pids
EOF_RUNNER

systemctl daemon-reload
systemctl restart "user@${runner_uid}.service"
systemctl restart "$service_name"
systemctl is-active --quiet "$service_name" || fail "runner service did not restart cleanly"

controllers_file="/sys/fs/cgroup/user.slice/user-${runner_uid}.slice/user@${runner_uid}.service/cgroup.controllers"
if [[ -r "$controllers_file" ]]; then
  echo "delegated user controllers: $(cat "$controllers_file")"
else
  echo "warning: could not read delegated user controllers at $controllers_file" >&2
fi

runuser -u "$runner_user" -- env \
  HOME="$runner_home" \
  USER="$runner_user" \
  LOGNAME="$runner_user" \
  XDG_RUNTIME_DIR="$runtime_dir" \
  DBUS_SESSION_BUS_ADDRESS="unix:path=$runtime_dir/bus" \
  podman info --format \
  'rootless={{.Host.Security.Rootless}} cgroupVersion={{.Host.CgroupVersion}} cgroupManager={{.Host.CgroupManager}}'

echo "Runner service delegation configured: $service_name"
