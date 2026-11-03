# Player sword edits and HOT movement repair — 2026-11-04

The retained test world is `combat-followup-schema327-seed139-20261103`.
No world reset, canonical inventory reset, discarded click or deleted fence was
used. Implementation is in `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`.

## Findings and changes

The user's iron sword was physically in slot2 of `container:1-depot`, while the
canonical slot was empty and prospectively reserved for harvest output. Player
transaction `6071e128-ef48-43ea-8c7a-2ebf1cc39745`, native operation
`046ce9b4-1537-4a9e-8fcd-b152f005ac3b`, repeatedly failed confirmation with
`resource deposit targets an occupied canonical slot`. Its retained click fence
then blocked subsequent edits. The ingress reducer was using the admission rule
for **future production**, including soft capacity claims, to confirm an item
the player had already physically deposited.

`ac39b712a` uses actual canonical inventory occupancy for that confirmation.
Actual items still prohibit overwrite. The existing common operation protocol
and exact click validation retain in-flight effect fences; soft forecast claims
are displaced through the existing backing calculation. A regression includes
the conflicting prospective claim, actual ingress, codec roundtrip and duplicate
refusal. Ownership remains in the existing inventory/operation mechanisms.

The exact original contribution
`item:player-contribution-d77368df-1e24-344c-82fd-a7340df5bfa8` is retained and was
subsequently held by guard `resident:1-16` Nora Bell. The old pending operation
appears to have settled on the **old** build after its soft reservation changed.
Consequently this is not evidence that new code freed that exact unchanged wait.

The same commit fixes two movement contradictions:

- Farmer speculative retargeting could construct a world-state draft and run
  native pathfinding for each of up to eight candidate cells. It also ran during
  equipment acquisition/return, where changing a crop cell did not change the
  actual service goal. Retargeting now applies only to a WORK_CELL goal, selects
  an eligible station cheaply and leaves route computation to the common
  navigator. The bounded rejected-cell pass survives candidate selection.
- The common navigator treated an advisory cached route hint as semantic goal
  identity. Loss of that hint could discard an accepted local leg despite the
  same movement order, scope, capability and legal stations. It now retains that
  leg across such refreshes. Authority changes and live geometry checks remain.

The original log attributed 100–200ms turns to field retargeting; source inspection
proves the unnecessary queries/drafts. The first short JFR ran after HOT departure
and is not used as hotspot attribution.

`ff16eb968` adds bounded-event capture/commit correlation logs for confirmed native
facts. An ordinary guard hit on that build was captured at00:17:26.060 and
canonically committed at00:17:26.120:60ms. This rejects journal delivery as the
cause of that particular later defensive-choice delay.

Source review also found that an Incident woke the settlement allocator but did
not wake **existing responders** to the same threat. Seen/ContactEnded did wake
them. When allocation stayed unchanged, another hit could leave the guard waiting
for a later perception/continuation. `389e26a82` applies the same addressed wake
to Incident, without changing assignments, movement authority, attack timing or
durability. The existing security regression now checks that repeated confirmed
harm wakes the exact responder through the registered command boundary and
does not manufacture a replacement appointment. This is a specific wake fix,
not a guarantee of zero wall-clock delay for every combat/navigation transition.

## Verification and evidence limits

Receipts are under IMPL `build/sword-click-stall/` and `/tmp/pm-*-*.log`:

- `/tmp/pm-click-route-final2.log`: guardrails,40 domain tests,9 adapter tests,
  44 native local-navigation GameTests, packaged JAR verification.
- `/tmp/pm-click-pilot-check.log`: guardrails, client compilation, retained test
  compilation and packaging. The obsolete orbit-only test was retired; useful
  farmland/gravity/navigation assertions remain. Invalid managed-cue calls on
  unmodeled fixture bodies were removed without weakening production authority.
- `ba64cb216` adds the ordinary live consecutive-sword scenario; `664f62567`
  corrects its existing withdrawal driver to require an actual quantity delta
  and emptied source slot. Merely already holding the first sword is insufficient.
- Full-pack run `3eea5e1a-8d27-40dd-ae03-04cc67338b45`: two ordinary consecutive
  sword deposits and two withdrawals in the same affected container, without
  transfer sleeps; canonical/socket assertions passed. Both UI frames were
  personally inspected. Manifest `client-manifest.json`; frames under
  `build/frontier-v3-scenarios/live_sword_consecutive_edits-3eea5e1a-8d27-40dd-ae03-04cc67338b45-*`.
- `/tmp/pm-harm-latency-check.log`: focused confirmation/native-fact tests,
  guardrails and package verification. `/tmp/pm-harm-wake-check.log`:37 security,
  local-reaction and security-codec tests, guardrails and packaging. Detached
  release builds and exact artifact/runtime verification passed.

Controlled45s HOT repeat `d4f7ee01-e5b3-4729-8bf2-258abcffe771` entered with JFR
already running: recent64 server ticks averaged4.81ms, max8.74ms. No >80ms slow
tick was logged during00:00:43–00:01:29.34 JFR one-second-average samples had
mean2.741ms/max8.231ms; these are not individual tick maxima. The prior first
profiler activation coincided with277.678/101.57ms ticks and many deoptimizations;
that lifetime peak is retained, not erased or proven solely profiler overhead.
The later COLD window averaged2.99ms/max4.83ms.

Guard hit probes are exploratory evidence, not formal combat/balance acceptance.
An early probe hit a PREPARED body; another failed because the target moved out
of the driver range. They do not establish steady reaction latency. The corrected
probe waited for HOT/RUNNING and revisited the exact guard before the ordinary hit.
The pilot's two tracked diamond swords remain in its inventory; follow-up probes
did not clear them or replace the user's iron sword.

Final repeated-hit run `df3bfc9a-dbd7-4d06-ae14-55bc75c85a6f` on389e26a82
completed19 actions. Both ordinary native hits were confirmed; their event
capture→commit times were109ms and199ms. The first BACKSTEP was selected8
simulation ticks after capture and launched16 ticks after capture. The second
hit retained the same responding guard and target; a new backstep is not required
inside its cooldown. The final security cut was ENGAGED/SELF_DEFENCE with the
exact target and guard1-16. This does not prove every reaction is below0.5s.
Manifest `repeated-harm-final-manifest.json`; `/tmp/pm-harm-wake-client.log`.

Final post-client summary instant63740:333residents, required/scene/inventory
conflicts0, incidentIndex0. No pending ordinary ticks or fast-forward. Recent64
ticks averaged3.61ms/max6.61ms; new JVM lifetime max58.73ms across1385samples.
Clients and private displays exited; MAIN remains running.

## Delivery

Final source `389e26a8221608492e8b709a98838ffeef6c0dd5`; clean detached RELEASE.
MAIN actual Java22PID1027868 (service wrapper1027844), start1793733644, same world/schema327.
JAR SHA256 `5aecaedd9096a8b15247d5c2387acdaaeb4117da1584ac672ac61fd869ffb0a4`.
JAR SHA512 `254539d103991b5c9b3e0c668e807e3dd1f77d1be7188a17daed047d8ef14bd1fe92282f347065b62981bbad6f86c86850a95a731ab0b0429feea40a6699b1cc`.
Root scripts published/installed the artifact; normal saved-dimensions and old
JVM/port exit were checked before each install. Final receipt
`/tmp/pm-harm-wake-verify.log` passed. PACK tracked state is unchanged; preexisting
`.f0v-baseline/` is preserved. Unrelated governance WIP is excluded from commits.
No push or subagents.
