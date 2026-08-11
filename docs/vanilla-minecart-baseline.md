# Vanilla minecart baseline route

## Product role

Fresh First Living Region worlds use a modest, readable `VANILLA_MINECART`
route between Mine17 and Ironhill. It is deliberately appropriate for a small
village: ordinary rails, periodic powered rails, simple oak trestles and a
small lit receiving platform. The MineSite's existing loading yard is the
origin. Neither endpoint creates a chest, a cargo inventory, or canonical
items.

The route represents a verified freight capability. `RouteContract` remains
the sole owner of `IRON` capacity and flow. A PM-labelled chest minecart is a
single lease-authorized visual carrier, not a delivery mechanism and not an
inventory boundary.

## Lifecycle

```text
fresh eligible settlement
-> MineSites materialized
-> persisted VANILLA_MINECART RouteContract and route job
-> player naturally loads corridor chunks
-> capture baseline on one bounded segment
-> recheck baseline / apply PM-owned rail, support and platform
-> postcondition and provenance receipt
-> structural route validation
-> ACTIVE abstract capacity
-> one optional representative cart
```

The complete cardinal geometry is saved before any physical write. Work is
limited to naturally loaded chunks: PM takes no chunk tickets and never asks
Minecraft to generate the unseen corridor. Therefore a player exploring from
Ironhill to Mine17 sees the railway emerge in the same loaded-chunk pipeline
as ordinary world activity, without an arbitrary "discover the mine, then
generate the route" trigger.

## Safety and recovery

For each planned cell, PM persists `baselineState`, `lastAppliedState` and a
conflict marker. The first pass captures every cell, then a later tick rechecks
it before writing. Unbreakable blocks and block entities block the route before
any destructive write. Once PM has written a cell, only the recorded baseline
or PM's own last state is acceptable; a later unknown or player change blocks
the job rather than being overwritten.

Every segment has a postcondition. After restart, completed segments are
verified from provenance and unfinished segments resume only where their chunk
is loaded. Canonical flow is not inferred from merely placing rail: the
physical corridor must finish verification, then the domain receives a typed
route validation command.

Existing schema-v26 `MANAGED_RAILWAY` records are retained as legacy physical
plans. Schema-v27 creates no vanilla route for them, does not rewrite their
track, and does not change their domain provider. This keeps a published world
safe while new exercise worlds receive the simpler baseline.

## Upgrade boundary

Create observations still certify player-built alternate logistics. The private
Railway Untold provider remains available for a later, deliberately selected
industrial corridor. These providers share `RouteContract` but never share
Minecraft block decisions: vanilla block placement stays in
`VanillaMinecartRailAdapter`, while Railway Untold remains isolated behind its
own adapter boundary.
