# PM-VIS002-SEMANTIC-SCENE-MAP-CAMERA-01: semantic scene map and camera planner

Revision: 1. Risk: development infrastructure and player-visible evidence.
Status: `PLANNED / AFTER OBS-002 / BEFORE NEXT PLAYER-VISIBLE CANDIDATE`.
PM / architect: Sol. Senior tech lead and sole executable author: Terra,
gpt-5.6-terra, reasoning high.

## Activation and outcome

Do not interrupt the active F0.6R3 correction or rebuild its r75 world for this
tool. After F0.6R3 acceptance and the reusable OBS-002 semantic-verification
slice, deliver one read-only semantic scene-map and camera-planning capability
before the next player-visible `HUMAN_CANDIDATE`. `ARC-001E` may proceed while
this visual-tooling gate is pending, but MAT-006 or another player-visible
candidate may not bypass it.

The tool must let the graphical pilot locate and frame a meaningful scene from
its semantic geometry instead of relying on hard-coded coordinates or manual
camera guessing. It does not move settlements to the world origin, change
scene geometry, load remote chunks, mutate canonical state or replace review of
the actual rendered frame.

Terra owns technical design, decomposition, file choice and proportionate
verification. PM reviews the read-only authority boundary, reuse across scene
shapes and the resulting graphical evidence rather than prescribing a camera
algorithm.

## Required result

- Produce one deterministic top-down scene-map artifact with a declared world
  coordinate frame and bounds. It distinguishes planned/current/observed/
  unknown data and shows, when applicable, building footprints, roads, fields,
  water, boards, work or interaction stations, route/effect envelopes and
  current exact actors.
- Discover subjects and geometry through authoritative typed identities and
  relationship/plan views. ID prefixes, fixed settlement coordinates, sole-
  match inference and relocation to `(0, 0)` are forbidden.
- Derive a bounded set of legal player-eye camera candidates around the target.
  Each candidate records body/clearance, view direction, frustum coverage and
  direct-visibility results for every required semantic target.
- Check actual occlusion only through read-only geometry already available to
  the ordinary graphical client. Unknown or unavailable geometry remains
  explicit and cannot be treated as clear line of sight. The camera planner may
  not force-load chunks or become a second world/scene authority.
- Deterministically choose the best valid pose where all required objects fit
  the frame and are not materially occluded. Retain every rejected candidate's
  typed reason. If no pose satisfies the contract, return `INCONCLUSIVE` with
  the blocking targets/geometry rather than capturing a misleading pass.
- Treat the observer as a physical input. A candidate overlapping a worker
  route, station, interaction or effect envelope is rejected as harness
  interference before the evidence interval.
- Save beside each screenshot: the semantic map in human-readable form, a
  machine-readable map/camera manifest, chosen pose and required targets,
  visibility/framing verdicts, source/world/run identity and checksums. The
  screenshot remains the player-visible evidence; the map explains and makes
  its selection reproducible.
- Support reusable point, area, path and group targets so the same contract can
  serve farms, workshops, caravans, battles and distributed hive scenes without
  family-specific camera code. Scene descriptors supply semantic targets and
  presentation priorities; the common planner owns geometry and selection.
- Keep output and computation bounded by declared map extent, candidate count,
  ray/visibility budget and artifact retention. A large or moving scene may
  select a bounded representative view or an explicit ordered view set, never
  an unbounded scan.

## Acceptance stories

1. The retained farm case produces a readable frame containing the field,
   `WHEAT FIELD` board and exact assigned farmer; the green occluder that
   invalidated r75's east/south frames is detected and those poses are rejected.
2. A workshop or another enclosed facility selects a legal player-eye pose that
   shows its named worker and required stations without clipping through a wall
   or occupying the worker's route.
3. One materially different path/group fixture proves the common target model:
   a moving caravan/battle group or distributed hive target yields a bounded
   ordered view set without hard-coded family branches.
4. A deliberately impossible composition returns `INCONCLUSIVE` with exact
   occlusion/unknown/interference reasons and cannot satisfy a graphical
   promotion receipt.
5. Repeating the same scene identity and geometry chooses the same camera/map
   manifest; a changed semantic target or observed occluder changes the
   manifest and invalidates stale screenshot reuse.
6. The map/visibility pass performs no canonical mutation, remote chunk load or
   route/actor movement. Tooling enabled/disabled leaves canonical hashes and
   simulation outcomes identical.

## Scope and economy

Reuse the existing test pilot, semantic camera assertions, typed relations,
scene/facility plans, OBS envelopes and graphical artifact manifests. The first
delivery proves the farm plus the smallest structurally different cases needed
to reject a farm-only abstraction. It is not a settlement relocation, terrain
campaign, renderer rewrite, computer-vision programme, pixel-golden suite or
all-scene screenshot matrix.

Run focused schema/geometry/visibility tests during implementation and one
exact graphical terminal pass on the frozen candidate. Do not repeat unaffected
F0.6R3, OBS-002, deployment or full release gates merely to certify the tool.
Human visual judgment and unbriefed M3 remain separate.

## Exclusions

- no canonical mutation, repair, scheduling or camera-driven gameplay action;
- no hard-coded seed, settlement origin, actor ID or one-off r75 coordinate;
- no force-loading, world-file edits or remote full-world scans;
- no screenshot pixel equality as a correctness oracle;
- no mandatory external map server, database or hosted telemetry service;
- no broad aesthetic redesign, MAT feature breadth, deployment, push or cutover.
