# Work order <ID>: <one bounded result>

Specification revision: <N>. Parent slice: <F0...>. Risk: <docs/small-code/critical-code>.
Engineer: supervising root. Executor: gpt-5.6-terra, high.
Active phase and acceptance owner: `CONTINUITY.md`.

This template inherits `docs/engineering-agent-operating-model-v3.md`. New
product slices use a fresh Terra context and isolated executor branch/worktree.
Keep the order below 2,500 tokens where practical; after three substantive
revisions, close/supersede it instead of appending a troubleshooting history.

## Baseline and prerequisites

- Exact repository/ref and dirty working-content identity; WIP to preserve.
- Previous writer handoff and active command/resource ownership.
- Required contracts, skills, previous accepted order and unresolved facts.

## Outcome and exclusions

- One observable deliverable; why it is needed by the parent slice.
- Explicitly excluded gameplay, refactors, migration/deployment and future work.

## Contract and transition

- Canonical and physical state owners, identities and mutable state.
- Inputs, expected version/epoch, events, persistence/confirmation boundary,
  observations and success/failure/recovery postconditions.
- Exact invariants and any predeclared non-exact comparator.

## Write scope

- Approved component/path boundary; expected files are navigation hints.
- Executor owns diagnosis, local design, helpers and focused/full test choices
  within that boundary, without per-file or per-method approval.
- Forbidden files/changes; conditions requiring a revised specification.
- Engineer-owned normative docs; executor supplies proposed corrections.

## Acceptance matrix

| ID | Required outcome/invariant | Focused positive test | Negative/recovery test | Physical/final proof |
| --- | --- | --- | --- | --- |
| AC-1 | <contract> | <test> | <test> | <scenario or justified N/A> |

Declare required test outcomes and mandatory gates. Executor selects and records
exact commands within the grant. Define the complete local verification
envelope here, including mandatory full gates, sole heavy owner, permitted
disposable paths and resource limits. Grant it once, not before each command.
Native/destructive/external operations outside that envelope need their actual
authority; identify any such unresolved boundary explicitly. Specify what evidence may be reused
and what must be fresh, plus source/spec/build identity and artifact paths.

## Review and execution permissions

This order inherits the protocol's exhaustive intervention taxonomy. It MUST
NOT add routine progress checkpoints, implementation-recipe approvals or
separate permissions for tests/fixes already inside the declared envelope.
Historical Gate A/B wording is non-operative when a later revision grants the
complete outcome. While the order is `EXECUTING`, one bounded `LIVENESS` check
is mandatory after each ten minutes without an executor event and never more
frequently. It is limited to collaboration state, exact task-owned process/job
liveness and a bounded progress marker; it never includes source/diff review.
Record an execution-economy observation epoch below. After every complete hour
from that epoch while the order remains `EXECUTING`, the engineer performs one
bounded `ECONOMY_AUDIT` of accumulated operation-purpose/duration/outcome
metadata. It is not an hourly code review or executor heartbeat.

- Initial grant (Gate A): authorize diagnosis, implementation, local focused
  and full verification, and corrections together. Acknowledgement does not
  require a second permission message. READ_ONLY only for a named reason.
- Optional consultation (Gate B): name any actual unresolved safety/authority
  boundary or impasse; otherwise NO intermediate stop. Full testing alone is
  not a boundary. Do not invent a consultation merely to populate the template.
- Final review (Gate C): one completed result and criterion-to-evidence packet;
  explicit ACCEPTED or consolidated evidence-backed findings. Terra selects
  corrections and verifies them without separate permission for each step.
- Heavy-run owner, world/port/display scope and safe stop/recovery procedure.
- Commit/push/deploy authority, if any; default none for this individual order.
- Engineer reviews stable deliveries and risk-selected independent evidence;
  no routine parallel duplication of diagnosis or complete test execution.
- Supervisor intervention must identify architecture/authority, a concrete risk
  or impasse, final review, explicit user audit or genuine liveness uncertainty.
  Use one protocol reason label. File/helper/algorithm/command preferences are
  not blocking findings and implementation suggestions remain non-binding.
- Reports by completion or exception; no executor heartbeat. The engineer
  performs the mandatory bounded ten-minute liveness check without messaging
  Terra when collaboration/process evidence is healthy.
- Apply the protocol's supervisor self-check before every intervention. The
  limit covers status messages and process/diff queries together. No terminal
  progress-only handoff requesting ordinary in-scope continuation permission.
- Declare the default economical proof path: localization; changed/failed-lane
  local regression; at most one runtime-relevant local physical preflight; one
  four-worker terminal matrix; immediate review. Record which steps are
  inapplicable. This is not a categorical one-attempt rule. Before an expensive
  repeat, record the material product claim/decision it can change, why cheaper
  evidence is insufficient and what outcome would alter acceptance or
  implementation. The same heavy proof claim crossing two
  consecutive hourly audits or four cumulative execution hours triggers a
  mandatory safe-boundary stop and explicit work-order reset; provider queue
  time is separate. No confidence, benchmark or accepted-infrastructure reproof
  can extend the budget.
- Before a milestone full gate that precedes native work, require a cheap
  carrier-readiness proof on that candidate: the declarative scenario,
  read-only diagnostic facts, semantic oracle and required negative/recovery
  discriminator must exist and be admissible. If missing carrier work changes
  identity after an otherwise useful gate, retain its receipt and decide the
  final-candidate gate by material product value rather than a run counter.
- State the evidence-entry boundary for a terminal matrix. A classified
  deterministic provider transport/admission failure before every Minecraft
  process and product-semantic lane produces no semantic evidence. Correction
  needs no special user permission. Redispatch additionally requires a changed
  candidate, a cheap faithful pre-Minecraft regression through the actual
  failed provider shape and the supervising engineer's finding of material
  expected product value. If it would only prove plumbing or repeat an accepted
  claim, defer it. Reuse all unaffected evidence; unchanged blind retry remains
  forbidden. Apply the same product-value test to semantic/runtime/flaky and
  unclassified failures.

## Execution-economy chronology

Observation epoch: `<UTC timestamp>`. Next audit boundary: `<UTC timestamp>`.
The ledger remains the sole status authority; this table is bounded cost/evidence
metadata. Populate it from ordinary milestone/failure reports and retained
receipts, never by requiring an executor heartbeat. Use `unknown` for unavailable
historical timing.

| UTC start/end or elapsed | Operation and product purpose | Outcome/evidence handle | Economy classification |
| --- | --- | --- | --- |
| <time> | <bounded operation; claim advanced> | <result; artifact/run> | <advanced / invalidated / necessary repeat / avoidable repeat> |

| Economy-audit UTC | Reviewed interval | Finding and action | Next boundary |
| --- | --- | --- | --- |
| <time> | <from--to> | <healthy, or concrete cost/method finding and conceptual correction> | <time> |

## Stop conditions

Conflicting authority, unknown WIP, active external writer, changed fingerprint,
new persistent/public meaning, missing permissions, contradictory requirement,
unexplained repeated failure or missing human evidence: report before expanding.

## Review record

Engineer records decision, exact reviewed identity, evidence references and
remaining exclusions here. The ledger remains the only active progress record.
