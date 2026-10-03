# PM-F06R3-RESTART-OPERATOR-CAPTURE-01: preserve and query the live farmer incident

Revision: 2. Parent slice: terminal live-client capture revision 2.
Risk: critical live recovery/diagnostic operation. Status:
`ACCEPTED_CAUSAL_CAPTURE / SERVICE_RESTORED`.
PM / architect: Sol. Autonomous senior tech lead and sole operator: Terra,
`gpt-5.6-terra`, reasoning high.
Workflow: [PM/TL protocol](../engineering-agent-protocol.md), revision
`2026-09-15-PM-TL-JUDGMENT-LOOP`.

## Outcome

Preserve the reproduced farmer incident through an ordinary graceful restart
and obtain its exact OBS site/settlement/work/worker/trace/why account through
a temporary loopback-only operator channel. Finish with the same verified JAR
and world running normally, RCON disabled and every secret/task process removed.

The exact input runtime is invocation `ee4259dbf5a84d3e800efe3910c8de97`,
Java PID 3014780, port 25565, world
`frontier-v3-f06-r3-obs-xact-r1-20260920`, JAR SHA-256
`466680618904274856311186c692bdfdcaf7dc9117577e1cd725e0339b883f17`.
Player `rd` disconnected at `2026-09-20T13:18:11+05:00`; verify no player is
present before stopping the service.

## Authority and safety

Terra owns the operation end to end without per-command approval.

- Resolve the exact service/world paths, gracefully stop and verify final save,
  then preserve one immutable exact pre-query world copy plus runtime/config
  identities. Never mutate the only copy or delete any world.
- Use a generated task-private secret and loopback-only RCON solely to issue
  read-only Pale Mirror inspect commands. Do not expose the secret in logs,
  shell transcripts, artifacts or the terminal packet.
- Do not fast-forward, teleport, load chunks, move players, mutate blocks/
  entities, repair state, write source/tests/pack, change JARs or include MAT
  WIP. No gameplay or harness correction belongs here.
- Query the site, settlement, exact work/worker, trace, why and incident facts
  available after restart. Absence or changed disposition is itself exact
  recovery evidence; never reconstruct a missing fact heuristically.
- Restore `enable-rcon=false` and an empty password, gracefully restart again if
  required for closure, verify port 25575 closed, and run the existing deploy
  verifier. End with the exact diagnostic JAR/world active on port 25565 and no
  task-owned process.
- Preserve unrelated services, repositories, worlds, evidence and user WIP. No
  commit, push, publication, new world, reset or cleanup is authorized.

## Terminal packet

Return exact before/pre-query/after runtime and world-copy identities, graceful
save/restart evidence, all OBS facts, whether the contradiction and cause
survived each restart, final deploy verification, closed RCON/task handles and
one concise existing-contract discrepancy for the next correction. Do not
claim a gameplay fix, M3 or F0.6R3 acceptance.

## Acceptance record

The first graceful stop retained final-save timestamps at
`2026-09-20T13:32:27-28+05:00`. Immutable pre-query evidence is under
`/home/rd/far-frontier-server/.pale-mirror-backups/f06r3-restart-operator-capture-20260920T133227+0500/`
with directory/world modes `500/555` and original-properties mode `400`;
properties SHA-256 is `550653c2...7449f5f`. Temporary invocation
`f44cf9c1...01a2d` bound game/RCON only on loopback, emitted no secret and was
closed. Main independently matched final invocation
`16890490a05c4aefa768dfc5fa3010a9`, wrapper PID 3044250, Java PID 3044280,
same JAR `46668061...83f17`, same world and port 25565. RCON is disabled with an
empty password, port 25575 is closed, fresh ready/runtime markers exist and the
deploy verifier passed.

The accepted recovered causal account is:

- site 7 is mature `READY` at growth stage 7 with no `activeWork`, conflict or
  terminal harvest disposition;
- settlement 7 retains an `ACTIVE` harvest facility objective, no pending
  harvest schedule and `harvestAdmission=FACILITY_LANE_BUSY`, despite an intact
  facility/depot and four living farmers;
- exact available farmer `resident:7-13` is alive, hungry, `IDLE`, but its
  `AMBIENT:PATROL` duty and ambient lease are `UNKNOWN_AFTER_RESTART`;
- all 93 recovered ambient HOT leases are retained unknown pending loaded-world
  recovery, while scene leases and incident-index entries are zero; and
- no active harvest work, trace or durable incident exists to explain the
  blocked lane.

Thus recovery preserves the contradiction rather than repairing it: an absent
harvest job and unknown ambient patrol can leave the facility lane permanently
busy while an exact eligible farmer and READY field exist. Missing causal
incident/trace is a second OBS contract discrepancy. This is accepted diagnosis,
not a gameplay fix or M3.
