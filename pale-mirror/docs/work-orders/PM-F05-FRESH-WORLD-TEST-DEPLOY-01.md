# PM-F05-FRESH-WORLD-TEST-DEPLOY-01: expose the accepted recovery build in a fresh human-test world

Revision: 2. Parent slice: post-F0.5 checkpoint. Risk: critical-code/deployment.
Status: ACCEPTED_OPERATIONAL at 2026-09-12T07:37:36Z.
PM / architect: Sol. Senior tech lead and sole coder: Terra,
gpt-5.6-terra, reasoning high.
Workflow: [PM/TL protocol](../engineering-agent-protocol.md), revision
2026-09-11-PM-TL. Server operation follows the repository
`pm-test-server-ops` rules and their current operations map.

## Product outcome and acceptance

Make the exact independently accepted F0.5 build available on the disposable
Far Frontier test server in a genuinely new world so a human can enter and
explore the current product rather than the obsolete r71 build. The deployment
is successful only when artifact identity, world identity, service readiness,
fresh logging, save/restart and absence of a new quarantine are proved from the
real runtime.

Acceptance requires:

- bind the deployment to the clean accepted F0.5 commit/tree, schema/envelope
  and packaged-JAR digest. Use the already accepted artifact when intact, or a
  clean detached reproducible build whose output matches its accepted identity;
- validate the outer Far Frontier pack and install/publish only the server and
  client artifacts actually affected by that candidate. Record whether a client
  refresh is required and provide the exact user action if it is;
- stop the resolved `far-frontier-v3-live.service`, resolve its current
  `level-name` and every corresponding world path under the disposable runtime,
  and remove only those literal paths without backup. Reject an empty, broad,
  escaped or unresolved target. Create a distinct new world identity and record
  its `level-name`, seed and Frontier catalog/profile identity;
- start the transient service through the checked-in outer-repository workflow
  and prove its actual Java process, configured listening port, fresh log
  timestamps, NeoForge and Pale Mirror load, dedicated-server ready line and
  absence of an immediate crash loop, quarantine or persistence/logging error;
- complete one ordinary save and graceful same-world restart, then re-prove the
  same accepted artifact and world identity, service readiness and writable
  logs. This is deployment recovery smoke, not another F0.5 native matrix;
- return the server address, artifact/world identities and a short human entry
  checklist. The server is then ready for exploratory human testing, but no M3,
  visual-quality, comprehension, co-op or release gate is inferred until a
  human actually performs and reports it.

This checkpoint does not authorize production cutover, v2 removal, natural-
terrain/Foundry hardening, new gameplay, a full native matrix, a performance
campaign or a formal human-acceptance claim.

## Context and continuation

- Canonical governance/ledger:
  `/home/rd/proj/pm-governance/pale-mirror`; adopted monorepo
  `/home/rd/proj/pale-mirror-monorepo`; implementation candidate worktree
  `/home/rd/proj/pm-f02c-projection-provider-snapshot-30/pale-mirror`.
  The accepted F0.5 release identity is commit
  `158f5b938ea0a75fb11b6d7ae29f89d1b650e7fc`, tree
  `1e3f91b141166d1142301f5c43b127375af1d043`, schema149/envelope60 and
  packaged-JAR SHA-256
  `269967bf391c770f6887ea8697896598b2077aa4f42f7d204f35da333bc74e1f`.
  No moving branch head is a release identity.
- Outer pack/deployment repository: `/home/rd/proj/minecraft`, identified by
  `pack.toml`. Disposable runtime: `/home/rd/far-frontier-server`. Discover the
  current service, paths and properties again immediately before mutation; this
  document records observations, not eternal environment constants.
- At planning time the live service has run since 2026-09-04 with old source
  commit `c78282d5`, world `frontier-v3-live-r71`, core JAR SHA-256
  `2ddf87eec262622e85dbc0b49d2069d0b552706e2f0849f8f401ba8921b0799b`
  and child Java PID `2330125` on port 25565. It is the explicit deployment
  target, not an unrelated process protected from this order.
- That old runtime emitted `No space left on device` save and log failures on
  2026-09-09. Space is currently available, but the week-old process and empty
  `latest.log` are not valid readiness evidence. Do not adopt or repair its
  world as the new test world.
- Preserve the original nested source/governance WIP, `.f0v-baseline/`, accepted
  F0.5 evidence, migration recovery bundles and every unrelated service/data
  root. The outer repository's existing unrelated dirty state is not deployment
  content.

Terra owns deployment diagnosis, exact safe path resolution, release packaging,
pack/install sequencing, related deployment/config corrections, service
lifecycle and verification through the complete operational result. A source
or product-semantics defect discovered here returns one coherent exception for
PM triage rather than being hidden by retries, world reuse or weakened checks.

## Authority and resources

- F0.5 acceptance and this explicit dispatch activate authority for the exact
  disposable live service/runtime, the exact resolved old world paths, a new
  world/config identity, affected hosted test artifacts and task-owned
  deployment evidence.
- The current disposable world may be deleted without backup after the service
  is stopped. No glob, unresolved variable, home/root/workspace target or other
  world/server may be deleted or reset.
- Stop/recreate the transient test service as required. Preserve unrelated
  services, ports, processes, player files outside the resolved disposable
  world, Git histories/WIP, global caches and private reference material.
- No Git push/history rewrite, public release, production service, new CI
  dispatch or broad cleanup is authorized. A pack/config correction required
  only for this exact deployment stays minimal, reviewed and separately
  identified in the terminal packet.

## Verification economy and delivery

Reuse the accepted F0.5 critical/native evidence. Do not repeat its full gate,
native matrix, JFR or timing proof merely for deployment. Run only release-
identity/pack preflight, the checked-in install workflow, deployment verifier,
one save/restart smoke and the cheapest diagnostic needed for a real deployment
failure.

Return one terminal packet with exact source/tree/schema/envelope/JAR and pack
identities; old and new literal world paths; deletion/new-world result; service
unit, PIDs, port and timestamps; startup and post-restart verifier receipts;
quarantine/save/log status; client-refresh requirement; human entry checklist;
repository dirty state for both histories; retained evidence; and remaining
human/product claims. F0.6 may start after PM accepts this operational boundary;
formal M3 evidence remains a later release prerequisite.

## Acceptance record

PM independently accepts the operational deployment of F0.5 commit
`158f5b938ea0a75fb11b6d7ae29f89d1b650e7fc`, tree
`1e3f91b141166d1142301f5c43b127375af1d043`, schema149/envelope60. The built,
hosted and installed core JARs share SHA-256
`269967bf391c770f6887ea8697896598b2077aa4f42f7d204f35da333bc74e1f`
and installed/hosted SHA-512
`33964f14f655087dad602138a4a720bd7e46e4514df027df1274b91dbc65f390cfb1c0233259cfdd16ad02fe6a735ed956df27bab472fb2ef217b7b5e5bbb502`.

The old service was stopped before removal. Literal world paths
`/home/rd/far-frontier-server/frontier-v3-live-r71`,
`frontier-v3-live-r71_nether` and `frontier-v3-live-r71_the_end` are absent;
only the first existed before deletion. The distinct new world is
`/home/rd/far-frontier-server/frontier-v3-f05-r1`, seed
`9031746258841137206`, with the `pale_mirror:frontier_graybox` datapack and
fresh `frontier-v3/frontier_graybox` snapshot/WAL progression.

Fresh invocation `fa9de272...` reached dedicated-server readiness and Frontier
v3 startup, then a normal stop advanced `level.dat`, reported systemd success
and released port25565. Same-world invocation
`005928cdd4a24346a0ea699620f9c851` independently passes the checked-in
read-only verifier. At acceptance `far-frontier-v3-live.service` is active with
service PID3376135, Java PID3376160 and port25565. Its fresh journal contains
`Done` and `Frontier v3 runtime started`, with no Frontier quarantine, new
no-space condition or save/persistence failure.

Deployment exposed and corrected one outer-pack workflow defect: preserved
ignored source/evidence roots could enter the loose-JAR check. Outer commit
`16eec629f650fb1eeb5eead7b8fd1f0a8c3f281a`, tree
`fa97dad4093507976ba11dc7a68be937a105a8ed`, restricts that check and
`.packwizignore` to the already preserved `pale-mirror/` and `.f0v-baseline/`
roots and updates only the derived Packwiz hashes. Pack validation passes;
outer dirty state remains only untracked `.f0v-baseline/`. The source worktree
is clean at the accepted F0.5 identity. The client host remains active and
serves the matching pack/JAR.

The server is ready for exploratory human use after the client instance is
refreshed from `http://192.168.0.103:8092/pack.toml`, then connected to
`192.168.0.103:25565`. No human has yet established M3 comprehension, visual
quality, co-op behavior or release acceptance.
