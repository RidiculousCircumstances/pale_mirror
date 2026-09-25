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

- Canonical instructions and the sole active continuity ledger are at
  /home/rd/proj/pm-governance/pale-mirror/AGENTS.md and
  /home/rd/proj/pm-governance/pale-mirror/CONTINUITY.md.
- Read them on session entry or context loss; use retained unchanged context
  during automatic continuations. Refresh changed instructions/state when needed,
  not all historical files on every turn.
- Resolve project skills and normative references from that governance directory.
  The current ledger names the implementation checkout and execution assignment.
- Local Pale Mirror entry files outside governance are redirects, not alternative
  authority. Preserve all histories/WIP; this is not a repository migration.

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
