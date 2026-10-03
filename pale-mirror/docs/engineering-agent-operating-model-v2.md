# Pale Mirror engineering operating model v2

Status: historical, superseded in full for workflow by
[`engineering-agent-protocol.md`](engineering-agent-protocol.md), unified
revision 2026-09-11. The preserved text below records prior decisions; it is not
an additional source of active requirements or execution authority.

## Purpose

Use one expensive engineer for product contracts and independent acceptance,
and one cheaper capable executor for autonomous implementation. Optimize for
verified product progress per hour, not agent activity, document volume or test
count.

## Roles

### Engineer

- owns the player promise, architecture, invariants, evidence method, work-order
  boundary and final acceptance;
- writes normative documentation and reviews one stable delivery;
- never writes production code, tests, scripts, build files or executable
  configuration;
- does not choose helpers, algorithms, filenames or command sequences for the
  executor;
- intervenes only under the protocol's named reasons;
- verifies claims from source identity, diffs and machine evidence rather than
  trusting prose summaries.

### Executor

- is one fresh `gpt-5.6-terra` high-reasoning agent per product slice;
- is the only author of code, tests, scripts and executable configuration;
- owns diagnosis, design, implementation, focused correction cycles, command
  selection and cleanup inside the work order;
- returns one exception only for a real boundary, or one terminal delivery;
- does not edit engineer-owned architecture, plans, ledger or work orders.

A completed executor may resume only for a correction to the same reviewed
delivery. A new slice gets a fresh agent context. Read-only specialist agents
are not routine; the engineer may use one only for a genuinely independent,
parallel audit whose result can change a decision. They never write code.

## Isolated workspace model

After F0.2C, every slice uses a dedicated Git branch and worktree created from
one accepted baseline. The executor owns that worktree, its build directory,
temporary files and external processes. The engineer works in a separate
governance/integration worktree. No Gradle output, native world, cache mutation
or uncommitted file is shared across the two roles.

The work order and accepted baseline are committed before the executor starts.
Engineer amendments during execution stay on the governance side and are sent
as an exact revision; they do not rewrite the executor branch. At acceptance,
integrate the executor's coherent commits while preserving Terra as author,
then commit the reviewed documentation state. Documentation-only integration
must not invalidate an unchanged source-content/JAR receipt.

The current shared F0.2C WIP is a grandfathered transition exception. Do not
move or rebase it mid-slice. Adopt isolated worktrees immediately after its
terminal review and cleanup.

## One bounded work unit

A work order states only:

1. one player/product outcome and why it matters now;
2. canonical owners, invariants and observable failure/recovery behavior;
3. explicit exclusions and authority boundaries;
4. an acceptance matrix mapping claims to the cheapest faithful evidence and
   any genuinely required physical/human tier;
5. the final verification envelope, evidence identity and stop conditions.

Expected files and implementation ideas are navigation hints, never required
patch recipes. A work order should remain short enough to reread completely.
After three substantive method/scope revisions, close it as accepted, rejected
or superseded and create a successor that references retained evidence. Do not
grow an append-only multi-day troubleshooting diary.

## Delivery lifecycle

1. **Frame.** Engineer records the bounded outcome, baseline and claim/evidence
   map. The test-framing check names a plausible defect for every expensive
   claim.
2. **Admit.** Create the isolated executor worktree and fresh Terra context.
   Terra reads only `AGENTS.md`, compact `CONTINUITY.md`, the active order and
   directly relevant contracts/skills.
3. **Build.** Terra autonomously diagnoses, implements and runs focused tests.
   There are no routine design approvals or progress reports.
4. **Prepare evidence.** Before a broad gate or native run, cheap checks prove
   that the actual declarative carrier, diagnostic facts, oracle, negative case
   and runtime inputs exist on the candidate. A missing carrier makes the broad
   gate premature.
5. **Verify once per candidate.** Run the required complete local gate on the
   final source candidate. Run native/CI only for claims unavailable at a
   cheaper tier. A changed candidate may require another gate; a run count never
   substitutes for the product-value decision.
6. **Deliver.** Terra waits for every process to terminate, writes the compact
   machine receipt, cleans only task-owned disposable state, commits/pushes the
   branch and returns one terminal packet.
7. **Review.** Engineer reads the complete stable diff once, checks ownership,
   recovery and test-method validity, verifies identities/receipts and runs only
   a targeted independent discriminator when evidence is missing or conflicts.
   It does not duplicate the complete gate for confidence.
8. **Accept or return one correction.** Findings are consolidated by violated
   invariant. Style preferences and alternate valid designs do not block. After
   acceptance, update/commit governance, compact continuity and start a fresh
   executor for the next slice.

## Evidence trust boundary

Executor prose is navigation, not proof. A terminal receipt is created only
after the observed command exits and contains:

- order revision; baseline, head, parent and source tree/content identity;
- schema/envelope/debt versions and packaged artifact hash;
- exact command, start/end/elapsed, exit status and result-file hashes;
- test/scenario identities and claim IDs actually covered;
- process/world/port/display identities for physical work;
- remaining exclusions and cleanup/dirty-state inventory.

The engineer independently verifies the minimum critical fields against Git,
raw logs/XML/manifests and live process state. A premature cleanup, missing
terminal status, mismatched identity or unsupported claim makes that receipt
invalid without automatically requiring a broad rerun. Compose unaffected
evidence with a smallest faithful correction when possible.

## Test and cost policy

- Tests prove product claims; they are not the product and do not earn progress
  merely by running.
- Use focused deterministic checks first, then the smallest real integration
  boundary, then a complete local milestone gate.
- Native Minecraft runs exist for physical/save/player claims that JVM or
  GameTest cannot establish. CI exists for provider isolation, concurrency or
  environment claims that local execution cannot establish.
- Before every expensive repeat, record the decision it can change, why cheaper
  evidence is insufficient and the result that would change implementation or
  acceptance. Changed code alone is not enough when the repeated test is
  unrelated.
- Reuse identity-applicable evidence. Never rerun for confidence, topology,
  timing, a dashboard color or an already accepted claim.
- Two hourly audits or four execution hours on the same heavy claim force a
  safe-boundary method/value reset. This is a circuit breaker, not a run quota.

## Context and supervision budget

`CONTINUITY.md` contains only the active goal, current accepted baseline, active
order, material decisions, live evidence debt and next action. Closed history
belongs in its work order or `docs/archive/`. The active ledger should target
less than 4,000 tokens; the active work order should target less than 2,500.

The engineer checks liveness once after each ten minutes of executor silence and
economy once per complete hour. Healthy checks create no message or ledger
entry. User status questions receive current stage/blocker/process information
without forcing Terra to stop. Code review occurs at terminal delivery or one
material proof-method boundary, not on a timer.

## F0.2C transition

F0.2C continues in its existing shared worktree under revision2/3 and preserves
all current WIP/evidence. Its method correction must prove a causal physical
postcondition, not only disappearance of a pending record. After terminal F0.2C
review, the engineer performs the one-time transition: accept or return the
delivery, commit durable governance, compact/archive continuity, create an
isolated next-order worktree and start a fresh Terra agent with no chat-history
fork. Product work then continues with F0.3.
