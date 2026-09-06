# PM-F0VA-NATIVE-CRASH-REVISION-01: admit the late-bound crash revision

Specification revision: 1. Parent: `PM-F0VA-NATIVE-QUAL-01`.
Risk: small-code correction on the native crash configuration boundary.
Engineer: `/root`. Executor: `/root/terra_crash_completion`, gpt-5.6-terra high.
State: GATE_C_ACCEPTED; exact two-file implementation grant CLOSED. Executor is
frozen/READ_ONLY. Native qualification remains main-owned.

## Reproduced defect

The first real assigned worker-3 run failed before server/client launch in
`:pale-mirror-neoforge:prepareFrontierV3PilotServerWorld`. The F0.V contract,
scenario parser, persistent descriptor and pilot Java probe all intentionally
accept `expectedRevision: "observed_at_boundary"`: the semantic boundary itself
supplies the exact durable revision, which is then authenticated in the marker,
proof and loss. The Gradle world-preparation validator alone admits only
`[0-9]+`, so the exact valid assignment cannot reach its pilot probe.

This is a configuration-language mismatch, not permission to resolve or guess a
revision before the boundary. The correction must admit either a nonnegative
decimal revision or the one exact token `observed_at_boundary`; empty, negative,
signed, whitespace, arbitrary strings and partial crash tuples remain rejected.

## Exact scope and baselines

Allowed files only:

- `pale-mirror-neoforge/build.gradle`, baseline
  `bcefc2aa87d9c9b55a28bf14a061258701e06217dbc2312abfe34558429c7406`;
- `tools/frontier-v3-test-pilot/test/run-f0va-persistent-matrix.test.mjs`,
  baseline `d188e635f19c95b3ab9d06f19680ac21200f5219552fca0e6e537fe75af02d8a`.

No Java, scenario/contract, runner, CI workflow, dependency, docs/ledger,
generated build output, live service, Git or remote changes. Do not weaken
boundary/owner/payload/run-id validation, do not translate the token to a guessed
number, and do not add retry/fallback behavior.

## Acceptance

1. Add a focused regression that fails on the frozen baseline and binds the
   runner's `observed_at_boundary` declaration to the Gradle preparation
   validator. It must also prove an arbitrary symbolic revision is not admitted.
2. Make the smallest Gradle validator correction: the exact token or decimal
   digits are valid only inside an otherwise complete exact crash tuple.
3. Existing focused persistent-runner tests remain green. Executor runs only:

```bash
node --test tools/frontier-v3-test-pilot/test/run-f0va-persistent-matrix.test.mjs
git diff --check
```

4. Freeze and report before/after regression result, exact two-file diff and
   hashes. Main performs Gate B review, focused Gradle preparation positives and
   negatives, then proportionate source/full gates. No native rerun is granted
   to the executor.

## Gate and stop rules

Gate A must state exact intended edits/test and confirm no unresolved design
choice. After explicit `IMPLEMENTING`, preserve all current WIP and edit only
the two paths. Unexpected scope, inability to express a strict negative,
concurrent writer or unrelated failure stops at READ_ONLY with evidence.

## Gate A acceptance, 2026-09-05

ACK ACCEPTED. Executor verified both exact baseline hashes and the retained
failure: worker-3 carried the declared token, while no server run, client PID or
lifecycle event was created and owned ports were closed. Intended production
edit is only the whole-value alternative `[0-9]+|observed_at_boundary` in the
existing complete-tuple validator. The source-backed focused regression will
bind that exact vocabulary and retain arbitrary-symbolic, signed, whitespace,
empty and incomplete-tuple negatives. No decision is unresolved; no executor
command or writer is active. After the documentation gate, the engineer may
grant `IMPLEMENTING` for exactly these two files and two permitted commands.

## Gate B/C acceptance, 2026-09-05

Executor's regression failed 7/8 on the frozen predicate, then passed 8/8 after
the one-predicate correction; diff check passed. Main reviewed the exact diff
and independently repeated the focused suite: 8/8 passed in 86.11 ms. Final
scoped hashes:

```text
pale-mirror-neoforge/build.gradle 98b59e9eb679a5149fd8c89714ddf5cc905e9d586f17d7b881e67fb814345c18
tools/frontier-v3-test-pilot/test/run-f0va-persistent-matrix.test.mjs 845f39c0dd80dc82eb59e4b983a50c0e8270f5a036540b8f0b4dc9e21635c3de
```

Main exercised the real Gradle task. A complete tuple with
`observed_at_boundary` passed. The same tuple with `late_bound_other` failed;
an otherwise valid tuple missing payload also failed. No server/client was
started by those preparation checks. Full Node passed 169/169. The critical
gate passed 71 tasks in 1 minute 16 seconds, including 290/290 GameTests plus
check/build/package/JAR verification. Gate C accepts only this correction; it
does not promote the failed revision1 native evidence or close F0.VA.
