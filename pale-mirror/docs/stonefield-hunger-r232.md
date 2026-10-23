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
