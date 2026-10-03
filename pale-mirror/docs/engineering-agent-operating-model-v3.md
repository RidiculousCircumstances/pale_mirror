# Pale Mirror engineering operating model v3

Status: historical, superseded in full for workflow by
[`engineering-agent-protocol.md`](engineering-agent-protocol.md), unified
revision 2026-09-11. The preserved text below records prior decisions; it is not
an additional source of active requirements or execution authority.

## Objective

Optimize verified player-facing capability per hour. Agent activity, test
volume, document volume and a green dashboard are not progress by themselves.
Keep architectural judgment, implementation and falsification independent
without turning review into continuous supervision.

## Three independent roles

### Supervising engineer

- owns the player promise, architecture, invariants, work-order boundary,
  evidence claims and final acceptance;
- writes only normative Markdown and declarative architecture;
- reviews stable terminal candidates, not implementation in progress;
- resolves owner/authority conflicts and consolidates one conceptual return;
- never writes production code, tests, scripts, build/CI or executable config.

### Builder

- is one fresh `gpt-5.6-terra` agent with reasoning `high` per bounded slice or
  successor order and is the sole executable-code writer;
- owns diagnosis, design, algorithms, affected files, tests, commands,
  correction cycles, cleanup and the coherent delivery commit;
- receives outcomes and invariants, not a patch recipe;
- performs its own adversarial preflight before spending a native/CI cycle;
- returns only a real exception or one terminal packet.

### Challenger

- is a fresh read-only agent used only for a stable `critical-code` terminal
  candidate or a genuinely disputed proof method;
- receives the order, exact candidate identity, stable diff and compact machine
  receipts, but no builder reasoning transcript;
- tries to falsify the claim by tracing owners, restart windows, negative cases
  and test construction;
- does not edit files, direct the builder, rerun a broad gate or create a second
  implementation; it returns findings to the supervising engineer.

The supervising engineer remains the decision owner. A challenger finding is
evidence, not an automatic veto. Small/docs slices normally need no challenger.

## Workspace and context isolation

- Every new slice and every successor after a semantic return uses a fresh
  builder context and a dedicated branch/worktree rooted at an exact checkpoint.
- The supervising engineer works in a separate governance/integration worktree.
  Builder and challenger never share mutable build output, worlds, caches or
  task processes.
- A failed terminal candidate may be committed to a private task branch as an
  explicit `WIP checkpoint`. It is not accepted, merged, published or presented
  as green evidence. This makes handoff atomic and prevents a long-lived agent
  context from becoming the only store of work.
- Governance is committed before the builder starts. Accepted builder commits
  retain their author when integrated.

The shared F0.2C aftermath worktree is the last transition exception. Its
current builder may only create the exact private WIP checkpoint and stop; the
successor starts from that checkpoint in an isolated worktree.

## Bounded product order

A work order contains:

1. one player/product outcome and why it matters now;
2. canonical owners and invariants, including failure/recovery behavior;
3. explicit exclusions and authority boundaries;
4. a claim-to-evidence table using the cheapest faithful tier;
5. final identities, receipts and stop conditions.

It must not prescribe helper names, internal algorithms, file-by-file edits or
command choreography. Expected files are navigation only. If the work crosses
two or more state owners, the order must name the owner seam and require one
cheap integration test that exercises their real production ordering from a
state that neither test helper has pre-satisfied.

## Execution lifecycle

`FRAMED -> CHECKPOINTED -> BUILDING -> SEAM_READY -> EXPENSIVE_READY -> DELIVERED -> CHALLENGED -> ACCEPTED`

1. **Frame.** Engineer writes the outcome, invariant set and evidence map. For
   every expensive claim, name a plausible product defect that must fail it.
2. **Checkpoint.** Create an exact clean baseline or explicit private WIP
   checkpoint, then create a fresh isolated builder worktree.
3. **Build.** Builder autonomously diagnoses, designs, implements and iterates
   focused deterministic tests. There are no routine design approvals.
4. **Seam-ready.** For every changed owner boundary, a cheap production-path
   seam test proves actual ordering, absence/pending states, negative cases and
   recovery interleavings. A fixture that manufactures the desired intermediate
   state is component evidence only and cannot admit a physical run.
5. **Expensive-ready.** A machine-readable admission receipt maps the real
   carrier, oracle, control history, source/JAR identity and discriminators to
   claims. The builder self-reviews it adversarially. Missing or synthetic
   prerequisites stop cheaply.
6. **Verify.** Run one complete local milestone gate for the final candidate.
   Native/CI follows only for claims unavailable below that tier. Changed
   product code may justify another run; unchanged confidence does not.
7. **Deliver.** Builder waits for owned processes, retains compact receipts,
   cleans only owned disposable state, commits the coherent branch and returns
   one terminal packet.
8. **Challenge.** On critical code, the read-only challenger reviews the stable
   candidate once. It does not inspect WIP or coach implementation.
9. **Decide.** Engineer independently checks the smallest critical identities
   and receipts, then accepts or returns one consolidated invariant-level
   correction.

## Semantic-return circuit breaker

- One ordinary consolidated return may resume the same builder and order.
- A second terminal semantic return for the same claim ends that builder
  context at a safe WIP checkpoint. The engineer must accept, reject or create
  a short successor order for a fresh builder; it must not append another
  troubleshooting chapter.
- Any repeated expensive failure first returns to a cheap discriminator. If no
  discriminator can represent the failure, that is an architecture/test-method
  boundary, not authorization for another physical run.
- Two hourly audits or four execution hours on the same heavy claim still force
  a method/value reset under the protocol, even if agent contexts changed.

This is a context and method limit, not a run-count superstition. A materially
changed candidate may receive proportionate verification when the result can
change a real product decision.

## Evidence design

Evidence has four non-promotable levels:

1. component rules and codecs;
2. real owner-seam ordering and integration;
3. native physical/restart behavior;
4. unbriefed player comprehension.

A test proves only its level. In particular:

- pre-seeding a ledger, receipt or terminal state cannot prove the producer and
  consumer coordinate under runtime order;
- a timeout is only a failure guard; causal milestones are the oracle;
- absence is `PENDING/UNOBSERVED` unless an owner has positively classified it;
- one happy path requires a defect-sensitive control or mutation;
- machine receipts carry identity and result; agent prose only navigates them.

## Supervision cadence

- Builder silence gets exactly one bounded liveness check after ten minutes,
  never source inspection or an implementation query.
- One hourly economy audit uses only operation metadata and asks whether the
  work changed a product decision.
- User status questions do not interrupt healthy execution.
- The engineer contacts the builder only under the protocol's named reasons.
- No agent polls another agent to watch coding. Reviews are event-driven at an
  exception, method boundary or terminal delivery.

## Progress accounting

Each terminal decision records only:

- capability gained or still missing;
- accepted evidence tier and exact identity;
- remaining product debt;
- elapsed expensive operations and avoided/reused work;
- next bounded slice.

Do not measure progress by commits, lines, test count or time spent. Closed
incident detail stays in its work order or archive, not active continuity.
