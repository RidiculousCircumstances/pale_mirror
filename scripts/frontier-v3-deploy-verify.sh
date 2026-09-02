#!/usr/bin/env bash
# Read-only post-start evidence gate for a Frontier v3 disposable deployment.
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/frontier-v3-deploy-verify.sh \
    --target <dedicated-server runtime> \
    --level-name <selected V3 world directory> \
    --sha512 <installed Pale Mirror JAR SHA-512> \
    --service <user systemd unit> \
    --not-before <Unix epoch at restart>

This command is read-only.  It proves the currently installed JAR, real user
service PID, selected listening port and fresh post-restart Frontier v3 startup
records. It rejects a stale log or any new Frontier v3 quarantine record.
EOF
}

target=""
level_name=""
expected_sha512=""
service=""
not_before=""
while (($#)); do
  case "$1" in
    --target) target=${2:?--target requires a runtime directory}; shift 2 ;;
    --level-name) level_name=${2:?--level-name requires a directory name}; shift 2 ;;
    --sha512) expected_sha512=${2:?--sha512 requires a SHA-512}; shift 2 ;;
    --service) service=${2:?--service requires a user unit}; shift 2 ;;
    --not-before) not_before=${2:?--not-before requires a Unix epoch}; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -n "$target" && -n "$level_name" && -n "$expected_sha512" && -n "$service" && -n "$not_before" ]] || {
  usage >&2
  exit 2
}
[[ "$expected_sha512" =~ ^[[:xdigit:]]{128}$ ]] || { printf 'Expected SHA-512 must be 128 hexadecimal characters.\n' >&2; exit 2; }
[[ "$level_name" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || { printf 'level-name must be a simple relative directory name: %s\n' "$level_name" >&2; exit 2; }
[[ "$not_before" =~ ^[0-9]+$ ]] || { printf 'not-before must be a Unix epoch.\n' >&2; exit 2; }
[[ -d "$target" ]] || { printf 'Missing dedicated-server runtime: %s\n' "$target" >&2; exit 2; }
target=$(cd "$target" && pwd -P)
[[ "$target" != / ]] || { printf 'Refusing filesystem root as runtime target.\n' >&2; exit 2; }

if command -v sha512sum >/dev/null; then
  checksum() { sha512sum "$1" | awk '{print tolower($1)}'; }
elif command -v shasum >/dev/null; then
  checksum() { shasum -a 512 "$1" | awk '{print tolower($1)}'; }
else
  printf 'sha512sum or shasum is required to verify the installed artifact.\n' >&2
  exit 2
fi

expected_sha512=$(printf '%s' "$expected_sha512" | tr '[:upper:]' '[:lower:]')
installed_jar="$target/mods/pale_mirror-hosted.jar"
[[ -f "$installed_jar" ]] || { printf 'Managed Pale Mirror JAR is missing: %s\n' "$installed_jar" >&2; exit 1; }
actual_sha512=$(checksum "$installed_jar")
[[ "$actual_sha512" == "$expected_sha512" ]] || {
  printf 'Installed Pale Mirror checksum mismatch.\n  expected: %s\n  actual:   %s\n' "$expected_sha512" "$actual_sha512" >&2
  exit 1
}

properties="$target/server.properties"
[[ -f "$properties" ]] || { printf 'Missing server.properties: %s\n' "$properties" >&2; exit 1; }
configured_level=$(awk -F= '$1 == "level-name" { print substr($0, index($0, "=") + 1); exit }' "$properties")
configured_level=${configured_level:-world}
[[ "$configured_level" == "$level_name" ]] || {
  printf 'Runtime level-name mismatch. requested=%s configured=%s\n' "$level_name" "$configured_level" >&2
  exit 1
}
graybox_datapack="$target/$level_name/datapacks/pale-mirror-graybox/data/pale_mirror/dimension/frontier_graybox.json"
[[ -f "$graybox_datapack" ]] || { printf 'Selected V3 world lacks graybox datapack: %s\n' "$graybox_datapack" >&2; exit 1; }

command -v systemctl >/dev/null || { printf 'systemctl is required.\n' >&2; exit 2; }
[[ "$(systemctl --user is-active "$service" 2>/dev/null || true)" == active ]] || {
  printf 'Service is not active: %s\n' "$service" >&2
  exit 1
}
pid=$(systemctl --user show --property=MainPID --value "$service")
[[ "$pid" =~ ^[1-9][0-9]*$ ]] || { printf 'Service has no live MainPID: %s\n' "$service" >&2; exit 1; }

port=$(awk -F= '$1 == "server-port" { print substr($0, index($0, "=") + 1); exit }' "$properties")
port=${port:-25565}
[[ "$port" =~ ^[0-9]{1,5}$ ]] || { printf 'Invalid configured server port: %s\n' "$port" >&2; exit 1; }
command -v ss >/dev/null || { printf 'ss is required to verify the listening port.\n' >&2; exit 2; }
ss -ltnH | awk '{print $4}' | grep -Eq "(:|\\.)${port}$" || {
  printf 'No listening TCP socket found for configured server port %s.\n' "$port" >&2
  exit 1
}

command -v journalctl >/dev/null || { printf 'journalctl is required for fresh startup evidence.\n' >&2; exit 2; }
fresh_log=$(journalctl --user -u "$service" --since "@$not_before" --no-pager -o cat)
[[ -n "$fresh_log" ]] || { printf 'No service journal records after restart marker %s.\n' "$not_before" >&2; exit 1; }
printf '%s\n' "$fresh_log" | grep -Fq 'Frontier v3 runtime started' || {
  printf 'Fresh journal lacks Frontier v3 runtime startup evidence.\n' >&2
  exit 1
}
printf '%s\n' "$fresh_log" | grep -Eq 'Done \(' || {
  printf 'Fresh journal lacks dedicated-server ready evidence.\n' >&2
  exit 1
}
if printf '%s\n' "$fresh_log" | grep -Eiq 'frontier v3.*quarantin|pmv3.*quarantin'; then
  printf 'Fresh journal contains a Frontier v3 quarantine record; deployment is not healthy.\n' >&2
  exit 1
fi

printf 'FRONTIER_V3_DEPLOY_VERIFY=OK\n'
printf 'pid=%s\nport=%s\nworld=%s\nsha512=%s\nnotBefore=%s\n' \
  "$pid" "$port" "$target/$level_name" "$actual_sha512" "$not_before"
