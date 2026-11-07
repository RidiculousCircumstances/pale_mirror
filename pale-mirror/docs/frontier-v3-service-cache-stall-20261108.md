# Service-boundary cache contention — 2026-11-08

The player reported a visually open door refusing entry for approximately ten
seconds, followed by all sword guards apparently standing idle when struck.
The live world was `clinical-care-seed139-20261107`, seed139/schema347/rules35,
core source `2c0a3b0f5`. Native damage reached guards immediately; the completed
45-second combat capture `152c3dc9-da2b-4c71-b766-cdadef0fcdce` retained all5817
events and showed sparse delayed sword responses. One guard was eating, which
does not explain the player's report about all guards.

## Evidence and cause

Minecraft tick time was approximately2ms while canonical simulation had over
10000 pending ordinary ticks. A security observation took five seconds to
commit. The server thread was not deadlocked; the exact preparation worker
was computing service clearance. A bounded25-second JFR attributed220/513
preparation execution samples and109/407 server execution samples to
`ServiceBoundaryComposition.declarations`;164 preparation samples were in
structural declaration equality.

The global single-entry declaration/index cache compared provider version
tokens across threads. `StorageServiceBoundaries` produces its version token
through a thread-confined immutable view. Therefore even unchanged inputs
on another worker invalidate the global token identity. Different captured
planning snapshots also displace each other. This rebuilt every service
boundary and repeatedly compared large occupied-surface sets under one shared
monitor. Clearance additionally revalidated that global index at every contact
of every candidate route. This is an evidenced source of simulation delay;
no claim that it explains every possible door collision or combat pause follows.

## Repair and verification

Source `bddf2c14274a0db7508de081e2ed7a1ec76daa7e` uses existing bounded,
thread-confined immutable declaration and operational views, one column image
per worker, and one captured immutable column map per clearance calculation.
Provider identity checks, real geometry, busy boundaries, outward-only exit,
retirement and physical durability fences remain. No canonical/schema/rules
change or migration is introduced.

New tests cover interleaved worker/world reads preserving an unchanged lane's
compiled image, captured old/new clearance views, and rejection of a foreign
service declaration before access. Existing route tests retain missing-support,
re-entry, unknown-route and no-path refusals.

- Focused25 JVM checks:24pass,1pre-existing failure. The unchanged
  `ResourceStorageAdoptionTest.replacementWarehouseOwnsMarketStockHarvestCapacityAndServiceFootprintAfterRecovery`
  fails at line110 finding a placement alternative; clean source2c0a3b0f5
  reproduces the identical failure. Neither test nor assertion was removed.
- Guardrails, architecture/style/debt checks and packaged-JAR validation pass.
- Full native GameTests: **475/475 required pass**, complete build5m23s.
- Clean detached release at the exact source passes guardrails/build/package.
  The separate `d83cc2204` build correction allows the legitimate standalone
  VibeCraft Gradle wrapper in tracked-file hygiene; arbitrary JARs remain rejected.
- No whole JVM-suite, graphical combat acceptance or universal latency claim.

Raw logs, bounded JFRs, profile summary, focused/baseline results, release hash
and before/after diagnostics are in implementation
`build/player-input-freeze-20261107/`. The earlier thread dump/performance image
are under `build/handcrafted-kitchen/`.

## Deployment

The native server saved all dimensions at00:12:20.573; JVM4078736 and25565 exited
before installation. Root publisher validated170pack hashes; the existing
checksum-pinned installer installed only the new core. Same world and datapack;
no reset. Clean detached release directory:
`/home/rd/proj/pm-player-freeze-release-20261108/pale-mirror`.

Core SHA512:
`6e32b7f9bb5aa8ca8028fad5b978d0c0e95c6841242981c2989924ac5d06a9148a04c606790a1e724c2625f5a2863040a4a394ec432ec06b44de4fb941fb7468`.
Visuals unchanged2fd468cb…994601. Hosted HTTP core checksum matches.
Preflight and fresh post-start verifier pass. User service
`far-frontier-v3-live.service`, wrapper536532/JVM536557,
invocation64dee655e1224aba8ab9fcbe41bae713, notBefore1794078761.
Initial post-start instant236205/revision857625 has pendingOrdinaryTicks0.
The subsequent66.62-second observation advanced1332 canonical ticks (19.99/s)
with pendingOrdinaryTicks0. A warm25-second JFR found4/144 preparation samples
in declarations and no declaration-equality samples; the server had0/143
samples in declarations. No player was connected during this check, unlike the
original capture: these sample proportions are not a controlled combat speedup
measurement. This establishes ordinary same-world progress without a backlog,
not player combat or door acceptance.

Handcrafted trial preparation is preserved separately; no furniture/modpack
change has been installed in this live world by this repair.
