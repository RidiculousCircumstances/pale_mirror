# Trauma care and shared bed rest — 2026-11-07

Source `041afc63936663a0f3778005ef112cffa03fe225`, schema347,
installed ruleset `frontier-v3-clinical-care-r35`. Technical implementation and
disposable-server delivery; no M3, co-op or full-pack visual acceptance claim.

## Behavior and ownership

One cloth produces four bandages through the common attended, fuel-free crafting,
stock, supply and market mechanisms. Production genesis grants no bandages.
Medical demand combines untreated injury with a bounded first-aid reserve.
Medics and patients use shared dynamic warehouse/ground pickup, personal pockets,
resource custody, UAE execution and known-geometry navigation. Ground sources
retain native item custody and require an observed transfer, as in other work.

A finite dressing consumes one actual bandage and covers up to four HP of injury;
it does not add HP. Existing MEDICINE controls procedure time and dressing quality;
self-care has a0.7quality factor. Quality cannot reroll repeatedly or create a new
healing budget. New injury remains untreated. Gradual recovery is analytical in
COLD and journaled, observed native healing in HOT. ActorCondition/common body
observation remains the only HP authority. This cut has no temporary physiology
multipliers: endpoint nutrition cannot retroactively grant/remove elapsed recovery.
Meals still wake patients and retain their bed reservation. Bandages do not cure
infection; the old infection-specific honey flow remains separate.

Generic complete-bed reservation/occupation supports declared ordinary and medical
profiles (natural recovery3000/5000permille), real native sleeping/waking, player
occupancy, bed loss and owner-approved release. A completed rest interval remains
until accounted. A short doctor visit is independent of the long patient stay.
Preparing dressing requires the common physical-operation fence free on BOTH
actors; bed/healing/supply preparation likewise respects that fence. No parallel
COLD HP writer can pass prepared/live native body custody.

Integration corrected missing clinical ambient placement, scheduled-subject and
pending-route handling. Shared loaded native movement remains executable beyond
presentation demand until ordinary unload hands custody back; no force load or
replacement body is introduced. r35 pins the modified crossbow/cloth-arrow recipe
sources under their own native IDs; older rulesets were not rewritten.

## Evidence

Implementation checkout `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`.
- `/tmp/pm-clinical-scoped-final-gate.log`: guardrails, scoped `check`, core build
  and packaged-JAR verification PASS;62frontier JVM checks (including10mandatory
  architecture and8clinical),47NeoForge JVM checks. Clinical kernel earns actual
  supply, consumes bandages without instant healing, reaches a bed, recovers a
  snapshot during rest and releases the task/bed. A skilled medic actually finishes
  independently while the patient keeps resting. Dressing/bed competing preparation
  is rejected in both orders; native custody and dead actors cannot receive COLD HP.
- `/tmp/pm-clinical-final-native.log`: full475native tests,458pass/17fail.
  Clean b7ae baseline `/tmp/pm-clinical-baseline-native.log`:470tests,
 453pass/17fail; identical failing test names. All five new native clinical cases
  pass: actual bandage recipe, debit/no instant HP/native reload, stale preimages,
  bed occupancy/loss/repeat use, and full installed native recipe/fuel validation.
  Comparison: IMPL `build/clinical-care/native-baseline-comparison.json`.
- Existing11JVM fixture failures reproduced unchanged on clean b7ae in
  `/tmp/pm-clinical-baseline-discriminators.log`. These concern old depot,
  production/bakery and patrol fixtures, not new passing clinical acceptance.
- Unfiltered `/tmp/pm-clinical-full-check.log` is INVALID: terminated after another
  compilation changed its outputs. Its later ClassNotFound/ClassCast results are
  not product evidence. It is not counted as a passing gate.
- `/tmp/pm-clinical-clean-release.log`: clean detached041afc639 release build,
  guardrails and packaged-JAR verification PASS. No blanket whole-suite green claim.

Checked-in `disposable-clinical-care-restart.json` uses ordinary Minecraft damage,
a healthy initial patient and explicit prerequisite medicine only. It does not
seed wounds, tasks, treatment, bed occupation or healed outcomes. Final pilot8
`/tmp/pm-clinical-pilot-8.log` and IMPL
`build/clinical-care/clinical-native-pilot.json` PASS, run
`2dadf46c-b462-47e4-838e-61303b97bd17`, scenario world
`v3-disposable_clinical_care_restart-26670616`.
Resident1-1 was damaged20→12HP, sourced and used actual bandages, physically
slept in an infirmary, retained the same sleeping pose after graceful native
save/restart, improved under resident1-11 (medicine70), and was discharged at
19.968683HP with task/rest NONE and semantic progress SATISFIED/TERMINAL. The
medic was already IDLE while the patient still rested (read-only contemporaneous
`/tmp/pm-clinical-native-medic-after-visit.json`). Three linked frames were inspected:
sleeping before restart, sleeping afterward, standing after discharge. This was
`disposable_lite` graphical automation, not human/full-pack cosmetic acceptance.
Task-owned client, test server and Xvfb109 exited. Earlier pilot7's real competing
TEND/bed-owner quarantine was corrected at admission, not suppressed.

## Deployment

Clean release worktree `/home/rd/proj/pm-r242-economic-release/pale-mirror`.
Core SHA512:
`473c411478c5b5aeae1e329668cbd2cd3a627bb1fbedb171ba98adecdd9992e9dfc6814576a031dd4f4bca341db8e97d1a42c13ba8e78ace8815fac70400fa79`.
Visuals unchanged:
`2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601`.

Old live JVM stopped through authenticated RCON; All dimensions saved observed
2026-11-07 20:47:20 local, then JVM/port exited. Old world preserved. New exact
world `/home/rd/far-frontier-server/clinical-care-seed139-20261107`, seed139,
world-local graybox datapack, explicit r35. Root publisher verified170pack-index
hashes; published core checksum matches the hosted URL. Client update required
for the new registered item. No pack tracked changes or automatic push.

Preflight and post-start scripts PASS (`/tmp/pm-clinical-deploy-preflight.log`,
`/tmp/pm-clinical-deploy-verify.log`). User unit `far-frontier-v3-live.service`,
wrapper PID3874795, invocation `1eafa30fa35b403aac2df43ddbb6f00a`, port25565,
pinned Java22. At tick606/revision940:12settlements/319residents, green,
0required/scene/inventory conflicts, incidentIndex0. No new quarantine.

Remaining unrelated uncertainty: an earlier long-simulation whole-state codec
`companies` equality mismatch was not classified. Exact clinical/population,
body/execution and inventory roundtrip plus actual clinical restart pass; this
receipt does not claim full-world equality or close that separate investigation.
