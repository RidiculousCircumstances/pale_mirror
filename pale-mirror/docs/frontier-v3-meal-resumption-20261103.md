# Exact meal continuation after safety — 2026-11-03

MAIN9ae557ddf stopped11:09:18 at canonical tick25170. Its activity review for
resident:1-5 resumed a meal previously interrupted by safety. MealActivityCapability
then emitted ReconsiderationRequested for resident.meal.progress. That kind is
an exact retained portion continuation, deliberately not a coalescible policy
review; the real schedule validator correctly rejected it and the runtime
quarantined/stopped. All dimensions were saved11:09:21. No watchdog/OOM cause.

3e0eb77fb fixes the producer: ResidentMealProcess owns one wake helper emitting
Rescheduled for the exact retained progress ID at the resumed checkpoint. The
capability delegates to it. Queue validation and the negative prohibition on
coalescing meals remain. Sibling production/extraction/forestry/shipment/group
resumptions already use exact rescheduling; guard progress explicitly declares
its different coalescing capability. No schema change or compatibility shim.

The existing nested work/meal/safety lifecycle test previously ended after
manually rebinding execution. It now invokes the actual activity planner,
reduces its real successor events, passes every schedule effect through the
production validator, and retains exact meal portion, progress ID, work/cargo,
body and snapshot checks. This closes the missed successor-dispatch boundary.
Focused45Frontier tests/guardrails PASS15s /tmp/pm-meal-resume-schedule.log.
Clean detached release guardrails/build/verifyPackagedJar PASS2m36s,
902NeoForge tests0fail /tmp/pm-meal-resume-release.log. Previous unchanged native
combat/field evidence reused; no new physical behavior claim.

Published/checksum-pinned deployment3e0eb77fb preserved the exact crashed world
combat-maneuvers-schema323-seed136-20261103. Preflight/deployverify PASS; start
epoch1793686595, wrapper2077548/Java22PID2077576. Actual summary reached29292,
revision50590, ACTIVE/statusok, no inventory conflicts/new quarantine. The exact
failing resident consumed at25452, satiety1000 then993/NOURISHED at25941,
mealPhaseNONE, empty meal claim. Evidence IMPL/build/meal-resume-incident/.

requiredConflicts1 is the unchanged retained historical kernel-failure incident:
diagnostic:v2:504:6:14:16:frontier:graybox:15:16:frontier:graybox:5,
first/last revision41320, tick25170, occurrences1. Its diagnostic awaitingReview
flag is not current engine quarantine. No history edits, diagnostic suppression
or world reset were used to claim recovery. Incident acknowledgement/resolution
is outside this repair; do not misreport summary as wholly green.

Source and detached release clean; pack tracked unchanged, existing untracked
.f0v-baseline/ preserved. No push or private client left running. No autonomous
monitoring after this delivered repair.
