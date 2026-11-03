# Active continuity — 2026-11-03

## Current assignment — COMPLETE, COMMITTED, DEPLOYED

User reported guards orbiting and sometimes winding up but hardly hitting even
an unmoving player; additionally requested intermittent fast movement with a
cooldown. Fixed connected native admission/contact/approach boundaries, verified
actual native player health loss and safe escape/restart, committed and deployed
the same disposable MAIN world. Sole main, no agents/push/goal. No private
server/client/Xvfb remains. Detailed receipt:
`docs/frontier-v3-melee-contact-20261103.md`.

Native windup had started BEFORE issuing deferred MELEE_ATTACK, so journal wait
could expire its finite3/4-tick contact window. It now starts through existing
NativeEffect preparation AFTER durable intent and canonical earliest-impact
admission. Full visible preparation, finite contact window, exact reach/LOS/cone,
body/equipment/target identity and one-shot receipts remain required. No larger
reach, replay relaxation or COLD damage. A preferred flank no longer vetoes
observed close contact; an overshoot corrects by the shorter arc. Optional closing
BURST cannot preempt an available attack; permitted BACKSTEP stays defensive.
Ordinary combat approach WALK/close CAREFUL; RUN only during lunge or existing
bounded cooldown-owned maneuver, not permanent pursuit/flanking. Shared combat
mechanics, role permission, engagement slots and UAE/navigation retain ownership.
No durable schema/payload/rule-format/network change.

## Verification

Guardrails, focused combat/security54, NeoForge904 PASS3m11s
/tmp/pm-contact-final-check.log. Final scene-strikes11/11 plus jar/verifyPackagedJar
PASS1m10s /tmp/pm-contact-final-game.log. Regressions: delayed native admission ->
full preparation -> real hurt once -> no replay; canonical wait cannot start
cue early; cancelled/expired cue cannot hit late; flank overshoot and absence of
permanent sprint. Existing operation recovery and body fences remain unchanged.

Checked-in retained-stationary-melee8/8 PASS after normal saved-world restart,
IMPL/build/melee-contact/native-3.json, run38fec2e0-63e3-49d6-973f-5e2dd7288e6d.
Actual health1000->964 over240ticks, six accepted hits by resident1-16. Declared
encounter has two participants; this does not assert both hit. Physical epoch3
restored normal manufactured guard/weapon and remembered pmresponse hostility.
After ordinary player escape health stays964 and responders/contact/plan0.
Frame personally reviewed (vanilla lite appearance and large test-health HUD).
Warm-world scoped combat evidence, not fresh-world/full-pack/M3 acceptance.
Earlier scoped observation had seven hits1000->958, but runner lacked an explicit
terminal diagnostic tied to its assertion; corrected scenario only, no product
change. Initial private permission setup also corrected. No NPC arrangement,
WAL edits or guessed effects. All exact-owned private processes/25583/4 stopped.

Prepared-4 source fingerprint33ff44b6c9df34e18c35704300d02a45ffd9dccfdcba515b8901733ef73e4452
matches committed content (build/melee-contact/committed-source-reuse.json).
NativeJAR SHA25629ec0cdcc7047f093be029c3c30434f8271f56b8d3b45be7fbc837ae7ffa21b0.
Detached release guardrails/jar/verifyPackagedJar PASS14s. All6690 ZIP entries
match except META-INF/pale-mirror-runtime-identity.properties, recorded in
release-class-reuse.json. No duplicate full test claim.

## Repositories

IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror,
branch feat/baker-carry-orders-20260926,
HEAD3fb952955889ee136e6864cbd24daf768ec5dc8d CLEAN.
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror CLEAN detached same.
PACK /home/rd/proj/minecraft HEAD888ed90d5e3b80b18c278107aa83bb003b541f69,
trackedCLEAN; pre-existing .f0v-baseline/ preserved.164Packwiz inputs validated,
exact PM +unchanged Visuals published; no new client dependency/network update.
GOV /home/rd/proj/pm-governance retains extensive unrelated WIP; this task changes
only own architecture combat boundary line, this ledger and new melee note.
No push. Canonical protocol/AGENTS assignment unchanged; subagents prohibited.

## MAIN runtime — latest delivery

Same world item-ingress-schema324-seed138-20261103,
seed138/rootr31 agricultural-flat. Old JVM3094242 saved all dimensions15:45:20
and actual process/25565/25575 exited before shared install; no world reset or
operation edit. far-frontier-v3-live started1793702763 (15:46:03 +05),
wrapper3263895/actual Java22PID3263929,25565/RCON25575. Source3fb952955,
release SHA2563d9e8e589f3aac9152f3eb643687f4b6730730fd4f96f1ab98cb38189f4af0ea;
SHA512b3394aeeed7845dcb6bec423f45e22968c84340b882d301c0280cba94591ce1c88e1ef8df6db23a5bb599dd4f42ecd45ad51d7ea1114629372f087c082e717c9.
Preflight/install/fresh deployverify PASS /tmp/pm-contact-*.log; initial verify
before socket opened correctly rejected and post-ready retry passed. Summary
51630/rev137645 statusok/green, required/scene/inventory conflicts0, incidents0,
IMPL/build/melee-contact/main-summary.txt. Startup/state evidence, not a new
full-pack combat run. Live server remains running; no task clients remain.

Build JDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
runtime22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
Artifact host127.0.0.1:18092 serves RELEASE/pale-mirror-neoforge/build/libs;
pack host8092 serves PACK root. Visuals hosted/pale_mirror_visuals-current.jar
unchanged2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601.
Detached build requires -PfrontierV3GrayboxDisabledModsCatalog=/home/rd/proj/minecraft/config/graybox-disabled-mods.txt.
RCON helper IMPL/build/forestry-native/query-runtime.mjs reads properties silently.
MAIN transient unit disappears on normal stop; recreate with systemd-run --user
--unit=far-frontier-v3-live --collect --working-directory=/home/rd/far-frontier-server
/home/rd/far-frontier-server/scripts/run-server-java22.sh. Require all-saved and
actual JVM/ports exit before replace/reset. Worlds disposable; never generalize
cleanup to unrelated data or history.

## Previous deliveries — do not redo

f2de641e5 plain player-item click identity fix; source of new sword item now
includes exact durable interaction, not a reused container slot. Native20/20
and904/19 checks; old ambiguous operation was not fabricated/unlocked.
`docs/frontier-v3-plain-item-click-20261103.md`.
344d29b5f shared engagement/party/envelope, stable sectors and body-fenced waves;
`docs/frontier-v3-coordinated-combat-20261103.md` (old native checks proved
motion/gestures/closure, NOT adequate actual hit cadence; this task closes it).
316eb206b HumanEra/FreshAnimations +managed dependencies/shared semantic poses;
`docs/frontier-v3-human-residents-20261103.md`.
9ae557ddf BURST/BACKSTEP and stable observation;
`docs/frontier-v3-combat-maneuvers-20261103.md`.
3e0eb77fb meal resumption after safety;
`docs/frontier-v3-meal-resumption-20261103.md`.
