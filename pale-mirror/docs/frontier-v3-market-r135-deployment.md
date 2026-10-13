# R135 settlement-market deployment

Delivered2026-10-14 by main alone, on explicit user commit/deploy authority.
No Git push. This receipt establishes the deployed identity and startup health,
not a new full-pack/HUMAN/M3 acceptance or a performance speedup.

- Implementation: `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`.
- Commit: `654f0ee4c5c62908b2a433591c55c33859afb989`.
- Source tree: `f2ab1e84edcd4485893064795be909fe2f87b857`.
- Clean detached release: `/home/rd/proj/pm-release-market.vU6Qti/source/pale-mirror`.
- Artifact: `pale-mirror-neoforge/build/libs/pale_mirror-0.3.0-SNAPSHOT.jar`.
- SHA512: `a5cd7777df4b445d7cb0542e89b2098a805461ab29482dd016d287517128e655c41d8f4b3e7b3fbc22a6831a9c5769c46a2aecd28e98be8ee326a71a1f09ecd9`.
- Runtime: `/home/rd/far-frontier-server`, NeoForge21.1.248 / Java22.0.2.
- Selected fresh world: `frontier-v3-market-r135-20261014`, seed20260918065.
- State schema286; explicit `frontier-v3-quarry-graybox-r11` / rules schema27.
- User service: `far-frontier-v3-live.service`; start epoch1791922632;
  invocation `c06c6d049c7b473a84e6b7fd2636ea8f`.
- WrapperPID1995910, JVM1995934; game25565 / RCON25575.

Clean release command passed in12s:

```sh
./gradlew -PfrontierV3GrayboxDisabledModsCatalog=/home/rd/proj/minecraft/config/graybox-disabled-mods.txt guardrails :pale-mirror-neoforge:jar :pale-mirror-neoforge:verifyPackagedJar --no-daemon
```

Prior dependency-valid checks and actual collection/recovery/retirement native8
are recorded in `frontier-v3-settlement-market.md`; they were not rerun for
confidence. The eight independently reproduced baseline Frontier failures remain
open. A successful scoped release build is not a green global `check`.

Publication used `scripts/publish-client-artifacts.sh` in the pack repository,
followed by checksum-pinned `scripts/install-server.sh` and explicit r11 selection.
Hosted and installed Pale Mirror checksums match. Visuals is unchanged.
Preflight checked a clean detached source, exact JAR, Java22, target and absence
of an existing selected level; the world-local graybox datapack was installed
before launch. The old `frontier-v3-supply-r129-20261013` world is retained,
not converted/deleted. No source/history/evidence cleanup was performed.

`scripts/frontier-v3-deploy-verify.sh` passed against the fresh service epoch,
ready line and Frontier startup; no new quarantine. Read-only live diagnostics
at1089 show12 settlements /366 residents, green retained-conflict verdict,
zero required/inventory/scene conflicts and zero ready lag/route waiters.
The recent64 host-through-PM samples average3.19ms/max12.33ms. This is only a
short observer-free startup sample, not a comparable old/new benchmark.

Evidence under the implementation `build/`:

- `market-clean-release-build.log`
- `market-r135-install.log`
- `market-r135-preflight.log`
- `market-r135-deploy-verify.log`
- `market-r135-live-diagnostics.log`

An exploratory unsupported `inspect runtime` query timed out; it produced no
readiness evidence. Valid summary/performance queries and the fresh-start
verifier above establish the bounded startup result.

## Preserved old-server delay investigation

User requested analysis of R134 delays. Before stop, captured read-only
diagnostics and a30-second JFR from exact service JVM1198401 at source8df887e4,
schema284/r10, old world536366. No optimization was performed in this deployment.

`market-r134-predeploy-diagnostics.log`: recent64 PM turns average37.73ms;
host-through-PM average38.17ms. Ready schedule lag, route waiting and journal
queued writes are zero in this snapshot. Historical overload warnings remain.

`market-r134-predeploy.jfr`:1245 Server-thread execution samples;117 include
unclaimed resource quantity/lot lookup,96 include meal candidate search, with
additional inventory/capacity/assignment/dependency calculation. These overlapping
sample counts identify investigation targets, not complete causal percentages.
`market-r134-jfr-attribution.json` and
`market-r134-jfr-caller-attribution.json` retain bounded summaries.
Next: inspect those real callers and retained old state for repeated work and
growing input sizes before choosing a fix. A fresh world is not that fix.

Source is clean; pack has only the pre-existing unrelated `.f0v-baseline/`.
Other canonical governance WIP remains preserved separately.
