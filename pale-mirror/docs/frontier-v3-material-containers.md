# Material container subsystem

The canonical `FungibleResourceLedger` owns quantities, lots, economic owners,
claims and custody. `ActorContainerItemOrder` describes actor/container and
actor/station handoffs independently of wheat, bread, baker or depot. A HOT
`PhysicalStackBinding` is only an epoch-scoped address of that account in a
Minecraft inventory. `MaterialSourceSelection` resolves a bounded claimed or
explicitly declared unclaimed lot portion against current bindings for any item
kind; the baker uses it instead of selecting a saved depot slot.
`FrontierV3ActorItemTransfer.FungibleStep` executes process-declared TAKE/PLACE
between an owned container and an actor hand, including partial PLACE with a
retained hand remainder. It checks the declared container owner, actor body,
machine port and physical pre/postconditions, but neither chooses work nor
writes canonical custody. A `MaterialContainerImage` is physical evidence, not an
economic ledger and not permission to create or transfer a resource.

There are two storage layouts. A `BULK` depot/store compares the total quantity
per item kind and keeps exact identified items slot-sensitive. An ordinary
split, merge or move of accounted stock can rebind current HOT slots without a
new lot or a replica conflict. `FIXED_PORTS` production stations compare every
slot because their declared input/output ports have different meanings. The
reference-container adapter chooses the layout from the declared production
station capability, not from an item name, a container-ID prefix or a baker
special case. The existing vanilla chest remains the physical surface for this
slice; an additional modded block is not required to solve slot rearrangement.

One fenced interaction follows this sequence: the process declares source,
destination and bounded amount; the canonical ledger validates owner, claim,
custody and account; the HOT observer binds the current complete physical
layout; the physical effect checks current stacks and writes once; an observed
postcondition commits the zero-sum handoff and next replica boundary. COLD uses
the same canonical order and ledger without physical slot identity. The baker
uses this path for depot wheat, hand carry, station input/output and depot
bread. No separate bakery balance is permitted.

Current qualification covers pure bulk order/split/quantity/exact-item
semantics, unclaimed partial-lot selection, native owned-depot
move/split/rebind/reconciliation through a checkpoint/recovery boundary and
native actor-hand TAKE/partial PLACE with owner/body-mismatch rejection. A
normal-client scenario moves accounted wheat to another slot in the owned depot,
then observes the same baker take it, load the station, make bread and deliver
64 bread to the depot in HOT. Its terminal assertion requires both the current
replica and acquired physical custody, not merely the presence of bread: this
exposed and fixed a stale source-input epoch being treated as live depot
authority after the baker had already taken the wheat. A separate normal-client
scenario proves the complete bakery cycle through graceful restart with the
same terminal custody assertion. Neither scenario proves
arbitrary player deposits, withdrawals or hopper transfers. An ordinary chest snapshot
cannot distinguish a same-kind equal-count replacement by the player from the
original stock, so it must not be treated as provenance proof. Ambiguous
changes remain local, visible conflicts. Before claiming the full player-flow
promise, introduce a controlled inventory interaction boundary (potentially a
PM-owned modded container/menu and automation adapter) that records each
transaction with source/destination custody and a durable pre-effect fence;
then prove custody-changing partial transfers, player save/restart and foreign-item
behavior. Do not invent lineage from a later slot scan.

The fingerprint grammar changed at Frontier state schema 209. Prior schema-208
test worlds are rejected on recovery; install this source only with a fresh
disposable world.
