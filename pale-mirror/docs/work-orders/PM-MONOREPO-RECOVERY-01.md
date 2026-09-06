# PM-MONOREPO-RECOVERY-01: verified pre-migration recovery inputs

Revision1. Parent: mandatory post-local-F0.VA migration gate, backup step only.
Risk: repository recovery plus docs; no implementation change.
Engineer /root; sole executor /root/terra_crash_completion, Terra high.

## Accepted baseline

Nested preservation commit b82c0406c50fd85dda08d8bd27114541bf8aec32,
parent0ac6f97a695d636ae928313de7a807d1f3ea05a1,
tree f67ad99e4ed219cd2ddbd71f87ec506804279a26 is independently accepted:
all240 frozen path blobs/modes and changed-path membership match the commit.
Nested status was clean; outer HEAD891a4dfe1a613857124775696973d065cc9399cb
retains only untracked .f0v-baseline/. Original common Git metadata backs other
worktrees; preserve all of them and all local artifacts/services unchanged.

Read AGENTS/ledger, engineering protocol, preservation order and migration
procedure in implementation-plan. Apply release-verification skill. Outcome:
two independently restorable Git history bundles with exact ref/tree proof,
not migration or feature acceptance. No production stop is necessary for
operations confined to new recovery paths and ordinary docs commit metadata.

## Granted sequence after acknowledgement

1. Verify only three main-owned documentation paths changed since b82c0406:
   CONTINUITY.md, docs/work-orders/PM-PRESERVATION-PRECOMMIT-01.md,
   docs/work-orders/PM-MONOREPO-RECOVERY-01.md. Review them, run
   ./gradlew guardrails --no-daemon and both repository diff checks. Explicitly
   stage only these three paths, require cached diff check and exact frozen
   bytes/modes, then make one non-amending docs commit on nested main with
   title docs: record preservation acceptance and recovery work order.
   Prove its parent is b82c0406 and its tree differs only at these three paths.
   No code/build/JAR/native rerun is required for these governance files.
2. Record exact resulting nested and unchanged outer HEADs and all refs. Stop
   on unexpected working changes, external writer or ref drift. Make a new
   unique private recovery directory using mktemp -d under /home/rd/proj/
   with prefix pm-migration-recovery.; resolve and report its absolute path.
   Do not create it inside either original repo or any linked worktree.
3. Create a standalone Git bundle for each repository containing all its refs
   and complete reachable history, not a thin/incremental bundle. Capture
   checksums and sizes; verify bundles and advertised refs against the frozen
   originals. Do not modify original refs/config or worktree metadata.
4. Restore each bundle into a distinct new bare verification repository inside
   this recovery directory using only the bundle, without shared-object,
   alternates or hardlink dependence on the source repo. Run connectivity/
   object verification and prove both exact main commit/tree IDs and all
   advertised refs recover. Restoration is a backup check, not a migration
   clone; no merge, checkout adoption or builds there.
5. Recheck original HEADs/refs and both statuses unchanged since the docs commit;
   retain all recovery outputs. Return a compact manifest with absolute paths,
   bundle checksums/refs, restored commit/tree evidence, limitations and both
   original repository states. Main independently accepts before migration.

## Boundaries and acceptance

Allowed writes: the exact three-document nested staging/commit and new files
inside the unique recovery directory; normal docs-gate generated output.
Executor writes no source or normative docs; main supplies all doc changes.
No outer commit, source editing, remote access/config, push, merge/migration,
service stop, served artifact replacement, reset/amend, prune/gc, cleanup,
ref rewriting or worktree relocation. Do not remove failed backup attempts.

Bundles preserve committed reachable objects and refs, not uncommitted/ignored
files or common Git worktree metadata. Original directories/linked worktrees
remain the preservation mechanism for those and must not be retired. Document
this limitation rather than calling a bundle a full filesystem backup.

AC-1: exact governance-only checkpoint with no implementation change.
AC-2: both immutable histories restored independently with identical main trees
and all advertised refs, checksum/object verification successful.
AC-3: original layouts, refs after checkpoint, WIP/local artifacts, worktrees
and services retained. No cleanup or external authority inferred.
AC-4: enough exact path/ref evidence for independent review; no whole-F0.VA or
monorepo acceptance claimed. Report stable completion or concrete blocker;
no intermediate heartbeat required. A failed invariant stops this grant.

## Review record

ACCEPTED by engineer on 2026-09-06. Checkpoint
bafbb8ebb59435ebb50138c8d87dac002ad6054d has exact parent b82c0406 and
only the three allowed documentation paths; tree
0e3656d4823fd7e8637635e184e11d7d6d6ff252. Main independently checked
both bundle SHA256 values, restored HEAD/tree IDs, matching persistent refs
and ref trees, actual fsck/complete-history verification reports and original
statuses. Recovery directory: /home/rd/proj/pm-migration-recovery.OaRskN.
Nested bundle SHA256 c53ae205ea0d7bda629c87148d53e18a96093e5b4b6374d6ef73712219c76062;
outer bundle SHA256 4a54192a16364d3c37e8ee44ee436d0a201bd8140eff33ecae50b4d487b3d8d1.
Seven nested persistent refs and one outer persistent ref recover exactly;
25 linked-worktree pseudo-HEAD objects are included without recreating their
metadata. Dangling commits in restored fsck are retained worktree-only objects,
not corrupt objects. Original nested clean, outer only .f0v-baseline/ untracked.
No migration/provider/product acceptance follows from this backup gate.
