---
name: pm-repository-context
description: Use Pale Mirror's canonical context map and local code graph for unfamiliar ownership, cross-module impact, lifecycle tracing, or repeated cross-layer defects. Do not use for trivial single-file work.
---

# Pale Mirror repository context

Use the repository context engine when a task crosses components, ownership is
unclear, a runtime symptom may have several upstream causes, or a proposed
change can affect persistence, HOT/COLD custody, physical materialization,
recovery or native evidence.

Read `docs/repository-context-engine.md`. The active work order and
`architecture.yml` remain authoritative.

Request one bounded task/path/change context before broad exploration. Read the
canonical documents and exact source anchors it returns. Use graph search,
trace or impact only to close the unresolved structural path; verify important
results in source.

Check graph identity/freshness against the active dirty implementation checkout.
Treat `stale`, `partial`, `busy` and `unavailable` as explicit limitations, not
empty results. Reindex only after relevant worktree changes or an explicit stale
state; do not run an indexing or tool-comparison campaign.

Before spending on a native run for a cross-layer defect, confirm that the
evidence carrier reaches the affected critical flow from its canonical owner to
the terminal result. The map informs that judgment but does not prescribe a
test sequence.

Graph output is advisory. It cannot override source, `architecture.yml`, the
active order, runtime evidence or a user observation. Do not enable mutation,
delete, vendor setup/update/wiki/ADR, UI, watcher or auto-injection operations
through this skill.
