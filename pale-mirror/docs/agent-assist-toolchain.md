# Pale Mirror agent-assist toolchain

Status: implementation focused-green, integrated into F0.6R3 WIP after an exact
safe writer handoff, and available to the current Terra execution lineage. The
active order and its revision are resolved from `CONTINUITY.md`; this document
does not pin a historical revision. This capability is nonblocking and does not
change the Frontier v3 product sequence.

## Purpose

The toolchain makes repository comprehension, runtime diagnosis and selection
of proportionate checks cheaper for the senior technical lead. It does not add
another reviewer, proof campaign, simulation, diagnostic authority or test
orchestrator.

Five capabilities compose without sharing authority:

1. `runtime_context.py` indexes already-produced JSON/JSONL/log evidence in a
   private bounded SQLite database and answers status, timeline and why queries.
2. `verification_selector.py` maps repository-relative changed paths to a
   deterministic advisory test plan. It prints commands and never executes them.
3. pinned Serena provides on-demand LSP symbol, reference, implementation,
   diagnostics and refactoring operations to a future Codex session.
4. ArchUnit enforces selected stable Frontier v3 dependency boundaries during
   the ordinary pure-Java test task.
5. a pinned async-profiler wrapper validates one explicit same-user Java PID,
   a new private output path and a 1–120 second capture. It is a dry-run unless
   `--run` is present.

The separately accepted repository-context engine is the sixth development
capability in the complete active toolkit. It supplies canonical/structural
task, path, change, trace and impact context and is governed by
`docs/repository-context-engine.md`.

## Operational availability

Implementation, installation and host configuration are not enough on their
own: the active senior-tech-lead session must be able to invoke every capability.
At activation of a new session or worktree, perform one bounded non-mutating
availability pass and retain a compact capability result:

- repository context: current-worktree status plus one bounded task/path query;
- runtime context: status plus one bounded query over its fixed evidence root;
- verification selector: one changed-path plan with no command execution;
- Serena: initialize and one symbol/reference navigation operation;
- ArchUnit: presence in the ordinary applicable Java gate, not a separate full
  suite; and
- async-profiler: pinned installation attestation and dry-run only, unless an
  independently justified owned-JVM profiling hypothesis exists.

Named MCP is preferred in a fresh TUI. The checked-in CLI-equivalent is the
required fallback for a long-lived TUI whose catalog predates registration.
Record `unavailable`, `stale` or `partial` honestly and continue through the
safe canonical/source fallback; repair a missing bootstrap seam at the smallest
coherent boundary. Do not repeat this pass per task, benchmark the tools, start
Minecraft, run a broad gate or capture a profile merely to prove availability.

The recorded adoption receipt used the CLI/manual-stdio fallbacks because its
long-lived host tool catalog predated the registrations. Repository
context status/path, runtime status/why and changed-path selection were
operational; both context indexes truthfully reported their current
`stale`/`partial` limits. Serena 1.7.0 initialized and completed one LSP symbol
lookup against the active worktree, the focused ordinary ArchUnit boundary gate
passed, and async-profiler 4.5 passed installation attestation plus an owned-JVM
dry-run without attachment. The server/LSP and owned test JVM then stopped. This
closes availability only; it proves no product behavior and does not require
repetition before later tasks in the same session.

## Authority boundaries

- The runtime index reads only evidence roots fixed at process startup. It never
  reads a caller-selected host path, loads a chunk, submits a command, repairs a
  world or writes into a worktree. Its cache is private user state.
- It redacts common credential forms, caps roots/files/source bytes/records/
  line size/result count/database size and exposes stale/partial/incomplete
  coverage. A missing row is not proof that a canonical event did not occur.
- The runtime index is a consumer of existing evidence. It neither implements
  nor completes the F0.6R3 diagnostic bootstrap or OBS-001 producer contract.
- The selector is a planning aid. Terra remains responsible for test framing,
  methodology, actual commands and the delivered product result. Unknown paths
  are visible. Milestone and native families are never silently promoted into
  routine edit feedback; a native scenario is always chosen explicitly.
- Serena complements the accepted repository context graph. No automatic
  onboarding, project memory, source upload or always-on watcher is required.
  Its result is a source-navigation hypothesis, not architecture truth.
- ArchUnit rules cover only accepted stable layer constraints. Adding a cycle,
  style or package-shape preference requires a separate architecture decision;
  a green rule is not a product gate.
- Existing JFR/jcmd evidence is preferred. async-profiler is used only when a
  named TPS/caller-path hypothesis remains unresolved, never against an
  unrelated live service and never as a routine benchmark.

## Versioned external tools

- Serena `1.7.0`, installed under
  `~/.local/share/pale-mirror-tools/serena-1.7.0/`; the official wheel SHA-256 is
  `6dbf1459670d96fb0595f84932adef34260a6fe14ba5135b901fdb3c8c76e891`.
- async-profiler `4.5` Linux x64, installed under
  `~/.local/share/pale-mirror-tools/async-profiler-4.5-linux-x64/`; the official
  archive SHA-256 is
  `89546fbb9ee0fc5496c7edd4099b0709489bc78b0d8057ccbb4b801f6b032b62`.
- ArchUnit `1.5.0` is a test-only Maven dependency of
  `pale-mirror-frontier`.

Private install manifests retain the selected source and digests. Upgrades are
deliberate; no `latest` launcher or floating Git checkout is used.

## Codex integration

`pale-mirror-runtime-context` exposes exactly:

- `pm_runtime_status`
- `pm_runtime_index`
- `pm_runtime_timeline`
- `pm_runtime_why`
- `pm_verify_change`

`pale-mirror-serena` uses the official `codex` context with a Codex-side
allowlist for symbol/reference/diagnostic/refactoring operations. Memory,
onboarding, shell and generic file tools are not allowlisted. MCP registrations
become visible only to a new Codex session.

The runtime MCP registration points at the integrated active source and its
task build evidence root. A live-server log root is selected explicitly by a
CLI process when needed, so an unrelated continuously-written service log does
not make the primary build-evidence index permanently stale. A future worktree
handoff atomically rebinds and smoke-tests the implementation and evidence root.

## Verification and integration

Implementation was prepared at `/home/rd/proj/pm-devtools-assist` and copied
without path overlap into the active F0.6R3 worktree. Its acceptance surface is
deliberately small:

- focused Python contracts for bounds, redaction, staleness, MCP tool surface,
  selection semantics and profiler safety;
- one focused `FrontierArchitectureTest` Gradle slice;
- Serena initialize/tool-list smoke with dashboards disabled;
- exact async-profiler install attestation and help/version smoke;
- one real read-only runtime index/query smoke, allowed to report partial or
  stale while its live evidence roots are changing.

Do not run Minecraft, a full gate, a native matrix, a profiler capture or a
deployment for this tooling-only capability. Integration waits for the active
product writer's safe boundary, reviews path overlap, and reuses these receipts
unless the tool inputs changed.
