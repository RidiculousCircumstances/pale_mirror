# R229: exact equipment at common body admission

Live R228 halted at tick33802/revision96716 with `extraction tool changed`.
Resident10-13 held stone hoe `item:bootstrap-field-tool-settlement-10-0`,
damage26/131 in canonical state. Native saved UUID
00723292-35ed-31a2-b2b3-d9eb1ec6e799 had empty MAINHAND and40wheat OFFHAND.
Scene birth omitted exact inventory; only ambient birth hydrated it privately.

Exact held items now pass one common private-body projection before journaled
insertion, after family initialization. A matching already-initialized hand is
idempotent; foreign hands and ambiguous multi-item declarations are rejected.
Existing live bodies are never silently refilled. Extraction mismatch diagnostics
include exact operation/execution/entity/target and actual item/condition.
A fresh disposable world avoids laundering the identified already-corrupt body.

Checks: 821 NeoForge JUnit,53 native economy tests, guardrails/build/JAR PASS
(build/r229-exact-admission.log). New native exact-worn-tool hydration,
serialization/idempotence/foreign-hand regression PASS in full400-test run.
Full suite had398/400 pass. Combat calibration timeout also reproduced unchanged
on clean deployed R228 (build/r229-baseline-native.log); this non-target scenario
is unresolved, not a release-wide green claim. Physics fixture incorrectly
compared current HOT canonical position with an earlier landing sample despite
allowing retained player demand. It now checks lower landing on release and
retained physical gravity/custody, as its bioform branch already does. All10
focused ambient-physics tests, guardrails/build/JAR PASS
(build/r229-physics-release.log). No timing limits increased.

Live client end-to-end farmer acceptance remains to be recorded after deployment.
