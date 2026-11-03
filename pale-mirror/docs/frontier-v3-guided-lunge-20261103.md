# Guided long attack and broad sword arc — 2026-11-03

Completed, source2fc2a8a3d1a7c45a0abb961737e2a165f36e1c82, baseline3fb952955. User requests modest steering during the
long attack and a visibly broad sword sweep. This is not an always-on sprint.

## Mechanics and responsibilities

EngagementRules holds the hashed policy: long attack half-angle85 degrees
(total170), early aim tracking650permille of native preparation, at most12degrees
per observed native tick, physical correction within2 support steps of its
retained approach. Default6-tick long preparation tracks through tick3; the last
two preparation ticks and contact keep the committed facing. Sword reach, LOS,
exact opponent, ordinary Minecraft hurt protection and one-shot journal remain.
Schema325 explicitly rejects old disposable worlds because the rule hash changes.

CombatPresentation owns native clock/facing observations and broadcasts the same
existing read-only cue. Start/durability discipline from the previous contact
repair remains. Impact no longer snaps the displayed facing onto a late target.
Client MeleeAnimation draws across the body, reaches the contact pose, follows
through sideways during the active interval, then recovers. No client damage
or timing authority and no new payload/network protocol.

MeleeGuidance supplies a short correction only for the current exact pending
long strike and a role-permitted HOT approach. It consumes the existing common
BodyObservation for both supported bodies and the registered combat permission;
no guessed feet layer, constant Y, target prediction or new path search. A
replaced/missing/COLD leg cannot receive the correction. At commitment it retains
the last observed point rather than tracking future player motion. Impact or
cancellation withdraws the exact request.

LocalGoalCorrection is a bounded disposable common navigation input keyed by
native body, full actuation, original MovementOrder and producer token. Its
finite scope and2-tick renewable lifetime cannot award semantic arrival. Shared
ActorMovementNavigation alone consumes it after ordinary movement/service
permission checks; GoalNavigation binds its provider permission to the original
order. It uses the same collision, crowd/courtesy and sole body actuator. Stale
orders/epochs/tokens cannot refresh another owner's request or cancel its path.
No guard-business checks were added to generic navigation. Other supported
combat roles reuse the same mechanic through their registered permission port.

## Verification and delivery

Guardrails, Frontier combat/security/codec113 and NeoForge906 PASS,
/tmp/pm-lunge-final-check.log (2m47s). Scene-strikes12/12 PASS,
/tmp/pm-lunge-check-3.log (1m20s), including finite early aim -> frozen contact,
wide side contact inside unchanged reach and rear dodge, in addition to the prior
journal/full-windup/one-hit/no-replay cases. A focused correction test fences
parent order, physical epoch and lifetime, and proves its point is not parent
arrival. Animation checks prove a cross-body sweep and recovery.

Checked-in retained-guided-lunge9/9 PASS on the naturally prepared and normally
saved/restored schema325 world v3-disposable_guided_lunge-11f451f0, seed41,
peaceful-guards. Receipt IMPL/build/guided-lunge/retained-native-4.json,
run739c7427-78d0-4838-9ec5-5379a50d5497. Guards/iron swords were produced through
the actual10k-tick COLD prelude; remembered player hostility survived normal
restarts. The ordinary creative player built/cleared a9x9 flat arena around the
actual guard using Minecraft fill commands. No NPC position/equipment/job/health,
canonical outcome or WAL was fabricated. Controlled player setup uses maxhealth
1000, no regeneration and knockback resistance1; this is a scoped mechanical
probe, not a survival balance or full-pack/M3 certification.

Actual400 samples over400ticks: player traversed85.58blocks by keyboard circuit;
all400 samples used human_era,97 rendered LUNGE samples and6 physical-motion
samples during long preparation. Five accepted real hits occurred during the
moving interval (994->964), plus one just before it (1000->994). The later240-tick
stationary interval ended946; ordinary escape preserved946 and retired contacts,
responders and engagement. Guidance requests at ordinals27040/27115 changed the
retained physical station and those same exact attempts applied real damage.
Evidence summary: build/guided-lunge/combat-evidence.json. Linked frame reviewed:
actual HumanEra guard on prepared floor; one post-observation frame alone does
not prove the complete sweep. Dynamic pose/motion receipts carry that claim.

Initial exploratory retries failed in test setup, not product: a copied visit
expected road-height65 on adjacent ground64; a small circuit crossed a raised
road (confirmed by actual diagnostic client frame); an old exact-two-guards
expectation became false when a third guard naturally armed; selecting the
second roster guard did not guarantee it was responding to this encounter.
The final focused retained scenario uses exact first actor identity beside the
player and a flat ordinary arena. It does not force a remote patroller to react,
relax the movement/animation assertions, or repeat the cold manufacturing prelude.
The scratch disposable scenario was removed rather than retaining duplicate
incorrect setup. Final product Java bytes stayed unchanged through those retries.

All private clients/Xvfb exited; exact private server saved all dimensions19:19:49
and25583/4 closed. Prepared-6 content fingerprint
1762dd9e12b09a7468836e676d8fd8adbb34107cddd8b1302d928f56e9aee2ad
matches committed source (committed-source-reuse.json). Native JAR SHA256
1125b8d8f6a249d91647b083eb76976f6129552a86a07d03213f5bb3cf7dbbd3.
Detached release guardrails/jar/verifyPackagedJar PASS14s; all6693 ZIP entries
match the verified native artifact except runtime identity properties
(release-class-reuse.json). No extra full-test claim.

PACK164 indexed inputs verified, PM artifact published with unchanged Visuals.
MAIN old world138 saved all dimensions19:25:45, old JVM3263929/ports closed before
shared installer. New disposable selected world guided-lunge-schema325-seed139-20261103,
seed139/rootr31 agricultural-flat; no old operation or world schema was edited.
Installed source2fc2a8a3d SHA256
c6f9c01fb4df17182e3d083c324cac20a32f210489605c17a433de1f08d518cf;
SHA512ebb20eefaa804e234d9a39246098fcb20357be0a297198b4c8224bb8bb7f1ce77fe6bb5d78fe6c8b7db258b763ad7eaa7e76a7337f437de985d4ee4f7ee30ac9.
MAIN far-frontier-v3-live started1793716026 (19:27:06 +05), wrapper4077307 /
actual Java22PID4077333,25565/25575. Preflight, fresh-world-absent preflight,
install and fresh startup verify PASS /tmp/pm-lunge-*.log. Summary3072/rev3414
ok/green, all required/scene/inventory conflicts and incident index0. Fresh normal
world production starts normally; no guard or sword injection for manual testing.
Client update is needed for the new visible sword sweep; payload/dependencies
remain compatible. No automatic push. IMPL/RELEASE clean; PACK tracked clean with
pre-existing.f0v-baseline/. GOV other WIP preserved.
