package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;

/** State-level closure for station fulfillment, independent of the requesting production policy. */
final class StationWorkValidation {
    private StationWorkValidation() { }

    static void validate(ExactInventory inventory, ProductionJob job, Settlement settlement, ProductionCatalog catalog) {
        StationWorkState work = job.stationWork().orElseThrow();
        var recipe = catalog.require(work.recipeId());
        if ((recipe.outcome() instanceof ProductionRecipeOutcome.Repair) != work.maintenanceTarget().isPresent()
                || work.maintenanceTarget().filter(target -> !target.itemId().equals(job.outputItemId())
                    || !target.itemKind().equals(job.outputItemKind())).isPresent())
            throw new IllegalArgumentException("station repair must retain its original exact product identity");
        if (StationWorkProduct.individual(catalog, job) && work.outputRepresentation() != StationWorkProduct.Representation.EXACT_ITEM
                || !StationWorkProduct.individual(catalog, job)
                    && (job.inputHold() instanceof ProductionInputHold.Materialized) != (work.outputRepresentation() == StationWorkProduct.Representation.EXACT_ITEM))
            throw new IllegalArgumentException("station product representation differs from its declared recipe protocol");
        if (!recipe.product().itemKind().equals(job.outputItemKind())
                || !recipe.ingredient().accepts(work.inputItemKind()) || recipe.outputForInput(work.inputQuantity()) != job.outputCount())
            throw new IllegalArgumentException("bakery work has a foreign declared recipe or output");
        var sourceStorage = inventory.containers().get(work.sourceContainerId());
        if (sourceStorage == null || !sourceStorage.ownerId().equals(job.settlementId())
                || sourceStorage.storage().isEmpty() && sourceStorage.productionStation().filter(s -> s.id().equals(work.stationId())
                    && s.facilityId().equals(job.facilityId())).isEmpty())
            throw new IllegalArgumentException("bakery source lost its retained owned storage capability");
        var destination = inventory.fungibleResources().accounts().get(work.destinationAccountId());
        if (destination != null && !destination.custody().equals(new ResourceCustody.Container(job.rights().destinationContainerId())))
            throw new IllegalArgumentException("bakery destination account belongs to another storage endpoint");
        ProductionStationSpec station = null;
        for (ContainerRecord container : inventory.containers().values()) {
            var candidate = container.productionStation();
            if (candidate.isEmpty() || !candidate.orElseThrow().id().equals(work.stationId())) continue;
            if (station != null) throw new IllegalArgumentException("bakery work has duplicate station identity");
            station = candidate.orElseThrow();
        }
        if (station == null) throw new IllegalArgumentException("bakery work has no current declared station");
        if (!station.facilityId().equals(job.facilityId()) || station.capability() != recipe.capability())
            throw new IllegalArgumentException("bakery work station differs from the job facility or capability");
        validateMaintenance(inventory, job, station, work);
        if (StationWorkProduct.individual(catalog, job) && (work.phase() == StationWorkState.Phase.STATION_UNLOAD
                || work.phase() == StationWorkState.Phase.DEPOT_DELIVERY || work.phase() == StationWorkState.Phase.DELIVERED))
            validateIndividualOutput(inventory, job, station, work);
        else if (job.inputHold() instanceof ProductionInputHold.Materialized) validateExact(inventory, job, station, work);
        else validateFungible(inventory, job, settlement, station, work);
    }

    private static void validateMaintenance(ExactInventory inventory, ProductionJob job, ProductionStationSpec station, StationWorkState work) {
        if (work.maintenanceTarget().isEmpty() || StationWorkCargo.output(job)) return;
        var target = work.maintenanceTarget().orElseThrow();
        var item = inventory.items().get(target.itemId());
        if (item == null || !item.economicOwnerId().equals(job.rights().resourceOwner().id())
                || !item.itemKind().equals(target.itemKind()) || item.condition().damage() == 0)
            throw new IllegalArgumentException("repair work lost its exact damaged target");
        boolean matches = switch (work.phase()) {
            case TARGET_PICKUP -> item.custody() instanceof InventoryCustody.ContainerSlot slot
                    && slot.containerId().equals(target.sourceContainerId());
            case TARGET_LOAD -> item.custody().equals(new InventoryCustody.Actor(job.workerId()));
            case DEPOT_PICKUP, STATION_LOAD, PROCESSING -> item.custody().equals(new InventoryCustody.ContainerSlot(
                    station.containerId(), station.maintenanceSlot().orElseThrow()));
            case STATION_UNLOAD, DEPOT_DELIVERY, DELIVERED -> throw new IllegalStateException("output validated separately");
        };
        if (!matches) throw new IllegalArgumentException("repair target has a foreign custodian for its phase");
    }

    private static void validateIndividualOutput(ExactInventory inventory, ProductionJob job, ProductionStationSpec station, StationWorkState work) {
        var output = inventory.items().get(job.outputItemId());
        var expected = work.phase() == StationWorkState.Phase.STATION_UNLOAD
                ? new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot()) : new InventoryCustody.Actor(job.workerId());
        var input = job.inputHold() instanceof ProductionInputHold.FungibleCold cold ? cold.claimId()
                : ((ProductionInputHold.FungibleBound) job.inputHold()).claimId();
        if (inventory.fungibleResources().claims().containsKey(input))
            throw new IllegalArgumentException("individual output retains a consumed material claim");
        if (work.phase() == StationWorkState.Phase.DELIVERED) {
            if (output != null && (output.custody().equals(new InventoryCustody.Actor(job.workerId()))
                    || output.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(station.containerId())))
                throw new IllegalArgumentException("delivered station work still holds its individual output");
        } else if (output == null || !output.custody().equals(expected) || !output.economicOwnerId().equals(job.rights().resourceOwner().id())
                || !output.itemKind().equals(job.outputItemKind()) || output.count() != 1)
            throw new IllegalArgumentException("station individual output has no exact phase-correct custodian");
    }

    private static void validateExact(ExactInventory inventory, ProductionJob job,
                                      ProductionStationSpec station, StationWorkState work) {
        if (work.phase() == StationWorkState.Phase.DELIVERED) {
            // Delivery releases the product to ordinary stock. The job remains only to
            // bring its worker home; another resident may take or consume the bread.
            ExactItemStack output = inventory.items().get(job.outputItemId());
            if (inventory.items().containsKey(job.consumedItemId())
                    || output != null && (!output.itemKind().equals(job.outputItemKind())
                        || output.custody() instanceof InventoryCustody.Actor actor
                        && actor.actorId().equals(job.workerId())
                        || output.custody() instanceof InventoryCustody.ContainerSlot slot
                        && slot.containerId().equals(station.containerId())))
                throw new IllegalArgumentException("delivered bakery job still holds exact input or output");
            return;
        }
        boolean outputPhase = work.phase() == StationWorkState.Phase.STATION_UNLOAD
                || work.phase() == StationWorkState.Phase.DEPOT_DELIVERY;
        SubjectId id = outputPhase ? job.outputItemId() : job.consumedItemId();
        ExactItemStack stack = inventory.items().get(id);
        if (work.phase() == StationWorkState.Phase.DEPOT_PICKUP
                && work.block().map(value -> value.reason() == BakeryWorkBlock.Reason.SOURCE_CHANGED
                        || value.reason() == BakeryWorkBlock.Reason.AMBIGUOUS_EFFECT).orElse(false)
                && (stack == null || !(stack.custody() instanceof InventoryCustody.ContainerSlot slot)
                    || !slot.containerId().equals(StationWorkStorage.source(job)))) {
            if (inventory.items().containsKey(job.outputItemId()))
                throw new IllegalArgumentException("blocked bakery pickup cannot already own its output");
            return;
        }
        InventoryCustody expected = switch (work.phase()) {
            case TARGET_PICKUP, TARGET_LOAD, DEPOT_PICKUP -> {
                ExactItemStack source = inventory.items().get(job.consumedItemId());
                if (source == null || !(source.custody() instanceof InventoryCustody.ContainerSlot slot)
                        || !slot.containerId().equals(StationWorkStorage.source(job)))
                    throw new IllegalArgumentException("bakery input has no exact depot slot");
                yield source.custody();
            }
            case STATION_LOAD, DEPOT_DELIVERY -> new InventoryCustody.Actor(job.workerId());
            case PROCESSING -> new InventoryCustody.ContainerSlot(station.containerId(), station.inputSlot());
            case STATION_UNLOAD -> new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot());
            case DELIVERED -> throw new IllegalStateException("delivered bakery resources are public stock");
        };
        if (stack == null || !stack.custody().equals(expected) || !stack.economicOwnerId().equals(job.rights().resourceOwner().id())
                || !stack.itemKind().equals(outputPhase ? job.outputItemKind() : work.inputItemKind())
                || stack.count() != (outputPhase ? job.outputCount() : job.inputCount())
                || inventory.items().containsKey(outputPhase ? job.consumedItemId() : job.outputItemId()))
            throw new IllegalArgumentException("bakery exact input/output has no sole phase-correct custodian");
    }

    private static void validateFungible(ExactInventory inventory, ProductionJob job, Settlement settlement,
                                         ProductionStationSpec station, StationWorkState work) {
        if (awaitingInputReallocation(inventory.fungibleResources(), job, work)) return;
        ProductionInputHold hold = job.inputHold();
        SubjectId claimId; Map<SubjectId, Integer> input;
        if (hold instanceof ProductionInputHold.FungibleCold cold) {
            claimId = cold.claimId(); input = cold.inputLots();
        } else if (hold instanceof ProductionInputHold.FungibleBound bound) {
            claimId = bound.claimId(); input = bound.inputLots();
        } else throw new IllegalArgumentException("bakery resource job has no declared input allocation");
        FungibleResourceLedger ledger = inventory.fungibleResources();
        if (work.phase() == StationWorkState.Phase.DELIVERED) {
            // The delivery receipt ended the job's resource custody, not its return
            // route. Its output lot can subsequently be split, moved or consumed.
            CustodyAccount actor = ledger.accounts().get(work.actorAccountId());
            CustodyAccount stationAccount = ledger.accounts().get(work.stationAccountId());
            ResourceLot output = ledger.lots().get(job.outputItemId());
            if (ledger.claims().containsKey(claimId)
                    || output != null && !output.itemKind().equals(job.outputItemKind())
                    || actor != null && actor.lotQuantities().containsKey(job.outputItemId())
                    || stationAccount != null && stationAccount.lotQuantities().containsKey(job.outputItemId()))
                throw new IllegalArgumentException("delivered bakery job still holds its input claim or output");
            return;
        }
        boolean outputPhase = work.phase() == StationWorkState.Phase.STATION_UNLOAD
                || work.phase() == StationWorkState.Phase.DEPOT_DELIVERY;
        SubjectId currentId = switch (work.phase()) {
            case TARGET_PICKUP, TARGET_LOAD, DEPOT_PICKUP -> work.sourceAccountId();
            case STATION_LOAD, DEPOT_DELIVERY -> work.actorAccountId();
            case PROCESSING, STATION_UNLOAD -> work.stationAccountId();
            case DELIVERED -> throw new IllegalStateException("delivered bakery resources are public stock");
        };
        CustodyAccount account = ledger.accounts().get(currentId);
        ResourceCustody custody = switch (work.phase()) {
            case TARGET_PICKUP, TARGET_LOAD, DEPOT_PICKUP -> new ResourceCustody.Container(StationWorkStorage.source(job));
            case STATION_LOAD, DEPOT_DELIVERY -> new ResourceCustody.Actor(job.workerId());
            case PROCESSING, STATION_UNLOAD -> new ResourceCustody.Container(station.containerId());
            case DELIVERED -> throw new IllegalStateException("delivered bakery resources are public stock");
        };
        Map<SubjectId, Integer> expectedLots = outputPhase ? Map.of(job.outputItemId(), job.outputCount()) : input;
        Map<SubjectId, Integer> expectedClaim = work.phase() == StationWorkState.Phase.STATION_UNLOAD
                ? Map.of(ProductionOperation.outputClaimId(StationWorkOperation.operationId(job)), job.outputCount())
                : outputPhase ? Map.of() : Map.of(claimId, job.inputCount());
        if (account == null || !account.custody().equals(custody)
                || expectedLots.entrySet().stream().anyMatch(entry -> account.lotQuantities().getOrDefault(entry.getKey(), 0) < entry.getValue())
                || expectedClaim.entrySet().stream().anyMatch(entry -> account.claimQuantities().getOrDefault(entry.getKey(), 0) < entry.getValue())
                || (!work.phase().maintenanceTransfer() && work.phase() != StationWorkState.Phase.DEPOT_PICKUP
                    && work.phase() != StationWorkState.Phase.PROCESSING && work.phase() != StationWorkState.Phase.STATION_UNLOAD
                    && (!account.lotQuantities().equals(expectedLots) || !account.claimQuantities().equals(expectedClaim)))
                || outputPhase == ledger.claims().containsKey(claimId))
            throw new IllegalArgumentException("bakery resource has no sole phase-correct account and claim");
        for (SubjectId lotId : expectedLots.keySet()) {
            ResourceLot lot = ledger.lots().get(lotId);
            if (lot == null || !lot.economicOwnerId().equals(job.rights().resourceOwner().id())
                    || !lot.itemKind().equals(outputPhase ? job.outputItemKind() : work.inputItemKind()))
                throw new IllegalArgumentException("bakery resource lot changed economic owner or recipe kind");
        }
        // A selected batch may leave a lawful remainder in the source lot.
        // Only the claimed part must be absent from this job's current custodian.
        if (!outputPhase && ledger.lots().containsKey(job.outputItemId()))
            throw new IllegalArgumentException("bakery output cannot pre-exist its station recipe");
    }

    static boolean awaitingInputReallocation(FungibleResourceLedger ledger, ProductionJob job, StationWorkState work) {
        if (!work.phase().inputAtSource() || work.pendingPhysicalStep().isPresent()
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
