#!/usr/bin/env bash
# Read-only stop gate for a Frontier v3 disposable-server deployment.
#
# It deliberately does not publish artifacts, reset a world, stop a service or
# start Minecraft.  Those are separate explicit operations after this gate has
# produced an evidence record.  The point is to reject the common "wrong tree,
# wrong JAR, wrong Java, wrong world" deployment before any mutable action.
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/frontier-v3-deploy-preflight.sh \
    --source-ref <immutable commit> \
    [--source-repo <clean detached Pale Mirror worktree>] \
    --artifact <verified Pale Mirror JAR> \
    --sha512 <artifact SHA-512> \
    --target <dedicated-server runtime> \
    --level-name <fresh V3 world directory> \
    --java <Java 22 executable> \
    [--service <user systemd unit>] [--require-world-absent]

The source checkout (default: ./pale-mirror) must be a clean detached Pale
Mirror worktree at exactly --source-ref. The outer pack checkout must also be
clean. This command is
read-only: it never publishes, installs, resets or starts a server.

Use --require-world-absent only after the exact selected V3 world was reset and
immediately before server start.  It rejects a stale canonical world rather
than accidentally reusing it after a schema/projection boundary.
EOF
}

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
source_repo="$repo_root/pale-mirror"
source_ref=""
artifact=""
expected_sha512=""
target=""
level_name=""
java_bin=""
service=""
require_world_absent=false

while (($#)); do
  case "$1" in
    --source-ref) source_ref=${2:?--source-ref requires a commit}; shift 2 ;;
    --source-repo) source_repo=${2:?--source-repo requires a Pale Mirror worktree}; shift 2 ;;
    --artifact) artifact=${2:?--artifact requires a path}; shift 2 ;;
    --sha512) expected_sha512=${2:?--sha512 requires a SHA-512}; shift 2 ;;
    --target) target=${2:?--target requires a runtime directory}; shift 2 ;;
    --level-name) level_name=${2:?--level-name requires a directory name}; shift 2 ;;
    --java) java_bin=${2:?--java requires an executable}; shift 2 ;;
    --service) service=${2:?--service requires a user unit}; shift 2 ;;
    --require-world-absent) require_world_absent=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -d "$source_repo" && -e "$source_repo/.git" ]] || { printf 'Missing Pale Mirror source repository: %s\n' "$source_repo" >&2; exit 2; }
source_repo=$(cd "$source_repo" && pwd -P)
[[ -n "$source_ref" && -n "$artifact" && -n "$expected_sha512" && -n "$target" && -n "$level_name" && -n "$java_bin" ]] || {
  usage >&2
  exit 2
}
[[ "$expected_sha512" =~ ^[[:xdigit:]]{128}$ ]] || { printf 'Expected SHA-512 must be 128 hexadecimal characters.\n' >&2; exit 2; }
expected_sha512=$(printf '%s' "$expected_sha512" | tr '[:upper:]' '[:lower:]')
[[ "$level_name" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || { printf 'level-name must be a simple relative directory name: %s\n' "$level_name" >&2; exit 2; }
[[ -x "$java_bin" ]] || { printf 'Java executable is not executable: %s\n' "$java_bin" >&2; exit 2; }
[[ -f "$artifact" ]] || { printf 'Missing deployment artifact: %s\n' "$artifact" >&2; exit 2; }
[[ -d "$target" ]] || { printf 'Missing dedicated-server runtime: %s\n' "$target" >&2; exit 2; }

target=$(cd "$target" && pwd -P)
artifact=$(cd "$(dirname "$artifact")" && pwd -P)/$(basename "$artifact")
[[ "$target" != / ]] || { printf 'Refusing filesystem root as runtime target.\n' >&2; exit 2; }
[[ "$artifact" == "$source_repo/"* ]] || {
  printf 'Deployment artifact must be built inside the verified source worktree: %s\n' "$source_repo" >&2
  exit 1
}
[[ -f "$target/server.properties" && -d "$target/mods" && -x "$target/scripts/run-server-java22.sh" ]] || {
  printf 'Runtime is incomplete (need server.properties, mods/, scripts/run-server-java22.sh): %s\n' "$target" >&2
  exit 2
}

resolved_ref=$(git -C "$source_repo" rev-parse --verify "${source_ref}^{commit}") || {
  printf 'source-ref is not an immutable commit in Pale Mirror: %s\n' "$source_ref" >&2
  exit 2
}
head_ref=$(git -C "$source_repo" rev-parse HEAD)
[[ "$head_ref" == "$resolved_ref" ]] || {
  printf 'Pale Mirror HEAD (%s) does not equal requested source ref (%s).\n' "$head_ref" "$resolved_ref" >&2
  exit 1
}
if git -C "$source_repo" symbolic-ref -q HEAD >/dev/null; then
  printf 'Pale Mirror checkout must be detached at the release commit, not on a branch.\n' >&2
  exit 1
fi
clean_checkout() {
  local checkout=$1 label=$2 status
  status=$(git -C "$checkout" status --porcelain)
  [[ -z "$status" ]] || {
    printf '%s checkout is dirty; refuse a non-reproducible deployment.\n' "$label" >&2
    printf '%s\n' "$status" >&2
    return 1
  }
}

clean_checkout "$source_repo" 'Pale Mirror source' || {
  printf 'Pale Mirror source checkout is dirty; build/publish from a clean detached ref.\n' >&2
  exit 1
}
clean_checkout "$repo_root" 'Pack/deployment' || {
  exit 1
}

"$java_bin" -version 2>&1 | grep -Eq '(version|openjdk) "22([."]|$)' || {
  printf 'Dedicated server deployment requires Java 22: %s\n' "$java_bin" >&2
  exit 1
}
if command -v sha512sum >/dev/null; then
  actual_sha512=$(sha512sum "$artifact" | awk '{print tolower($1)}')
elif command -v shasum >/dev/null; then
  actual_sha512=$(shasum -a 512 "$artifact" | awk '{print tolower($1)}')
else
  printf 'sha512sum or shasum is required to verify the deployment artifact.\n' >&2
  exit 2
fi
[[ "$actual_sha512" == "$expected_sha512" ]] || {
  printf 'Artifact checksum mismatch.\n  expected: %s\n  actual:   %s\n' "$expected_sha512" "$actual_sha512" >&2
  exit 1
}

configured_level=$(awk -F= '$1 == "level-name" { print substr($0, index($0, "=") + 1); exit }' "$target/server.properties")
configured_level=${configured_level:-world}
[[ "$configured_level" == "$level_name" ]] || {
  printf 'Runtime level-name mismatch. requested=%s configured=%s\n' "$level_name" "$configured_level" >&2
  exit 1
}
world_path="$target/$level_name"
[[ "$world_path" == "$target/"* && "$world_path" != "$target" ]] || {
  printf 'Refusing unresolved world path: %s\n' "$world_path" >&2
  exit 2
}
graybox_datapack="$world_path/datapacks/pale-mirror-graybox/data/pale_mirror/dimension/frontier_graybox.json"
[[ -f "$graybox_datapack" ]] || {
  printf 'Selected V3 world lacks required graybox datapack: %s\n' "$graybox_datapack" >&2
  exit 1
}
if "$require_world_absent" && [[ -e "$world_path/level.dat" ]]; then
  printf 'Selected V3 world still exists after requested reset: %s\n' "$world_path" >&2
  exit 1
fi

"$repo_root/scripts/validate-graybox-runtime-profile.sh"
if [[ -n "$service" ]]; then
  command -v systemctl >/dev/null || { printf 'systemctl is required to inspect service %s\n' "$service" >&2; exit 2; }
  service_state=$(systemctl --user is-active "$service" 2>/dev/null || true)
else
  service_state=not-requested
fi

printf 'FRONTIER_V3_DEPLOY_PREFLIGHT=OK\n'
printf 'sourceRef=%s\nsourceRepo=%s\nartifact=%s\nsha512=%s\nruntime=%s\nworld=%s\nserviceState=%s\n' \
  "$resolved_ref" "$source_repo" "$artifact" "$actual_sha512" "$target" "$world_path" "$service_state"
