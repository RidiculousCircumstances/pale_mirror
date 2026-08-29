# Frontier v3 test harness

Status: active development contract for reproducible v3 verification. This is a
development/test facility, never a second simulation or a production control
surface.

## Purpose

The harness proves one causal chain against a real NeoForge server:

```text
declarative scenario -> ordinary network player packets -> Minecraft result
-> v3 observation/command -> WAL transaction -> immutable diagnostic snapshot
-> optional visible player-height frame
```

It complements, but does not replace, pure reducer tests, NeoForge GameTests,
restart tests or unbriefed player product tests.

## Components and authority

| Component | Runs where | Authority |
| --- | --- | --- |
| `frontier-v3-test-pilot` | One normal visible NeoForge client | The actual pilot runs the standard client interaction layer on `DISPLAY=:0`; it has no canonical-state access. Its temporary operator status is limited to declared world setup and read-only diagnostics. |
| Scenario runner | External Node process | Validates the scenario, launches one visible NeoForge client, reads its ordinary client log/diagnostic replies and writes an evidence manifest. It does not alter canonical state. |
| V3 diagnostic command | Server thread | Reads one immutable v3 checkpoint/projection and emits bounded JSON. It submits no command, writes no WAL entry and never loads a chunk. |
| Visible audit client | Optional real NeoForge client on `DISPLAY=:0` | Observes/captures frames only. It is never launched alongside the pilot: the one visible pilot is normally its own player-height observer. |

Mineflayer remains an offline protocol preflight only. The full Far Frontier
server requires NeoForge's mod-channel handshake, so a Mineflayer connection is
correctly rejected before it can be evidence. The actual pilot is therefore the
one graphical NeoForge client, not a protocol imitation; its movement and
interactions still travel through ordinary Minecraft packets.

## Read-only diagnostics

`/pale_mirror v3 inspect` is operator-only and is available only when the v3
launch mode owns Graybox. It writes one prefixed, compact JSON document to the
calling command source:

```text
PMV3_DIAG {"schema":1,"kind":"site",...}
```

Supported views are summary, site, actor, item, operation, physical intent and
bounded recent trace.
They expose stable canonical identity, owner/custody, location, current phase,
lease/intent status and the revision/instant sampled. A missing subject is an
explicit bounded `not_found` result. The response has a fixed maximum size;
large collections are represented by counts and deterministic first-N entries.

The command is intentionally not an HTTP service and cannot fast-forward,
materialize, teleport, mutate a player or reveal mutable internals to adapters.
The scenario runner parses the same client log/chat text that an operator sees.

## Pilot actions

Each action is performed using standard client packets. Initial positioning and
game-mode changes are explicit `setup` commands; they are never evidence.
Evidence actions use the pilot's real movement, look and block-attack paths.
`inspect` is a separately declared read-only observation, delivered through the
same ordinary client command channel and retained in the runner manifest.

The first supported action set is deliberately small:

- connect/disconnect and wait for a loaded position;
- walk/look at a declared block position;
- wait until the client actually has the named block, or fail with the observed
  block state rather than relying on a guessed delay;
- break a block and issue a read-only v3 inspection;
- wait for an ordinary server tick interval.

Native container/inventory/death paths are the next pilot slice. They remain
deliberately unclaimed until each has a real-server proof; the old Mineflayer
implementation cannot supply that proof against the required NeoForge handshake.

No evidence-bearing pilot action may call a Pale Mirror mutation command,
submit a domain payload or write world files. The runner may issue only
`/pale_mirror v3 inspect` between steps through its temporary operator account;
that read is recorded as diagnostic evidence, never as an action result. A
scenario that needs a world mutation names it as server setup and cannot use
that mutation as causality evidence.

## Scenario format and evidence

Scenarios are versioned JSON files. They pin world identity/seed, pilot name,
ordinary player actions, read-only predicates and required frames. The runner
writes one manifest containing the source scenario hash, pilot protocol
version, server/world identity, diagnostic samples, correlation IDs and PNG
paths. No screenshot pixel equality is used as a correctness oracle.

The initial regression is `visible_field_player_break`:

1. join a clean v3 world and naturally load Northwatch's field;
2. wait for the client to confirm the named wheat block rather than assuming a
   teleport loaded its chunk;
3. pilot breaks one named crop through its normal packet path;
4. assert the site becomes `CONFLICT`, the returned revision advances and the
   diagnostic cause identifies the pilot;
5. retain before/after player-height frames and the bounded event trace.

Later fixed scenarios cover depot theft, route obstruction, infection,
explosion, leave/return and restart during an effect. Each new scenario must
state its normal path, rejection/conflict expectation and recovery assertion.

## Correlation and retention

The runner records a deterministic `scenario:<run>:<step>` identifier beside
each action in its manifest. A standard player observation has a server-owned
correlation `player:<UUID>` and, after acceptance, its command,
transaction and revision in a bounded recent trace. The runner stores both
identifiers and queries the latter with `inspect trace`. Trace loss is reported
as `not_found`, never inferred from a changed state. The trace is test evidence
only and does not become canonical simulation state.

## Acceptance boundaries

- Test-pilot evidence proves level-3 network/physical causality, not visual
  comprehension or balance.
- The visible observer proves a frame existed at normal player height; it does
  not make remote chunks HOT by itself.
- Production jars do not require Node or Mineflayer. Test tooling is excluded
  from Packwiz and the normal client/server artifact.
