# R266: startup equipment cleanup and service access lifetime

## Removed scenario equipment from ordinary genesis

Normal worlds no longer receive four iron engineering picks in every town.
Engineering scenarios explicitly supply their own kit in `testFixtures`; generic
inventory tests use the actual agricultural startup equipment. The old field-tool
slot limit reserved for the engineering kit is replaced by actual container
capacity. Ordinary agricultural and mining equipment, finite starter food and
configured initial public stock remain part of the graybox startup. This change
does not remove engineering processes or change resource prices and reserves.
A fresh disposable world removes equipment already granted by the old genesis;
there is no destructive inventory migration or per-item cleanup heuristic.

## Confirmed live failure and its cause

R265 at 2026-10-25 23:23:40, revision131378/tick37781: courier `resident:6-6`
retained `shipment:internal/19b8d17c-5d09-3191-b0bf-6f5d18cc2215`, carrying one raw
iron from Hearthvale's warehouse to the smelter INPUT. The physical destination
was temporarily unavailable while the logical destination could receive it.
`ShipmentServiceAccess` omitted the access declaration whenever that transient
transfer-readiness check failed. Movement still retained the same valid job,
actor execution and goal, and `requireDeclared` correctly rejected the missing
owner declaration. Quarantine then invoked the forestry shutdown callback,
which incorrectly required active canonical state and raised a second error.

## Shared contract

A service declaration lives with its current workflow and exact execution.
`ServiceAccessDemand.readyToEnter` separately expresses whether it may enter now.
Unready approaches retain their identity, wait outside through the existing
clearance navigator, and cannot win arbitration. Actual occupied footprints
continue blocking entry until physically cleared. Prepared physical effects
retain their admitted service. Current phase/owner checks remain mandatory.
Shipment does not lose its declaration when receiver capacity or a physical
layout is temporarily unavailable. A later readiness change reuses the same
journey. No movement invariant is caught or silently skipped.

Quarantine revokes command authority. Forestry shutdown performs cancellation
only with active canonical authority, and preserves journals for recovery after
quarantine. It must not start commands from passive retained ownership or mask
the original failure. A missing declaration now reports actor, owner and point.

## Verification

The immutable copied R265 save reproduces one missing service declaration with
the previous code; the repair recovers the same revision with zero missing
movement declarations. The exact failing courier now has a declared, unready
approach while its physical destination is absent. Focused tests cover full
station / capacity release, recovery, startup contents and quarantined shutdown.
Detailed run and deployment receipts are recorded in the continuity ledger;
this is a scoped correction, not a full economic or engineering acceptance run.
