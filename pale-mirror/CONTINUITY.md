# Active continuity — 2026-11-03

## Current assignment — COMPLETE, COMMITTED, DEPLOYED

User reported plain sword deposit locking every later container edit. Fixed
actual producer's malformed ID and reused-slot identity collision, verified
ordinary/negative/restore boundaries, committed and deployed fresh disposable
MAIN. Sole main, no agents/push/goal. No private client/Xvfb or monitoring remains.
Detailed findings and delivery: `docs/frontier-v3-plain-item-click-20261103.md`.

Exact MAIN failure at14:31:17 on344d29b5f/seed137: native click moved item,
FrontierV3PlayerClickObservation then threw for item:player-contribution:UUID
(two colons). PLAYER_CONTAINER_CLICK ticketd11a8466-0759-4804-b9bd-7748023de54a,
interactionc7e753dd-cf62-48df-8268-75e4205832a1, container1-depot/player rd,
AMBIGUOUS/EFFECT_EXCEPTION/no outcome. Later CONTAINER_CLICK_PENDING was the
correct physical fence, not routing latency. Canonical summary green covers only
its stated scope, not absence of an unresolved physical operation. Raw cause
retained IMPL/build/plain-exact-clicks/main-original-failure.txt and old world.

Producer now emits legal item:player-contribution-UUID from the explicit durable
interaction plus member container/slot. Old code used container as cause and would
alias a later new item in a reused slot. Tagged moves/returns keep their old ID;
no sword-specific branch, validation relaxation, new protocol/schema or guessed
recovery. Source scan found this sole malformed SubjectId literal prefix. Missing
captured outcome cannot be invented from a later matching layout or replayed;
old operation was not silently unlocked. Fresh-world delivery is authorized.

## Verification

IMPL guardrails/NeoForge904/19player-storage GameTests/build/verifyPackagedJar
PASS2m49s /tmp/pm-plain-exact-check.log. Existing ExactContainerIngress GameTest
now also does two plain iron swords, immediate withdrawal, later new sword in
freed slot (different ID), return of original (same ID), alongside bread/coal.

Checked-in disposable-plain-sword-container-restart native20/20 PASS,
IMPL/build/plain-exact-clicks/native-4.json,
run61b2fc20-5bd8-466b-96f0-7767a9553394,
worldv3-disposable_plain_sword_container_res-1a1c365d, seed41/schema324,
peaceful-guards. Setup's ordinary empty chest was adopted by shared territorial
storage. Diamond swords avoid unrelated normal guard acquisition; iron uses the
same real callback in GameTest. Physical player packets put two swords, normal
save/restart/reconnect, repeated withdrawals/returns and terminal current empty
chest. Exact counts0→1→2→1→0→1→2→1→0; two IDs retained through restore/return,
identity-evidence.json. Frames personally reviewed: two swords in chest then two
in hotbar, no loss/duplication. One20FPS client reused its JVM, all private
processes/Xvfb/25583/4 stopped. Earlier setup failures were method-only (command
vocabulary, existing three hoes in depot, feet66 vs observed65) before tested
click; corrected using observed plaza/independently adopted empty chest. No fake
NPC outcomes or WAL edits. This is scoped inventory/UI evidence, not M3/world TPS.

Committed source bytes equal frozen prepared-5.json source content
09328e4a1440e18159f15b94101c5a70b5b5c0395cf28dbc0760b7b25d0ffca6,
committed-source-reuse.json. NativeJAR SHA256
7fe8ffbbd4e64c7481f92ff08993f3660e9afef97d14eb6efc96bb5695b7094f.
Detached release guardrails/jar/verifyPackagedJar PASS15s
/tmp/pm-plain-exact-release.log. All6486 classes/resources equal native except
META-INF/pale-mirror-runtime-identity.properties, release-class-reuse.json.
Reuse IMPL904/19results through that exact byte match; no duplicate test claim.

## Repositories

IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror,
branch feat/baker-carry-orders-20260926,
HEADf2de641e5851b0d6464875466c9016cd8b8134c3 CLEAN.
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror CLEAN detached same.
PACK /home/rd/proj/minecraft HEAD888ed90d5e3b80b18c278107aa83bb003b541f69,
trackedCLEAN; pre-existing .f0v-baseline/ preserved.164Packwiz inputs validated,
exact PM +unchanged Visuals published. Server-only change; no client dependency
or network update needed. No automatic push.
GOV /home/rd/proj/pm-governance retains extensive unrelated WIP. This task changes
only the ledger/new plain-item-click note; do not include other working changes.
The previous combat task's own architecture/inventory entries are already committed.

## MAIN runtime — latest delivery

Selected world item-ingress-schema324-seed138-20261103,
seed138/rootr31 agricultural-flat. Previous seed137 saved all dimensions14:59:52,
actual old JVM/ports closed before shared installer. No previous operation edit.
far-frontier-v3-live started1793700150 (15:02:30 +05), wrapper3094218/
Java22PID3094242,25565/RCON25575. Sourcef2de641e5, installed release SHA256
5a175ea9ba89a9ebf65ce10f46b2fd5f13ab890a8525aef49afd28b0b367f861;
SHA512b31b759fff5d908622f7bff2159920c4978b4275a63ac1e84fb7c0af5b71e7f1ba33f4243584f9e0e3263e411d07256aaaeea2a08089d479d62096cc74030dbd.
Preflight/install/fresh deployverify PASS /tmp/pm-plain-exact-*.log.
Summary1265/rev1842 statusok/green, required/scene/inventory conflicts0,
incidentIndexSize0, IMPL/build/plain-exact-clicks/main-summary.txt.
New normal world starts with ordinary production/staffing, no injected weapons.

Build JDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
runtime22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
Artifact host127.0.0.1:18092 serves RELEASE/pale-mirror-neoforge/build/libs;
pack host8092 serves PACK root. Visuals: hosted/pale_mirror_visuals-current.jar.
Detached build requires -PfrontierV3GrayboxDisabledModsCatalog=/home/rd/proj/minecraft/config/graybox-disabled-mods.txt.
RCON helper IMPL/build/forestry-native/query-runtime.mjs reads properties silently.
MAIN transient unit disappears on normal stop; recreate via systemd-run --user
--unit=far-frontier-v3-live --collect --working-directory=/home/rd/far-frontier-server
/home/rd/far-frontier-server/scripts/run-server-java22.sh. Require native all-saved
and actual JVM/ports exit before replacement/reset. Worlds disposable by standing
authorization; never generalize deletion to unrelated paths/evidence.

## Previous deliveries (do not restart completed work)

344d29b5f shared CombatEngagement/CombatEnvelope, exact party+target participants,
stable pressure/flank/cutoff/support and body-fenced single-use waves. Faster
combat gaits, shortened lunge/native reach, common navigation only, no COLD hits.
Guard adapter is enabled; other roles register capabilities and explicit party.
`docs/frontier-v3-coordinated-combat-20261103.md` holds source boundaries,
15/15 real two-guard/restart/closure and exact release receipts/visual limits.
316eb206b HumanEra +FreshAnimations/author activator +EMF/ETF and shared rig poses,
`docs/frontier-v3-human-residents-20261103.md`.9ae557ddf BURST/BACKSTEP, stable
pursuit/body observation, `docs/frontier-v3-combat-maneuvers-20261103.md`.
3e0eb77fb meal continuation after safety; `docs/frontier-v3-meal-resumption-20261103.md`.
adb13df4b exact pending field layout identity across appended land. Historical
kernel requiredConflict1 belongs to old323world, not current new delivery.
