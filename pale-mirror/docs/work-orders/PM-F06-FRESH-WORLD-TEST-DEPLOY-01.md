# PM-F06-FRESH-WORLD-TEST-DEPLOY-01: expose accepted F0.6 in a fresh human-test world

Revision: 2. Parent slice: accepted F0.6 / suspended MAT-004. Risk: operations/release.
Status: ACCEPTED_OPERATIONAL at 2026-09-12T21:21:05+05:00.
PM / architect: Sol. Autonomous senior tech lead and sole coder/operator: Terra,
gpt-5.6-terra, reasoning high.
Workflow: [PM/TL protocol](../engineering-agent-protocol.md), revision
`2026-09-11-PM-TL`. Server and release operations follow the current
`pm-test-server-ops` and `pm-release-verification` skills.

## Product outcome

Make the exact independently accepted F0.6 build available to the player on
the disposable Far Frontier test server in a genuinely new graybox world. This
deployment must expose the accepted continuous farmer locomotion and harvest-
liveness corrections without mixing in unfinished MAT-004 patrol work.

The operational result is:

- accepted source commit
  `2190797dcbd9d747df34a8e2cf12ed646eb1b195`, tree
  `923954b31721e8567d68bb2957c6f18554655546`, schema150/envelope60 and
  packaged-JAR SHA-256
  `171f8e7d56ee5c8bef840c551e48d6e13347aefd2ee0df8603a88d1e91abd9fc`
  are the immutable release identity; no branch head or MAT-004 checkpoint may
  substitute for them;
- the matching server/client artifact is published and installed through the
  checked-in Far Frontier workflows, and the exact client refresh action is
  reported;
- a distinct new world named `frontier-v3-f06-r1` is generated with a newly
  selected and recorded seed and the intended graybox catalog/profile;
- the service reaches real dedicated-server readiness, Pale Mirror runtime
  startup and the configured listening port with no new crash loop, quarantine,
  no-space, save or persistence failure;
- one ordinary save and graceful same-world restart preserves the same artifact
  and world identities and returns to readiness;
- the prior `frontier-v3-f05-r1` world and its dimensions remain present and
  untouched as the rollback point.

This is operational deployment readiness for exploratory human testing. It
does not itself prove human/M3 comprehension, visual quality, co-op behavior,
natural-terrain hardening, MAT-004, production cutover, v2 removal or release.

## Current boundary and preserved work

- Canonical governance is `/home/rd/proj/pm-governance/pale-mirror`; the outer
  pack/deployment repository is `/home/rd/proj/minecraft`; the disposable
  runtime is `/home/rd/far-frontier-server`. Resolve every target again before
  mutation rather than treating these observations as permanent facts.
- At dispatch, `far-frontier-v3-live.service` is the authorized deployment
  target and exposes accepted F0.5 on port25565 in world
  `frontier-v3-f05-r1`, seed `9031746258841137206`. The separately hosted
  client service is active on port8092.
- MAT-004 is safely suspended at clean WIP commit `c750d50b` in
  `/home/rd/proj/pm-f02c-projection-provider-snapshot-30`; its latest focused
  Java/unit lane is green and no task-owned MAT-004 Minecraft/GameTest process
  remains. Preserve this commit, branch, evidence and temporary roots exactly.
- Build or recover the F0.6 artifact only from a clean detached checkout of the
  immutable accepted commit. Do not switch, reset or clean the MAT-004 worktree.
  Reuse the accepted artifact if its provenance and digest are intact; otherwise
  perform the narrow reproducible detached build and packaged-JAR verification.
- Preserve both Git histories, the outer repository's unrelated
  `.f0v-baseline/`, all retained acceptance evidence, caches and unrelated
  processes. Report dirty state for both repositories.

## Authority and prohibitions

The user's deployment instruction authorizes Terra to update the affected
locally hosted pack artifact/metadata, install the matching server artifact,
change the disposable runtime to the new world identity, and stop/start or
recreate the resolved `far-frontier-v3-live.service` as needed. Terra owns the
exact safe sequencing, technical diagnosis and release result.

No source/product implementation change, new gameplay, CI dispatch, public
release, remote push, history rewrite, broad cleanup or unrelated service/data
mutation is authorized. Do not delete, reset, rename or overwrite the F0.5
world. Do not deploy the MAT-004 WIP. If an actual deployment-workflow defect
blocks the release, Terra may make the smallest directly related pack/deployment
correction, verify and identify it separately; a source/product defect or a
change to persistence semantics returns one coherent exception to PM.

## Required execution and acceptance

Terra chooses and executes the implementation details, but the terminal result
must establish all of the following without a repeat F0.6 proof campaign:

1. Resolve the active service, Java22 executable, port, runtime, current world,
   hosted pack and source/artifact paths. Prove available disk space and record
   the pre-deployment service/world state.
2. Bind a clean detached checkout and artifact to the exact commit, tree and
   accepted SHA-256 above; derive and record SHA-512. Validate the outer pack
   and run the checked-in read-only deployment preflight before the destructive
   service/world boundary. Do not weaken a failed preflight.
3. Prepare the distinct `frontier-v3-f06-r1` identity and required graybox
   datapack/profile. Reject reuse if that destination already contains a world;
   choose a new suffix instead of deleting an unexplained destination. Stop the
   resolved live service only after artifact, pack, paths and restart command
   are ready. The F0.5 world remains untouched.
4. Publish/install the exact accepted artifact, start the transient user
   service through the checked-in workflow and pass
   `scripts/frontier-v3-deploy-verify.sh` against fresh timestamps. Inspect the
   actual child Java process, configured socket, current journal/logs and new
   world persistence rather than inferring health from the wrapper alone.
5. Perform one ordinary save and graceful stop/restart into the same F0.6 world,
   then rerun the deployment verifier and the cheapest direct checks for
   artifact/world identity, writable persistence and absence of new quarantine,
   crash, save, logging or disk-space errors. One green terminal run is enough.
6. Leave the accepted F0.6 service running and the hosted client update
   reachable. Return the address and concise player steps for refreshing the
   pack and entering the new world. Do not claim that the farmer observation is
   human-accepted until the user actually tests it.

No full GameTest/native/pressure/JFR matrix is requested: the accepted F0.6
evidence is reused. A failed operational check is localized by the narrowest
faithful diagnostic; unrelated or redundant reruns are prohibited.

## Terminal packet

Return one concise packet containing:

- source commit/tree/schema/envelope and built, hosted and installed JAR
  SHA-256/SHA-512 identities;
- pack commit/tree or explicitly identified deployment-only WIP, validation
  result and hosted URLs;
- old and new literal world paths, proof the old F0.5 world remains, new seed
  and graybox catalog/profile identity;
- service unit, wrapper/Java PIDs, configured/listening port, invocation and
  fresh startup/post-restart timestamps;
- initial and post-restart verifier receipts plus save/quarantine/crash/log/
  disk observations;
- exact client refresh/connect action and remaining human/product claims;
- source and pack repository dirty states, preserved MAT-004 checkpoint and
  confirmation that no task-owned deployment process remains beyond the live
  service.

After PM operational acceptance, resume MAT-004 autonomously from the preserved
`c750d50b` boundary; do not reinterpret this deployment as MAT-004 evidence.

## Acceptance record

PM independently accepts deployment of F0.6 commit
`2190797dcbd9d747df34a8e2cf12ed646eb1b195`, tree
`923954b31721e8567d68bb2957c6f18554655546`, schema150/envelope60. The accepted,
hosted-over-HTTP and installed core JARs all have SHA-256
`171f8e7d56ee5c8bef840c551e48d6e13347aefd2ee0df8603a88d1e91abd9fc`
and SHA-512
`9e48369e4e90db6c6fa72825aed331700aaf3d2a6bab6a1afe21f628d7f8a57f889cddbf1d141247b42c813fd879955b72825548e888937c0e2c06305218f59c`.

The distinct world `/home/rd/far-frontier-server/frontier-v3-f06-r1`, seed
`4774247516566241850`, uses graybox catalog SHA-256 `1efa152e...8e25ab` and
dimension SHA-256
`965f8450c47f77784fee7e8a85bcc81aae86bc08c1e96c4dffd38dbcf0ac57ec`.
The rollback world `/home/rd/far-frontier-server/frontier-v3-f05-r1` remains
present. Initial invocation `b023fafd8c344c27a21dd9ea3b5e8cb1` reached
dedicated-server and Frontier runtime readiness, then stopped gracefully.
Same-world invocation `1b3f2949d8914779b24f4efc0468bfc4` independently
passes the checked-in verifier; snapshot/WAL sequence continues advancing after
restart with no fresh Frontier quarantine, crash, no-space, save or persistence
failure.

At acceptance `far-frontier-v3-live.service` is active with wrapper PID3937975,
Java PID3937999 and listening port25565. The client host remains active on
port8092 and serves the matching pack, checksum sidecar and JAR. The player can
refresh from `http://192.168.0.103:8092/pack.toml` and connect to
`192.168.0.103:25565`; the Tailscale alternative is
`http://100.100.78.29:8092/pack.toml`.

Deployment exposed one pack preflight mismatch with the explicitly preserved
untracked evidence root. Outer commit
`f714474ddf8f291db9beab6f74542881ec3d8072`, tree
`37ae68bde60e3d9c7185836bcec8ba1c19d61529`, excludes only
`.f0v-baseline/` from the cleanliness check while every other dirty path remains
fatal, and updates derived Packwiz hashes. The pack repository now reports only
that preserved untracked root. MAT-004 is clean and preserved at `c750d50b`,
was not deployed, and may resume. This acceptance is operational only; human/M3
and all later product/release claims remain open.
