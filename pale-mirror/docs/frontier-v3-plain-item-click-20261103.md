# Plain durable-item container clicks — 2026-11-03

User placed a sword in container1-depot; every later deposit/withdrawal failed.
MAIN source344d29b5f, schema324seed137. Exact native log14:31:17 identifies
PLAYER_CONTAINER_CLICK d11a8466-0759-4804-b9bd-7748023de54a, interaction
c7e753dd-cf62-48df-8268-75e4205832a1, AMBIGUOUS/EFFECT_EXCEPTION. Native callback
already moved the sword, then capture threw Identifier.require for
item:player-contribution:42928a33-4217-3d9b-92bf-db5c46047a09. Vanilla logged the
ReportedException and suppressed that packet error. The retained witness has no
captured outcome; subsequent clicks correctly reject CONTAINER_CLICK_PENDING.
The canonical summary remained green because its scope is retained canonical
conflicts; it is not proof that every physical operation has an outcome.

## Cause and correction

FrontierV3PlayerClickObservation constructs the initial identity of an untagged
vanilla damageable item. Two errors in that producer:

- A second colon violated the existing namespace:path contract. Fix the producer,
  not Identifier validation.
- UUID salt used transaction.id(), which is the first container ID, rather than
  the retained player interaction ID. A later new item in the same slot would
  alias an older item, even after fixing the spelling.

The valid item:player-contribution-UUID is now derived from the durable explicit
interaction, member container and destination slot. Those last two values
separate members of one composed edit; they do not become an item's continuing
identity. Ordinary tagged returns and later moves retain the existing item ID,
ownership and durability. No sword-specific branch or new protocol is added.
A production literal scan found this sole malformed SubjectId prefix.

The common physical operation and click fence remain strict. Missing outcome
cannot be invented from a later matching layout, nor can the already-run native
click be replayed. The old ambiguous test-world operation will not be silently
unlocked or edited. A fresh disposable MAIN is authorized and used for delivery;
no backward migration or guessed recovery is introduced.

## Verification and delivery

Existing ExactContainerIngress GameTest now covers two consecutive plain iron
swords, immediate withdrawal, a fresh sword in the freed slot (different ID),
and return of the original tagged sword (same ID), alongside bread/coal exchanges.
NeoForge904 +19player-storage GameTests/guardrails/build/verifyPackagedJar PASS
2m49s /tmp/pm-plain-exact-check.log. Native completed20/20 actions including ordinary save/restart,
IMPL/build/plain-exact-clicks/native-4.json, run61b2fc20-5bd8-466b-96f0-7767a9553394,
worldv3-disposable_plain_sword_container_res-1a1c365d/schema324/seed41,
peaceful-guards profile. Actual identities62e29fc1-2d3a-3c51-a67c-58e1d4655d97 and
82865bac-43a0-376c-87d1-e532a4145f82 remain the same across restore/returns.
Container exact counts0→1→2→1→0→1→2→1→0 and terminal OBSERVED_CURRENT/ACQUIRED,
summary no canonical conflicts.20FPS private client reused its JVM on reconnect;
frames personally reviewed: two swords in chest, then the same total in player
hotbar with chest empty. Private JVMs/Xvfb/ports stopped. The empty chest was
adopted by normal territorial storage after ordinary setup, no WAL edits.

Sourcef2de641e5851b0d6464875466c9016cd8b8134c3 CLEAN. Committed source content
09328e4a1440e18159f15b94101c5a70b5b5c0395cf28dbc0760b7b25d0ffca6 equals frozen
native candidate, committed-source-reuse.json. Clean detached release packaging/
guardrails/verifyPackagedJar PASS15s /tmp/pm-plain-exact-release.log. Full NeoForge
and GameTest results above reused via exact artifact comparison: all6486 classes/
resources equal native; only META-INF/pale-mirror-runtime-identity.properties
changes, release-class-reuse.json. No fresh duplicate test-suite claim.
NativeJAR SHA2567fe8ffbbd4e64c7481f92ff08993f3660e9afef97d14eb6efc96bb5695b7094f.
Release/installed SHA2565a175ea9ba89a9ebf65ce10f46b2fd5f13ab890a8525aef49afd28b0b367f861;
SHA512b31b759fff5d908622f7bff2159920c4978b4275a63ac1e84fb7c0af5b71e7f1ba33f4243584f9e0e3263e411d07256aaaeea2a08089d479d62096cc74030dbd.

MAIN old seed137 saved all dimensions14:59:52, actual old JVM/ports exited before
install. Shared publisher/installer/preflight and fresh deployment verify PASS.
New selected world item-ingress-schema324-seed138-20261103, seed138/rootr31.
far-frontier-v3-live start1793700150/15:02:30 +05, wrapper3094218/Java22PID3094242,
25565/RCON25575. Latest1265/rev1842 statusok/green, required/scene/inventory
conflicts0 and incidentIndexSize0; main-summary.txt. Original failure retained in
main-original-failure.txt and old world, not reclassified into guessed success.
No client dependency/network changes. PACK888ed90 tracked unchanged, existing
.f0v-baseline preserved. No push.

Checked-in disposable-plain-sword-container-restart uses a separately adopted
empty chest and ordinary player packets, two plain diamond swords, normal
save/restart, repeated withdrawals/returns and terminal current empty container.
Diamond avoids normal AI guard acquisition during the observation; iron follows
the same native path in GameTest. No fake NPC/stock outcome or WAL edit.
Setup command/action vocabulary was corrected before JVM startup. Initial native
fixture incorrectly assumed empty depot (three existing stone hoes); next setup
visit incorrectly expected feet at66 although native floor was65. Both rejected
before the tested inventory action. Current setup uses the observed ordinary
plaza and an empty chest admitted by the existing storage mechanism.
