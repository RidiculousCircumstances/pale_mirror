# Structural integrity, physical access and future repair

Status: implemented and deployed to the disposable test server on 2026-11-06.
Final product source `6a343e01146f2db19ecd8a70c10618000537ad4a`, schema342/markup4.
Autonomous resident construction/repair remains deferred. This is scoped delivery,
not a claim that the repository's entire historical test suite is green.

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

Implemented scope: correct admission/result separation,
trustworthy damage/restoration records, function-specific availability/access and
repair-ready data. Autonomous material-backed resident repair is a subsequent stage,
not silently delivered by documenting this contract. No automatic fallback to the
old global DAMAGED gate or manual reset of the complete building is permitted.

## Implemented owner boundary

`StructureIntegrity` receives typed `StructureCellObserved` facts, updates exact
material damage and actual collision together, and supplies ABSENT/REPLACED/UNKNOWN
repair candidates. Existing legacy material-backed restoration delegates its
integrity change to this same owner; its separate material/intent receipt remains
with the repair protocol. `FacilityFunctionAvailability` assesses owner-declared
requirements. `ProductionFacilityAvailability` owns the retained production job/station/
workplace relationship; the generic evaluator never inspects a production job.
Work owners select the exact station/job and shared navigation proves
access; a second usable workplace does not authorize work on a missing contact.
Beds use exact retained head/foot blocks. Farmhouse integrity does not own crop life.

The native structural participant uses ManagedBlockWrites, the paged graybox
journal and existing operation-confirmation ledger. The common transient ordinary
player input continuation prepares asynchronously in the existing physical EFFECT
stage, then revalidates player/hand/world/reach/permission/exact predecessors and
invokes the whole vanilla game-mode call once. It preserves NeoForge placement
snapshot/event/rollback and item consumption. New input, expiry or disconnect cancels
unattempted work; no native input is replayed after restart. Only the STRUCTURE owner
opts in in this change; other managed families retain their existing protocols.

This is not permission to overwrite player replacements. Only observed original
material/typed visual data settles exact damage. Repair candidates remain evidence,
not executable repair instructions or resource reservations.

## Bound verification

Product implementation: `d2369cdf2`. Test fixture closure is included in the
final source above (initial closure `33b43bfde`). The test fixtures
now declare real outdoor loading sites for animal transport; no game navigation
permission or body envelope was relaxed. A transient passage and an exact service
station retain distinct physical clearance requirements.

Receipts are under the implementation checkout's `build/northwatch-dining-placement`:
`native-v9/manifest.json` proves actual door open/close, aborted hit/no damage,
confirmed destruction, SAMEworld graceful restart and one ordinary restoration.
`dining-native8/manifest.json` proves a physically HOT resident consumes actual
pantry bread (192 to191), reaches NOURISHED/NONE and releases the stock claim.
Native authoring checks cover original sign/material/banner serialization and
reject real material or text substitutions. These receipts do not claim an
observed eating animation or human visual acceptance.

Final delivery is bound by `build/northwatch-dining-placement/live-site2-delivery.json`
in the implementation checkout. The full-pack client completed23actions/11frames,
with its loaded-mod inventory PASS; all facade frames were reviewed. Source and
installed JAR match the clean detached release build. The client was capped at20FPS
and closed after review. Exact world, checksum and verification limits are in the
active continuity record. No autonomous resident repair is enabled by these checks.
