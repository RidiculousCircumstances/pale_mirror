# Work order <ID>: <one bounded result>

Specification revision: <N>. Parent slice: <F0...>. Risk: <docs/small-code/critical-code>.
Engineer: supervising root. Executor: gpt-5.6-terra, high.
Active phase and acceptance owner: `CONTINUITY.md`.

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
complete outcome. With no evidenced liveness uncertainty, routine checks are
zero; ten minutes is only the absolute maximum frequency when `LIVENESS`
actually applies.

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
- Reports by completion or exception; no heartbeat. Routine liveness checks at
  most once per ten minutes across turns, not automatically every ten minutes.
- Apply the protocol's supervisor self-check before every intervention. The
  limit covers status messages and process/diff queries together. No terminal
  progress-only handoff requesting ordinary in-scope continuation permission.

## Stop conditions

Conflicting authority, unknown WIP, active external writer, changed fingerprint,
new persistent/public meaning, missing permissions, contradictory requirement,
unexplained repeated failure or missing human evidence: report before expanding.

## Review record

Engineer records decision, exact reviewed identity, evidence references and
remaining exclusions here. The ledger remains the only active progress record.
