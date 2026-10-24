# R245: native preparation and field handoff

Scope: slice A of the approved economic/native-operation rework. Economic publication
(slice B) is still pending; this report is not completion of the overall goal.

## Defects and correction

The field projector invoked `Level.setBlock` after consuming its native attempt.
Managed write ingress can defer that call until navigation/geology knowledge is
withdrawn durably. The negative return was classified as an unknown attempted
physical effect, and the field handoff subsequently treated it as unfinished work
forever. The original live warning did not record which veto/postcondition failed.
Frozen snapshots show the affected first-sown cells lacked geological exclusions
before the incident. Deliberately delayed native geology and navigation scenarios
reproduce the missing preparation boundary; they do not retrospectively invent a
receipt for an old unknown attempt.

The common executor now accepts a typed native effect. Its journal is durable
before prerequisite preparation; preparation identifies its waiting dependency and
retains the unused attempt. Owner continuations re-enter through current intent
validation. Only the exact effect about to run consumes an attempt. An interrupted
batch no longer consumes permissions belonging to later callbacks. Positive native
refusal remains distinct from unknown outcome; a matching preimage is insufficient
for recovered replay. All current `apply`/`Request` callers use the typed interface.

Field projection, field work and mining declare block write preparation through
one shared integration port. They no longer depend on separate executor preflight
ordering. Other effects explicitly retain their conservative observed-outcome
adapter or positive-refusal proof. Diagnostics distinguish preparation dependencies,
precondition mismatch, missing live permission, unknown result, exception and
postcondition mismatch.

Projection batches return individual cell outcomes. Advancing the bounded review
cursor is not a successful-write count. Unknown projection tickets reach field
handoff as CONFLICT: their chunk can be presented for inspection, but that does not
grant HOT execution or silently overwrite the unresolved physical state.

## Verification and delivery

Focused delayed preparation: 4/4 native tests passed. Shared storage/field/body
slice: 50/50 passed, including preserved later permission after interrupted batch.
The final integrated native, adapter, packaging and guardrail checks are in progress
in `build/r245-integration.log`. Final candidate and retained-world evidence will be
recorded after those gates and deployment; no live repair or <5-second claim yet.

Checked-in player scenario `live-field-preparation-handoff.json` covers first and
repeat visits to retained incident chunk 28,-1, physical block reception and frames.
A classified historical conflict is allowed to remain visible; the scenario does
not equate presentation readiness with successful recovery of all field operations.
