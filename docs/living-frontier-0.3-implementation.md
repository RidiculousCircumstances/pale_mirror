# Living Frontier 0.3 implementation

This document records the implemented first 0.3 slice. Product direction and
release gates remain canonical in
[`product-review-0.2-and-vision-0.3.md`](product-review-0.2-and-vision-0.3.md).

## Region instances

`iron_frontier` is no longer a singleton campaign. A fresh world can bind up
to three current, strong and adapter-eligible vanilla/Integrated Village
observations when their anchors are at least 2048 blocks apart.

```text
world seed + observed place ID
    -> RegionBindings
    -> region/community/mines/routes/sites/station names
    -> persisted CampaignRegionRecord placement plan
```

The hash-derived identities are stable over restart and independent of
observation iteration order. `CampaignRegionRecord` now pins the archetype,
layout version and player-facing display name alongside its planned mine
columns and physical work state. A legacy `pale_mirror:ironhill_v2` record is
recognized explicitly and retains its previous IDs and coordinates; schema
v28 does not re-place its mines or route.

`CampaignRegionDefinition` is the compiled `RegionArchetype` for this first
archetype. Its persisted record is the corresponding layout/site plan: it
contains the observed settlement anchor, primary/alternate MineSite columns,
baseline and alternate route identities, and the bounded refugee placement
ring. The legacy vanilla minecart corridor remains a physical representation
of the primary abstract route; player-built Create remains the alternate
contract proof.

## Crisis choices

The existing supply-crisis aggregate retains four valid outcomes:

| Player response | Canonical result |
| --- | --- |
| Clear primary MineSite | `PRIMARY_SUPPLY_RESTORED` and recovery flow |
| Complete same-vehicle Create proof | `ALTERNATE_SUPPLY_VALIDATED` |
| Prepare and begin evacuation | `COMMUNITY_EVACUATED`, with population hosted at a shelter |
| Decline or do nothing | policy proceeds through its grace window and autonomous displacement |

No presentation action owns these results. The settlement continues rationing,
requesting supply, degrading defence and evacuating if Narrator selects
`NO_SCENARIO`.

### Prepared evacuation

The ledger offers **Prepare refugee site** during an open emergency. The
server issues a persisted permit bound to player, `StoryAudienceId`, community
and emergency deadline. Its `Refugee Anchor` item only carries that permit ID.
It is accepted only when the player places it:

- in the same dimension as the affected settlement;
- on a clear level 7×7 PM-owned footprint;
- 96–160 horizontal blocks from the settlement;
- while the exact emergency is still open and the community has residents.

The resulting `WorldSite(SHELTER)` starts degraded. A persistent
`RefugeeCampRecord` captures per-cell baseline/provenance, materializes a
small camp only in its loaded chunk and reconciles the site to operational
through `DomainCommand.SetWorldSiteOperational`. Representatives appear only
after the existing population group actually becomes displaced/resettled;
they never become a second population source. If no player prepares a site,
the emergency policy still chooses a safe fallback camp after the grace
window.

## Narrator v2

`NarrativeCandidate` is a pure-domain transient value derived from a real
`DomainEvent`; it is not persisted as a second queue. The runtime groups
candidates by audience and `Narrator.offerBest` scores them using integer
inputs:

```text
urgency, significance, audience relevance, capability fit,
novelty, distance bucket, recent same-archetype penalty,
active-story intensity penalty
```

The tie-breaker is a stable candidate identity. A candidate below the pacing
threshold records `NO_SCENARIO`; unavailable definitions/capabilities also
record that explicit decision. Candidate selection cannot modify a settlement,
source adapter or physical world.

The implemented candidate sources are threat, supply crisis, development
opportunity and resettlement opportunity. `SETTLEMENT_CRISIS_DETECTED`,
`SETTLEMENT_DEVELOPMENT_PLANNED` and `SETTLEMENT_RETURN_PLANNED` are re-read
from canonical history on later simulation steps. Discovery also immediately
reconsiders a previously unpresented crisis for that newly bound audience.
Thus a cooldown, restart or late discovery cannot silently erase a delayed
story. An accepted development offer gates only the PM-owned storehouse
execution; declining it or receiving no offer permits the autonomous policy
to proceed. An accepted resettlement offer gates a safe return-home transition.

## Player-facing presentation

The built-in Regional Ledger uses the closest recognized region or the
audience's first known region. It displays readable story titles rather than
opaque scenario IDs; the exact ID remains only in the click command payload.
It also differentiates a merely issued Refugee Anchor from a physically
operational prepared shelter before offering the begin-evacuation action.
Operator debug views deliberately retain full IDs and coordinates.

## Verification

The 0.3 slice is covered by:

- domain tests for candidate ranking, `NO_SCENARIO`, cooldown retry,
  opportunity completion and shelter-hosted population;
- core GameTests for three independent region instances, v27→v28 saved-data
  preservation, readable clickable scenario presentation and no-player
  MineSite commissioning;
- existing provenance, restart, recovery, route and adapter GameTests.

The core NeoForge profile currently reports **26/26 required GameTests
passed**. Full packaged/restart/client validation is still a release gate and
must run against the produced v28 artifact before publishing it to the private
server.
