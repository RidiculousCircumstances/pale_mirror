# Pale Mirror repository context engine

Status: accepted architecture decision and locally accepted implementation;
the retained result is recorded by `PM-CTX001-REPOSITORY-CONTEXT-ENGINE-01`.

## Problem

Pale Mirror has authoritative product and architecture documents, but an agent
still has to reconstruct the code ownership path with repeated text searches.
That is especially expensive for a cross-layer defect whose story passes from a
canonical process through scheduling, HOT/COLD custody, a Minecraft body,
physical projection, observation, recovery and a native evidence carrier.

A generated call graph alone does not know the product contract. A handwritten
system map alone becomes stale and cannot answer symbol/reference questions.
The project therefore needs both, joined at one bounded development-only
boundary.

## Decision

Use a two-layer local context engine:

1. The existing `AGENTS.md`, `CONTINUITY.md`, `architecture.yml`, active work
   order and named contract documents remain the semantic authority. A compact
   machine-readable cross-link index maps governed paths to architecture
   components, critical flows, canonical documents and verification families.
2. A pinned local `DeusData/codebase-memory-mcp` supplies the disposable
   structural graph: symbols, references, call paths, architecture clusters and
   impact hints for the current implementation checkout.

A small Pale Mirror context facade joins those layers and returns a bounded
task/path/change context. It never promotes graph output to product truth and
never becomes a runtime dependency.

The implementation may reuse the design lessons from the read-only local
`/home/rd/proj/stream_miner` checkout, particularly its Context MCP, Change
Graph and attested Codebase Memory bridge. Pale Mirror must not import that
repository, depend on its files or duplicate its unrelated memory and runtime
diagnostic surfaces.

## Vendor choice

The initial structural engine is the MIT-licensed
[`DeusData/codebase-memory-mcp`](https://github.com/DeusData/codebase-memory-mcp),
pinned to a reviewed release identity. It is local, persistent, supports Java
and Gradle-aware cross-file analysis, and exposes graph/search/trace operations
without another LLM or API key. The exact v0.8.1 binary already installed on the
host may bootstrap Pale Mirror only after its digest and MCP handshake match the
declared pin; the Pale Mirror integration may not depend on Stream Miner's
installation directory or mutable configuration.

Alternatives remain deliberately uninstalled:

- GitNexus has a strong process/cluster graph and Codex integration, but its
  current PolyForm Noncommercial license and broad automatic setup are a poor
  default for the project; its Java control-flow graph is also not yet the full
  implementation offered for TypeScript/JavaScript.
- Serena is an excellent LSP-level symbol/reference/refactoring companion, but
  it is not the persistent process/impact graph needed here. It is a future
  fallback only if the selected engine proves a concrete Java-resolution gap.
- Sourcegraph MCP and Augment Context Engine add managed-service, account,
  pricing or deployment dependencies that are unnecessary for this local
  repository.

Do not run a comparative benchmark campaign. Replace the selected engine only
after one concrete unsupported requirement or reliability failure.

## Required context surface

The facade must make these bounded questions cheap:

- Given a task or path, which component owns it, which canonical documents and
  active order apply, and which critical flows can be affected?
- Given a symbol, process or symptom, where are its definitions, callers,
  callees and cross-module dependencies?
- Given a prospective changed path set, what is the union of affected owners,
  contracts, critical flows and verification families?
- Is the graph ready for the exact current dirty implementation checkout, or is
  its result stale, partial, busy or unavailable?

The initial cross-link coverage must include every declared top-level component
and the complete active F0.6R3 chain: canonical work/assignment/schedule,
HOT/COLD driver, actor lease and inactive carrier, Minecraft adoption and
reconciliation, graybox projection, causal diagnostics, persistence/restart and
the native carrier/oracle. Missing governed coverage is visible; it is never
silently classified as unrelated.

## Authority and safety

- The engine reads canonical governance from the governance checkout and code
  from one explicitly selected implementation checkout. It reports both
  identities with every task context; a stale checkout-local copy of governance
  is never substituted.
- Index and caches are private bounded user state outside Git worktrees. No
  generated graph, vendor setup block, memory, hook output or binary is committed
  automatically.
- Source, paths, queries and graph data stay local. The ordinary query path has
  no API key and no source upload. Vendor update/setup/wiki/ADR/publication
  operations are outside the surface.
- Expose only the operations required for readiness, bounded search,
  architecture, code snippets, call tracing and impact. Arbitrary graph writes,
  project deletion and repository/config mutation are absent or disabled.
- Repository roots are fixed at process activation or selected from the
  declared current work context. Tool calls cannot browse an arbitrary host
  path. External paths and ambiguous project identities fail closed.
- Output, recursion depth, result count, input size, call duration, cache size
  and concurrent indexing are bounded. Partial results say that they are
  partial.
- No always-on watcher, UI server or auto-injection hook is required for the
  first version. Freshness is explicit and incremental; reindex only when the
  relevant working-tree identity changed.
- MCP tools are read-only from the repository's perspective and must not create
  interactive approval churn. Codex configuration uses a tool allowlist and the
  documented non-prompting policy for those tools.

## Agent workflow

For an unfamiliar or cross-layer task, first request one task context. Read the
returned canonical documents and exact source anchors, then use graph trace or
impact queries only for the unresolved ownership path. Before an expensive
native run, confirm that the proposed evidence carrier spans the affected
critical flow and that the graph is current for the changed implementation.

This is a navigation discipline, not a mandatory query count. Trivial local
work does not need the engine. A graph result is a hypothesis to verify in
source; it cannot override architecture, the active order, observed runtime
facts or a user product contradiction.

## Lifecycle

The current Terra session must have a CLI-equivalent entrypoint because MCP
catalog changes require a later Codex session to reconnect. A future session
must receive the same read-only operations through a named Pale Mirror MCP
server. Both paths use the same pin, roots, cache identity and freshness rules.

The context engine is accepted only after it answers representative F0.6R3
ownership and caller-path questions against the current dirty worktree, detects
one deliberate stale/change condition, restarts cleanly from its persistent
cache and leaves no owned background process. This is a focused tooling smoke,
not another product proof campaign.
