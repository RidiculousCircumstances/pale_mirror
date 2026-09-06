# Minecraft workspace guardrails

## Workspace layout

- This checkout contains the Far Frontier pack/deployment tree at the root and
  the Pale Mirror source project at `pale-mirror/`, in one Git repository.
- Preserve the non-squashed ancestry of both original repositories. Do not
  create an embedded `.git`, gitlink or submodule under `pale-mirror/`.
- Inspect and commit the combined repository from its root, with explicit
  reviewed paths. Report verification for each affected ownership boundary.
- Migration is not accepted merely because the histories have been assembled.
  Until explicit adoption, preserve both original checkouts, common Git
  metadata, linked worktrees, local artifacts and verified recovery bundles.
  No force-push, original cleanup, service change or deployment is implied.

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
- Temporary migration rule: this candidate has not been adopted. Its imported
  ledger is historical; additionally read the active ledger and work order at
  `/home/rd/proj/minecraft/pale-mirror/CONTINUITY.md`. Only the engineer may
  transfer ledger authority after the required migration/provider gates.

## Ownership boundaries

- The root pack/deployment boundary owns the modpack manifest, client/server installers,
  hosted artifacts and deployment scripts.
- `pale-mirror/` owns mod source, architecture, tests and build outputs.
- Crossing the boundary must be explicit: build and verify in `pale-mirror/`,
  then publish/install through root scripts according to the Pale Mirror
  release and server-operation rules.
- Git tracking is not pack payload admission. Source, CI, build outputs and
  diagnostic evidence must stay out of the distributable Packwiz payload.
  Validate incoming pack/index metadata without silently accepting its repair;
  deliberate regeneration is a separate reviewed step.
- Root `.github/workflows/` owns CI entry points. Shell steps for Pale Mirror
  run from `pale-mirror/`; action/artifact paths resolve from checkout root.
  Evidence identity must retain relevant root CI inputs after relocation.
