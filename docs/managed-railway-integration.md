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
JAR SHA-512: 2993b2274cbdc8775d7ab960feee2a2e50aa23d22486c811b426c0eb309d43fa9225ed5f69470caae87b2b09a7658216c974f95cdf0de792946f96cf066f2f73
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

## Commissioning lifecycle

```text
PLANNED
-> RAIL_BUILDING
-> RAIL_READY
-> TRAIN_COMMISSIONING
-> BASELINE_VALIDATION
-> ACTIVE
```

The record is saved before physical construction starts. Construction receives
a bounded moving chunk ticket around its current head; endpoint tickets exist
only while the service is being commissioned. There is no permanent route
force-loading.

The Mine17 loading station is inside the PM-owned mine site. The receiving
station is placed 64 blocks outside the observed village, so native village
blocks remain read-only. A successful scheduled arrival at Ironhill validates
the primary route. The first infection cannot be scheduled until five later
simulation steps, giving the player a visible healthy baseline.

If the player edits the PM train's schedule, the service becomes `SUSPENDED`
instead of overwriting the player change. Missing capability puts dependent
work into a visible blocked state without changing canonical stock.

## Safety and recovery

Before every proposed block mutation, the fork asks PM's placement guard. PM
allows only positions inside the persisted connection envelope whose current
state still equals the recorded baseline or last PM-approved state. A first
touch must be air, fluid, or natural terrain and may not contain a block entity.
The only endpoint exception permits the proposed registered station. Unknown,
crafted, player-owned or changed cells fail closed.

After restart, both PM and the fork load their persisted records. Completed
postconditions are observed rather than replayed; unfinished construction
continues from the recorded head. Canonical route capacity is not validated
until the full service postcondition is observed.

Schema v24 snapshots migrate to v25 with `LEGACY_WORLD_DISABLED` commissioning.
PM deliberately does not retrofit a railway into an already materialized old
region. Fresh development worlds receive the full product setup.

## Automated evidence

- Core GameTests: 20/20.
- Exact Create + Railway Untold PM GameTests: 20/20.
- Fork `test build`: passed.
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
