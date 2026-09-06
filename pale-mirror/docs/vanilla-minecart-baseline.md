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

For a fresh authored region, the immutable genesis manifest is the initial
topology observation. Canonical baseline capacity is therefore available as
soon as the healthy region is registered; it does not wait for a player to
load every corridor chunk. Unlike a Create traversal receipt, this topology
evidence does not expire with simulation time. It remains valid until loaded
physical evidence explicitly reports a disconnected or missing graph.

## Lifecycle

```text
fresh authored region manifest
-> persisted VALIDATED VANILLA_MINECART RouteContract and route projection job
-> healthy abstract flow begins off-screen
-> player naturally loads corridor chunks
-> adopt world-generated rail, support and platform into provenance
-> reconcile the observed RailShape graph
-> keep capacity when connected, or block it when loaded evidence is missing
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
is loaded. Unknown chunks preserve the genesis topology assertion; a loaded
segment that fails its postcondition immediately validates the canonical route
at zero capacity. Repair or a connected player reroute restores nominal
capacity through the same typed validation boundary.

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
