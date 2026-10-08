package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;

/** State-level closure for the one current custodian of a bakery job's input or output. */
final class BakeryWorkValidation {
    private BakeryWorkValidation() { }

    static void validate(ExactInventory inventory, ProductionJob job, Settlement settlement) {
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        ProductionStationSpec station = null;
        for (ContainerRecord container : inventory.containers().values()) {
            var candidate = container.productionStation();
            if (candidate.isEmpty() || !candidate.orElseThrow().id().equals(work.stationId())) continue;
            if (station != null) throw new IllegalArgumentException("bakery work has duplicate station identity");
            station = candidate.orElseThrow();
        }
        if (station == null) throw new IllegalArgumentException("bakery work has no current declared station");
        if (!station.facilityId().equals(job.facilityId()) || station.capability() != ProductionStationSpec.Capability.BAKING)
            throw new IllegalArgumentException("bakery work station differs from the job facility or capability");
        if (job.inputHold() instanceof ProductionInputHold.Materialized) validateExact(inventory, job, station, work);
        else validateFungible(inventory, job, settlement, station, work);
    }

    private static void validateExact(ExactInventory inventory, ProductionJob job,
                                      ProductionStationSpec station, BakeryWorkState work) {
        if (work.phase() == BakeryWorkState.Phase.DELIVERED) {
            // Delivery releases the product to ordinary stock. The job remains only to
            // bring its worker home; another resident may take or consume the bread.
            ExactItemStack output = inventory.items().get(job.outputItemId());
            if (inventory.items().containsKey(job.consumedItemId())
                    || output != null && (!output.itemKind().equals("minecraft:bread")
                        || output.custody() instanceof InventoryCustody.Actor actor
                        && actor.actorId().equals(job.workerId())
                        || output.custody() instanceof InventoryCustody.ContainerSlot slot
                        && slot.containerId().equals(station.containerId())))
                throw new IllegalArgumentException("delivered bakery job still holds exact input or output");
            return;
        }
        boolean outputPhase = work.phase() == BakeryWorkState.Phase.STATION_UNLOAD
                || work.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY;
        SubjectId id = outputPhase ? job.outputItemId() : job.consumedItemId();
        ExactItemStack stack = inventory.items().get(id);
        if (work.phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                && work.block().map(value -> value.reason() == BakeryWorkBlock.Reason.SOURCE_CHANGED
                        || value.reason() == BakeryWorkBlock.Reason.AMBIGUOUS_EFFECT).orElse(false)
                && (stack == null || !(stack.custody() instanceof InventoryCustody.ContainerSlot slot)
                    || !slot.containerId().equals(FrontierWorldState.depotId(job.settlementId())))) {
            if (inventory.items().containsKey(job.outputItemId()))
                throw new IllegalArgumentException("blocked bakery pickup cannot already own its output");
            return;
        }
        InventoryCustody expected = switch (work.phase()) {
            case DEPOT_PICKUP -> {
                ExactItemStack source = inventory.items().get(job.consumedItemId());
                if (source == null || !(source.custody() instanceof InventoryCustody.ContainerSlot slot)
                        || !slot.containerId().equals(FrontierWorldState.depotId(job.settlementId())))
                    throw new IllegalArgumentException("bakery input has no exact depot slot");
                yield source.custody();
            }
            case STATION_LOAD, DEPOT_DELIVERY -> new InventoryCustody.Actor(job.workerId());
            case PROCESSING -> new InventoryCustody.ContainerSlot(station.containerId(), station.inputSlot());
            case STATION_UNLOAD -> new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot());
            case DELIVERED -> throw new IllegalStateException("delivered bakery resources are public stock");
        };
        if (stack == null || !stack.custody().equals(expected) || !stack.economicOwnerId().equals(job.rights().resourceOwner().id())
                || !stack.itemKind().equals(outputPhase ? "minecraft:bread" : "minecraft:wheat")
                || stack.count() != job.outputCount()
                || inventory.items().containsKey(outputPhase ? job.consumedItemId() : job.outputItemId()))
            throw new IllegalArgumentException("bakery exact input/output has no sole phase-correct custodian");
    }

    private static void validateFungible(ExactInventory inventory, ProductionJob job, Settlement settlement,
                                         ProductionStationSpec station, BakeryWorkState work) {
        if (awaitingInputReallocation(inventory.fungibleResources(), job, work)) return;
        ProductionInputHold hold = job.inputHold();
        SubjectId claimId; Map<SubjectId, Integer> input;
        if (hold instanceof ProductionInputHold.FungibleCold cold) {
            claimId = cold.claimId(); input = cold.inputLots();
        } else if (hold instanceof ProductionInputHold.FungibleBound bound) {
            claimId = bound.claimId(); input = bound.inputLots();
        } else throw new IllegalArgumentException("bakery resource job has no declared input allocation");
        FungibleResourceLedger ledger = inventory.fungibleResources();
        if (work.phase() == BakeryWorkState.Phase.DELIVERED) {
            // The delivery receipt ended the job's resource custody, not its return
            // route. Its output lot can subsequently be split, moved or consumed.
            CustodyAccount actor = ledger.accounts().get(work.actorAccountId());
            CustodyAccount stationAccount = ledger.accounts().get(work.stationAccountId());
            ResourceLot output = ledger.lots().get(job.outputItemId());
            if (ledger.claims().containsKey(claimId)
                    || output != null && !output.itemKind().equals("minecraft:bread")
                    || actor != null && actor.lotQuantities().containsKey(job.outputItemId())
                    || stationAccount != null && stationAccount.lotQuantities().containsKey(job.outputItemId()))
                throw new IllegalArgumentException("delivered bakery job still holds its input claim or output");
            return;
        }
        boolean outputPhase = work.phase() == BakeryWorkState.Phase.STATION_UNLOAD
                || work.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY;
        SubjectId currentId = switch (work.phase()) {
            case DEPOT_PICKUP -> work.sourceAccountId();
            case STATION_LOAD, DEPOT_DELIVERY -> work.actorAccountId();
            case PROCESSING, STATION_UNLOAD -> work.stationAccountId();
            case DELIVERED -> throw new IllegalStateException("delivered bakery resources are public stock");
        };
        CustodyAccount account = ledger.accounts().get(currentId);
        ResourceCustody custody = switch (work.phase()) {
            case DEPOT_PICKUP -> new ResourceCustody.Container(FrontierWorldState.depotId(settlement.id()));
            case STATION_LOAD, DEPOT_DELIVERY -> new ResourceCustody.Actor(job.workerId());
            case PROCESSING, STATION_UNLOAD -> new ResourceCustody.Container(station.containerId());
            case DELIVERED -> throw new IllegalStateException("delivered bakery resources are public stock");
        };
        Map<SubjectId, Integer> expectedLots = outputPhase ? Map.of(job.outputItemId(), job.outputCount()) : input;
        Map<SubjectId, Integer> expectedClaim = outputPhase ? Map.of() : Map.of(claimId, job.outputCount());
        if (account == null || !account.custody().equals(custody)
                || expectedLots.entrySet().stream().anyMatch(entry -> account.lotQuantities().getOrDefault(entry.getKey(), 0) < entry.getValue())
                || expectedClaim.entrySet().stream().anyMatch(entry -> account.claimQuantities().getOrDefault(entry.getKey(), 0) < entry.getValue())
                || (work.phase() != BakeryWorkState.Phase.DEPOT_PICKUP
                    && (!account.lotQuantities().equals(expectedLots) || !account.claimQuantities().equals(expectedClaim)))
                || outputPhase == ledger.claims().containsKey(claimId))
            throw new IllegalArgumentException("bakery resource has no sole phase-correct account and claim");
        for (SubjectId lotId : expectedLots.keySet()) {
            ResourceLot lot = ledger.lots().get(lotId);
            if (lot == null || !lot.economicOwnerId().equals(job.rights().resourceOwner().id())
                    || !lot.itemKind().equals(outputPhase ? "minecraft:bread" : "minecraft:wheat"))
                throw new IllegalArgumentException("bakery resource lot changed economic owner or recipe kind");
        }
        // A selected batch may leave a lawful remainder in the source lot.
        // Only the claimed part must be absent from this job's current custodian.
        if (!outputPhase && ledger.lots().containsKey(job.outputItemId()))
            throw new IllegalArgumentException("bakery output cannot pre-exist its station recipe");
    }

    static boolean awaitingInputReallocation(FungibleResourceLedger ledger, ProductionJob job, BakeryWorkState work) {
        if (work.phase() != BakeryWorkState.Phase.DEPOT_PICKUP || work.pendingPhysicalStep().isPresent()
                || work.block().map(value -> value.reason() != BakeryWorkBlock.Reason.SOURCE_CHANGED).orElse(true))
            return false;
        SubjectId claimId = switch (job.inputHold()) {
            case ProductionInputHold.FungibleCold cold -> cold.claimId();
            case ProductionInputHold.FungibleBound bound -> bound.claimId();
            default -> null;
        };
        return claimId != null && !ledger.claims().containsKey(claimId);
    }
}
