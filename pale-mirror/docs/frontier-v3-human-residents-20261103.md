# Human resident presentation — 2026-11-03

The accepted scope replaces resident appearance, retaining ordinary Villager
server bodies and all Pale Mirror gameplay. No new NPC AI, profession or body
custody owner is introduced. No schema or network version changes.

## Composition

Pack repository owns pinned client downloads: EMF 3.3.12 (197m3VoU), ETF 7.2.6
(lwNx1nD1), Fresh Animations 1.11.2 (HC1XRKGD), Human Era Villager Only 3.91.5
(dBGiwRhR), plus its author-provided HVO FreshAni Activator. Original archives
remain separate, unmodified files downloaded from official Modrinth CDN URLs;
no third-party models/textures are copied into the Pale Mirror JAR. Exact file
SHA-512s and source URLs live in the pack descriptors and managed manifest.

Official authors: [Human Era](https://modrinth.com/resourcepack/human-era-villagers-illagers),
[Fresh Animations](https://modrinth.com/resourcepack/fresh-animations),
[EMF](https://modrinth.com/mod/entity-model-features),
[ETF](https://modrinth.com/mod/entitytexturefeatures).

ResidentResourcePacks validates manifest, paths, pins and dependencies and mounts
one required client resource group in FA → Human Era → activator order.
Absent/explicitly disabled manifest selects the articulated vanilla fallback.
An enabled incomplete profile fails visibly. Profiles change on client restart.

ResidentVisuals is the one semantic final pose compositor. ResidentPoseRig
abstracts arms, torso and look. CombatVillagerModel implements the vanilla rig;
HumanEraVisuals is the isolated optional EMF adapter for the pinned human rig.
Fresh Animations supplies base locomotion and face motion. Existing exact
server combat cues select QUICK/LUNGE/BURST/BACKSTEP, ahead of held-item poses.
The adapter does not invent attacks, damage, speed, movement or item ownership.
HumanVillagerRenderer preserves ModelLayers.VILLAGER and the native texture
pipeline, replacing only crossed-arm item presentation with explicit hands.

EMF exposes custom bones with EMF_ prefixes and selects state per actual entity
variant. Binding at renderer construction is premature. Its animation-end
boolean means cancellation; successful after-animation composition uses false.
The hand transform leaves native ItemInHandRenderer its standard grip offset;
adding another arm-length displacement made the sword hang below the hand.
Bindings are keyed by exact root + variant, capped at 128 and cleared on logout;
render receipts are capped at 256 and expire after two client ticks.

## Verification

Guardrails/check/verifyPackagedJar PASS9m13s:
`/tmp/pm-human-integration-final.log`. 324 domain, 2275 Frontier and 904 NeoForge
tests, zero failures; affected render corrections also compiled and crossed the
native scenarios below. Node scenario boundary: 56 passed, including parsing
both checked-in new carriers. Manifest tests reject escaping paths, duplicate
archives, missing pins and unsupported rig declarations before loading.

IMPL `build/human-residents/terminal-combat.json`: PASS11/11, run
`a00d6a5d-0bb8-4e6a-82ab-9de1421f0365`, real player harm, 99/100 observed human
rig samples, actual QUICK cue/pose, escape without extra damage and terminal
cleared response. Linked close-combat frame personally reviewed.
`delivery-carry.json`: PASS2/2, run `1f648bb3-52e5-4fcb-97af-8733de7b6423`,
ordinary patrol/carry and same-world reconnect, frame reviewed with correct
sword grip. Exact production Java/resource bytes for these final runs:
JAR SHA256 `636fc620d5ee74663a28c31895c3a9ae0662ad695617eaa0dce37f3d852ab122`,
prepared-terminal.json and native-assets.json pin the source and external inputs.
The server has no EMF/ETF installed and still starts normally: client dependencies
are optional server-side. All private clients/Xvfb and25583/4 stopped; all world
dimensions saved12:22:30. Targeted20FPS Xvfb evidence is not full-pack M3 acceptance.

The initial no-dependency run was a setup failure. Discriminating runs exposed
EMF bone names, cancellation semantics, premature binding and displaced grip.
A second model must never be constructed over an earlier baked root: EMF consumes
its last-created-root slot in Model construction. The final renderer retains the
single native VillagerRenderer model. Original combat maneuver carrier remains
unchanged. Human visual carrier checks real QUICK and a terminal response, not
optional tactical proposals: retained-world exploratory runs lacked a BURST
admission or could not move the player around a circuit. They stay failed and
are not counted as complete acceptance. Actual final-byte partial rendering also
saw BACKSTEP; earlier same-compositor runs saw LUNGE/BURST, without claiming a
fresh complete four-maneuver native acceptance on this appearance profile.
No canonical/WAL edits, actor teleports or manufactured combat outcomes.

## Delivery

Source316eb206bf2db0f4152c7a646fd6f8492e462c92; PACK888ed90d5e3b80b18c278107aa83bb003b541f69.
Clean detached release guardrails/build/verifyPackagedJar PASS2m43s, all904
NeoForge tests0fail: /tmp/pm-human-release.log. All6472 class files and resources
equal the actual final native JAR; only runtime identity metadata differs.
Comparison: IMPL/build/human-residents/release-class-reuse.json.
Release SHA25604ae9677ca4de7edef04cfb652f719aaf60d2778563fcf767434ecbfa79583d2.
Release SHA51229214f53d10ca3147e7e938cf9b7e0da1bacaf005cdf58820e84d701f4c1922574da58051841084721e2cfc9000e563e17bbe00555d3d5187748051e4b18a1d4.
PACK publisher verified164 indexed files and published exact PM plus unchanged
Visuals. Shared installer verified the installed hash. MAIN same world
combat-maneuvers-schema323-seed136-20261103 saved12:26:49; new service started
epoch1793690847, wrapper2358470/Java22PID2358496,25565/RCON25575. Preflight and
fresh deploy verify PASS (/tmp/pm-human-{preflight,deploy-verify}.log).
Actual post-start summary109331/revision289985 statusok, scene/inventory conflicts0;
requiredConflicts1 remains the historical retained kernel incident, not a new
quarantine. No world reset or schema migration. Client updater is required for
the new presentation; no manual resource order. IMPL/RELEASE clean, PACK tracked
clean with pre-existing.f0v-baseline preserved. No push. Governance unrelated WIP
is preserved; only this note, active ledger and own architecture key are committed.
