# Pale Mirror agent-assist tools

These tools shorten diagnosis and feedback without changing canonical state,
Minecraft worlds or acceptance rules.

## Runtime evidence context

`runtime_context.py` builds a bounded private SQLite index from evidence roots
fixed when the process starts. It recognizes JSON, JSONL/NDJSON and text logs,
redacts common credentials, and exposes `status`, `index`, `timeline` and `why`.
The same surface is available over stdio MCP, together with the verification
selector. Cache state is outside the repository under
`~/.local/state/pale-mirror/runtime-context/`.

This is an evidence navigator, not the OBS-001 diagnostic producer, canonical
state, a repair interface or an acceptance oracle. `stale`, `partial` and
`not_found` remain explicit.

Example:

```sh
python3 tools/engineering/agent_assist/runtime_context.py \
  --implementation "$PWD" \
  --evidence-root "$PWD/build" index

python3 tools/engineering/agent_assist/runtime_context.py \
  --implementation "$PWD" \
  --evidence-root "$PWD/build" why actor:resident:17
```

## Change-to-tests selector

`verification_selector.py` maps repository-relative changed paths to the
smallest checked-in test families. It prints commands but never executes them.
The normal result defers the full local gate; `--milestone` includes one full
gate after cheap changed lanes pass. A native carrier always requires explicit
scenario selection.

Removed Java tests select inventory compilation, never a nonexistent class filter.
Test-only edits do not imply native scene/economy checks. Native families remain
manual suggestions; neither a filename keyword nor an unknown path can schedule
an expensive campaign. Unknown paths visibly require engineering selection.
Use `--root <implementation>` when planning another checkout; MCP uses its
configured implementation root. Documentation selects `git diff --check`.
Visual-generation tooling has its own `./gradlew verifyVisualTooling` gate;
ordinary `guardrails`/`check` retain architecture and source-safety checks only.

```sh
python3 tools/engineering/agent_assist/verification_selector.py \
  pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/kernel/ScheduledAction.java
```

## async-profiler wrapper

`async_profile.py` validates the pinned private async-profiler installation,
an explicit same-user Java PID, a bounded 1–120 second duration and a new
private output path. It is a dry-run unless `--run` is present. Never attach it
to an unrelated or unowned JVM.

```sh
python3 tools/engineering/agent_assist/async_profile.py 12345 \
  --event cpu --duration 30 --format jfr --name f06r3-stall
```

Serena supplies on-demand LSP symbol/reference navigation in future Codex
sessions. It complements the architectural repository map; it does not replace
the canonical contracts or authorize edits. ArchUnit enforces selected stable
Frontier v3 bytecode boundaries during the ordinary Java test task.
