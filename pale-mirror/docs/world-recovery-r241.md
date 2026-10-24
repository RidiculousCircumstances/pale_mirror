# R241 working-world recovery

The old R232 world is retained for diagnosis. Its seven absent Stonefield bodies
and one ambiguous historical pickup cannot be recreated/replayed from absence.
A fresh r26/seed126 world was started on 91484b0de without copying native or
canonical records. This reset is a retest, not a repair of those historical effects.

## Confirmed acceleration defect

At ticks 24,524–31,919 a real full-pack client visited Hearthvale, Clearwater,
and Hearthvale while a previously admitted relative advance was still queued.
Hearthvale had 187 bread, 25 meal claims, and residents held in PREPARED ambient
leases. One exact example is Ada Ash (resident:6-3): MOVE, physical body custody,
no movement order, native first admission still `never_created`. Two other bodies
were inserted but physical continuation was not serviced. No quarantine occurred.

`FrontierV3ServerLifecycle.tick` intentionally selects canonical-only advancement
while a relative request is queued. `FrontierV3FastForwardSafety` checked legacy
physical intents, scenes and field projection cursors, but omitted the shared
body authority. Consequently new activity families could be frozen in HOT while
nutrition and the rest of the world advanced. The native inspection passed its
conflict assertions; this was **not** proof of progressing meals.

The correction adds a common body boundary, independent of job type. Any retained
physical incarnation stops canonical-only acceleration. Before body preparation,
ordinary player demand at the projected actor position also stops it, using the
existing spatial index, scene demand policy and body projection. Unobserved COLD
actors and loaded chunks without player demand remain eligible. The existing
operator rejection receipt reports actor/phase/epoch or presentation demand and
returns the server to ordinary physical execution. No body or resource is
fabricated, and no durable operation is canceled as a recovery shortcut.

Evidence: `build/r241-cold-observation.jsonl`, `r241-hot-ground-result.json`,
`r241-life6-3.txt`, CRC-checked read-only journal copies in `r241-physical-copy`,
`r241-carriers.json`, `r241-operations.json`. The first exploratory camera was
invalid (Y above ground); the corrected grounded scenario completed all 16 actions.

Focused verification: 15 fast-forward safety/absolute-time tests and guardrails passed.
The clean detached artifact was deployed into the same R241 world, retaining the
stalled residents. Full-pack native client observation then confirmed Ada's meal
completed (NOURISHED 1000, meal NONE, claim released) at tick 37,836. An ordinary
advance request during HOT was rejected at the unchanged tick 37,305 with the
exact PREPARED body/epoch. After client departure and body release, COLD advance
was admitted. All twelve settlements had no hungry residents at tick 44,422;
subsequent hunger cycles continued to resolve. At tick 89,479 five sales had
settled, active harvesting/mining continued and Redwillow had recorded new bread.
No new quarantine or inventory/scene conflicts appeared. Maximum host tick since
the corrected deployment was 58.7 ms; this is a bounded observation, not a global
latency guarantee. Evidence: build/r241a-hot-result.json, r241a-hot-advance.txt,
r241a-cold-observation.jsonl and r241a-deploy-verify.log.

## Missing production observations

At tick 71,210 Clearwater worker 7-9 was processing a station recipe; the job later
finished, but analytics had no corresponding output row. Static tracing found
that `StationWorkProcess.reduceColdStep(RECIPE)` invokes the common station
transformation directly. Analytics recognized `ProductionStationRecipeChanged`,
but omitted the equally explicit worker-owned recipe boundary. Which schedule
committed the conversion therefore determined whether it was counted.

The read-only facts adapter now recognizes COLD recipe steps, confirmed HOT
processing and applied retired processing receipts. It continues to calculate
only the actual inventory delta; transfers, preparation, unapplied recovery and
unchanged stock cannot manufacture output. A focused conversion test checks
three wheat consumed, one bread produced, unchanged-state suppression and
negative transfer/recovery cases. No production, stock, balance or time rules
are changed. Historical missing observations are not backfilled by inference.
Four economy projection tests and guardrails pass. The first new test fixture
incorrectly created a second account for an existing container; it was corrected
to use the existing account. Deployment and sustained-world validation continue.


## Work-scope hand custody after HOT → COLD → HOT

R241b's ordinary native Hearthvale → Clearwater → Hearthvale visit exposed
Maya Baker (`resident:6-25`), harvest job `job:site-harvest-6-wheat-field-7`,
with 19 wheat in both physical hand and canonical account but no physical binding.
The old scope `crop-62-r404164` was closed; its successor `crop-52-r412109` conflicted.
The common body remained the same epoch 3 across native residence generations.
R241c bounded diagnostics confirmed `unbound-hand-without-settled-projection`.

Both harvest and station work explicitly removed the resource binding when
closing the work scope, although the common body retained physical custody.
The adapter tried to replace that proof with an entity-local carry tag in an
asynchronous post-command callback. A body already saved/unloaded could miss that
callback. The successor then correctly rejected an unwitnessed nonempty hand.

Typed harvest/station release now validates its exact hand as before and closes
the scope while retaining the binding. The common body departure remains the
only owner of physical-inventory retirement; no scene receives authority to
release a still-physical stack. Generic untyped scene release still cannot bypass
the family's hand validation.

Existing isolated harvest conflicts may additionally use the field owner's
retired physical-work acceptance: exact job, last accepted work ordinal, layout,
epoch, actor/body, observed hand amount, and positively closed original scope.
An arbitrary matching stack, another checkpoint/body, or pending effect provides
no such permission. Recovery binds the existing accounted hand through the
existing atomic reconciliation, without replaying harvest or creating resources.
Evidence: `build/r241b-hot-result.json`, `r241b-conflict-snapshot.bin`,
`r241b-carrier25.json`, `r241b-fields.json`, `r241c-hot-result.json`.
This finding invalidates a complete HOT/COLD acceptance claim until retested.

## R241e: station fuel was allocated without material backing

The continuing same-world run found a second, independent starvation mechanism.
At tick 175251, Southgate's smeltery and forge each retained 1600 heat with no
ingredients or operation; its bakery retained zero heat and an accepted wheat
order. The first two imported coal were consumed by idle machines. Evidence:
`build/r241d-machine9.txt`, `build/r241d-station9.txt`, and the read-only copied
checkpoint probe `build/r241e-fuel-proof.txt`.

`ProductionStationSupply` previously traversed container IDs. The fuel owner
requested heat from output targets alone, and `ProductionStationEnergy.next`
preheated every fuelled machine to its maximum recipe load. Thus naming order
could consume the only fuel before an urgent, materially backed operation.

Fuel demand now requires actual accessible ingredients, an admitted ingredient
shipment or accepted worker input. The closed station capability composition
supplies that evidence; the common energy owner does not inspect job phases.
The same material bound guards fuel burning, including after snapshot recovery.
Station supply orders candidates by the production plan's output deadline,
with stable identity only resolving equal deadlines. Logistics still owns
ordinary claims and transfer; this does not move energy or refund past burns.

The copied live snapshot now yields zero fuel demand for both idle metal
stations and an urgent supported bakery demand. Tests cover an unbacked output
reserve through recovery, followed by real ingredient arrival; existing HOT
fuel decrement/replay and recipe lifecycle checks remain applicable. Empty
forge fixtures now supply their actual ingredients before requesting heat.
Economic prices, treasuries and initial stocks are unchanged.


## R241f: resume saved-body departure without a full actor sweep

The R241e native visit of towns 10 → 9 → 6 ended at tick 243311 with zero
retained conflicts; the client disconnected normally at 13:16:55. A bounded
COLD request at 244065 was correctly rejected for resident 10-30, despite its
native departure having been recorded at 13:15:09. It subsequently retired;
the next request waited on resident 10-5. This was delayed release, not authority
to bypass the physical-body guard or recreate either resident.

The body owner had no addressed wake when its exact save proof became available.
After a probe submitted an asynchronous carrier/operation journal write, another
full roster sweep supplied its next attempt. Save acknowledgement now wakes the
existing bounded actor observer for that exact resident. A departure which has
passed all physical, scene and inventory fences retains its next probe while its
journal/command acknowledgement is pending. Each probe revalidates current
identity, residence, absence and authority; rejected evidence does not grant COLD.
Ready actors rotate through the existing queue; no extra executor, body owner,
world scan or parallel mutation is introduced.

Focused verification: 41 existing/new actor, departure-save and operation tests
pass, including stale/missing evidence and pending write behaviour. Live latency
verification on the same retained world remains pending deployment.

Economic observations before this change: COLD reached tick 240407 with 64
settled sales; Mossbrook baked 27 bread after fuel delivery and its hunger fell
from 32 to 20. Southgate baked 5, then lacked fuel/cash; buyer-collection missions
were admitted for 211 of its wheat. Those are actual shipments, not spendable
forecast revenue. Full economic recovery is not established; no price adjustment,
resource injection or historical refund was applied.

R241f was deployed and revisited with the full-pack native client (10 → 6),
ending at 258259 with no retained conflicts. Resident 10-30's epoch-4 native
unload was recorded at 13:27:03; snapshot 258355 still retained its HOT ambient
scope. Source tracing found the remaining prerequisite: saved-position
checkpointing, carrier fencing and ambient drain/release each returned Pending
without retaining the addressed continuation. Those exact pending branches now
reuse the same bounded observer queue; missing/stale save evidence and rejected
commands still do not enter it. Existing queue fairness, departure-consumption
and body-lifetime negative coverage applies; no timeout or custody rule changed.

## R241h: unload before canonical insertion acknowledgement

The G revisit ended at 261932 without conflicts. Exact resident 10-30 closed
its ambient scope and body by snapshot 261717, observed 43 seconds after unload.
A subsequent COLD admission correctly refused resident 6-19 in PREPARED epoch 7.
Its copied native actor row proves residence 10 was actually inserted and saved:
current body departure, saved-body marker and retained `body-admission-v1` link.
The same row retains no unstarted insertion permission or old inactive carrier.
Both common departure paths accepted only RUNNING/AMBIGUOUS, while insertion
confirmation required an indexed live mob. Unload before acknowledgement thus
left a positive saved insertion with no permitted continuation.

The body operation owner now confirms that already completed insertion from
its exact saved native operation participant plus current saved departure.
It checks world, declared actor/body/epoch, residence, operation ticket and full
fingerprint; prepares, applied-only, rejected, stale or foreign evidence cannot
admit it. The existing canonical admission command and operation receipt closure
are reused, followed by the ordinary saved departure path. No entity is created,
no custody check is skipped, and this does not award any activity completion.
Negative/round-trip assertions extend the existing body admission operation test.
