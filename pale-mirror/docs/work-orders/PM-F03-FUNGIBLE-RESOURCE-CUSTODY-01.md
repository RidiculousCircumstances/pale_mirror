# PM-F03-FUNGIBLE-RESOURCE-CUSTODY-01: make ordinary resources exact without permanent stack identity

Revision: 2. Parent slice: F0.3. Risk: critical-code.
Status: ACCEPTED_LOCAL; dispatch state belongs to CONTINUITY.md.
PM / architect: Sol. Senior tech lead and sole coder:
`/root/f02c_projection_snapshot30_correction`, gpt-5.6-terra, reasoning high.
Workflow: [PM/TL protocol](../engineering-agent-protocol.md), revision
2026-09-11-PM-TL. Canonical governance:
`/home/rd/proj/pm-governance/pale-mirror`.

## Product outcome and acceptance

Complete the full F0.3 foundation outcome in
[seamless foundation](../frontier-v3-seamless-foundation.md): ordinary fungible
Minecraft resources can split, merge, move, be consumed, destroyed, dropped or
picked up while canonical quantities, ownership, reservations and custody stay
exact. A stack is not a permanently identified economic object. Stable identity
remains for equipment, cargo batches, contracts and reserved allocations.

Deliver the existing plan's lots, claims, custody accounts and transient HOT
physical bindings, and migrate the named production, provision, cargo,
equipment and hive-biomass families in the required fresh-world schema cut.
There must be one item unit per physical item unit, zero-sum transactions and no
second stock counter. Player/container activity becomes typed evidence rather
than a generic conflict or permission to overwrite physical custody.

Existing F0.3 evidence criteria are unchanged:

- player split, partial move and merge preserve exact total and ownership;
- hopper movement, world drop/pickup and partial recipe consumption work;
- an observer-free ticking container/hopper retains exclusive physical custody,
  including release and restart without concurrent COLD spending;
- partial player/container transfers survive declared physical-save/canonical
  confirmation crash windows without duplication or blanket rollback;
- theft of a reserved portion interrupts only its owning work;
- duplicate quantity, mixed kind, over-consumption and stale binding fail closed
  without minting or deleting resources;
- snapshot/WAL replay preserves exact totals and allocations.

This is F0.3 foundation acceptance, not broad economic gameplay, markets,
finance, Create/rail integration, F0.4 tactics/navigation, natural-terrain
hardening, human M3 acceptance, deployment or v2 removal.

## Context and continuation

- Start from the clean accepted F0.2 candidate
  `3210c8b5a3ff9be1b12b14baec9670548d4bddb1`, tree
  `70d2e790d62b2713d414fafb379e777c902e8f14`, in
  `/home/rd/proj/pm-f02c-projection-provider-snapshot-30/pale-mirror`, branch
  `terra/f02c-projection-provider-snapshot-30`. The path/branch name is
  historical and does not narrow F0.3.
- F0.VA/V/VB/VC, F0.1, F0.2A/B/C and the F0.2 foundation are accepted only in
  their recorded scopes. Reuse their infrastructure and unchanged receipts;
  do not re-prove them.
- Preserve replica/custody separation, physical eligibility, bounded knowledge,
  deferred aftermath, exact actor/object identity, one canonical mutation lane,
  durable-before-effect semantics and local recovery established through F0.2.
- The contract's physical-economy section and execution-semantics F0.3 row are
  normative. Terra determines the actual current call paths and migration
  surface; prior type names are responsibility descriptions, not a file recipe.

Terra owns technical investigation, design, decomposition, persistence format,
implementation, harness methodology, test adequacy, technical self-review and
integration through the whole working outcome. Related stage runtime/test/
harness repairs and disproved approach changes remain autonomous. There is no
METHOD_READY, per-file allowlist, per-run permission or PM technical review.

## Authority and resources

- Writable implementation boundary: stage-related source, tests, build wiring,
  technical implementation documentation and disposable harness resources in
  the existing implementation worktree. The fresh-world schema/envelope cut
  required by F0.3 is authorized; no compatibility path for rejected dev worlds.
- Private WIP commits are allowed. No push/publication, production deployment,
  live-server mutation, repository migration or destructive cleanup.
- Use isolated task-owned local roots/processes/loopback ports and task-private
  Xvfb when needed. Physical `:0` is unavailable and does not count as human
  evidence. Preserve unrelated PID2330125 and all user WIP/evidence.
- Use accepted F0.VC preparation, persistent-client and cached-build mechanisms.
  Run a full matrix only when it answers a remaining stage claim; any such new
  complete matrix uses the established four isolated workers. This order grants
  no new GitHub publication/push or runner-provisioning authority; a genuine
  external-resource need is NEEDS_DECISION.
- Retain only bounded useful evidence under the project64GiB raw-proof cap.
  Useful diagnostic/recovery retries are autonomous; unchanged confidence-only
  repeats and infrastructure speed reproof are excluded.

## Product supervision and delivery

The blocker starts at F0.3 dispatch and remains one blocker across internal
repairs. While EXECUTING, PM performs only ten-minute silence liveness and an
hourly whole-blocker product/cost audit. These checks do not inspect
intermediate code or approve Terra's technical method.

Deliver one coherent result mapping every F0.3 criterion above to scoped
automated, integration, native and recovery evidence; include technical
self-review, exact candidate identity, schema/envelope change, remaining risks,
dirty/WIP state and owned-process check. A real product/architecture/authority
decision or evidenced impasse escalates; ordinary technical failure does not.
PM performs product/architecture acceptance before F0.4 starts.

## Final review disposition: native durability closure required

The first terminal implementation packet at clean candidate
`e3edbedf5c32ba637e7cdb52a3a9f9ab7ef3fa32`, tree
`e33e2a1149f20a7cd7f56793f3cdd9faa500f7bb`, is technically accepted within
its automated scope but does not yet close F0.3.

PM independently matched snapshot schema140/persistence envelope51, packaged
JAR SHA-256
`05c53482984a0c66398e2c6bbf22eb904befb7e37389a96f8ecd7f399ffb40dc`,
the clean candidate identity, the saved-shutdown core log with309/309 GameTests
in1.066min and1,319 zero-failure/error JUnit cases across260 reports. Focused
test names and complete-run batches cover exact lot operations, transient
bindings, player portions, hopper/drop/pickup, cargo impact/reload, reservation
forfeiture and stale/mixed/duplicate rejection. No task-owned process remains;
unrelated PID2330125/port25565 is intact.

The packet explicitly reports that no dedicated F0.3 native scenario exists.
GameTest physical actors and in-process restart composition cannot establish
the required real player/container save boundary. Therefore the existing order
and execution-semantics F0.3 exit remain unmet for observer-free physical
transfer and partial-transfer physical/player durability windows. Automated
correctness must not be promoted to native custody or crash evidence.

Terra owns one coherent narrow closure from the accepted automated candidate:

- provide the smallest faithful native/recovery evidence set that crosses an
  ordinary client/player/container boundary and proves exact partial fungible
  conservation/ownership through the applicable real save and restart windows;
- include observer-free ticking container or hopper custody through release and
  restart with no concurrent COLD spend, and at least one actual abrupt boundary
  where physical/player persistence and canonical confirmation can diverge;
- bind every claimed action, quantity, owner, reservation, transient binding,
  source/JAR identity and recovered outcome in the retained scenario evidence;
  obsolete permanent-stack pilot semantics, synthetic outcome injection and a
  graceful restart standing in for a crash boundary are invalid;
- compose existing green automated evidence for the remaining criteria instead
  of forcing every F0.3 behavior into one native history.

Reuse the accepted F0.VC persistent-client/prepared-runtime infrastructure and
existing applicable scenarios or extend them coherently. Technical scenario,
crash-control and oracle design belong to Terra. Do not repeat the already green
full critical gate if production/package inputs remain byte-identical; validate
only changed harness/scenario code and the missing native boundary. If runtime
bytes change, Terra selects the proportional focused and terminal regression.
No new gameplay breadth, CI/provider run, deployment, structural-convergence
work or human/M3 claim is authorized by this correction.

## Final review acceptance

PM accepted F0.3 locally at clean candidate
`aa2783b81e06c54153663ce4c9596f7cfdf548b5`, tree
`54a6dd985539a4e65496e524de5df990fe53b89e`, parent `e3edbedf`, after
independently matching the packaged JAR SHA-256
`166707937ff6e7c6c185775436cd5acda4bb39aaa8f6d5ca2b64f1e4c72a5151`
and all 32 correction paths against retained source-content identity
`72b6a511...6e277`.

The three content-addressed native receipts are `status: ok` and bind their
scenario, terminal manifest and packaged artifact:

- observer-free demand loss checkpoints and releases the exact 64-unit HOT
  binding, leaves zero transient bindings and no COLD spend, and preserves that
  released state through a real second server JVM;
- an ordinary client container interaction moves exactly 32 of 64 units into
  the same player's real inventory and preserves 32+32, lot owner and bindings
  through a durable save and restart;
- an independently armed abrupt boundary fires after the visible physical
  transfer and before its typed observation, closes the first server/port and
  recovers the same 32+32 custody without duplication or blanket player rollback.

PM also matched the clean worktree, scenario/manifest/receipt SHA chains,
309/309 saved-shutdown GameTests, 1,326 JUnit cases across 261 reports with zero
failures/errors and the absence of task-owned server/client/Xvfb processes.
This closes F0.3 foundation acceptance only. It does not claim natural-terrain,
human/M3, deployment, release or F0.4 completion.
