# Historical monorepo migration instructions

Archived 2026-09-18 from the implementation plan. This is consumed sequencing,
not current execution, publication or migration authority. Accepted evidence and
checkout boundaries remain in the canonical ledger and engineering protocol.

## Mandatory post-F0.VA monorepo migration gate

This is an ordered infrastructure boundary, not permission to interrupt an
active measurement or fold uncommitted work into a synthetic import commit.
It runs after all locally verifiable F0.VA implementation, timing, negative,
critical-gate and original graceful-restart requirements are green, but before
resuming the remaining original F0.V matrix. A dedicated GitHub-provider timing
matrix is no longer an intervening prerequisite.

The resulting monorepo preserves the workspace layout: the Far Frontier
pack/deployment tree is at its root and Pale Mirror remains at `pale-mirror/`.
Assembly uses an isolated candidate; it does not replace the original live
workspace or authorize deployment. Both original histories must be ancestors
of final `main`. Checkout adoption is a separate gate below.

Migration procedure and stop conditions:

1. Reach a process-safe boundary for the affected checkout, artifact paths and
   evidence inputs. Prove there is no competing writer and no active measurement
   observes changed inputs. Unrelated live services and artifact readers remain
   untouched; their existence is not permission or a requirement to stop them.
2. Finish and commit the F0.VA work in `pale-mirror/`. Independently inventory,
   verify and commit any intentional outer-pack changes. Do not discard,
   silently ignore or absorb unknown dirty/untracked files. Record both exact
   pre-migration HEADs and create verified Git bundles before changing either
   repository layout.
3. Build the merge in a disposable clone of the outer repository. Import the
   exact nested `main` history under `pale-mirror/` with a non-squashed subtree
   merge (or an equivalently proven history-preserving merge). Never force-add
   a live embedded `.git` directory and never use a squashed subtree.
4. Remove the outer `/pale-mirror/` ignore rule in the migration tree. Retain
   the root and scoped `AGENTS.md` files, ownership documentation and all
   source/deployment paths. The checkout has exactly one root Git metadata
   directory; its tracked tree contains neither embedded `.git` nor gitlinks.
5. Move or compose Pale Mirror GitHub workflows into the monorepo-root
   `.github/workflows/` directory. Give jobs explicit `pale-mirror` working
   directories and update artifact/action paths. Preserve existing workflow
   families and pack/deployment scripts; if the original outer repository has
   no workflows, record that fact rather than inventing a missing family.
   Retain root CI bytes in relevant prepared/cache identities. Normalize Git
   change discovery into the selector's source coordinates, separately handling
   root CI and unrelated pack inputs; unknown relevant impact stays fail-closed.
6. Prove both old HEADs are ancestors of final `main`. At the import checkpoint,
   prove exact nested-tree equality and unchanged outer files. Then reconcile
   every adaptation against that checkpoint, including deliberate normative
   documentation updates; no unexplained source or payload drift is allowed.
   Verify status, ignores, workflow syntax, pack checks and the complete Pale
   Mirror critical gate from CI working paths. Pack validation checks incoming
   bytes and both manifest hashes without silently refreshing them. Deliberate
   migration-only regeneration requires an explained payload delta and a
   repeatable idempotence check. Repository-only source/CI/evidence stays out
   of the pack, while legitimate nested worldgen assets remain included.
   A content, ancestry or verification mismatch stops the migration.
7. Inspect the destination before mutation with `git ls-remote`. The only
   authorized destination is
   `git@github.com:RidiculousCircumstances/pale_mirror.git`. If `origin` is
   absent, run exactly:

   ```bash
   git remote add origin git@github.com:RidiculousCircumstances/pale_mirror.git
   ```

   If `origin` names anything else, or the destination contains unrelated or
   non-fast-forward history, stop and report it. Never force-push and never
   delete remote refs to make the migration pass.
8. Push the verified `main` branch with `git push -u origin main` and push only
   collision-free verified tags. Re-read the remote refs and clone the result
   independently before treating the remote as canonical or retiring the two
   recoverable pre-migration bundles/checkouts.
9. Use the checked-in GitHub four-worker correctness pipeline for each newly
   started complete native matrix. Require four simultaneously available
   isolated slots, deterministic assignment, a complete duplicate-free
   fail-closed merge and exact commit/evidence identity. Same-host slots are
   valid only after capacity and isolation preflight. Do not repeat a valid
   matrix that was already running when this requirement was adopted; worker
   topology accelerates future pipeline work and is not a new F0 acceptance
   assertion. Record comparable timing when it arises, but do not run or repair
   a standalone campaign merely to meet a numeric speed ratio.
10. After the clean remote clone and local F0.VA correctness gates pass, the
    engineer may accept migration and F0.VA, adopt the monorepo checkout for
    continued work and resume the preserved original F0.V matrix. Keep recovery
    bundles until the user explicitly accepts their removal.
