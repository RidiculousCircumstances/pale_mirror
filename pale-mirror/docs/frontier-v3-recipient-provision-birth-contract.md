# Frontier v3 exact household and birth contract

Status: accepted target for the birth portion of `MAT-006` and `V3-AUD-039`;
implementation and evidence remain pending. The former settlement-wide ration
cycle is **superseded**, not a second nutrition authority. Exact resident
feeding, needs, activity, stock and HOT/COLD consumption are specified by
[resident life and resource accounting](frontier-v3-resident-life-resource-contract.md).

## Player promise and owners

A resident is not created after an invisible population timer. One birth
belongs to one exact household, its participants, home and exclusive bed
capacity. Approaching, leaving or restarting changes only how its current
stage is executed and shown; it never selects replacement parents, creates an
adult substitute, oversubscribes a home or produces a second child.

`HumanPopulation` owns residents, households, settlement membership, birth
instant and exact personal need state. `ActorCondition` owns physical vitality;
the common resource ledger owns food quantity, economic owner, claims and
custody. A birth lifecycle references and atomically updates those owners
through typed transitions. A scene, Villager, board and physical container own
none of them. A parent's food requirement reads their exact need and current
food claim, never a settlement-wide `SettlementProvision` result.

## Household and birth lifecycle

One bounded birth lifecycle retains one exact household and settlement, exact
living parent/participant IDs, home facility, one exact unoccupied home/bed
slot reservation, required food/care allocations, conception/birth epoch,
stage and typed failure/recovery evidence. It never pre-creates a hidden person
or selects a replacement participant after admission.

```text
eligible household + exact capacity and care reservation
  -> PREPARING
  -> CARE / DUE
  -> BIRTH_TRANSITION
  -> exact new resident, need state and ActorCondition created atomically
  -> newborn/child body confirmed HOT or retained COLD current state
  -> RECOVERY / COMPLETED or typed loss/failure
```

While HOT, the exact parent/participants occupy their retained household or
care stations and the new age-correct body appears only at the observed birth
transition. While COLD, the same canonical stages may progress autonomously;
later loading projects an already-existing person and never reenacts birth.
Chunk load alone cannot create a resident or supply missing capacity.

The resident register, need state and actor index change in one canonical
transition. The bed reservation becomes that exact child's home slot and
cannot be reused by migration or another birth. Participant death, lost
capacity, quarantine, missing care, food departure or player obstruction
produces a typed owned wait, interruption, loss or failure. A confirmed living
child is never deleted merely to balance a failed household process.

The reserved future identity becomes exactly one canonical resident with one
stable Minecraft UUID and either one naturally eligible live body or one
inactive actor carrier. Canonical creation, UUID/carrier binding and bed
assignment recover as one exact-once state machine: restart cannot leave a
resident without reconstruction evidence, create both body and carrier, or
select a substitute child.

## HOT/COLD, recovery and evidence

Birth is a registered class-F lifecycle using the shared Process/Scene SDK.
Its lease binds exact version, participants, facility stations, current stage,
allocations and actor/custody epochs. COLD advances retained semantic stages
from canonical knowledge. HOT uses exact managed Villagers, local navigation
and visible station interaction, then submits typed observations; only the
canonical owner advances stages or creates the person. Demand loss and restart
retain the same stage and do not replay pregnancy or birth.

A physically started birth remains bounded `UNKNOWN_AFTER_RESTART` until its
exact body/bed postcondition is inspected. Ambiguity isolates the affected
household transition; no person or bed is guessed into existence. Active and
unknown records cannot be compacted. Fresh-world format changes reject old
bytes instead of synthesizing participants or reservations.

MAT-006 birth M2 evidence requires exact household/bed admission and negative
cases, COLD stage progression, codec/WAL recovery around birth, focused HOT
scene tests for participant identity and interruption, and one ordinary-player
native lifecycle with a linked frame and exact emergence through restart.
Existing identity, port, navigation and recovery evidence may compose if
their dependency identity is unchanged. Fixtures may establish initial
households but never inject the born resident as the asserted result.

M3 still requires an unbriefed player to recognise household preparation and
the child without diagnostics. Natural-terrain hardening, demographic
calibration, pregnancy detail, child growth, final art, deployment and v2
removal remain separate.
