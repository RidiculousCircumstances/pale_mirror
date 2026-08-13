#!/usr/bin/env bash
set -euo pipefail

# Builds a disposable dedicated server from this exact checkout. It deliberately
# retains logs/worlds in .work/ for investigation instead of deleting evidence.
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

: "${JAVA_HOME:?Set JAVA_HOME to the pinned Java 22 JDK/JRE}"
java_bin="$JAVA_HOME/bin/java"
"$java_bin" -version 2>&1 | grep -Eq '(version|openjdk) "22([."]|$)' || {
  echo "Java 22 is required" >&2
  exit 1
}
command -v curl >/dev/null
command -v packwiz >/dev/null || {
  echo "packwiz is required; install it before the smoke test" >&2
  exit 1
}

neoforge_version=21.1.248
neoforge_sha256=68eeab77059ba53df1812f1afa5bf530ab2566a3cdcd5f924aa6e71be42e410c
bootstrap_version=0.0.3
bootstrap_sha256=a8fbb24dc604278e97f4688e82d3d91a318b98efc08d5dbfcbcbcab6443d116c
work_root="$repo_root/.work"
mkdir -p "$work_root"
server_dir=$(mktemp -d "$work_root/server-smoke.XXXXXX")
tools_dir="$work_root/tools"
mkdir -p "$tools_dir"
installer="$tools_dir/neoforge-$neoforge_version-installer.jar"
bootstrap="$tools_dir/packwiz-installer-bootstrap-$bootstrap_version.jar"

fetch_verified() {
  local url=$1 path=$2 expected=$3
  if [[ ! -f "$path" ]]; then
    curl --fail --location --retry 3 --output "$path" "$url"
  fi
  printf '%s  %s\n' "$expected" "$path" | sha256sum -c -
}

fetch_verified \
  "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neoforge_version/neoforge-$neoforge_version-installer.jar" \
  "$installer" "$neoforge_sha256"
fetch_verified \
  "https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v$bootstrap_version/packwiz-installer-bootstrap.jar" \
  "$bootstrap" "$bootstrap_sha256"

"$java_bin" -jar "$installer" --installServer "$server_dir"

serve_log="$server_dir/packwiz-serve.log"
packwiz serve --port 8091 >"$serve_log" 2>&1 &
serve_pid=$!
server_pid=""
server_input=""
cleanup() {
  [[ -z "$server_pid" ]] || kill -- "-$server_pid" 2>/dev/null || true
  [[ -z "$server_input" ]] || rm -f "$server_input"
  kill "$serve_pid" 2>/dev/null || true
}
trap cleanup EXIT

for _ in $(seq 1 30); do
  if curl --fail --silent http://127.0.0.1:8091/pack.toml >/dev/null; then break; fi
  sleep 1
done
curl --fail --silent http://127.0.0.1:8091/pack.toml >/dev/null

(
  cd "$server_dir"
  "$java_bin" -jar "$bootstrap" -g -s server http://127.0.0.1:8091/pack.toml
  # The bootstrap's noninteractive mode accepts every option. Caliber is not core:
  # retain its downloaded artifact with a non-JAR suffix instead of testing it.
  if [[ -f mods/createcaliber-0.2.0.jar ]]; then
    mv mods/createcaliber-0.2.0.jar mods/createcaliber-0.2.0.jar.disabled
  fi
  mkdir -p world/datapacks
  for datapack in "$repo_root"/datapacks/*; do
    [[ -f "$datapack/pack.mcmeta" ]] || continue
    cp -a "$datapack" world/datapacks/
  done
  printf 'eula=true\n' > eula.txt
  printf '%s\n' '-Xms4G' '-Xmx12G' > user_jvm_args.txt
  server_input="$server_dir/server-smoke.input"
  mkfifo "$server_input"
  exec 3<>"$server_input"
  setsid ./run.sh nogui <"$server_input" >server-smoke.log 2>&1 &
  server_pid=$!
  tail --pid="$server_pid" -f server-smoke.log &
  tail_pid=$!
  deadline=$((SECONDS + ${SMOKE_TIMEOUT:-600}))
  while ! grep -q 'Done (' server-smoke.log; do
    if ! kill -0 "$server_pid" 2>/dev/null; then
      wait "$server_pid" || true
      echo "Dedicated server exited before reaching Done; evidence: $server_dir" >&2
      exit 1
    fi
    if ((SECONDS >= deadline)); then
      echo "Dedicated server did not reach Done before the smoke timeout; evidence: $server_dir" >&2
      exit 1
    fi
    sleep 1
  done
  printf 'stop\n' >&3
  exec 3>&-
  wait "$server_pid"
  wait "$tail_pid" 2>/dev/null || true
  server_pid=""
  rm -f "$server_input"
  server_input=""
)

grep -q 'Done (' "$server_dir/logs/latest.log"
echo "server smoke test passed; evidence: $server_dir"
