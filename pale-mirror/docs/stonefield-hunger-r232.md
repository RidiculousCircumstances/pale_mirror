# Stonefield: mining admission blocked nutrition

## Evidence and cause

On 2026-10-23 Rosa Moss (`resident:2-1`) and Ada Wells (`resident:2-10`)
were starving despite usable bread. Snapshot tick233132 retains their extraction
jobs58 and45 with physical steps prepared around tick156694/157567. Both picks
are serviceable: damage18/131 and16/131. The work capability correctly refuses
to relinquish an unresolved physical effect; no meal has started.

Archived runtime log `2026-10-23-6.log.gz` establishes the initiating cause:
17:13:41.822 and17:14:25.476 report `KNOWLEDGE_FENCE_NOT_ADMITTED` at the exact
coal cells, immediately followed by `retained mining block effect refused:
UNAVAILABLE`. The native operation had already consumed its one attempt.
Its following poll reports `AMBIGUOUS`, with no applied/saved participants.
This predates restart; restart is not the initiating cause.

Mining called `setBlock` inside its non-replayable operation without first
admitting the asynchronous navigation knowledge fence. Ordinary backpressure
therefore became an ambiguous physical operation. The field-work caller had
the same missing boundary for collision-changing actions such as tilling.

## Correction and responsibility

Both work executors now use the existing common `ManagedBlockWrites`
preparation before entering the native operation. A pending force starts no
native attempt. Its continuation rechecks the exact current job/body owner
before proceeding. Terrain observation, persistence, mining, nutrition and
inventory retain their separate responsibilities. There is no food-specific
unlock, free resource, repeated mutation or relaxed recovery rule.

The old binary did not persist a positive no-effect receipt for these two
refusals. Matching present blocks/hands alone cannot safely recreate a consumed
permission after restart. This correction prevents recurrence but deliberately
does not manufacture recovery evidence for the already ambiguous operations.
The user-authorized fresh world will remove those historical obligations; keep
this snapshot and the initiating logs as diagnosis evidence.

## Verification

Compilation passed. Native block-extraction slice2/2 passed: actual Minecraft
loot/extraction plus a real terrain operation with deliberately deferred WAL.
The latter proves no native ticket/effect before the fence, one mutation after
acknowledgement, observation without a second mutation and rejection of a new
attempt after journal recovery. Field-turns native slice20/20 passed. Focused
operation-ledger/runtime JUnit33/33 and guardrails passed; results are in
`build/r232-hunger-verification.log`. This is boundary verification, not proof
that the two old residents resumed or a full-world hunger acceptance.

## Used-tool departure defect found during the new HOT/COLD run

After the live mining visit and subsequent release restart, Rosa and Ada retained
UNKNOWN_AFTER_RESTART body leases. Their work/meal positions stopped advancing
in COLD, although no native mining effect was pending. The earlier unload log
recorded CAPTURE_UNAVAILABLE for their bodies. Static inspection found a second
independent contradiction: ActorHandEvidence accepted exact identity tags but
compared the remaining components with a pristine Minecraft stack. Every worn
pick carries `minecraft:damage`; therefore a usable tool rejected body capture.
The offline native-save reader likewise rejected this supported component.

One shared hand-component boundary now retains the exact supported integer patch
(damage, maximum damage, maximum stack size) alongside item identity. Live capture,
common body/scene receipt persistence and vanilla saved-hand inspection agree.
Different observed wear cannot certify the old receipt; unsupported components
still reject visibly. Scene hand observation uses this same boundary. This does
not repair tools, erase ambiguous operations, fabricate departure evidence or
change item/resource ownership. Native defaults remain native defaults; offline
inspection does not require Minecraft registry initialization.

Verification:31focused body/scene/ledger persistence checks and guardrailsPASS;
native `hand-evidence` slice1/1PASS. The native test serializes a worn exact pick
using vanilla ItemStack.save and compares it with live evidence, then rejects
changed wear and unsupported components. build/r232-hand-tests.log and
build/r232-native-hand.log. Live return/unload validation follows deployment.

## Preparation crossing ordinary unload

The37ea87484 live run d6cb9c44-a014-446c-aa54-38bb33e47417 confirmed
recorded=true body departures (Rosa epoch3/residence5); Rosa ate at98086,
then delivered batch4 and resumed mining. At client logout99894 a new canonical
BlockWork preparation preceded its native journal entry. The body unloaded and
was saved, but ExtractionExecutor rejected every departure with pending work.
Neither COLD progression nor safe nutrition could release that intent.

The native operation boundary now supports withdrawal of an exact still-pending
canonical preparation. An absent exact journal entry proves native preparation
never happened: entries cannot retire before owner closure. Existing entries
need positive unattempted/cancelled evidence; recovered ambiguous attempts stay
fenced. Canonical durability blocks execution while withdrawal commits; direct
mining continuations observe that same barrier. The typed family consequence
validates exact body/execution/work revision and clears no resources or wear.
It wakes work and activity arbitration. No timeout or matching block is a proof.

Extraction departure also retains exact settled generated block witnesses before
the chunk leaves. After the pending effect is discharged, an unloaded ACQUIRED
source can release only against that complete, current, durable region image.
Missing/stale/pending witnesses cannot release custody. This closes the source
boundary as well as the actor boundary; a missing entity alone cannot unlock it.

Final boundary checks:22focused domain tests (including architecture),16operation
ledger/confirmation tests and guardrailsPASS. Added current-codec/engine recovery
coverage for unused living preparation, exact stale-body rejection, duplicate
cancellation rejection and unchanged resources/deposits. Fixture schedules include
the resident wake which production already owns. No broad obsolete-test campaign.

On54572fb75, native scenario16468bba-af1c-4565-8ee3-97228476fc16 passed ordinary
HOT→away→HOT. Rosa mined one more coal cell (9→10extracted), delivered batch4→5,
ate when the next hunger threshold121946 arrived, and reached RETURN_TOOL rev21
by123429. After final disconnect20:23:19 the worn-pick departure was recorded;
124027 showed CLOSED/bodyreleased/COLDavailable and satiety989. By124635 work15
had retired, preserving Rosa; she was nourished980. Ada also ate again at126440
and was nourished993 at126997. This proves the original symptom's continuation,
not permanent zero hunger. No pending effect was forcibly replayed.

Frames show the generated coal worksite, rendered on both visits; the overhead
camera does not establish a complete visual mining animation. The native scenario
and correlated state/log evidence establish work, nutrition and departure facts.
Server retained zero conflict counts and the departure queue drained tozero.
