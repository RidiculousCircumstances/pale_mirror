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
