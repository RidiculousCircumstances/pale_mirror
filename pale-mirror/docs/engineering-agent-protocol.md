# Pale Mirror engineer / executor protocol

Status: accepted by the user on 2026-09-05; applies to ongoing Frontier v3 work.

The slice lifecycle, workspace isolation, fresh-builder, owner-seam admission,
challenger, evidence-receipt and context-budget rules in
`engineering-agent-operating-model-v3.md` are binding. It supersedes v2; this
file continues to own detailed intervention, liveness, economy and safety
behavior.

Autonomy amendment accepted on 2026-09-05: delegate complete bounded engineering
outcomes, not predesigned patches. This amendment governs future orders and
explicitly amended active orders; historical evidence is unchanged.

Binding anti-micromanagement amendment accepted on 2026-09-06. The rules below
replace mandatory intermediate Gate A/B permission exchanges. They govern new
orders and all subsequent revisions of PM-CI-COMPILE-DEPS-01; historical
evidence and unrelated external/destructive restrictions are not retroactively
changed.

Binding liveness-cadence amendment accepted on 2026-09-07. It requires one
bounded check after every ten minutes of executor silence while an assignment
is `EXECUTING`; it does not restore implementation supervision or executor
heartbeats.

Binding execution-economy amendment accepted on 2026-09-08. It requires one
bounded historical cost review after each complete hour of an executing order,
using a work-order-local operation chronology. It detects repeated or
disproportionate work without restoring hourly implementation supervision.

### Normative force and precedence

This amendment is a role boundary, not advice. `MUST`, `MUST NOT`, `ONLY` and
`NO` in this section are mandatory. If an older work-order paragraph asks for a
routine intermediate handoff, exact implementation recipe, progress checkpoint
or separate permission for an already authorized local test/fix cycle, that
paragraph is historical and non-operative. The latest explicit work-order
revision still controls the bounded outcome and real safety/authority limits;
it cannot silently restore micromanagement.

The following reasons are exhaustive for engineer contact with, interruption
of, or intermediate inspection of an executing Terra assignment:

1. `ARCHITECTURE`: a product invariant, public/persistent meaning or owner
   boundary needs a decision;
2. `AUTHORITY`: an external, destructive, credentialed, paid, production or
   otherwise ungranted action needs authority;
3. `RISK`: concrete evidence identifies a safety or correctness risk outside
   ordinary executor-owned iteration;
4. `IMPASSE`: Terra reports an evidenced impasse or contradictory requirement;
5. `FINAL_REVIEW`: Terra has delivered a coherent terminal result for Gate C;
6. `USER_AUDIT`: the user explicitly requests an audit of current execution;
7. `LIVENESS`: the mandatory bounded ten-minute check of executor and exact
   task-owned process/job liveness, or resolution of concrete ownership/process
   uncertainty. It is not an implementation-progress inquiry.
8. `ECONOMY_AUDIT`: the mandatory bounded hourly review of accumulated operation
   purpose/duration/outcome metadata for avoidable cost, repeated failure or an
   inadequate verification method. It is not a source/diff review or a routine
   request for Terra to justify implementation choices.

Before acting, the engineer MUST name exactly one of these reasons in its own
working note and in any message to Terra. If none applies, contact,
interruption, intermediate diff/hash inspection and unsolicited design direction
are prohibited. While an assignment is `EXECUTING`, one bounded `LIVENESS`
check is mandatory after each ten minutes without an executor milestone,
exception or terminal packet, including across turns. It MUST NOT occur more
frequently; an executor event resets the interval. The check is limited to
collaboration status, the exact task-owned process/job handle and a bounded
progress marker such as state/exit/elapsed time or output modification time. It
MUST NOT inspect source, diffs, implementation choices or semantic intermediate
results. A healthy check causes no message or direction to Terra and no ledger
update merely to record a heartbeat.

Independently of silence, one `ECONOMY_AUDIT` is due after every complete hour
from the active order's recorded observation epoch while its status remains
`EXECUTING`, including across turns. It reads only the work-order-local execution
chronology, executor milestone/exception reports, exact command/job metadata and
bounded timing/status summaries in retained receipts. It MUST NOT inspect WIP
source/diffs or turn ordinary local design into an approval gate. A healthy
audit records one concise work-order audit row and causes no Terra contact. A
message is allowed only when the history supports a concrete actionable finding
such as repeated equivalent attempts without new evidence, expensive-tier work
before its faithful cheap discriminator, redundant accepted-infrastructure or
confidence reruns, unexplained no-progress time, avoidable serialization, or a
test whose cost is disproportionate to its product claim. State the evidence,
cost and required observable correction; Terra owns diagnosis and remedy.

## Mandatory autonomy and intervention boundary

- Delegate one complete, independently reviewable engineering result, including
  diagnosis, local design, implementation, verification and correction. Do not
  split an ordinary repair into separate permission-only work orders.
- Terra owns algorithms, helpers, file choices within the affected components,
  test design, command selection, sequencing and evidence-backed iterations.
  The initial grant includes the necessary local full gates within its declared
  resource envelope. Moving from focused tests to a full gate is not itself a
  reason to request permission.
- The engineer intervenes only for (1) architecture/product/invariant or genuine
  ownership/authority decisions, (2) an executor-reported impasse or an evidenced
  safety/correctness risk, (3) final review of a completed coherent result, or
  the explicitly enumerated `USER_AUDIT`/`LIVENESS`/`ECONOMY_AUDIT` cases above.
  Optional progress notifications never become mandatory approval checkpoints.
- Every blocking review finding must state the violated outcome/invariant and
  concrete evidence or falsifiable failure sequence. Mark an untested risk as
  such. Do not reject a solution merely because it differs from the engineer's
  preferred implementation, helper layout or an unnecessarily narrow file map.
  Terra owns the remedy; one consolidated review replaces serial micro-orders.
- No routine duplicate investigation, full-suite rerun, intermediate diff/hash
  inspection or re-audit of accepted evidence without changed inputs, a concrete
  contradiction or a previously identified open acceptance item.
- No approval for its own sake: each exception requiring an intermediate stop
  must name the real risk or missing authority. A command being expensive, a
  helper touching another file, or an ordinary test failing is insufficient.
- The engineer must not make itself the scheduler of Terra's local work. If
  Terra waits only for an ordinary in-scope test/repair permission, consolidate
  the grant at the next safe boundary instead of adding another checkpoint.

These rules do not waive tests, independent final acceptance, preservation of
WIP, sole-writer/resource ownership, or human product gates. New public or
persistent semantics, production operations, publication, paid infrastructure,
credentials and destructive actions still require the appropriate authority.
Authorize disposable test resources once with exact boundaries; do not seek
fresh approval for each ordinary operation already covered by that envelope.

### Enforceable supervisor self-check

Before contacting the executor or inspecting intermediate work, the engineer
must identify one permitted reason: a user-requested audit, a concrete
architecture/authority decision, an evidenced risk or reported impasse, final
acceptance, a permitted liveness check, or a due economy audit. If none applies,
do not intervene.
Elapsed time below the required interval, an automatic continuation and
curiosity about implementation are not reasons. The mandatory ten-minute
`LIVENESS` check is itself an allowed reason once due. The interval covers all
routine status mechanisms together, including messages requesting status and
process inspection; switching tools does not reset it. Source/diff inspection
never becomes allowed merely because the liveness interval elapsed.

An intervention names its reason in the message and states the outcome or
decision needed, not a sequence of implementation commands. Keep the bounded
operation chronology inside the active work order; do not create a competing
global supervision/continuity journal or require extra executor heartbeats just
to fill it.
Even when a defect is proven, prescribe the violated invariant and observable
acceptance result, not the helper, file layout, algorithm or command sequence.
Any implementation idea from the engineer is explicitly non-binding unless it
is itself a previously accepted architectural invariant or safety boundary.
Ordinary in-scope failed tests, launcher repairs and evidence-backed iterations
remain executor-owned. A progress notification does not require approval, and
the executor must not end an otherwise executable assignment merely to obtain
permission to continue. A real missing-authority boundary still requires a stop.

If supervision violates these rules, withdraw the unnecessary checkpoint and
return the complete remaining in-scope outcome to Terra. Do not compensate by
weakening acceptance or by adding another reporting ceremony.

## Mission and authority

Deliver the existing Frontier v3 objective under `frontier-v3-contract.md`,
`frontier-v3-execution-semantics.md` and `frontier-v3-implementation-plan.md`.
The durable objective is in `CONTINUITY.md`; the active goal in the supervising
session must express the same promise. This protocol governs delivery, not a
new gameplay architecture or a replacement plan.

The player experiences one autonomous 1024×1024 world with twelve settlements
and one distributed hive. Exact identities, resource accounting and history
survive HOT/COLD transitions and restart. Physical detail may differ; COLD
knowledge is bounded, observed consequences remain causal and unknown geometry
does not grant omniscience. No protected zones means accounted legitimate
effects, not unbounded work or instantaneous mutation of unloaded Minecraft.
Confirmed effects require the declared physical/canonical recovery protocol.
Production cutover and v2 removal wait for all required technical and human
product gates. A green test cannot substitute for unbriefed player acceptance.

## Roles and write ownership

| Role | Owns | Must not do |
| --- | --- | --- |
| Supervising engineer, normally Astra | Research, architecture decisions within accepted scope, work orders, normative docs, independent review, acceptance and the continuity ledger | Write or patch implementation code, tests, build/CI/deployment scripts or executable configuration, including an apparently trivial fix |
| One executor, `gpt-5.6-terra`, reasoning `high` | Diagnosis, local design, implementation, test design and authorized verification of a bounded engineering outcome | Redefine requirements, weaken tests or debt ceilings, cross approved boundaries, close its own gate or advance to an unapproved work order |
| User | Product promise, material scope/authority changes, external approvals and required human acceptance | No routine approval of individual helper methods is required |

The engineer may edit Markdown documentation and the declarative architecture
map. A normative YAML statement is documentation; executable YAML configuration
is executor-owned. The engineer may run read-only diagnostic checks or an
agreed verification command, but coordinates expensive execution with the
executor. Do not generate a source patch indirectly through a shell script or
other tool to evade the role boundary.

Exactly one executor has source-write authority. Confirm the requested model
and effort when spawning; do not silently substitute another model. Give it
only the work-order context and the required local reading, not the entire
conversation. The executor cannot recursively delegate without new approval.

## Safe adoption of existing work

1. Read scoped AGENTS, ledger, relevant contracts and mandatory skills.
2. Record both repository HEADs and dirty/untracked state. Existing changes
   belong to the user; no reset, blanket staging or synthetic import commit.
3. Obtain an explicit stop/handoff acknowledgement from the previous writer
   or the user, and inspect real host processes and evidence writers. A quiet
   diff or sandbox-only `ps` is not proof that another agent has stopped.
4. Let an identified current run reach its terminal safe boundary. Do not
   mutate its fingerprint inputs or compete for Gradle, CPU, ports, worlds or
   the one visible display. A handoff does not authorize killing arbitrary
   processes. If stopping a run is necessary, resolve ownership and obtain the
   required authority first; retain its failure/partial evidence.
5. Record the transferred state and exact remaining work. Only then grant the
   new executor source-write authority. Read-only investigation may run earlier.

The existing non-squashed monorepo/push gate remains ordered after local F0.VA
closure. Actual provider timing is advisory and may be observed during ordinary
later work; it is not a reason for a standalone certification campaign. This
protocol is not permission to migrate, deploy or delete v2 early. Each such
operation needs an explicit work order and the existing safety/verification
gates.

## Work-order contract

Use `work-orders/TEMPLATE.md`. Every implementation order has an ID and revision,
one bounded outcome, its parent F0 slice, an observed baseline and preserved WIP,
contract/invariants, state/authority transitions, expected files and allowed
write scope, exclusions, exact acceptance tests and stop conditions.

Expected files are a starting map, not a default exact-file whitelist. The
executor may choose algorithms, internal types, helper files, test fixtures and
focused tests within the approved component/path boundary without intermediate
permission. It explains material local choices in its delivery report. Exact
file whitelists require a specific safety or ownership reason, not reviewer
convenience. The engineer specifies outcomes and invariants, not helper method
signatures or internal data structures. Changing an architectural owner,
persistent meaning or public interface requires an amended order; merely using
another implementation file in the approved affected components does not.
A whole F0 phase is not an
adequately bounded order where several independently reviewable cuts exist.

Normative meaning comes from the accepted contract/map. If a work order or test
conflicts, the executor reports evidence and alternatives instead of choosing
the easiest interpretation. The engineer resolves within accepted scope or asks
the user for a product/authority decision. Neither agent may relax acceptance
to manufacture progress.

## Execution state machine and review gates

`DRAFT -> AUTHORIZED -> EXECUTING -> FINAL_REVIEW -> ACCEPTED`

`FINAL_REVIEW` may return to `EXECUTING` with consolidated findings; any phase
may enter `NEEDS_DECISION` for a genuine boundary or impasse.
The ledger owns the active order, revision, executor, phase and last acceptance.
An agent message is a notification, not a competing durable state database.

- Gate A (initial grant): authorize the outcome and its complete local test
  envelope together. Terra acknowledges and proceeds without waiting for a
  second implementation message unless it discovers a real conflict. READ_ONLY
  is reserved for explicitly diagnostic requests or an unresolved authority
  boundary; it is not the default prelude to every implementation.
- Gate B (optional consultation): no mandatory stop before local full gates.
  Use only for a named boundary, impasse or safety decision not covered by the
  initial grant. Terra otherwise continues through verification and repairs.
  No ongoing measurement is restarted for a reviewer status request.
- Gate C (final review): engineer reviews the stable final diff and maps each acceptance item
  to the exact code/test/result. Only the engineer records ACCEPTED and issues
  the next order. Acceptance of an order is not closure of its whole F0 slice.

Commit/release execution is assigned to the executor after explicit engineer
acceptance and only within existing user authority. Specify exact files/refs;
never include unknown WIP. Content changed after review invalidates acceptance
of the changed boundary and requires re-review/relevant verification.

## Communication and economical supervision

The executor sends a concise update at a stable milestone, material failure,
scope mismatch or blocker. Routine focused failures remain local iterations
unless they trigger the causal-review rule below. No unchanged heartbeat is
required merely because time has elapsed.
Use: order/revision, phase, completed change, test status, active command/run ID,
operation start/end or elapsed duration when known, next step and decision
needed. Do not send entire logs, JSON fingerprints or unchanged status
repeatedly; link exact artifacts and project only needed fields.

Each active work order contains a bounded execution-economy chronology. At a
stable milestone, material failure, heavy command completion or terminal packet,
the engineer records the operation class and purpose, known UTC start/end or
elapsed duration, result, evidence handle and whether it advanced, invalidated
or repeated a product claim. Unknown historical timing stays `unknown`; never
invent it. This is cost/progress metadata, not a raw command log, dialogue
transcript, second status authority or request for per-step reporting. Existing
executor events and retained receipts are the source; Terra does not pause work
to maintain the Markdown row.

After each complete 60-minute interval from the order's recorded observation
epoch, perform exactly one `ECONOMY_AUDIT` at the next available supervising
turn. Review the chronology as a sequence, not one isolated long command: ask
what product claim advanced, which tier consumed the time, whether the same
failure or evidence was repeated, whether accepted acceleration was used where
applicable, and whether the test method remains proportionate. Record a concise
finding and next audit boundary. If healthy, do not message Terra. If a concrete
problem exists, send one consolidated conceptual correction under
`ECONOMY_AUDIT`; do not prescribe helpers, files or command order. The hourly
audit does not reset or accelerate the ten-minute liveness cadence.

The engineer reviews a completed result, not every internal milestone or method.
During silence, perform exactly one bounded liveness check when each ten-minute
interval becomes due, including across automatic goal continuations; never poll
more frequently. Between checks, prefer event-driven waiting. The check reads
only collaboration state and an exact known task-owned process/job handle plus a
bounded progress marker. It does not read source/diffs or judge implementation.
If the executor is active or the owned handle remains live and attributable,
take no action. If the handle disappeared, a job is terminal but unreported, or
two due checks show neither executor activity nor any live/moving owned handle,
send one `LIVENESS` request; do not restart or kill anything solely from silence.
A long test is not a hang while its handle remains live. A passive wait timeout
is not evidence of failure. Avoid repetitive user-facing "still waiting"
messages where session rules permit; never claim progress or change the ledger
merely because another interval elapsed.

The executor owns routine investigation and test operation. The engineer does
not convert a reported implementation hypothesis into mandatory helper/state
design. For a reproduced defect, specify the observable successful and rejected
sequences; the executor selects the local correction and may replace its own
hypothesis with a better evidenced solution inside the component boundary.
Routine focused failures and fixes stay within the same implementation grant.
The engineer does not run a parallel duplicate investigation or inspect intermediate hashes and
files merely to track activity. Intervene at a delivery milestone, material
boundary question, evidence-backed risk or unexplained repeated failure.
Review findings state the violated requirement and evidence; the executor
chooses the correction. Do not require a rewrite just to prefer another valid
local design.

Independent acceptance means inspecting the stable diff and actual evidence,
with targeted reruns/adversarial checks selected for risk. It does not require
both agents to execute the identical whole suite. Mandatory gates still run on
the accepted identity and are independently checked; executor results can
satisfy them when their scope, identity and outputs are verified. Heavy/native
runs retain explicit resource ownership and safety approval, and may be operated
by the executor. No blanket delegation of destructive or external actions.

At most one local heavy verification/measurement owner and one visible native
client on DISPLAY=:0. CI correctness may run on the approved isolated workers;
timing follows its declared controlled-host policy. The test/run lease is an
explicit owner/command/path agreement in the work order/ledger, not a new
runtime framework to implement.

Do not edit source, scenarios, normative acceptance or launch inputs during an
identity-bound measurement. Finish doc/spec changes before preparing its new
identity. Governance notes cannot retroactively certify a historical run or
change its comparator. No historical green result is promoted beyond its scope.

After two consecutive attempts at the same failure without new evidence, stop
blind retries and request causal review with the smallest reproducer and a new
testable hypothesis. Do not lengthen timeouts, drop assertions, force-load or
reseed merely to get a pass. This local review rule does not redefine the
product's goal blocked/completion mechanism.

The supervising engineer is accountable for verification economy. This does
not authorize intermediate source review or implementation direction: it
operates at work-order, dispatch and acceptance boundaries. An independently
accepted shared test capability such as F0.VC is reused without requalification
unless its contract/input changed or contradictory evidence appears. Every
changed or previously failing native lane must first pass a faithful local
preflight/regression on the candidate before a new complete CI matrix is
dispatched. A provider-only admission or identity failure must be isolated by a
bounded pre-Minecraft provider preflight rather than another full semantic
matrix. Before every expensive repeat, the engineer records the product claim
or decision it can materially change, why cheaper evidence cannot answer it and
what outcome would alter acceptance or implementation. A repeat needs no
routine user permission when it proportionately confirms or falsifies a
material product invariant. It is deferred when it would only re-demonstrate
provider plumbing, infrastructure hygiene, confidence, timing, topology or an
already accepted claim. After one complete green terminal matrix, normally
proceed directly to independent review. If review changes source, comparator or
required evidence, the changed identity receives only the minimum newly
applicable qualification. The engineer owns this value judgment; a fixed run
count cannot substitute for it.

The mandatory proof budget for one product candidate is ordered and finite:

1. smallest deterministic localization of the changed or failing claim;
2. faithful local regression for every changed or previously failing lane;
3. at most one fresh local physical preflight, and only when runtime behavior or
   its observation boundary changed;
4. one complete terminal matrix on four simultaneous workers;
5. immediate independent review and stop.

An inapplicable step is skipped, not replaced by a broader run. Confidence,
benchmark and accepted-infrastructure reproofs are outside this budget. This is
the default economical path, not a categorical one-attempt prohibition. A
failure can justify another after a changed hypothesis/candidate has a smallest
cheap passing discriminator and the engineer records why the repeat has
material expected product value.

The budget is not a mechanical run counter. When a CI invocation launches no
Minecraft process and observes no product-semantic lane because a classified
deterministic provider transport or admission seam failed, it has produced no
semantic evidence. Terra may correct that seam without special user permission,
but redispatch still requires both a cheap faithful pre-Minecraft regression
through the actual failed provider shape and the engineer's recorded finding
that another matrix can materially advance a product claim or decision. All
unaffected accepted evidence is reused. An unchanged blind retry is forbidden:
every recurrence needs a concrete new cause, changed candidate and passing
discriminator. Semantic, Minecraft/runtime, flaky or unclassified failures
follow ordinary product/test review under the same value test. The hourly
economy audit compares realized value with the recorded expectation and stops or
replans work whose product value has not materialized.

There is also a categorical circuit-breaker against multi-day proof drift. If
the same acceptance claim remains in heavy proof work at two consecutive hourly
economy audits, or records four cumulative execution hours after its first
heavy attempt, stop at the nearest safe boundary. Separate provider queue time,
preserve all evidence, classify the remaining work as product correction, test
correction, infrastructure correction or no-new-value proof, and obtain an
explicit revised work-order decision before another heavy run. The supervising
engineer must trigger this boundary proactively; compute availability, sunk
cost and an executor's willingness to continue are not reasons to defer it.

The same circuit-breaker applies to disk retention. Raw native/CI workspaces
are temporary execution custody, not durable evidence. Once the engineer has
recorded terminal identity, result and a compact receipt/checksum, full clones,
Gradle caches, runner binaries/update payloads, prepared-runtime copies,
disposable worlds and verbose logs are reclaimed. Keep only source/Git history,
normative summaries and a minimum artifact for a specifically open disputed
claim. One local-heavy root and one provider root per active order are the
default maximum, with a 64GiB project-wide raw-proof ceiling. Crossing that
ceiling blocks another heavy launch until reclamation or an explicit named user
exception; free disk capacity is not permission to accumulate archives.

The binding 2026-09-08 causal-evidence policy in
[`frontier-v3-accelerated-verification-loop.md`](frontier-v3-accelerated-verification-loop.md#mandatory-causal-evidence-instead-of-timing-luck)
applies immediately: semantic correlated milestones and retained causal facts
replace timing-sensitive polling and positional snapshot assertions. Canonical
time remains part of exact domain verification; wall time only guards failure.
Repeated proof-sequencing failures require coherent scenario-level diagnosis
before another expensive run. Preserve recovery coverage and the test pyramid;
do not create a new infrastructure programme or intermediate approval gate.

### Mandatory methodological review

The supervising engineer owns the methodological validity of acceptance tests,
not merely their execution status. At specification/work-order definition and
again at `FINAL_REVIEW`, map every material claim to its actual subject,
preconditions and controls, causal observation, oracle, discriminating
negative/recovery case, execution tier and exact candidate/evidence identity.
Classify the result as: sound for the stated claim, sound only for an explicitly
narrower claim, or insufficient/unsound. Green execution cannot upgrade that
classification.

Before authorizing an expensive native or CI lane, the engineer MUST also make
an explicit adequacy-of-test-framing determination. The scenario must exercise
the promised product behavior through the intended authority, preserve a fair
control history, contain an oracle that would fail for a named plausible defect,
avoid fixture-created success, use the required evidence tier and justify its
cost relative to the cheapest faithful discriminator. An inadequate framing is
corrected in the work order or narrowed before the expensive run. Waiting for a
green, timeout or ambiguous result to discover that the test never addressed
the claim is a supervision failure.

When a milestone full gate is sequenced before native evidence, carrier
readiness is itself a cheap admission precondition. The final candidate must
already expose the required declarative scenario, read-only diagnostic facts,
causal oracle and negative/recovery discriminator before the broad gate starts.
Otherwise the gate is premature: adding the missing carrier changes the
candidate and makes another final-candidate gate necessary. Preserve the first
receipt, then authorize the replacement only through the ordinary material-
product-value rule; never interpret a mechanical run count as a ban.

The review must reject circular oracles, fixture-created outcomes, scenario
names used as evidence, comparison histories with unequal relevant inputs,
polling luck, stale/wrong-subject receipts, hidden normalization, and synthetic
proof substituted for a required Minecraft/restart/player boundary. It must
also ask whether the test would fail for the plausible defect it claims to
exclude and whether the cheapest faithful tier precedes expensive native work.
Code existence, automated semantics, physical-world integration and human
comprehension remain separate claims.

This responsibility does not transfer test implementation or routine local
design from Terra. The engineer reviews the claim-to-evidence method, not helper
names, algorithms or coding style. No new intermediate checkpoint is required
for an already specified method. If Terra must materially change a proof
carrier, oracle or claimed scope, it reports one bounded `RISK` boundary before
the affected expensive run so the engineer can assess the new method once.

## Evidence and continuity

For each acceptance criterion retain the code boundary, exact command/result,
source/build/spec identity, negative/recovery proof and limitations. Distinguish
implementation existence, automated correctness, physical-world continuity and
M3 player comprehension. Final gates use fresh evidence as required by F0.VA.

The engineer alone edits `CONTINUITY.md` during managed execution; the executor
sends proposed ledger facts. Compact or archive history through the existing
ledger rules, never create another root or agent continuity file. Work orders
retain specifications and review records, not a second active progress ledger.

After interruption/compaction, read the ledger and current order. Resume the
same ten-minute liveness interval across turns; inspect agent and exact command
handles when the scheduled check is due or ownership/liveness is otherwise
uncertain. Automatic continuation alone does not reset or accelerate the
interval. Resume the same executor if available. Replacing it
requires releasing the previous write/run ownership first. Preserve all WIP
and output; do not restart the project from an old plan paragraph.

Also resume the active order's recorded hourly economy-audit boundary across
turns. If it became due while no supervising turn was running, perform one
catch-up audit at the next opportunity and advance the boundary to the next
future hour; do not emit one audit per missed hour. If an identity-bound
measurement forbids documentation writes, perform the read-only assessment on
time and defer its single row until the nearest safe documentation boundary.

Continue accepted in-scope work while the supervising goal/session is active.
Do not claim invisible background supervision after termination. Human gates,
unavailable external services and new permissions remain real boundaries;
record what is unproved and ask the user when required. A completed local order
does not complete the overall Frontier v3 goal.
