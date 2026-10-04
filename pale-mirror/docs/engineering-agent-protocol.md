# Pale Mirror execution protocol

Status: binding, revision 2026-09-24-EVIDENCE-FIRST. This replaces the prior
[protocol](archive/engineering-agent-protocol_2026-09-22_pre_simplification.md).
Product contracts, safety, actual authority and acceptance requirements are
unchanged. Historical instructions are evidence, not additional workflow rules.

## Assignment and autonomy

The user's current assignment, recorded in CONTINUITY.md, determines roles.
Currently main is the sole executor: code, tests, diagnosis, technical review,
documentation and result. No subagents. No skill or old work order may restore
a PM-only prohibition, require Terra or introduce an intermediate permission gate.

If the user later explicitly restores paired work, the executor owns technical
design and the delivered result; PM owns product priorities, contracts and
conformance review. PM does not micromanage methods or duplicate diagnosis.
Only in that paired mode: check executor liveness after ten minutes of silence,
never more frequently; inspect its exact process handle/status, not intermediate
implementation. A healthy check needs no message. Silence is not failure.
Do not spawn or resume an agent merely because paired-mode rules exist.

Ordinary scoped refactoring, debugging, tests, harness repairs and justified
retries need no repeated approval. Escalate actual scope/contract/authority
changes, safety risks or evidenced impasses, not each failed command.
A work order states outcome, invariants, known contradiction, authority and
acceptance. It does not prescribe every file, algorithm, run or report.

## Context and communication

Canonical governance lives in /home/rd/proj/pm-governance/pale-mirror.
At session entry or loss of retained context, read its AGENTS.md and compact
CONTINUITY.md. Consult the active order and relevant contracts when scope,
authority or an affected invariant needs clarification; reopening unchanged
documents is not a prerequisite to resuming a well-defined repair. During uninterrupted
work reuse already-read unchanged instructions. Check a small diff/hash when
external changes are plausible; do not reload all files on each goal continuation.
Project-local skill routes are optional reference navigation, not topic-triggered
prerequisites. Read a selected skill fully, respecting host-required skills;
reuse unchanged retained context. Read only task-relevant linked references. Archived ledgers,
inactive orders and former operating models are not bootstrap dependencies.

Keep CONTINUITY.md current, concise (roughly 1000 words, maximum 1000 lines),
and factual. Archive historical detail with links, preserving authority,
contradictions, receipt identity and cumulative blocker cost. Do not keep a
second active ledger in a source checkout. Exact headings, risk-label reports,
separate execution checklists and duplicate plan synchronization are not required.
Keep enough durable context to resume safely, without making documentation a
prerequisite for an ordinary scoped repair.

Work in coherent blocks through implementation and relevant verification.
Communicate material progress/blockers concisely; do not end a goal turn merely
to restate a plan, restart context reading or produce ceremonial status tables.
Do not claim background monitoring when no live mechanism exists.

## Evidence gate and working-result priority

These are binding decision rules, not a new form, approval gate or reporting
ceremony. Apply them while choosing and implementing work:

1. A problem is actionable when it is observed in the actual product path,
   demonstrated by a reachable source-level counterexample (precondition,
   transition, wrong result), or is an explicit unmet contract/acceptance
   obligation. A synthetic fixture or plausible failure story alone is not
   evidence of a product defect. Label an unverified possibility as a risk,
   never as an established finding or completed diagnosis.
2. Static analysis of real production callers, authority, transition and
   successor state is the default way to find contradictions. A concrete
   source-level counterexample is sufficient to begin a fix; reproducing it
   first in a client is not required. For an unobserved hypothesis whose
   reachable failure cannot be shown in source, investigate only enough to
   discriminate it, then defer it if unsubstantiated. Do not implement a
   remedy for that hypothesis; continue diagnosing any separately observed
   product failure.
3. Before expanding a fix, identify the affected active path and exact
   terminal player-visible or contract result it must restore. Do not build a
   defensive subsystem, scanner, journal, registry or test harness solely for
   a conceivable failure. An irreversible-state hazard may justify a minimal
   guard only when a concrete reachable sequence or explicit contract duty
   establishes it; this rule never permits knowingly unsafe publication or
   waiver of required negative/recovery behavior.
4. Prefer one wired production vertical reaching its terminal/successor result
   to several inactive foundations. Every helper or test facility needs an
   immediate named production consumer or a specific unresolved discriminator.
   If an increment ends with an unused helper, the next increment must connect
   it, establish why it is required, or stop expanding it and resume a
   demonstrated blocker. Preserve WIP; this is not destructive-cleanup authority.
5. A test must be able to change a diagnosis, implementation decision or
   acceptance claim. Use the smallest faithful focused regression for changed
   logic. Use a native/client probe early only if it discriminates a question
   that source analysis cannot settle; use it later when a claim requires real
   physical or player observation. Synthetic negative tests prove their modeled
   case, not the product. Do not recursively validate test infrastructure,
   repeat unchanged matrices or add broad campaigns for reassurance without a
   concrete method failure or decision it would resolve. Rerun when a relevant
   correction or material unanswered risk makes it useful.
   Do not retain an exploratory test merely because it passes. Before adding a
   permanent test, name the reachable defect or contract invariant, the
   production boundary it exercises, and the decision its failure would change.
   If the fixture omits the live condition under investigation, discard that
   experiment after reading its result rather than treating a green baseline as
   regression coverage. Prefer extending an existing focused test to growing
   a separate test suite for every diagnostic hypothesis.
   Audit an existing failing test by the same standard. Separate a product
   contradiction from invalid setup, obsolete contract, fixture interference
   and an over-specified assertion. A fulfilled semantic arrival must not fail
   merely because it took fewer samples than an assumed minimum. Native fixture
   coordinates, declared world bounds and canonical body authority must agree;
   do not weaken production validation to admit an impossible fixture. Retiring
   a test requires naming the removed obligation or its valid replacement,
   not merely a slow/red result. Unclassified failures remain open; changing
   gate selection does not turn a failed release gate green. Prefer a focused
   repaired case before another full aggregate, and record unrelated debt
   explicitly instead of silently widening the current feature.
   When an implementation path is retired, remove tests of its private
   mechanics rather than maintaining a test-only execution path or restoring
   obsolete production APIs. Establish retirement from real callers, not the
   test name or a red result. Preserve live semantic invariants and recovery/
   negative coverage through the current owner; adapt a useful existing test
   to that owner when needed. Record what became obsolete and what coverage
   remains. Compiling the retained test inventory and focused affected checks
   suffice for test sanitation; this alone is not native/product acceptance.
6. At each coherent increment, ask what now works through the active call graph
   that did not before. If only tools, tests, receipts or inactive APIs advanced,
   report plainly that no product result was delivered. Do not start another
   such increment until its consumer is connected or a demonstrated blocker is
   re-prioritized. A theoretical risk must not displace a reproducible player
   failure merely because it admits more tests.
7. Keep evidence labels distinct: observed/reproduced, source-proven,
   contract-required and hypothetical. Passing a unit test proves its modeled
   case only. Close a player-visible fix after its active production path and
   relevant negative/recovery behavior are checked with evidence appropriate
   to the claim. This is a finite acceptance decision, not an indefinite proof
   campaign.

## Diagnosis and verification economy

Start from the strongest observed contradiction and trace the relevant owner,
transition, custody and terminal/successor path. Choose the next source audit,
focused regression, instrumentation or native check by information gain and cost.
No fixed technique is mandatory first. A method incapable of observing the
current subject is not product evidence; fix the method without permission churn.

For a cross-layer product defect, use source inspection as the default diagnostic
route before another graphical/client run. Read the affected authoritative
transitions and their callers, persistence/recovery, projection, physical
observations and output/custody edges; record concrete contradictions and repair
them together. Use small focused tests to check uncertain logic. Do not use a
client run to rediscover a defect already visible in code, or as a substitute
for following the full source path. Once the source path is coherent and the
remaining question genuinely needs runtime/player evidence, the current
executor personally runs the relevant client check and inspects its result.
This is an ordering and ownership rule, not a demand to prove source correctness
with certainty or a ban on a discriminating runtime probe when code alone cannot
answer a specific question. Do not delegate routine client debugging to the
user; ask for human product judgement only where it adds distinct value.

Trace the affected lifecycle end to end before dividing its repair into local
verification cycles: admission/selection -> physical execution and observation
-> unload/interruption -> COLD continuation -> HOT return -> terminal/successor,
including the changed persistence and resource edges. Inspect the real callers
and authoritative transitions, not only the helper currently being edited.
Repair the source-proven connected contradictions as one coherent increment;
do not repeatedly discover the next predictable adjacent mismatch through tests.
This is a scoped source review, not another repository-wide audit or a demand
for certainty before editing. During that increment, compile when useful and
run a focused test only for a named unresolved question or the completed changed
contract. Do not launch a verification cycle merely because one helper is done.
Once the path is coherent, prioritize its integrated product check over more
isolated confirmations; reuse unchanged green evidence. A passing local boundary
is not an end-to-end result and does not justify postponing the affected product
loop until every independent family has been exhaustively retested.

Workflow autonomy takes precedence over historical method prescriptions in
orders and tooling guides: no compulsory all-tools onboarding, context-query
sequence, per-edit native run or report ceremony. Choose methods for the current
question. This does not waive technical invariants, applicable integration/release
gates or evidence required for a claimed player-visible result.

This precedence includes current skill/tool wording, not only archived orders:
instructions to query a map, invoke a selector, read an unchanged file again or
run a particular test first are not mandatory method gates. Read selected
skills fully, retain their technical and safety constraints, and choose
the actual diagnostic method independently. Project-local reference selection
is demand-driven; host-required skill instructions remain binding. Exploratory client observations
are allowed but cannot be presented as repeatable native acceptance. Record
material decisions/results, not a separate checklist or receipt for every edit.

No additional workflow gate may be inferred from an old work order, run number,
checklist or skill example. When method wording conflicts with this autonomy,
retain its substantive safety/acceptance requirement and omit the prescribed
ceremony. Do not remove product criteria or weaken a failing test under this rule.

Classify failures as product, method, infrastructure or missing delivery metadata.
Do not suppress failures, inflate timeouts to conceal non-progress or repeat
unchanged attempts blindly. An evidenced harness deadline error may be corrected
with a finite hang bound and unchanged semantic acceptance; this requires no
separate permission and is not proof that the product works.
A rerun is appropriate when it can resolve a material remaining risk or follows
a relevant correction. Recover missing receipt facts instead of repeating sound
work for paperwork. Reuse accepted F0.VA/VB/VC tooling and dependency-valid evidence.
Parallelize independent matrix lanes when available workers and isolation make
that materially faster. Worker count is an execution choice, not a prerequisite
for diagnosis or acceptance; workers and speed ratios are not product criteria.

Select focused checks and negative/recovery cases for changed behavior, not
for each edit or touched file. Existing dependency-valid coverage is reusable;
neither a documentation edit nor a private checkpoint triggers a new campaign.
Applicable full gates run at integration milestones/releases, not every edit
or private checkpoint. Commands and module additions are in release-verification.
Documentation-only feedback uses patch hygiene and the affected contract check,
not the entire simulation/tooling gate. Shared `guardrails`/`check` retain source
safety and architecture checks; independent visual-generation experiments have
their own scoped gate. Test deletion selects compilation of the retained
inventory, never a filter for the removed class. A path keyword cannot choose
a native campaign; unknown ownership requires explicit engineering selection,
not automatic execution of the complete milestone gate.
Do not widen a test campaign simply because a helper changed; do not omit an
affected boundary merely to obtain a green result.

Assess cumulative cost and information gain when results stop changing the
diagnosis, including context/report overhead. Keep useful existing command
timings/results, not a new logging framework or scheduled self-report ritual.
When work stalls, change the causal model or diagnostic method and communicate
the actual impasse/options. No fixed hourly review, two-hour reporting deadline
or artificial timeout governs a demonstrably useful operation.
Renaming a task, agent, revision or run never resets cumulative blocker cost.

## Evidence and completion

Source existence, unit/integration correctness, native behavior and human
comprehension are distinct. Bind each claim to its actual subject, artifact and
evidence scope. An intermediate phase, final inventory, clean new world or total
passing count does not prove the promised lifecycle.

Tests assert semantic transitions, not arbitrary sleeps. Wall-clock deadlines
detect hangs; canonical ticks and acknowledgements establish domain causality.
Native fixtures/diagnostics must not force-load chunks, edit canonical outcomes,
fabricate receipts or repair the subject. Isolated unit tests may construct
initial state. Runtime observability remains read-only, never another authority.

For player-visible corrections, the exact product candidate must resolve the
strongest known contradiction through the real affected composition. Applicable
terminal gates establish a RELEASE_CANDIDATE. Before HUMAN_CANDIDATE, join the
authorized exact deployment with an ordinary graphical full-pack client, follow
the complete relevant story through its terminal/successor outcome, capture
readable distinct frames and inspect them. Require the existing semantic
progress oracle/correlated evidence, not logs or endpoint snapshots alone.
Passing this preflight does not infer human M3, co-op or clean-room acceptance.

Correlate user observations to source/JAR/service/world identity. A mismatch is
a delivery incident; a matching observation is product contradiction. Do not
answer it with unrelated green tests or a new world. Human diagnostic help is
allowed when materially cheaper/more discriminating: state the current identity,
hypothesis and smallest useful observation; do not use the user as a repetitive
regression runner.

Formal candidate promotion and diagnostic access are different operations.
No complete automated promotion receipt or full lifecycle run is a prerequisite
for a useful exploratory inspection or targeted human diagnostic check. State
what remains unverified; do not turn diagnostic access into a readiness claim.
Existing deployment authority and operational safety still apply.

Completion accounts for every existing criterion, required negative/recovery
case and retained contradiction. Narrower or missing evidence stays unproved.
No mandatory ceremony, separate reviewer, evidence table, mutation campaign
or single monolithic carrier. Report changes, decisive verification, remaining
scope and owned-process state. Do not claim completion because work is expensive.

## Safety, resources and handoff

Preserve dirty WIP, histories and unrelated services. Repository access is not
permission to publish, deploy, reset or delete unrelated data. Confirm real
operational targets and existing grants; follow server-ops for authorized work.
No destructive cleanup, world reset, commit or push is implied by this protocol.

Choose isolated run directories to avoid collisions and preserve relevant
evidence; their count is not a workflow gate. Retained raw proof storage is
capped at 64GiB absent an explicit exception. Check capacity
before heavy work or on concrete risk. Retain compact evidence and reclaim only
exact authorized disposable targets; preserve disputed evidence and source.
Report material deletions and recoverability.

At interruption, preserve WIP/evidence and record the actual last action,
remaining blocker and exact running-process handle. Stop only task-owned
processes when authorized. A wait timeout alone does not justify restarting.
