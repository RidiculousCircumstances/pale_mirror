# Current test environment map

Discover and verify these values before every operation; they may drift.

## Repositories and runtime

- Pale Mirror code: `/home/rd/proj/minecraft/pale-mirror`
- Far Frontier pack: `/home/rd/proj/minecraft` (`pack.toml` is the identity)
- Disposable server runtime: `/home/rd/far-frontier-server`
- Live Frontier v3 service: transient user unit `far-frontier-v3-live.service`
- Client artifact host: user unit `far-frontier-client-host.service`
- Current server launch wrapper: `scripts/run-server-java22.sh`
- Current world property: read `server.properties`; do not assume `world`

## Pack scripts

- Validate pack: `scripts/validate.sh`
- Publish hosted client artifacts: `scripts/publish-client-artifacts.sh`
- Install/update server: `scripts/install-server.sh`
- Install Java 22 runtime: `scripts/install-server-java22.sh`
- Server smoke: `scripts/server-smoke-test.sh`
- Windows client: `scripts/update-client.cmd`
- macOS client: `scripts/update-client-macos.command`

Read a script before using it when arguments or destructive behavior matter.

## Runtime proof

Check the user service, Java process, port from `server.properties`, and fresh
`logs/latest.log`. A valid start includes mod loading and the dedicated-server
ready line, not merely an active transient unit. For world reset, stop the unit,
resolve the exact `level-name` paths under the runtime, remove only those paths,
then start and verify new seed/catalog evidence.

`far-frontier-v3-live.service` is intentionally created with `systemd-run` and
`CollectMode=inactive-or-failed`. It disappears after `systemctl --user stop`,
so a later `systemctl --user restart` cannot recreate it. If it is inactive or
not found, launch the exact runtime again with:

```bash
systemd-run --user --unit=far-frontier-v3-live --collect \
  --working-directory=/home/rd/far-frontier-server \
  /home/rd/far-frontier-server/scripts/run-server-java22.sh
```

Use `restart` only while that transient unit is already active. Always finish
with `scripts/frontier-v3-deploy-verify.sh`; an active unit or an open port by
itself is not deployment evidence.

## Detached release build

`pale-mirror-neoforge` reads the graybox disabled-mod catalogue from the
companion pack. A detached release worktree outside that sibling layout must
build with the verified pack input explicitly supplied; otherwise Gradle fails
before compilation. Use:

```bash
./gradlew -PfrontierV3GrayboxDisabledModsCatalog=/home/rd/proj/minecraft/config/graybox-disabled-mods.txt \
  :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar
```

The resulting JAR remains valid for preflight only when it is inside the clean,
detached source worktree at the exact release commit.
