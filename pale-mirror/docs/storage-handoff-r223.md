# R223: storage provider and asynchronous handoff

Scope: investigate live HOT/COLD delays and Ironmeadow resident8-7, then repair
the connected server stop. Preserve the existing R222 world; no reset or push.

## Evidence and causes

- Live R222 tick68881: Ironmeadow depot had113 bread and ACQUIRED custody while
  its socket was UNLOADED. Resident8-7 had a TAKE action held at61931. Westhaven
  had the same custody state and a provisioning mission held at LOAD_SERVICE_ACCESS.
- Later both depots released. Westhaven's mission departed; Ironmeadow resident8-7
  consumed at73292 and was NOURISHED with no meal by75204. The intermediate
  DRAINING body was not evidence of a permanently lost actor. Do not introduce
  a teleport, forced consumption or reset to repair this observation.
- `confirmProjectionObservation` registered asynchronous mobile activation, but
  remembering the current-process physical observation existed only after a
  synchronous Accepted return. An unload before another naturally-ticking sweep
  therefore made an observed epoch look like unobserved restart custody. Its
  HOT resource bindings prevented COLD takes until another physical visit.
  The exact successful command continuation now remembers that epoch. Unseen
  restart evidence and different epochs remain fenced.
- Live server stopped20:37:54+05 at `container:observed--110_64_-333`.
  Read-only region inspection proves the block is a RIGHT half of a vanilla
  `minecraft:trapped_chest`, not missing. Its block entity retains the exact
  container identity and replica provenance. Discovery accepted ChestBlockEntity,
  but the fixed provider subsequently required the single Blocks.CHEST block.
- A second bug turned the attempted local isolation's Pending disk receipt into
  a fatal exception. Recovered WAL includes that very surface-conflict command
  at revision184985: it committed, rather than being rejected.

## Changed responsibilities

The fixed provider defines accepted chest block variants separately from its
default construction block. Discovery, inspection, unavailable-block-entity
deferral and surface recovery consume that provider contract. A supported chest
half retains its own27-slot identity; the existing compound-menu mechanism owns
double-container composition. No inventory rewrite, new ownership inference or
barrel/modded-container capability is added.

Surface transition exposes CommandResult to callers that must distinguish
Pending from Rejected. Real missing providers still isolate locally; arbitrary
foreign stock/identity never becomes accepted by this fix. Existing exact
surface verification restores the falsely isolated live chest on observation.

## Verification

Focused runtime/physical-executor checks, full NeoForge unit suite and guardrails
PASS (`build/r223-final-checks.log`). Native reference-projection13/13 PASS,
including actual double trapped-chest inspection, rejection of an unsupported
barrel, genuine provider loss with a Pending disk receipt, and asynchronous
confirmation with an exact HOT binding. The unseen-restart negative stays fenced.
Tests control disk completion, not a wall-clock performance threshold.
Unchanged domain/frontier evidence from R222 is reused; no domain/schema change.

Checked-in `live-storage-handoff-recovery.json` visits the retained live chest,
checks its ACTIVE recovery, visits Ironmeadow and checks unloaded storage reaches
RELEASED. This is a scoped existing-world diagnostic, not whole-economy acceptance.
Clean-ref packaging, deployment and live recovery remain pending.
