# Living Frontier presentation: Atlas, landmarks and JourneyMap

This 0.3 presentation slice keeps the server authoritative. The client never receives SavedData or runs settlement policy.

```text
SavedData -> bounded Atlas snapshot -> native Atlas screen + optional JourneyMap waypoints
```

At most three current `iron_frontier` regions of the player's `StoryAudienceId` are projected. The snapshot holds readable names, derived facts, known physical anchors, and opaque return targets needed only for server-validated actions.

## Native Atlas

Press **P** after joining a PM server. A fresh server snapshot opens `Pale Mirror Atlas` and shows iron stock/capacity/net flow/reserve, defence/crisis, baseline route health, primary-mine state, an offered story, and available response buttons.

`Accept`, `Decline`, `Refugee site` and `Evacuate` are requests, not local state transitions. The server repeats audience, lifecycle, shelter and emergency checks, then returns a new snapshot. A stale screen or copied target cannot mutate another audience or bypass the Refugee Anchor flow.

## In-world readability

PM never places arbitrary signs, beacons or overlays in an observed village. Every 40 ticks it emits a local signal only at already materialized PM-owned infrastructure within 96 blocks of a player:

| Point | Healthy signal | Crisis signal |
| --- | --- | --- |
| Primary MineSite surface | end-rod survey flare | soul-fire flare while infected |
| PM supply depot | villager activity particles | smoke while its community is in crisis |

The MineSite and depot already have their entrance/loading yard and lantern. Signals make role and state legible without overwriting village blocks, loading chunks, or changing canonical values.

## JourneyMap

The integration is a client-only optional plugin compiled against JourneyMap API v2. It is not embedded in the PM JAR and creates no server dependency. If JourneyMap is present, the latest snapshot creates non-persistent PM waypoints for the recognized settlement, PM supply depot, represented primary mine and represented alternate source. Labels are readable (`Ironhill — primary mine`) and colour reflects projected mine state.

Before adding points PM calls `removeAll("pale_mirror")`; points rebuild from the newest server projection and clear on client logout. They are not a second persisted source of truth, cannot reveal a planned/unrepresented mine, and cannot issue PM actions or prove logistics capability.

## Verification boundary

`runGameTestServer` runs without JourneyMap and proves the PM JAR boots on a dedicated server with the optional API absent. Client rendering and map appearance still need a graphical smoke or manual walkthrough: log in with JourneyMap, confirm session waypoints, press **P**, and inspect MineSite/depot signals during a normal scenario.
