# Melee techniques and presentation — 2026-11-02

Accepted scope: short close strike, longer telegraphed lunge, movement during
preparation and sustained spatial pressure. No poise system or COLD damage.

Shared combat owns the explicit technique and exact attempt, weapon condition,
body/execution and target incarnation. World-hashed `MeleeTechniques` owns
preparation, active window, recovery, approach opportunity and selection/pressure
thresholds. A short strike prepares for4 ticks; a lunge for10, using the initial
policy. Native sensing selects from actual separation and two recent visible
sightings; it cannot read future player controls or continue hidden tracking.

A role capability supplies permission, equipment and the exact approach. Combat
preparation preserves only that permitted approach. Guard patrol/equipment or
mandatory clearance cannot be reused as combat movement. Prepared effects still
block role/equipment replacement; the existing mission movement provider can
continue the same INTERCEPT order. Weapon recovery limits the next attack,
without adding another post-attack navigation hold. Existing common geometry,
sector claims, spatial spacing, body authority and navigation remain owners.

A lunge's approach allowance is an admission opportunity, never extra damage
reach. Native damage still requires actual weapon reach, line of sight, live
exact participants and unchanged equipment when the journaled effect applies.
A finite native presentation window prevents slow persistence from producing
arbitrarily late hits. Miss/cancellation applies no durability wear. The shared
Minecraft operation receipt continues to reconcile an attempted hit exactly;
an animation cannot cause or replay an effect.

Client-only articulated Villager rendering keeps the server's original
`minecraft:villager` and UUID. The short strike lifts and slashes; the lunge
pulls the weapon back, leans and extends the arm. Both recover; cancellation
retracts instead of playing the forward strike. The received cue carries exact
UUID, physical epoch, attempt, technique and timing. Tracking admission displays
remaining progress; client caches are bounded, expire and clear on logout.
Unrelated villagers and noncombat appearance retain vanilla presentation.

Schema322 intentionally requires a fresh disposable world. The animation channel
requires the new client artifact. No additional animation library/assets are
needed: geometry extends the installed vanilla model, using vanilla textures,
and the procedural pose curves are original project code.

Source commit: `8aad1392042d17f88a156e041b3ee6df2a74c828` (2026-11-03).
The existing `disposable-melee-tactics-restart.json` now includes actual rendered
QUICK/LUNGE pose sampling and body motion during preparation, plus the retained
player-harm, dodge/no-additional-damage and restart/hostility terminal story.


Verification:
- Full integration passed: Frontier2270, NeoForge900, native GameTests416,
  guardrails/check/build/package. Scoped subsequent combat/death/no-retarget,
  renderer/animation/parser checks also passed. Unchanged Domain324/Visuals79
  evidence is reused; this is not a fresh run of those modules.
- Final exact native artifact SHA256
  `08e7adc39971b2cd382a4195fe58109ba9de3aca598555998b516fceaa0a8f3e`:
  checked-in `live-melee-techniques.json` passed16/16; ordinary player harm,
  shared response, actual rendered QUICK/LUNGE and actual windup movement,
  escape during preparation without additional damage, then cleared response.
  Receipt: implementation `build/melee-techniques/live-before5.json`.
- Normally saved/restarted the same genuinely manufactured seed41/schema322
  world; `live-melee-hostility-recovery.json` passed4/4 with the same player,
  retained hostility, a real native guard hit without a second player offence,
  then escape and terminal no-contact/no-response. Receipt `live-recovery.json`.
- Actual frames reviewed: `frames-final5/frame-0011.jpg` raised close strike,
  `frame-0124.jpg` lunge pose, adjacent frames show movement/retraction.
  Visual heading follows the server-authored target bearing independently of
  the existing navigation heading. Native body UUID remains unchanged.
- Initial aggregate/follow-up failures remain FAILED. Their camera was inside
  the body or lost it; observer now follows the real rendered actor and waits
  for preparation. A later setup required both guards to be simultaneously
  free of meals (`PRESERVED`), unrelated to the melee obligation. Logs showed
  restored moving bodies; that over-specified precondition was removed.
  No canonical fixture or false physical receipt was injected to get a pass.

Limits: this is a real graphical client on task-private Xvfb with bounded20FPS,
not full-pack HUMAN_CANDIDATE or human M3 approval. No COLD damage, poise,
new navigation actuator, larger weapon reach or new animation dependency.
Task-native clients/private display/server are stopped after these checks.

Release build from clean detached8aad passed guardrails, all900 NeoForge tests,
build and packaged-JAR verification in2m37s. All6445 packaged classes are
byte-identical to the final native-tested artifact; only
META-INF/pale-mirror-runtime-identity.properties differs. Release SHA256
`8dd120f46365265f8c33806a85f93a9c71b5242c00bcf8b5e71b01979572175f`.
Root publisher validated all158 Packwiz inputs and published PM plus unchanged
Visuals. Hosted artifacts/checksums are ignored deployment outputs; root has no
tracked changes to commit (existing .f0v-baseline remains untouched).

Deployment: root preflight and deploy-verify passed for source8aad, runtime
`/home/rd/far-frontier-server`, Java22PID3929432 (wrapper3929406), start1793648795,
fresh `melee-techniques-schema322-seed135-20261103`, seed135/r31 agricultural_flat.
Old runtime stopped normally with all dimensions saved and operations0. Exact
installed/public SHA512
`13a91831d6486a4d19da5788fe5bbc176bf3359fa2601981ec6989094c387a2f01b0e3530e97eff7193e142e2436fe183956bf5c6273aad15768eff9618176e1`.
Fresh summaryrevision965/tick384 green, required/inventory conflicts0,
12settlements/356residents. No assertion of full-pack human acceptance.
