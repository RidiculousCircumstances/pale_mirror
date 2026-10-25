# R267: HOT movement static audit

Scope: active resident movement shared by farming, mining, forestry, production,
hauling, meals and expedition members. Followed goal issuance, exact body/execution
permission, native route refresh, arrival observation, access waiting, idle
clearance and pedestrian courtesy. Inactive hive/engineering gameplay is outside
this audit. No claim that every possible future navigation defect is eliminated.

| Finding | Source contradiction | Correction |
| --- | --- | --- |
| Exit alternatives lose continuity | `ServiceClearanceNavigation` starts again with the first eight exits every refresh. After that batch fails and a later batch starts moving, the next refresh replaces it with the first batch, clearing the accepted native path and retry history. | Retain the current candidate pass under the exact body/execution, owner and phase. Reject proven failures, retain a progressing later batch, and retry a completed pass when the environment can have changed. |
| Waiting pocket remains latched after a stall | `ServiceApproachNavigation` discards unavailable targets but ignores terminal `PATH_STALLED`. A physically supported pocket can remain unreachable after a path obstruction. | Shared rejection policy also retires stalled targets; the existing bounded waiting cursor continues to another pocket. Live-traffic waits and unfinished search budgets retain their evidence. |
| Idle clearance repeats a failed target and unbounded probes | `ServicePointClearance` ignores terminal navigation failures. Its native reachability predicate also bypasses the shared eight-probe continuation used by courtesy. | Reuse `ClearanceProbe`; remember rejected candidates in the current bounded pass and select another target after a terminal failure. No extra process, activity or position writer. |
| Broken contracts masquerade as waiting | `ActorMovementNavigation` catches arbitrary `IllegalArgumentException` from goal construction/navigation, stops the body and repeats the same request forever. | Operational failures remain typed results. Contract failures propagate with actor, owner, goal and execution plus the original cause. |

`GoalAlternatives` owns only a transient physical candidate pass. The service
owner retains its semantic work and access declaration. The shared navigator
alone actuates bodies; supported physical observation alone acknowledges arrival.
Re-ranking candidates by the moving body's distance does not reset an accepted
pass. A stalled chosen endpoint rejects that endpoint, preserving the other
alternatives. Caller authority and changed candidate sets invalidate the pass.

Checked native Minecraft/NeoForge 1.21.1 collision semantics through the existing
ModLens dependency snapshot. `CollisionGetter.noCollision` includes native hard
entity collisions; separate live pedestrian checks remain deliberate. No guessed
replacement collision rules or changes to native movement speed were introduced.

Focused evidence: 13 unit checks for candidate continuation, real failure versus
traffic/budget waits, probe continuation, route hints, meals, courtesy and single
navigation ownership. The local-navigation GameTest slice passed44 cases,
including a real villager walking to the ninth exit beyond an unreachable batch.
The initial new test incorrectly exhausted its own candidate list; corrected it
to match production's next-pass behavior and used a sufficiently sized template.
This is native path evidence, separate from a normal full-pack client visit.

`live-hot-service-circulation.json` declares a full-pack live HOT/COLD/HOT
observation around Hearthvale's service area, with snapshots and graphical frames.
Current runtime receipts and any follow-up findings are kept in the canonical
continuity ledger. No simulation fast-forward or fabricated activity completion
is required by that observation.

Live follow-up: one loaded, canonically COLD actor without current body authority
caused `admissionDiagnostic` to call `ActorBodyAuthority.current` and throw. The
population wrapper then swallowed the exception and removed physical observations
for the whole settlement. Report `NO_CURRENT_BODY_AUTHORITY` for that exact absent
authority state; never allocate an incarnation from a read-only query. Unexpected
inspection errors now produce `inspection_failed`, error type, cause and a bounded
stack instead of an apparently successful canonical-only response. Existing native
before-admission/cancellation coverage also checks this non-mutating observation.

The now-visible client evidence identified an additional real stall: Maya Baker,
resident:6-25, retained crop9 and an unused FIELD_WORK ticket across a normal
server stop. Recovery lost the live-only unused permission and correctly refused
execution with NO_LIVE_ATTEMPT_PERMISSION; the field and hand still matched the
preimage, which alone is not permission to replay. The general operation journal
now seals positive unused-attempt evidence at quiescence, after owner callbacks
finish. Hydration validates the exact operation ticket/sequence and absence of
applied effects; the next preparation removes and forces that proof before any
mutation. A crash after proof consumption remains fail-closed. Attempted,
ambiguous, cancelled and conditionally excluded operations gain no new permit.
Quiescence rejects late mutation admission. No field-specific replay or clearing
of old ambiguity is introduced. Native operation journal format6 explicitly
rejects format5; the affected R266 test world is retained for diagnosis, and the
new build requires a fresh disposable world.
