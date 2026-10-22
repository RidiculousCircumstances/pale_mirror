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

Focused runtime/physical-executor checks, full NeoForge unit suite821/821 and guardrails
PASS (`build/r223-final-checks.log`). Native reference-projection13/13 PASS,
including actual double trapped-chest inspection, rejection of an unsupported
barrel, genuine provider loss with a Pending disk receipt, and asynchronous
confirmation with an exact HOT binding. The unseen-restart negative stays fenced.
Tests control disk completion, not a wall-clock performance threshold.
Unchanged domain/frontier evidence from R222 is reused; no domain/schema change.

Checked-in `live-storage-handoff-recovery.json` visits the retained live chest,
checks its ACTIVE recovery, visits Ironmeadow and checks unloaded storage reaches
RELEASED. This is a scoped existing-world diagnostic, not whole-economy acceptance.
Clean-ref packaging and same-world deployment completed (receipt below).

First same-world client check exposed one additional legacy call: surface
recovery requested only graybox support, so an adopted RESOURCE_STORAGE on
ordinary terrain returned DEFERRED despite its actual provider/slots being
current. Recovery now consumes `declaredSupport`, like initial admission and
normal readiness (terrain, worksite and graybox remain separately owned).
The live scenario is the regression: before correction the exact retained
double trapped chest stayed CONFLICT with READY support/CURRENT slots.

## Delivery and retained-world check

Implementation commits79d4dc06 and0453d60e; final clean detached source
`/home/rd/proj/pm-r223b-release/pale-mirror`. JAR/verifyPackagedJar PASS;
last support-call correction compile/focused executor tests/guardrails PASS.
No domain or wire-schema change; no mandatory client update for this fix.
Published/installed SHA512:
`c878e7054c2a43b8a265373d2e9319413a270767c4813253c8f6aec3a093617d09ac43fd4a84c2fa007ee0eeae1ae3373dd7f6df597a3ffaf430ee9ab774e4aa`.
Deploy preflight/verify PASS; service `far-frontier-v3-live.service`, wrapperPID714983,
start1792685156, unchanged world `frontier-v3-r222-current-handoff-20261022`.
No world reset or canonical repair edits. The intermediate service stop was via
systemd, not a separately proven graceful save; the final check therefore includes
actual retained-world startup/recovery, not a claimed graceful-restart scenario.

Full-pack ordinary client diagnostic PASS:
`build/r223b-live-recovery.json`, run4af5daca-401f-4f29-8e0d-a2ae1643b2b2.
Task-private Xvfb/20FPS (physical display unavailable), client/display stopped.
At revision199732 the exact trapped chest resumed through `reference-surface-verified`;
frame21:06:48 inspected: same double chest physically present. Ironmeadow storage
acquired epoch3 on visit, then RELEASED/unloaded by21:06:57 after departure21:06:53.
Bindings released normally, no physical stock rewrite to resolve the conflict.
Resident8-7 NOURISHED916/no meal/no held action (consumption predated this fix).
Terminal diagnostics required/scene/inventory/custody conflicts0, incidentIndex0.
Follow-up tick80293: recent64 host turns mean7.99ms/max76.60ms, ready event lag0,
WAL awaitingDurability=false. These are narrow observations, not an economic soak
or universal performance guarantee; startup had a2.7s keep-up warning.

Source tree clean after the task receipt; outer pack tracked files unchanged,
pre-existing `.f0v-baseline/` retained. No push. Canonical governance contains
pre-existing WIP, with only the active continuity entry amended for this task.
