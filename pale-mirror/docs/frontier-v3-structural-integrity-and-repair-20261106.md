# Structural integrity, physical access and future repair

Status: user-agreed design, NOT implemented. Existing building damage still has
the premature START_DESTROY_BLOCK defect documented in CONTINUITY.md. This decision
does not assert current code conformance or activate resident construction/repair.

## Shared block-change lifecycle

The existing common Minecraft-operation boundary owns admission and confirmed
physical aftermath for buildings, roads and other managed blocks. Beginning a
player hit captures the exact actor/position/before-state and proposed change; it
must not record a block loss. Before an irreversible change, the required durable
admission must be available without blocking the server thread. The confirmed
Minecraft result, not a start packet or permissive pre-break event, records damage.
Abort, cancelled break and unchanged state close without damage. Unfinished durable
operations reconcile their exact physical outcome on recovery, without blind replay.

## Integrity and availability have different owners

A building integrity owner records exact confirmed affected cells, their original
declared role/material/state and their current classified disposition. No aggregate
building HP is introduced. Cosmetic integrity and functional availability are
separate derived results; a damaged decoration does not disable a station.

The authored building definition supplies semantic areas, work/service places and
requirements. Shared navigation/geometry proves physical access using actual body
profiles. Loss of an annotated entrance or passage invalidates its prior route
proof, but does NOT itself disable a building or function. Any physically valid
permitted route to the required place may satisfy access. Unknown/planning remains
a typed wait, not an inaccessible verdict. Work owners consume function-specific
assessment rather than independently interpreting damage counts or blueprint paths.
No second navigator, collision model, resource ledger or task controller is added.

## Repair extension contract

Residents will later repair damaged buildings when resources and workers are
available. Integrity publishes bounded repair candidates; the settlement chooses
priority/budget; the repair planner composes exact target/material/tool/labour work.
Existing resource custody/reservations, storage selection, hauling, navigation/UAE
and common physical operations execute the work. A confirmed physical restoration
closes only its exact damage entry. A repair attempt or scheduled task is not proof
of restoration. No material may be consumed twice across interruption or restart.

Damage is not unconditional authority to restore the original blueprint. A player
restoration can settle the affected cell after observation. A replacement block or
player redesign must be classified explicitly; repair may not remove it merely to
match the old template. Physical access may remain valid throughout damage/repair.

Immediate implementation scope when resumed: correct admission/result separation,
trustworthy damage/restoration records, function-specific availability/access and
repair-ready data. Autonomous material-backed resident repair is a subsequent stage,
not silently delivered by documenting this contract. No automatic fallback to the
old global DAMAGED gate or manual reset of the complete building is permitted.
