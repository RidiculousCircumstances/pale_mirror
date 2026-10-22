# Active v3 asynchronous-migration audit (R220)

Scope: the 2026-10-22 live shutdown and the active v3 paths affected by R218/R219
asynchronous canonical durability. Inspect producers, commit publication,
continuations and recovery, not merely occurrences of `Accepted`. This is not
proof that every possible v3 defect has been eliminated. No inactive hive,
combat or medical feature is enabled by this audit.

## Confirmed findings

1. **Displaced meal pickup could quarantine the world.** Live R219 stopped at
   tick25842, revision39937, resident:6-22. Snapshot plus WAL recovery reproduces
   it: task TAKE, body(-105,64,17), required body(-105,65,14), no physical custody.
   COLD planning trusted TAKE as arrival; ActorItemCustody correctly refused the
   remote transfer. Native departure had retained the actual displaced body.
   Meal admission already handled placement displacement, but later physical
   displacement/release did not have the equivalent COLD continuation.
   Repair: the meal owner emits an exact, replayable approach-required fact,
   retaining execution, source and claim. The normal navigator reapproaches;
   neither food nor body moves in this transition. An unresolved physical effect
   cannot be reset. Loaded storage does not suppress this movement-only repair.

2. **Common COLD item transfer omitted retained body authority.** Its own guard
   checked ambient/scene leases but not independent body custody. Callers often
   supplied the stronger check, but the shared transfer boundary itself did not.
   Repair: use ActorExecutionCoordinator.coldAvailable consistently at that
   boundary. No physical custodian can compete with an abstract resource transfer.

3. **Scene release dropped its post-commit work.** The initial Accepted branch
   refreshed carried-resource witnesses and continued saved departures. Pending
   could publish CLOSED later, removing that branch from subsequent execution.
   Repair: one owner continuation performs transient cleanup, inventory witness
   confirmation and departure progression after the exact receipt. Keep one
   owner callback rather than overwrite it with separately registered actions.

4. **Meal consumption dropped carried-inventory witness refresh.** Portable
   remainder/provenance refresh followed a synchronous boolean; a durable later
   completion retired the meal without running it. Repair: the existing common
   operation-confirmation owner callback performs the same refresh.

5. **Conflict and handoff aftermath still assumed immediate acceptance.** Field
   conflict publication could miss its physical-ledger conflict marker. Production
   grounded-handoff fall-counter cleanup and service-handoff movement stop could
   also be skipped. Repair: receipt-bound owner continuations. Original exact
   pose/actuation guards remain in place; no successor movement is stopped.

6. **Bakery confirmation collapsed waiting and rejection into one boolean.**
   `observe` treated an unavailable receipt as AMBIGUOUS_EFFECT, although common
   confirmation also returns false while native evidence or canonical durability
   is pending. Repair: common confirmation exposes distinct confirmed, awaiting
   native evidence, awaiting canonical commit and rejected outcomes. Only a real
   canonical rejection becomes the bakery's local ambiguity; native ambiguous
   effects retain the existing operation evidence/recovery path, never replay.

## Inspected paths with no equivalent contradiction established

Native verification also exposed a misleading body-inspection message: ordinary
Pending was logged as OBSERVATION_REJECTED, with a changing command/revision in
the deduplication key. It now records the stable pending reason; actual rejection
retains its full result. This changes diagnostics only, not movement admission.

- Bakery COLD recomputes a route before selecting a station action; extraction
  and expedition supply/replenishment check arrival and their service/custody
  permissions. Their old phase alone does not authorize remote item handoff.
- Harvest delivery, field world changes/player breaks, extraction block changes,
  storage/station adoption and container removal retain explicit intermediate
  witnesses/phases, and recognize the already accepted consequence next turn.
  Their `Accepted` booleans are not automatically missing-continuation bugs.
- Shipment, extraction and expedition transfers already use common operation
  confirmation callbacks. Native death/resource recovery retains explicit owner
  aftermath. Receipt pinning distinguishes PREDECESSOR from admitted DURABILITY.
- Repeated confirmation registration intentionally replaces the same owner
  callback; the runtime orders protocol closure before owner completion. Do not
  replace this with an unbounded callback list during every polling retry.
- Direct native/player boundaries settle their predecessor before reading state;
  ordinary physical turns remain cooperative. Do not make all commands blocking
  again or equate queued admission with durable success.

## Evidence and limits

Exact original contradiction: `build/r220-recovered-meal.log`, read-only recovery
of the stopped R219 world. No world edit or reset used to diagnose it.
Focused tests cover displaced pickup/reapproach, claim/resource preservation,
round-trip encoding, rejected stale pose, unresolved physical operation and
independent body custody; shared confirmation checks waiting versus rejection,
receipt pinning and recovery. Final build/runtime receipts are recorded in the
continuity ledger. Source inspection is not HOT gameplay or economic-soak proof.
The new population event changes the closed descriptor inventory: schema299
requires a fresh test world, not a compatibility repair of the saved R219 world.
