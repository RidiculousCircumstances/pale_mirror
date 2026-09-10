# PM-F02C-HOT-GATE-CORRECTION-16: restore the diagnostic structure ratchet

Specification revision: 1. Status: `READY_ASSIGNMENT`.
Parent slice: F0.2C. Risk: critical-code/native. Engineer: supervising root.
Executor: fresh `gpt-5.6-terra`, reasoning `high`.

This successor inherits `docs/engineering-agent-operating-model-v4.md` and the
accepted method/resource/component evidence named by order15. It receives a
fresh bounded context because order15 terminated cleanly at its gate.

## Observed defect and required outcome

Start from exact clean `a360d9b092b217334d9c589c6040e4ea355d8cd9`, parent
`06aa49d8`, tree `2018399005ddb663c48b9d82fd11bfbb4050f6f9`.
`FrontierV3DiagnosticJson.java` is 1,016 lines and fails the binding 1,000-line
Java-file debt ratchet. The file was already 1,015 lines before the one-line HOT
receipt addition. Correct this as a coherent production ownership/refactoring
boundary, not by compressing formatting, weakening/exempting the guardrail or
removing diagnostics.

Preserve byte-level diagnostic meaning, including every existing field and the
new exact HOT `strikeIntent` field. Diagnostics remain bounded, immutable,
read-only server-thread observations and may not force-load, mutate canonical
state or become authority. Terra owns the local design, file/helper choices and
tests. No scenario, domain rule, physical effect, timing, threshold, build
guardrail or public contract changes are authorized.

## Evidence order and circuit breaker

1. Add or select the smallest deterministic focused regression that proves the
   extracted boundary preserves the affected diagnostic output, including the
   exact HOT receipt fields, and rejects omission or identity drift. Run that
   regression plus `verifyLargeFiles` first.
2. Only when those cheap checks pass, run one complete clean-HEAD critical-code
   gate. Its exact command remains the order15 aggregate and must prove the
   ordinary aggregate, package boundary and saved-world shutdown.
3. Only when the full gate is green, run exactly one targeted native history
   through existing `run-f02c-hot-receipt-carrier.mjs` and return
   `HOT_NATIVE_TERMINAL_R11`.

Reuse without execution order15's resource and declaration-entrypoint probes,
R7 Node/identity, R9 effective-resource and scene64/64, the accepted COLD
receipt and constructive-obstruction evidence. Do not rerun those leaves. Wall
clock is only a deadline. A pre-launch environment rejection may be corrected
without consuming the semantic attempt. An actual changed-candidate failure
may receive one ordinary bounded correction only after a cheaper discriminator
would have caught it; no confidence rerun.

Return exact HEAD/parent/tree and changed paths, commands/durations/exits,
receipt and artifact hashes, build/JAR/native identities, claim mapping and
zero task-owned process/port/display state. Commit coherent private WIP only.

No general native/CI/provider/terrain/F0.3/tactics/publication/deployment, no
second authority, and no mutation of accepted evidence.

## Custody

Use branch `terra/f02c-hot-gate-correction-16`, worktree
`/home/rd/proj/pm-f02c-hot-gate-correction-16` and task-private root
`/home/rd/proj/pm-f02c-hot-gate-correction-16-tmp`. Preserve order15's worktree
and evidence. Own and stop only task-created processes; do not touch the live
port25565 service or unrelated host state.
