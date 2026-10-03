# PM-CTX002-GIT-ROOT-FINGERPRINT-01: truthful dirty identity across a nested project root

Revision: 2. Parent: accepted CTX-001 and live F0.6R3 human candidate.
Risk: small-code, development tooling only. Status: `ACCEPTED_LOCAL`.
PM / architect: Sol. Autonomous senior tech lead and sole coder/operator: Terra,
`gpt-5.6-terra`, reasoning high, `/root/ctx002_fingerprint_root`.
Workflow: [PM/TL protocol](../engineering-agent-protocol.md), revision
`2026-09-15-PM-TL-JUDGMENT-LOOP`.

This is the user-authorized independent work that may proceed while F0.6R3
waits for human M3. It must not modify or invalidate the deployed human
candidate, its active dirty source checkout, JAR, service or world. It does not
authorize OBS-001, XACT-001 or later gameplay work before their existing gates.

## Product outcome and acceptance

The repository-context engine must truthfully identify the exact dirty Git
worktree even when the declared Pale Mirror implementation directory is nested
below the actual Git top level. A tracked or untracked content-byte change in
the declared repository identity must change the current fingerprint; restoring
the bytes must restore it. A structural query may never reuse an index whose
recorded fingerprint belongs to different bytes.

The result must:

- report the actual Git top level and the declared implementation/code scope as
  distinct identities, and resolve Git-returned relative paths against the same
  root whose status they describe;
- retain the existing fixed-root, read-only, bounded and local-only safety
  model; ambiguous roots, path escape, unsupported entries and symbolic-link
  substitution fail visibly;
- mark the accepted old index stale against the current frozen F0.6R3 WIP and
  reproduce its correct top-level dirty-content fingerprint
  `d8636b0046b8e63f6fa75ce3d63591e1b304a11ff4c1f01c347e9e7ac220db81`,
  not the silently unchanged `ce2ab4ef...ba43` result;
- prove in a disposable fixture that nested-root tracked modification,
  untracked creation/content change, deletion/restore and clean cache reopen
  produce truthful deterministic readiness without mutating a source checkout;
- keep graph/index content scoped to the declared implementation while binding
  freshness to its declared Git repository identity; document any deliberately
  excluded repository paths rather than silently omitting them;
- leave no watcher, MCP server, test process or temporary mutation running.

This is tooling acceptance only. It proves no gameplay, runtime, performance or
M3 claim and must not trigger Gradle, GameTest, native or deployment evidence.

## Context and continuation

- Canonical governance/ledger:
  `/home/rd/proj/pm-governance/pale-mirror`, binding protocol revision
  `2026-09-15-PM-TL-JUDGMENT-LOOP`.
- Frozen human-candidate implementation is read-only at
  `/home/rd/proj/pm-f06r3-human-ingress-repair/pale-mirror`; its Git top level
  is `/home/rd/proj/pm-f06r3-human-ingress-repair`.
- Independent review observed that the facade consumes top-level-relative Git
  paths but resolves content under the nested implementation directory. Changed
  files can therefore be treated as nonexistent and their bytes omitted, which
  let `ce2ab4ef...ba43` survive an executable source correction. Independent
  hashing from the actual top level produced `d8636b00...db81`.
- F0.6R3 remains revision 34 `HUMAN_CANDIDATE`: installed JAR SHA-256
  `e4901485...042d2`, service invocation `89f4b686...51835f87`, Java PID
  1948420, world `frontier-v3-f06-r3-human-r33-20260917`, port 25565. Human M3
  remains open and is unaffected by this order.
- Previous Terra `/root/f06r3_r30_structural` is terminal with a clean handoff.
  CTX-001's architecture, pin, cross-links and read-only surface remain valid;
  replace only the disproved identity/freshness behavior and affected evidence.

Terra owns technical design, isolation method, implementation, focused tests,
self-review and correction iterations. There is no prescribed file, algorithm
or test sequence. The final packet must identify the isolated source bytes,
focused executed results, the active frozen-worktree read-only status result,
cache/process state and a clean integration handoff for after M3.

## Authority and resources

- Writable boundary: one task-owned isolated worktree or equivalent isolated
  source projection under `/home/rd/proj/pm-ctx002-fingerprint-root`; bounded
  task-owned temporary/cache paths and the named read-only context-tool user
  state are allowed. Terra chooses the coherent isolation form.
- The governance checkout may be read but not edited by Terra. The active
  F0.6R3 implementation checkout, runtime, server properties, installed mods,
  world, logs and service are read-only for this order.
- No commit, push, merge, active-worktree integration, deployment, restart,
  client/pack change, public/network publication, MAT/OBS/XACT work, broad
  cleanup or unrelated MCP reconfiguration is authorized.
- A task-local MCP/CLI smoke and bounded cache replacement are allowed when
  necessary. Preserve all unrelated caches and existing Stream Miner tools.
- Focused tooling tests and negative fixture cases are the complete technical
  verification envelope. Do not run product gates or a tool benchmark campaign.

## Delivery and next boundary

The isolated result is independently accepted. The corrected read-only status
reports declared implementation scope and actual Git root separately, marks the
old cache stale and reproduces current fingerprint
`d8636b0046b8e63f6fa75ce3d63591e1b304a11ff4c1f01c347e9e7ac220db81`.
Terra reports 11/11 focused tests plus the no-index whitespace check green.
Accepted file hashes are `8f2cca29...abb3` for the facade,
`b5b96482...dd54` for its tests and `f6b1f0aa...cc84` for the README. PM
matched those bytes, the exact status result, the unchanged 154-entry frozen
candidate state and installed JAR `e4901485...042d2`. The one task-owned MCP
process was identified by its session record and stopped; older unrelated MCP
processes and live Java PID 1948420 remain untouched.

Return one coherent terminal result. `ACCEPTED_LOCAL` means a verified isolated
correction is ready for later integration; it does not alter the exact live
human candidate. Integration into the main implementation identity occurs only
after F0.6R3 M3 is resolved or under a separate explicit identity-preserving
decision. A human observation arriving during this work takes priority and is
handled against the still-frozen deployed candidate.
