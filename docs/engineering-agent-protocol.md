# Pale Mirror engineer / executor protocol

Status: accepted by the user on 2026-09-05; applies to ongoing Frontier v3 work.

Autonomy amendment accepted on 2026-09-05: delegate complete bounded engineering
outcomes, not predesigned patches. This amendment governs future orders and
explicitly amended active orders; historical evidence is unchanged.

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
closure and before actual provider evidence. This protocol is not permission
to migrate, deploy or delete v2 early. Each such operation needs an explicit
work order and the existing safety/verification gates.

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
signatures or internal data structures. Crossing an
owner/module, changing persistent meaning or expanding the public interface
requires an amended order before implementation. A whole F0 phase is not an
adequately bounded order where several independently reviewable cuts exist.

Normative meaning comes from the accepted contract/map. If a work order or test
conflicts, the executor reports evidence and alternatives instead of choosing
the easiest interpretation. The engineer resolves within accepted scope or asks
the user for a product/authority decision. Neither agent may relax acceptance
to manufacture progress.

## Execution state machine and review gates

`DRAFT -> READY -> ACKNOWLEDGED -> IMPLEMENTING -> REVIEW -> VERIFYING -> ACCEPTED`

`REVIEW` may return to `IMPLEMENTING`; any phase may enter `NEEDS_DECISION`.
The ledger owns the active order, revision, executor, phase and last acceptance.
An agent message is a notification, not a competing durable state database.

- Gate A: engineer marks the specification READY; executor acknowledges scope,
  baseline, intended transitions, tests and unresolved questions. Engineer
  explicitly grants IMPLEMENTING. A READ_ONLY order grants no writes/runs.
  Acknowledgement covers outcome, boundaries and risks, not approval of every
  local design choice. The grant includes ordinary local diagnosis, focused
  tests and correction iterations inside the declared scope; no new order is
  needed for a local implementation decision.
- Gate B: after the main change and focused tests, executor reports the exact
  diff and failed/passed evidence; engineer reviews ownership and negative paths
  before authorizing expensive native or full-gate verification. No ongoing
  measurement is restarted just because a reviewer asks for a status update.
- Gate C: engineer reviews the stable final diff and maps each acceptance item
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
next step and decision needed. Do not send entire logs, JSON fingerprints or
unchanged status repeatedly; link exact artifacts and project only needed fields.

The engineer reviews a delivered evidence packet at each gate, not every method.
During silence, routine agent-status checks occur no more than once per ten
minutes, including across automatic goal continuations. Earlier checks require
a user request, a delivered milestone/blocker, or concrete evidence of a safety
or authority risk. Passive event-driven waiting takes precedence over repeated
status, diff, process and hash queries. A long test is not a hang without
process/output evidence. The supervisor keeps the user informed according to
session rules without turning those updates into executor polling.

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

## Evidence and continuity

For each acceptance criterion retain the code boundary, exact command/result,
source/build/spec identity, negative/recovery proof and limitations. Distinguish
implementation existence, automated correctness, physical-world continuity and
M3 player comprehension. Final gates use fresh evidence as required by F0.VA.

The engineer alone edits `CONTINUITY.md` during managed execution; the executor
sends proposed ledger facts. Compact or archive history through the existing
ledger rules, never create another root or agent continuity file. Work orders
retain specifications and review records, not a second active progress ledger.

After interruption/compaction, read the ledger and current order, inspect agents
and active commands, and resume the same executor if available. Replacing it
requires releasing the previous write/run ownership first. Preserve all WIP
and output; do not restart the project from an old plan paragraph.

Continue accepted in-scope work while the supervising goal/session is active.
Do not claim invisible background supervision after termination. Human gates,
unavailable external services and new permissions remain real boundaries;
record what is unproved and ask the user when required. A completed local order
does not complete the overall Frontier v3 goal.
