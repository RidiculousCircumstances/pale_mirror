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
- Executor owns diagnosis, local design, helpers and focused test choices
  within that boundary, without per-file or per-method approval.
- Forbidden files/changes; conditions requiring a revised specification.
- Engineer-owned normative docs; executor supplies proposed corrections.

## Acceptance matrix

| ID | Required outcome/invariant | Focused positive test | Negative/recovery test | Physical/final proof |
| --- | --- | --- | --- | --- |
| AC-1 | <contract> | <test> | <test> | <scenario or justified N/A> |

Declare required test outcomes and mandatory gates. Executor selects and records
exact focused commands within the grant; heavy/native/external operations need
an explicit command/resource agreement. Specify what evidence may be reused
and what must be fresh, plus source/spec/build identity and artifact paths.

## Review and execution permissions

- Gate A: acknowledged outcome/boundaries/risks; explicit implementation grant,
  not approval of every internal design decision.
- Gate B: stable implementation plus focused evidence; native/full-gate grant.
- Gate C: criterion-to-evidence review; explicit ACCEPTED or required changes.
- Heavy-run owner, world/port/display scope and safe stop/recovery procedure.
- Commit/push/deploy authority, if any; default none for this individual order.
- Engineer reviews stable deliveries and risk-selected independent evidence;
  no routine parallel duplication of diagnosis or complete test execution.

## Stop conditions

Conflicting authority, unknown WIP, active external writer, changed fingerprint,
new persistent/public meaning, missing permissions, contradictory requirement,
unexplained repeated failure or missing human evidence: report before expanding.

## Review record

Engineer records decision, exact reviewed identity, evidence references and
remaining exclusions here. The ledger remains the only active progress record.
