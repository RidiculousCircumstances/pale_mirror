# Minecraft workspace guardrails

## Workspace layout

- This workspace contains the Far Frontier pack/deployment repository at the
  root and the independent Pale Mirror source repository at `pale-mirror/`.
- Preserve both Git histories. The nested repository is intentionally ignored
  by the outer repository; do not convert it to a submodule, subtree or ordinary
  tracked directory unless the user explicitly requests a repository migration.
- Inspect, stage and commit each repository separately. A cross-repository task
  must report verification and dirty state for both repositories.

## Pale Mirror work

- Pale Mirror is the primary development project in this workspace. At the
  start of every assistant turn concerning this workspace, before acting or
  selecting tools, read `pale-mirror/AGENTS.md` and
  `pale-mirror/CONTINUITY.md`. This bootstrap is mandatory even when the task
  begins in the root repository or appears to concern only packaging/runtime.
- Rules and mandatory skills declared by `pale-mirror/AGENTS.md` remain binding.
  Resolve its relative paths from `pale-mirror/`.
- `pale-mirror/CONTINUITY.md` is the single continuity ledger for Pale Mirror
  source, packaging and disposable test-server work. Do not create a competing
  workspace-level copy.

## Ownership boundaries

- The root repository owns the modpack manifest, client/server installers,
  hosted artifacts and deployment scripts.
- `pale-mirror/` owns mod source, architecture, tests and build outputs.
- Crossing the boundary must be explicit: build and verify in `pale-mirror/`,
  then publish/install through root scripts according to the Pale Mirror
  release and server-operation rules.
