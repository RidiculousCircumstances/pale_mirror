# Published economic plans and native recovery: R245–R249

The accepted scope is implemented: execution and diagnostic reads no longer run the
settlement economic solver; native effects distinguish preparation, a real attempt,
a proved refusal without effect and an unknown outcome. Two live defects found
while verifying this scope were repaired in the shared container and shipment owners.
This report does not claim general economic balance, a complete field cycle or a
global80ms server-tick bound.

## Source and running build

- `f1baa8330`: native-effect preparation/outcome protocol and field conflict propagation.
- `7b54d42ba`: durable settlement plans, addressed invalidation and all active readers.
- `09e18015f`: exact initial container admission and pending terrain visibility.
- `e3410faac`: read-only oracle recognises confirmed delivery by its current harvest job.
- `4c3fad007`: exhaustive station shipment port persistence and schema303.

R249 runs in `/home/rd/far-frontier-server`, world
`frontier-v3-r249-clean-20261025`, seed126, rules
`frontier-v3-quarry-graybox-r26`. Installed JAR SHA512:
`4507b1784e1a71778fb392234b907dbacc9f2e377de11a7891177b210d35b2b56571d2688ca187f5dd9c0580e2af8fa9aa5bf54a09eea1cc43f45e31fbb71afe`.
Fresh startup and both subsequent recovery checks passed the existing deployment
verifier. Final receipt: `build/r249-graceful-verify.log`, PID110388,
notBefore1792876783. A process identity is evidence for that start, not permanent health.

## Causes and boundaries

Economic execution used to assemble facts and solve on reads, including queries
made once per station or worker. `GoodsParticipantProcess` now publishes the accepted
plan. Readers combine it with current exact stock and commitments. Addressed input
changes request one coalesced review in their own WAL transaction; unrelated movement,
crop age and unchanged physical-save receipts do not invalidate economic inputs.
Concurrent producers share retained production ceilings. No economic price, reserve
or subsidy policy was adjusted to obtain a performance improvement.

Native writes used to consume an attempt before geometry exclusions were ready, and
boolean results could hide an unknown field outcome behind an endless WAIT. Shared
admission prepares fences before each attempt. An unresolved old effect remains an
explicit local restriction; matching its historical preimage never authorises replay.
The first original historical refusal was not logged, so its retrospective cause is
not asserted. See `native-operation-preparation-r245.md`.

A second real terrain blocker was an adjacent existing container awaiting independent
effect recovery. Its exact declared physical image may now be PRESENTABLE_PENDING:
terrain can be transmitted while new HOT effects remain forbidden. Missing/foreign
providers still wait. This was confirmed against actual Sodium renderer bytecode and
native neighbouring-chunk receipts, not inferred from a camera alone.

The player's new chest rejected deposits as CUSTODY_NOT_ACQUIRED:MISSING until a
slow discovery pass. Presentation selection ignored an absent lease when contents
already matched. First interaction now uses the existing declaration/observation/
acquisition owner immediately and rereads its successor; no pending or released
custody can be bypassed. See `container-first-interaction-and-visibility-r247.md`.

Finally, real recovery turned a smelter OUTPUT shipment into FUEL: the codec wrote
both as2. The exhaustive mapping is now INPUT1/FUEL2/OUTPUT3. Existing tests recover
the actual dispatch event and state before pickup, then perform delivery. Formats301/
302 irreversibly lost the nominal type and are rejected before hydration by303; the
temporary migration was removed. Old world `frontier-v3-r241i-clean-20261024` is retained
unchanged for diagnosis. No inferred port, invented receipt or balance edit was used.
See `station-output-port-recovery-r249.md`.

## Verification and measurements

Relevant retained checks include1864 frontier tests,55 native economy GameTests,
focused adapter/recovery checks and guardrails. After the two evidenced adapter
fixture failures were fixed,29 adapter plus18 publication tests passed. R249 separately
passed42 affected frontier tests, runtime recovery tests and55 native economy
GameTests. Detached release/package verification passed with these applicable test
receipts reused. This is not a claim that a new full858 adapter gate was run.
The earlier full408 native gate had two excluded combat/patrol failures; those
unrelated features were not repaired or promoted by this scope.

On the identical frozen input, all36 station event hashes matched. Sum of per-station
query medians was18.069→3.683ms (about4.9x); allocated bytes3,930,200→1,358,784
(about65.4% less). Receipts: `build/r245-station-allocations.txt` and
`build/r246-station-allocations.txt`. These are query measurements, not overall TPS.

The retained problem area passed native visibility with first chunk receipt0.100s,
compiled target4.100s and inspected frame4.924s; revisit receipt0.200s/compiled1.300s
(`build/r247-field-hot.json`). This establishes the agreed ready-native visit bound,
not arbitrary first world generation, JVM startup or every settlement.

Fresh R249 completed ordinary COLD advance requests18000 and6000 with explicit
COMPLETED receipts (`r249-cold-advance-completed.json`,
`r249-cold-maturity-completed.json`). The settlement started actual work itself.
Native scenario `live-published-economy-harvest.json` then passed in creative:
`build/r249-economy-ground.json`, run2c34b659-ed27-44fb-97eb-78ba427478a5.
During2400 canonical ticks, the same job/worker/intent advanced physically from the
start of the work window to12 confirmed cells/48yield; oracle PASS:IN_FLIGHT. It did
not claim terminal delivery or completion of all256 cells. The client left for the
Overworld for10s, returned in3.200s, targeted compiled terrain and inspected a fully
ready field with0 mismatched cells and no pending/foreign change. Both linked frames
were inspected. The airborne creative-camera attempt was invalid and explicitly
excluded; the grounded scenario corrected it without changing product code.

Current-work recovery was also checked through the real runtime. The first systemd
stop had no full native-save receipt and is classified as abrupt, not graceful;
recovery retained the same job and52yield. The next stop used RCON `stop`, with all
four dimensions explicitly saved. After startup the same job resumed13→14cells,
52→56yield; an accepted settlement plan and unchanged treasury remained available.
Receipts: `r249-before-graceful.txt`, `r249-graceful-stop.log`,
`r249-after-graceful.txt`, `r249-graceful-verify.log`. This is a runtime recovery smoke,
not a separate declarative native restart promotion.

A final read-only native re-entry then verified that same retired job's physical
closure, not merely its absence from active work. Scenario
`live-published-harvest-terminal.json` passed (`build/r249-harvest-terminal.json`,
run c19af1d5-c1aa-4a70-afcb-0ae2d064150d): intent CONFIRMED with receipt
`observation:field-deferred-site-harvest-7-wheat-field-1`; both
physicalReceiptResolved/Confirmed true and completeForColdHotReceipt true. Field
mismatch0; linked frame inspected. Current stock was not used as a surrogate for a
receipt, because subsequent legitimate production can consume that exact output.
This establishes terminal closure of this job, not harvesting all256 cells or a
repeat full growth/harvest cycle.

COLD native-tick peak before first visit was41.66ms. In the measured stable HOT
window native TPS was about20, recent mean about3–4ms, short peaks about9ms,
ready-action lag0 and ordinary debt returned to0 (5 at the final returned-scene
sample). Navigation preparation still had outstanding requests; no empty-queue
or general throughput claim is made. First generation of the previously unvisited
area produced a1,776.8ms native-tick peak and about19s ingress in the excluded camera
run. Its cost needs separate attribution; it is not hidden by the subsequent ready
visit measurements or claimed fixed. No new canonical quarantine was observed.

After the final restart, the subsequent bounded native re-entry sample had a72.93ms
cumulative peak, recent maximum21.20ms, debt1 and ready lag0. The previous1,776.8ms
first-generation peak remains recorded above; a restart does not erase that finding.
