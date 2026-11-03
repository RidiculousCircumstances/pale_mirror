# Resident work presentation — 2026-11-03

Source: `648dc5a3f03c89aedf53581a50783a02d0c65da6`.

The user asked to audit/add work animations. The later guard damage report was
withdrawn: the player was in creative mode. No damage or combat AI fix was made.

## Source finding and boundary

Existing work owners emitted native arm swings, but the shared resident model
compositor overrode those arms with the carrying pose. Minecraft 1.21.1 Villager
also does not advance LivingEntity.updateSwingTime like Monster/Player (checked
against the imported ModLens NeoForge21.1.248 compile snapshot). Mining emitted
only the final block swing, without a labour-duration gesture.

FrontierV3WorkPresentation now emits a finite six-native-tick cosmetic cue for
one declared physical body, with UUID/runtime ID, authority epoch and sequence.
An object-weak stamp throttles emission. It stores no work/job/resource state.
The existing owners select STRIKE, CULTIVATE or HANDLE; neither common code nor
the renderer discovers a job from its role/item/name. Work observers cannot
credit labour, damage blocks, consume items or move a body.

The bounded client channel expires and clears on disconnect, rejects older
sequences/epochs, gates the pose on rendered settling, and composes combat over
work over carrying/base gait through the existing vanilla and Human Era rigs.
Main-hand work preserves an occupied opposite hand. Navigation to a non-arrived
goal and combat admission retire prior work presentation. The old farmer duty
channel still explains phase, but no longer starts a second native arm clock.

Actual work branches are connected: field labour; mining; existing forestry
labour; attended station processing; existing production, engineering, service
and treatment gestures. Passive machine processing does not invent attendance.
No durable schema, ruleset, inventory, execution selection or work-rate change.

## Verification and limits

- guardrails + NeoForge908 tests pass, `/tmp/pm-work-check-2.log`.
- Subsequent presentation/scenario focus49 tests pass,
  `/tmp/pm-work-final-focused.log`; Node scenario56 pass,
  `/tmp/pm-work-scenario-node.log`.
- Detached guardrails/jar/verifyPackagedJar pass14s,
  `/tmp/pm-work-release.log`. All6705 ZIP entries match the exact native-tested
  artifact except runtime identity metadata. Receipt:
  IMPL/build/work-animation/release-reuse.json.
- Actual graphical prefix from checked-in retained-resident-work-animation,
  run481e85b1-2c8e-4161-9ded-20fedb6b5f9e,
  IMPL/build/work-animation/native-6.pmv3.jsonl and work-pose-series.json:
  exact Owen Bell UUID70d472d7-5dbd-3b85-8ead-072d3db39619,
 240samples, Human Era240/240,95 changing CULTIVATE poses, all in
  HARVESTING:RUNNING. Shoulder range-1.35..-1.075. Nine actual travel samples
  contain zero work receipts. Linked near-camera frame was inspected.

This is scoped real-client farmer presentation evidence, not full-pack/M3 or
an all-job animation campaign. The later HOT/COLD return was not accepted:
its ordinary overworld visit used a copied Y64 over actual Y53 terrain. The
checked-in target now uses the already-observed spawn12,66,-138; that corrected
return is UNRUN. The user explicitly offered to check remaining workers and
report missing animations; do not restart an expansive automatic campaign.
All task clients/Xvfb/private servers were stopped; retained private world saved
all dimensions20:21:56. MAIN is a separate target.

Earlier attempts were invalid method/fixture evidence, not product acceptance:
a retained job completed before the first visit; a legacy agriculture fixture
without current agriculture rules was paired with the r31 agricultural generator
and failed genesis; one private ops reset removed the chosen pilot permission;
name-based/distant observation did not establish the intended rendered actor.
Exact UUID observation established the successful work prefix. Failed pose
observations now retain their partial series instead of throwing away evidence.
No production validation was weakened to accommodate those methods. The invalid
fixture world was stopped after ordinary stop could not finish; it is not reused.

## Delivery

Release SHA256 a05d4bf7ce3ad41c2fed2c979cbaffa50cb14f393b386236cf029ed71363f939.
Release SHA512
1ea968b40e5fd1b1de06e74ec21a96205726ce352e8af52e3f0904a34ca40470ef34f2efe0e58634e88adb4033e18f5271d18342f7fa04391eade9ad7718134d.
Client update is required for the new work payload and poses; no new dependency.
Existing MAIN world guided-lunge-schema325-seed139-20261103 is retained.
Fresh startup verification is recorded in CONTINUITY.md and /tmp/pm-work-verify.log.
Source and pack remain separate histories. No push; unrelated governance WIP
and PACK .f0v-baseline/ are preserved.
