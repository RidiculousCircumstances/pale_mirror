# Managed regional railway integration

## Product role

The First Living Region begins with a complete, physically readable freight
railway between Mine17 and Ironhill. The line explains the primary `IRON` flow
before a crisis occurs. It is not a per-delivery item simulation: one verified
service establishes physical capability and Pale Mirror advances the canonical
resource flow abstractly.

This baseline is distinct from the existing Create logistics observation path:

- the baseline line is PM-authored and executed by the private Railway Untold
  fork through `RailInfrastructureAdapter`;
- an alternate line is built and scheduled by the player, then certified by
  the read-only Create adapter after the same train reaches both registered
  endpoints;
- Pale Mirror owns both `RouteContract` state and all stock mutations.

## Pinned provider

```text
repository: /home/rd/proj/railways-untold-pm
mod id: railwaysuntold
version: 1.2.1-pm.1
upstream base: Railway Untold 1.2.1
Minecraft: 1.21.1
NeoForge: 21.1.248
Create: 6.0.10
managed API commits: af227f2, b9f7be4, 70aca1f
JAR SHA-512: 8945940277dfae78038ac7e78032f0b3a32c7db93cd643ac498dd81fb5632084c66b8bc3d8c7b35cc2bb5b6a9127c904d1329b77300052982c48ec8a2e5ad0d2
```

The private pack must enable:

```toml
[features]
paleMirrorManagedMode = true
```

Managed mode disables Railway Untold's autonomous connection generation. An
exact version mismatch, disabled managed mode, or missing API reports a
`BLOCKED`/`ABSENT` adapter state; PM does not silently fall back to an invisible
primary route.

## Ownership boundary

Pale Mirror persists:

- region, connection and freight-service identities;
- endpoints, maximum envelope, axis and assembly direction;
- plan/native references and schedule fingerprint;
- commissioning state, baseline arrivals and infection eligibility;
- each first-touched cell's baseline, last approved state and conflict marker;
- the canonical route validation and simulated `IRON` flow.

The fork owns only the physical execution mechanics required to produce Create
track, endpoint stations, a train and its schedule. PM calls it through generic
DTOs. All provider class names and reflection are confined to
`internal.integration.railwaysuntold`.

The production policy is `LOADED_CHUNKS_ONLY`. `AUTONOMOUS_DEV` exists only for
bounded harnesses and may ticket one next segment footprint at a time.

## Commissioning lifecycle

```text
eligible settlement recognized
-> regional layout persisted
-> both MineSites preflighted and materialized autonomously
-> PLANNED
-> RAIL_BUILDING
-> RAIL_READY
-> TRAIN_COMMISSIONING
-> BASELINE_VALIDATION
-> ACTIVE
```

MineSite commissioning starts as soon as the settlement evidence creates the
campaign region. It uses one bounded temporary ticket around the current site,
records terrain-dependent anchors and complete baselines before writes, and
never waits for a player to visit either planned mine. A conflict blocks the
region before partial site placement.

The railway record and complete route geometry are saved before physical work.
The provider splits the immutable corridor at chunk boundaries, caps a segment
at 32 blocks and expands its eligibility footprint by eight blocks. In
production a segment is eligible only when every footprint chunk is already
loaded by ordinary Minecraft activity. PM and Railway Untold issue no route or
endpoint generation tickets. Segments are independent, may complete out of
order and appear on the first safe server ticks after their chunks load. This
looks like world generation to the player without mutating Create block
entities or graphs from parallel worldgen threads.

Each segment persists `PLANNED -> RUNNING -> TRACK_PLACED -> COMPLETED`.
Track placement, terrain clearing and Railway Untold supports still use its
normal Create-aware executors. A restart after track placement recovers the
physical postcondition and completes supports idempotently. The connection
does not advance to `RAIL_READY` until every segment and the shared Create
track graph are verified. No generic exploration head exists for a schema-v26
connection.

The Mine17 loading station is inside the PM-owned mine site. The receiving
station is placed 64 blocks outside the observed village, so native village
blocks remain read-only. A successful scheduled arrival at Ironhill validates
the primary route. The first infection cannot be scheduled until five later
simulation steps, giving the player a visible healthy baseline.

The railway is baseline world infrastructure. Player discovery reveals the
completed or visibly commissioning region; discovering a mine is not a command
to generate the route.

If the player edits the PM train's schedule, the service becomes `SUSPENDED`
instead of overwriting the player change. Missing capability puts dependent
work into a visible blocked state without changing canonical stock.

## Safety and recovery

Before every proposed block mutation, the fork asks PM's placement guard. PM
allows only positions inside the persisted connection envelope. During initial
commissioning the persisted authored corridor is the authority: first-touch
terrain and block entities may be transformed and repeated provider writes are
accepted until the first validated baseline arrival. Every touched cell still
records its original state and the last provider-approved state. After that
baseline, repair/retry work is strict: only the recorded baseline or last
approved state may be changed, and unknown/player changes fail closed.

Origin and destination stations are independent persisted postconditions. Each
is placed when its own endpoint chunks load; they never need to be loaded
simultaneously. After both exist, only the origin must be loaded to assemble
the representative train. Not-yet-loaded endpoints remain a retryable
`PLACING` state, not a permanent failure.

After restart, both PM and the fork load their persisted records. A `RUNNING`
segment first checks physical track ownership; `TRACK_PLACED` repeats the
idempotent ensure only to recover geometry and supports. `observedRevision` is
not represented by segment count until these postconditions pass.

Schema v26 is the chunk-driven boundary. Schema-v25 commissioned or
train-commissioning lines load as `LEGACY` and are never rebuilt. An untouched
v25 plan becomes `LOADED_CHUNKS_ONLY`. A partially written v25 line is suspended
with an explicit recovery diagnostic rather than guessing which executor owns
its blocks. Schema v24 remains `LEGACY_WORLD_DISABLED` and is not retrofitted.

## Automated evidence

- Core GameTests: 22/22, including autonomous no-player site commissioning
  and fail-closed MineSite preflight.
- Exact Create + Railway Untold PM GameTests: 23/23. The added negative test
  materializes naturally loaded segments, leaves the distant remainder
  planned, verifies the far endpoint chunk was not generated and confirms no
  legacy expansion head exists.
- Fork `test build`: passed.
- Live v25 recovery: a generic head retired 393 blocks before its target and
  left the provider in `BUILDING`; the optimized provider replaced it once and
  reached the persisted endpoint in 5 seconds (07:45:34–07:45:39), then
  recovered a transient endpoint-chunk wait into a running managed train.
- Packaged-JAR harness: installs a clean NeoForge server with only final PM,
  Create and Railway Untold JARs, starts it twice on the same world, and sees
  `pale_mirror:managed_railway: AVAILABLE` after both starts.
- A managed-railway client bootstrap profile is included in the seven-profile
  graphical harness. It was not executable in the current run because Xvfb is
  unavailable, so no new graphical-pass claim is made.

Two upstream Railway Untold bridge-pillar GameTests remain known failures in
the unmodified upstream test suite; they are outside the managed API path. They
must not be presented as passing PM evidence.

## Remaining live acceptance

Automation proves loading, API boundaries, planning and persistence, but does
not replace a graphical playthrough. Before declaring 0.2 complete, verify on a
fresh private-pack world:

1. natural village recognition and welcome kit;
2. readable Mine17 entrance, loading yard, full track and receiving station;
3. the scheduled PM freight train reaches both stations before infection;
4. restart preserves one line, one train and one service;
5. infection interrupts canonical supply and the ledger explains why;
6. combat recovery restores the primary route;
7. a player-built alternate Create route is independently certified;
8. player schedule edits suspend rather than get overwritten;
9. no changed/player-owned block is overwritten during any retry.
