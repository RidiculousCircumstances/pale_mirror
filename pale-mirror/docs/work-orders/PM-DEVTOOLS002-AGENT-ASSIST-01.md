# PM-DEVTOOLS002 — bounded agent-assist toolchain

Status: `INTEGRATED_FOCUSED_GREEN`. Revision: 2. Risk:
`small-code` tooling plus a test-only dependency. Parent capability: accepted
CTX-001. Nonblocking sibling: active F0.6R3 revision 24.

## Authority

The user explicitly authorized the supervising Sol agent on 2026-09-15 to
implement capabilities 1–5 itself. This is a one-time narrow exception to the
normal PM/Terra writer split for an isolated developer-tool worktree and private
tool installation/configuration only. It grants no product runtime, world,
server, deployment, commit, publication or active-Terra-WIP mutation authority
and does not change the standing operating model.

Implementation worktree:
`/home/rd/proj/pm-devtools-assist` at base
`18ee25cccb9a0f92df8dfd58cc39a5730c0ff455`.

## Required outcome

- A bounded local runtime evidence explorer provides explicit freshness and
  causal timeline/why lookup over fixed roots, without becoming OBS-001.
- A deterministic changed-path selector emits, but never executes, the smallest
  relevant feedback plan and separates milestone/native work.
- Current pinned Serena is installed and registered for future-session LSP
  navigation with a bounded Codex tool allowlist.
- A narrow ArchUnit test enforces already-declared Frontier v3 bytecode
  boundaries without package-style or speculative cycle rules.
- Current pinned async-profiler is installed and reachable only through a
  same-user, explicit-PID, bounded, dry-run-by-default wrapper.

`docs/agent-assist-toolchain.md` owns the complete design and safety boundary.

## Delivered paths

- `tools/engineering/agent_assist/{README.md,runtime_context.py,verification_selector.py,verification_catalog.json,async_profile.py}`
- `tools/engineering/test_agent_assist.py`
- `pale-mirror-frontier/build.gradle`
- `pale-mirror-frontier/src/test/java/io/farfrontier/palemirror/frontier/v3/architecture/FrontierArchitectureTest.java`
- private pinned tool directories and the named Codex MCP registrations

No production Java, scenario, harness, world or service was changed. After the
prior Terra returned a clean process handoff, the exact devtool paths were
copied into the active F0.6R3 worktree and the runtime MCP was rebound there.

## Verification receipt

- `python3 -m unittest tools/engineering/test_agent_assist.py -v`: 10/10 pass,
  including conservative widening for an unmapped path.
- `./gradlew :pale-mirror-frontier:test --tests '*FrontierArchitectureTest' --no-daemon`:
  pass again after integration in 10 seconds; seven tasks, two executed and five
  up-to-date.
- `python3 tools/engineering/context/test_repository_context.py`: 7/7 pass;
  the refreshed structural graph is `ready` at dirty fingerprint
  `9f4fdc3f...d6dfe`, 33,946 nodes and 223,471 edges.
- Serena `1.7.0`: CLI version/help pass; MCP initialize returns protocol
  `2024-11-05`; bounded Codex allowlist registered.
- async-profiler `4.5`: official archive digest matched GitHub release metadata;
  installed launcher/library digests match the private manifest; version/help
  pass; no JVM attachment was attempted.
- runtime explorer: synthetic freshness/redaction/query/MCP tests pass. Its
  active-build index contains a bounded 300,000-record window in a 53,276,672
  byte private database and reports `partial`, not falsely complete. A separate
  explicit CLI process can include the continuously-written live-server log.
- Canonical `git diff --check` and `./gradlew guardrails --no-daemon`: pass;
  34/34 documentation/architecture guardrail tasks executed in 17 seconds.

## Remaining delivery boundary

The changes are integrated in the active worktree but remain uncommitted and
unpublished with the surrounding F0.6R3 WIP. The isolated preparation worktree
is retained until a later frozen candidate preserves the files. A future
worktree handoff must rebind both fixed MCP implementation/evidence roots. The
ordinary applicable small-code/static checks may compose with that candidate's
existing final gate; no separate full native/product proof is justified by this
tooling alone.
