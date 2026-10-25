# Field ingress latency investigation (R250)

The earlier R249 report established an approximately19s first ingress, but did not
attribute it to native generation. That attribution is withdrawn. An actual return
login to the already-created field7 on the same R249 world also waited about35s.
At the retained sample the native chunk was loaded and STATIC_CURRENT, but field
handoff was WAITING:field_projection_or_observation, with160 mismatched cells.
See build/r250-login-handoff.txt and r250-first-area.log.

This is waiting for our materialization, not a35s continuous server-thread stall.
The bounded JFR build/r250-first-area.jfr (task-owned JVM110412) includes that login.
The server retained roughly20 native TPS; cumulative native-tick peak at this stage
was81.64ms. Much of the captured server waiting was ordinary Minecraft waitForTasks.
The capture is not a matched before/after benchmark or proof of no individual CPU cost.

An unvisited field9 then needed35s for its receiving chunk; after another30s the
render assertion failed. Actual farmland had arrived, but Sodium's section was not
compiled. Neighbour -92,92 still reported waits for the primary field and two
cultivation fields. One target's presentationReady is insufficient visual evidence.
Receipt build/r250-first-area.json / log; build/r250-final-blockers.txt. The separately
polled handoff JSONL had overlapping shell writers and is incomplete/corrupted; do
not use it as a complete causal timeline.

Source review found the8-cell projection cap unchanged since bf2a23b0bc
(2026-09-17). It remained on current-image catch-up after fields became larger and
dynamic. R245 correctly added exact pre-attempt native preparation, but catch-up
still performed that integration independently for cells instead of declaring a
whole exact planned window through the existing managed-write preparation owner.
Initial/static projection already used that shared mechanism. Each durable phase
and cooperative physical pass adds turns to the small-window backlog; the renderer
then waits on all required neighbouring chunks too.

The changed path declares the next exact pending cell writes as one bounded managed
window. Individual preconditions, durable operation permissions and outcomes still
run for every cell. Actual native effects use the existing shared256-count/5ms turn
budget, admitted after durable preparation and before beginAttempt. A capacity wait
spends no attempt, and an asynchronous fence spends no effect budget. Preparation
and sparse discovery are bounded by the same256-cell window, with fair continuation
for larger fields. Biological crop growth, worker processing time, yield, custody,
foreign-block rules and unknown-outcome recovery are unchanged. The obsolete test
assertion equating catch-up projection with the8-cell work cadence was removed;
existing sparse/fairness and deterministic admission checks were updated.

Verification so far: focused9 adapter tests passed; guardrails and50 native shared
field/storage/body tests passed (build/r250-field-tests.log,
build/r250-shared-native.log). The full adapter run and real rendered-ingress
measurement are still pending. This is a candidate correction of the confirmed
small-window/per-cell-fence path, not yet a claim that all latency or the missing
neighbour has been fixed. Existing world and diagnostic evidence are retained.
