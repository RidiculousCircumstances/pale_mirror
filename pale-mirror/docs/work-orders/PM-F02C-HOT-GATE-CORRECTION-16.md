# PM-F02C-HOT-GATE-CORRECTION-16: restore the diagnostic structure ratchet

Specification revision: 3. Status: `TERMINAL_PRODUCT_RED`.
Parent slice: F0.2C. Risk: critical-code/native. Engineer: supervising root.
Executor: `/root/terra_f02c_hot_gate16`, fresh `gpt-5.6-terra`, reasoning
`high`.

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

Execution assigned at `2026-09-10T18:51:48Z` from the exact clean baseline and
isolated custody paths above. The first ten-minute liveness audit is due at
`2026-09-10T19:01:48Z` after silence; the first economy audit is due at
`2026-09-10T19:51:48Z` if execution remains active.

## Terminal result

Scoped structural correction `3ed045b4a22dab68c8619e8c454e0e70bcb51230`
is accepted: scene diagnostics moved to a coherent helper, the primary file is
874 lines, affected diagnostics and `verifyLargeFiles` pass, and no diagnostic
field or authority changed. The complete gate's runtime/package leaves and
303/303 GameTests passed with saved shutdown; one stale pure-test intent ID was
then aligned to the already-existing production binding and its exact seven-test
class passed. No monolithic confidence rerun was performed.

The one native attempt is a valid product negative, not a HOT receipt. After
the ordinary visit the exact lease remained `PREPARED`; readiness returned
`AWAITING_EXACT_FLOOR`, all 28 bodies remained absent and no strike intent or
receipt was created. It timed out at its action-2 terminal barrier, then saved
all dimensions and stopped cleanly. Bundle SHA-256 is
`bc4c39175b30e3a13f357bdffc4a6e87591931e9374eeaab3e73ace3c6272587`.

Independent read-only review decoded the retained graybox ledger: all 24
resident supports have exact settlement public-surface claims, while all four
attacker supports are unclaimed; the first sorted missing support is
`bioform:west-18 @ (-362,64,-346)`. The candidate compiler accepts actor floors
using canonical-clear geometry without proving a registered physical provider
serves them, while the immutable structural projection excludes moving actors.
The scene correctly refuses to invent or substitute a floor and therefore
defers on the first attacker. Successor order17 owns this production seam.
