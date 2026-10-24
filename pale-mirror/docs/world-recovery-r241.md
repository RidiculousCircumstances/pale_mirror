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
