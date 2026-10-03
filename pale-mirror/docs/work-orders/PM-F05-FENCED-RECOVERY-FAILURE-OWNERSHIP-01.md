# PM-F05-FENCED-RECOVERY-FAILURE-OWNERSHIP-01: keep crashes local and autonomy recoverable

Revision: 5. Parent slice: F0.5. Risk: critical-code.
Status: ACCEPTED_LOCAL at 2026-09-12T07:26:18Z.
PM / architect: Sol. Senior tech lead and sole coder: Terra,
gpt-5.6-terra, reasoning high.
Workflow: [PM/TL protocol](../engineering-agent-protocol.md), revision
2026-09-11-PM-TL. This is one complete stage outcome, not a sequence of
implementation approvals.

## Product outcome and acceptance

Make a restarted world resume every safely recoverable autonomous process even
when an old physical projection is still unloaded, while never duplicating a
body, cargo, container consequence or physical effect when that projection
later returns. Ordinary player damage, theft, obstruction, death and partial
delivery remain local events with an owning response; uncertainty freezes only
the smallest affected asset/front. Only actual canonical or persistence
corruption may quarantine the whole Frontier instance.

Complete F0.5 from the
[seamless foundation](../frontier-v3-seamless-foundation.md) and the explicit
[execution-semantics recovery boundary](../frontier-v3-execution-semantics.md):

- every body, cargo, container and effect binding has one monotonic durable
  authority epoch tied to its exact canonical owner/version. Recovery either
  reclaims the exact current binding, safely revokes it and resumes COLD under a
  newer epoch, or isolates the smallest ambiguous consequence; it cannot wait
  forever for a visitor;
- retain explicit `PREPARED`, physically attempted/running, observed,
  recoverably confirmed and ambiguous meanings across canonical WAL/snapshot,
  chunk/entity/container state and player custody. A flushed intent is
  authority to attempt, never proof that every participating save completed;
- every physical-capable family declares a stable recovery contract: actual
  save/acknowledgement boundary, bounded postcondition inspection, the exact
  reversible unconfirmed subset, ambiguity owner, retry/repair/abandonment and
  compaction policy. Missing, duplicate or incompatible ownership fails closed
  before recovery or execution;
- retain a bounded stale-projection tombstone/rejection fact with exact binding
  identity and epoch. A late naturally loaded old body, cargo carrier, container
  or effect is rejected, removed or locally reconciled without a second domain
  event, false death/theft, replacement actor/resource or replayed effect;
- external and player custody is never blindly rolled back or overwritten from
  canonical history. Only explicitly reversible unconfirmed pose/checkpoint
  state may roll back after an abrupt crash;
- classify failure as a normal domain disruption, smallest-owner reconciliation
  ambiguity or canonical/persistence corruption. `CONFLICT` is not a terminal
  wastebasket: every expected disruption/ambiguity has a visible reason, owner,
  bounded next action and retention/compaction outcome;
- unrelated settlements, processes and fronts continue while one asset/front is
  ambiguous. Whole-instance quarantine occurs before mutation only for broken
  conservation/ownership, invalid persistence or duplicate current authority;
- preserve the accepted F0.4 parent/front/decision ownership and exact F0.3
  custody model. Recovery and failure routing cannot introduce another roster,
  cursor, resource ledger, effect history or scene-local strategic decision.

Required evidence is the existing F0.5 programme, not a broader reliability
campaign:

- deterministic domain/codec/snapshot/WAL evidence for monotonic epochs,
  reclaim/revoke/isolate decisions, fail-closed recovery-contract ownership,
  bounded retry/abandonment/compaction and old/stale byte rejection;
- graceful and authenticated abrupt-crash coverage at the applicable PREPARED,
  physical-attempt/RUNNING, observed-unconfirmed, DRAINING/release and effect
  boundaries. Correlated semantic milestones, not sleeps or enlarged timeouts,
  identify each crash window;
- partial physical/player save and canonical confirmation in both arrival
  orders. The outcome preserves exact conservation and custody, neither replays
  a consequence nor rolls back unrelated/player-owned state;
- one no-visit liveness flow resumes safe COLD work under a newer epoch before
  the stale location is loaded, then naturally loads it and proves rejection or
  local reconciliation without duplicate progress;
- body, cargo, container and effect bindings each have positive and stale/
  ambiguous recovery evidence at the cheapest faithful tier; native flows cover
  the genuinely Minecraft/player-save-dependent seams rather than promoting a
  synthetic fixture;
- late stale bodies/cargo cannot emit false death/theft, ordinary player damage
  cannot quarantine the world, and an injected true conservation/ownership or
  persistence corruption quarantines before any further canonical mutation;
- report M0/M1/M2 only where the retained evidence establishes them. A
  task-private Xvfb is not human/M3 evidence.

This closes the F0.5 recovery/failure foundation only. It does not close the
separate natural-generation shutdown risk V3-AUD-055, natural-terrain/Foundry
hardening, additional gameplay/materialization breadth, combat balance,
F0.6 HOT/COLD calibration/scale, human comprehension, deployment, publication,
v2 removal or release acceptance.

## Context and continuation

- Canonical governance and ledger:
  `/home/rd/proj/pm-governance/pale-mirror`; protocol revision
  `2026-09-11-PM-TL`.
- Implementation worktree:
  `/home/rd/proj/pm-f02c-projection-provider-snapshot-30/pale-mirror`, branch
  `terra/f02c-projection-provider-snapshot-30`. Start from accepted clean F0.4
  candidate `c8be658f01ac5adb1a26a489c8b89648427a758f`, tree
  `8cc658a8c4fc1b014153e637e709193407278c0c`, schema147/envelope58 and JAR
  SHA-256 `45fb5d4bf2e1ba9d9131033550c64cfd3f45fe25f2153208dcb0a008a64096ce`.
- F0.4 acceptance and evidence are recorded in its work order. Reuse unchanged
  310-GameTest/1,337-JUnit, native return and JFR results; do not re-prove them
  merely because F0.5 starts.
- Normative owners are `frontier-v3-fenced-recovery-flow`,
  `frontier-v3-restart-recovery`, the existing process/scene SDK and FND-06/07.
  Close V3-AUD-049 and V3-AUD-050 truthfully; do not relabel V3-AUD-055 or other
  materialization/natural-terrain debt as solved.
- Existing body, cargo, container, effect, physical-intent, replica, front and
  custody implementations are technical context to converge. They are not a
  requirement to retain a disproved recovery design.

Terra owns current-path investigation, technical design/decomposition,
algorithms, schemas/codecs, implementation/test/harness changes, failure
classification, test adequacy, technical self-review and all ordinary
correction/retry decisions through one working F0.5 result. There is no
METHOD_READY, file allowlist, per-run permission or PM technical-method review.

## Authority and resources

- Writable boundary: F0.5-related source, tests, build/harness wiring and
  technical implementation documentation in the implementation worktree, plus
  isolated task-owned temporary/runtime/evidence roots. A required fresh-world
  schema/envelope cut is authorized; no compatibility reader for rejected
  development worlds is required.
- Private coherent commits and abrupt termination of exact task-owned crash-test
  server/client processes are authorized. No push/publication, production/live
  deployment, v2 removal, history rewrite, unrelated service/data mutation,
  broad repository cleanup or destructive evidence deletion.
- Reuse existing caches, persistent-client tooling and F0.VC/F0.VB acceleration.
  Local iteration uses the cheapest faithful lane. One terminal critical gate
  follows a coherent candidate. Any genuinely new complete native matrix uses
  the established four isolated worker slots; focused representative native
  flows need not be inflated into a complete matrix. No speed-ratio proof or
  unchanged-infrastructure recertification is required.
- Use task-private loopback ports and Xvfb if necessary. Physical `:0` remains
  excluded and private Xvfb is not human evidence. Preserve unrelated PID
  `2330125` on `:25565`, accepted evidence and original-workspace WIP.
- Raw evidence remains under the project 64 GiB cap. External provider/runner
  dispatch, public repository changes, deployment and destructive cleanup need
  separate authority.

A product/public-persistence contract contradiction or missing external
authority is `NEEDS_DECISION`; a red test or ordinary in-stage design change is
not.

## Verification economy and supervision

Use focused deterministic recovery/negative tests before Minecraft/native
tiers. Classify every failure as product defect, invalid method, infrastructure
or metadata gap before widening evidence. A runtime-only crash boundary may use
one bounded instrumented diagnostic with competing hypotheses and a stop
condition; never retry only for confidence or lengthen a timeout as the fix.

The terminal candidate receives one applicable critical-code gate and only the
native crash/player-save evidence required above. A fresh JFR or performance
campaign is unnecessary unless F0.5 changes a budget/limit or introduces an
actual performance claim. Reuse unaffected accepted results and recover missing
metadata from artifacts before repeating work.

Whole-blocker chronology starts at dispatch. PM performs one bounded liveness
check after each ten minutes of Terra silence and one hourly whole-blocker
product/cost audit. Two consecutive hourly audits without meaningful progress
or uncertainty reduction require an impasse review before an equivalent cycle.
These reviews do not inspect intermediate implementation or approve methods.

## Delivery and next boundary

Return one coherent terminal packet mapping every criterion to automated,
Minecraft/native, partial-save and abrupt/graceful recovery evidence. Include
candidate/tree/schema/envelope/JAR identities; exact executed report counts;
architecture/audit status; technical self-review; retained ambiguous-state and
compaction bounds; materialization levels and unproved claims; worktree/WIP and
owned-process state.

PM accepts product/architecture conformance or returns one consolidated existing
contract gap without prescribing the fix. F0.6 cannot start before F0.5
acceptance.

## Independent final-review return — 2026-09-12

Candidate `d23ba4145e2cac6a069c53a95a45d115059ef58a`, tree
`92b8229beab2efd452e1c92183bba3ea7b00ece4`, schema 148/envelope 59 remains
the current clean technical candidate. Its packaged JAR SHA-256 is
`39d649969beac54386a187596d80f34bee8587e1a90cded815f9ec6e41fd1601`.
The terminal critical gate, affected-module JUnit evidence and graceful native
receipt remain reusable; no confidence-only repeat is requested.

F0.5 is not yet accepted because the returned F0.5 native evidence contains
only a graceful restart of one current body binding. Its final manifest
explicitly records `recovery.mode: graceful`, and no successful F0.5 artifact
records an authenticated hard-crash boundary. The same packet also does not
bind the required partial physical/player-save and canonical-confirmation
outcomes in both arrival orders to this F0.5 candidate. Deterministic state and
GameTest coverage is valuable but cannot replace the explicit hard-crash and
split-save requirement in the seamless foundation and execution semantics;
graceful restart alone is expressly insufficient there.

Return to `EXECUTING` for one consolidated remaining outcome: establish the
smallest faithful candidate-bound evidence that closes the applicable
PREPARED, physical-attempt/RUNNING, observed-unconfirmed, DRAINING/release and
effect hard-crash windows, including both partial-save arrival orders where a
physical/player owner participates. Map each required boundary to its exact
evidence tier and explain any genuinely inapplicable boundary. Reuse all
unaffected green evidence, and do not repeat the terminal critical gate unless
source/artifact inputs that support its claim actually change. Terra retains
full ownership of the technical method, scenario decomposition, corrections
and justified execution.

## Independent final-review return — 2026-09-12, revision 3

Candidate `7fe0176d8f73d2d6558a74164043e5bb8796d6ec`, tree
`da296b9385e16400eeb50a46a58f3f443ed958fb`, schema 148/envelope 59 is clean
and retains packaged JAR SHA-256
`39d649969beac54386a187596d80f34bee8587e1a90cded815f9ec6e41fd1601`.
The three semantic hard-crash windows, physical-first raw player-save result,
graceful receipt, critical gate and unaffected deterministic evidence remain
reusable. No confidence-only repetition is requested.

F0.5 remains unaccepted at one narrower existing criterion. The canonical-first
manifest authenticates a crash after the durable observed handoff and records
the pre-crash player save with zero wheat. After restart it proves only that
the canonical resource accounts report source/player quantities 32/32. The
`resource` diagnostic renders `FrontierWorldState.inventory().fungibleResources()`
and its retained `PLAYER_SLOT` address; it does not read the reconnected
Minecraft player's actual inventory. The observation adapter also documents
that it never writes player stacks and treats an existing player-custody
account as already claimed. Consequently the retained result cannot distinguish
correct recovery from a phantom canonical quantity whose real player slot
remains empty. That would lose player-owned physical custody while the test
still passes.

Return to `EXECUTING` for only the smallest faithful closure of this gap. Bind
the canonical-first hard-crash result to the actual authenticated reconnected
Minecraft player inventory and prove that its concrete item kind, slot/count
and the canonical source/player quantities agree with exact conservation after
recovery, without duplicate replay or rollback of unrelated/player-owned
state. The recovery/fencing behavior itself, not merely a stronger assertion
over the same divergent state, must satisfy the existing F0.5 contract. Terra
owns the technical diagnosis, design and cheapest valid evidence. Reuse every
unaffected receipt; rerun the terminal critical gate only if relevant
production/artifact inputs change.

## Independent final-review return — 2026-09-12, revision 4

Candidate `55830aa54834d7443e01656b432ceba82ddea9fa`, tree
`1813ac828b8f9e18006f4a9a67afca96318a1443`, is clean and packages JAR
SHA-256 `06e4d62cb00f47d983883dd22068742872867b970685d3dca8710072320a8025`.
The new canonical-first native manifest validly proves an authenticated abrupt
crash, pre-crash player-save count zero, reconnect and an actual live player
slot containing exactly `minecraft:wheat` x32 in agreement with canonical
source/player quantities 32/32. That evidence and every unaffected earlier
receipt remain reusable.

F0.5 nevertheless remains unaccepted because the recovery behavior used to
obtain that result violates its existing no-replay/player-custody invariant.
Every runtime start captures every canonical player account as pending. On the
first matching player login, an empty retained `PLAYER_SLOT` alone authorizes
materialization of the complete canonical stack. The pending fact contains no
durable discriminator proving that this exact account/binding crossed the
canonical-first partial-save crash window. The implementation also does not
exclude the same stack having been legitimately moved elsewhere before an
ordinary restart. An empty bound slot can therefore mint a duplicate from
canonical history rather than isolate/reconcile the physical discrepancy. A
nonempty incompatible-slot check does not close this empty-slot replay case.

Return to `EXECUTING` for this one product correction: only an exact durable
unresolved partial-save boundary may authorize recovery of player-owned
physical custody. An ordinary restart with an empty, moved, consumed, dropped
or otherwise physically divergent player stack must not create a replacement
from canonical state; it must follow the existing bounded observation/local-
ambiguity contract without overwriting player agency. Retain the successful
canonical-first crash/reconnect proof, but add the cheapest faithful negative
evidence that discriminates crash recovery from ordinary empty-slot divergence
and prove exact-once conservation. Terra owns the technical design and test
method. Reuse all unaffected gates/receipts, and run one final applicable
critical gate only for the resulting production candidate.

## Independent final-review return — 2026-09-12, revision 5

Candidate `99f050396960751785eea90110fc89c866f0388a`, tree
`e5bc15ec0afc35d36a9e791d868288950685b64b`, schema 149/envelope 60 is clean
and packages JAR SHA-256
`4093c9a8ca87173bc472dfb3bf09e7cdecdf94887d212ca63cab10721d3e9189`.
Its terminal critical gate is green with 313/313 GameTests and 1,350 JUnit
tests. The earlier HOT-bomber red was a parallel GameTest-cell overlap and its
test-only correction is accepted within scope. The canonical-first native
receipt and the new ordinary-start marker discriminator remain valuable and
reusable.

F0.5 remains unaccepted at the same exact-once boundary. A canonical-first
recovery with no saved witness chooses `MATERIALIZE` and writes the recovered
stack into the live slot, but that recovery does not establish a durable
completion witness for the same fence. The player therefore continues to carry
the canonical unresolved fence after restoration while its persistent marker
remains absent. If the restored stack is later legitimately moved, consumed or
dropped and that player state is normally saved, the next restart again sees
an empty slot plus an absent witness and may materialize the same canonical
quantity a second time. The current negative covers a player marker produced
by the original handoff, not this restore-then-save-then-diverge lifecycle.

Return to `EXECUTING` only for closure of this same replay path. A successful
partial-save recovery must itself reach a durable exact-once state such that a
later ordinary player action and restart cannot reuse the old fence to create
property. A crash before that completion must remain recoverable without loss;
a durable later physical divergence must remain local ambiguity/observation,
not a replacement. Prove the complete recovery -> durable player-save ->
move/consume/drop -> restart negative at the cheapest faithful tier and retain
the already accepted native seam. Terra owns the technical design and method.
Reuse all unaffected evidence and perform only one final applicable critical
gate for the corrected production candidate.

## Acceptance record

PM independently accepts the F0.5 product/architecture boundary at clean
candidate `158f5b938ea0a75fb11b6d7ae29f89d1b650e7fc`, tree
`1e3f91b141166d1142301f5c43b127375af1d043`, direct parent `99f05039`.
Schema 149/envelope 60 are the accepted fresh-world format. The packaged
NeoForge JAR SHA-256 is
`269967bf391c770f6887ea8697896598b2077aa4f42f7d204f35da333bc74e1f`.

The final correction makes successful canonical-first player-custody recovery
arm the exact durable player-save fence immediately after materialization. A
crash before the following ordinary player save remains the reversible missing-
witness window; once that save persists the restored stack and witness, a later
move, consumption or drop plus restart is classified as local ambiguity and
cannot reuse the old fence to create property. The connected regression proves
recovery, an actual `ServerPlayer` save containing the witness, later empty-slot
divergence and a fresh recovery runtime with no replacement and exact retained
canonical quantity.

Focused economy GameTests pass 24/24. The final applicable critical gate passes
in 3m51s with 314/314 GameTests, saved-world shutdown and 1,350 JUnit tests in
267 reports with zero failures/errors; build, packaged-JAR verification,
guardrails and repository hygiene are green. PM independently matched the
candidate/tree/JAR, report totals, terminal GameTest log and two-path correction;
no task-owned process or port remains.

The retained native evidence is deliberately composed rather than repeated:
the graceful receipt
`build/f05-native/fenced-route-recovery.20260912T034201Z-final/receipt.json`
has SHA-256
`3c732ad3942b35890d9825191d9b44d5a61b76d3f90dac61b02a37df49a89ae2`;
the accepted authenticated canonical-first crash/player-slot receipt
`build/f05-native/fenced-hard-crash.20260912T123000Z/receipt.json` has SHA-256
`0d583ec56b2bec5e742df261e1d13ec9a2493abdadc5332ec9ca05304b0faad7`
and manifest SHA-256
`062f014eddbb1623d5e819603fe71e6c6c5d7dfe9a8441c3ba7c1f1e1e4f26e0`.
Those receipts bind the unchanged Minecraft crash/save seams on their recorded
earlier artifacts; the current candidate's new completion-witness behavior is
bound by the focused and full current GameTests, as revision 5 required. The
physical-first manifest SHA-256 remains
`44977323b0090158cabe48b9f89b3a50b0a753a9587b977dda83d7e556c22016`.

This accepts fenced recovery, no-visit safe COLD resumption, stale-projection
rejection and smallest-owner failure isolation at the F0.5 foundation level.
It does not close V3-AUD-055, natural-terrain hardening, F0.6 neutrality/scale,
human/M3 comprehension, production cutover, v2 removal or release acceptance.
