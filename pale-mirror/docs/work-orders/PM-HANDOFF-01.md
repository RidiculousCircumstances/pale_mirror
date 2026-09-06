# PM-HANDOFF-01: adopt F0.VA without competing writers

Specification revision: 1. Parent slice: F0.VA. Mode: READ_ONLY executor audit.
Engineer: /root. Executor: /root/terra_executor, gpt-5.6-terra, high.
Active phase and write/run grants are owned by `CONTINUITY.md`.

## Baseline

Nested source HEAD observed as `0ac6f97a`; extensive existing F0.1/F0.V/F0.VA
dirty work is preserved. Outer pack also retains `.f0v-baseline/` untracked.
This order does not attribute existing changes to the new executor or accept
them. The latest current run observed on the host at 2026-09-05 14:35 +05 was
PID 1979855, `run-f0va-feedback-timing.mjs`, prepared/output under
`build/f0va-current21/`. Re-discover process state before every operation;
the PID is historical identification, never standing permission to terminate.

## Required reading

Scoped AGENTS/ledger and mandatory skills, `../engineering-agent-protocol.md`,
`../frontier-v3-accelerated-verification-loop.md`, relevant implementation-plan
gates and `../frontier-v3-execution-semantics.md`.

## Outcome and scope

1. Inventory latest completed/incomplete/failed timing and native runs using
   bounded JSON projections and exact source/build/run identities.
2. Map all six F0.VA capabilities and exit requirements to existing code,
   current proof, historical-only proof or missing evidence. Inspect the actual
   final-server save/port closure before classifying its barrier gap.
3. Identify the smallest next work order, expected files, transitions and
   focused negative/recovery checks; do not implement it under this order.
4. Engineer records a safe writer handoff and makes the protocol discoverable
   from the normal plan/brief at a fingerprint-safe boundary.

## Prohibitions

Executor must not write files, modify Git, run tests/Gradle/native clients,
launch or stop servers, publish, migrate or spawn another agent. It must not
infer handoff from sandbox ps, stale ledger or an old green report. Normative
documentation updates belong to the engineer and cannot disturb the active
measurement's fingerprint inputs. No F0 closure is authorized here.

## Acceptance

| ID | Required evidence |
| --- | --- |
| AC-1 | Exact latest run/status/fingerprint and historical comparisons distinguished; no unsupported speedup claim. |
| AC-2 | Six-capability/exit gap map with source/test/artifact paths and local/provider distinction. |
| AC-3 | Final-stop protocol finding tied to actual control flow, evidence validation and negative tests; distinguish lost data from missing proof. |
| AC-4 | Previous writer/user handoff acknowledgement, current run safe boundary and preserved repository inventories before any new write/run grant. |
| AC-5 | Engineer-owned objective/protocol/plan linkage is consistent; docs diff check and guardrails pass after the measurement-safe boundary. |

The executor submits read-only findings for AC-1–AC-3, then waits. The engineer
owns AC-4–AC-5 and independent review. No fresh native test is required to accept
an audit; any implementation/proof repair needs a separate approved order.

## Engineer review record

2026-09-05: AC-1–AC-3 read-only findings reviewed in part. Current21 persistent
native run `d96f13b0-64cd-4fc3-8403-ca73d08bbd7c` has one client/four server
runs/three worlds and verified operational final save/exit/port closure. The
initial stronger finalStop defect claim was corrected after source inspection;
no protocol expansion is authorized. Timing run `9f75476b-7f1f-47d7-b516-19fe8272db4a`
remains in progress, so no aggregate-current speedup is accepted yet.

Baseline `lifecycle_restart-baseline-2` under that timing run has `ok` executed
T0–T3 evidence, including cache/selector/fixture policy tests. Its verification
source is a 2016-entry inventory; the prepared source is a separate 1531-entry
inventory. Their distinct hashes are not drift. Engineer directly confirmed
the prepared current21 fingerprint still equals `d835013a9e4cad29f75c21a737f4204e9fb43fad6c3deb7aea48d666ac22c9b6`
after governance doc edits; no input drift was introduced.

Terminal review, 2026-09-05 (supersedes the in-progress observations above):

- AC-1: current21 feedback report is terminal `ok`, 18 lanes, aggregate
  5.065026x >=3x. It proves only the declared iterative-feedback workloads.
- AC-2: VA.1/.4 have current21 persistent-native proof; VA.2/.3 have executed
  T0/T1 plus exact-key candidate reuse and selected/executed tier evidence;
  VA.5 actual provider proof remains UNCONFIRMED_EXTERNAL; VA.6 has historical
  development bootstrap/consumer proof, not current final acceptance.
- AC-3: operational final save/exit/port checks confirmed; no 30-event journal
  expansion is authorized. Existing negative tests are not missing merely
  because an old ledger did not mention them.
- AC-4: user relayed the former writer's explicit no-more-writes/tasks handoff.
  Main verified PID1979855 absent and no conflicting F0.V runner; both HEADs
  and dirty state are preserved in the ledger.
- AC-5: objective/protocol and all four primary execution briefs are aligned;
  post-linkage `git diff --check` and `./gradlew guardrails` pass (34 tasks,
  16 seconds). Ledger compaction preserves the entire previous file in the
  named archive; the concluding docs check also passed (34 tasks,16 seconds).

Gate C: ACCEPTED by /root. This accepts safe adoption and documentation only,
not inherited source or any remaining F0.VA exit. Both repositories were checked:
root diff check passes with only inherited untracked `.f0v-baseline/`; nested
docs guardrails pass while its 199 dirty/untracked entries remain preserved.

The next order is `PM-F0VA-VERIFY-01.md`. Its Phase A packet has been submitted;
new native runs still require causal review and an explicit VERIFYING grant.
