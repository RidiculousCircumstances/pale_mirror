package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Player admission and attendance adapter. Inventory, energy and clock remain with their shared owners. */
public final class ProductionStationOperator {
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, ProductionStationOperatorChanged event) {
        var machine = state.productionStations().require(subject);
        var record = ProductionStationEnergy.record(state, subject); var station = record.productionStation().orElseThrow();
        if (!event.stationId().equals(subject) || machine.revision() != event.machineRevision()
                || machine.pendingRecipe().isPresent()
                || event.stage() != ProductionStationOperatorChanged.Stage.PAUSE
                    && (!ReferenceContainerCustody.hasOperationalCustody(state, record.id())
                        || ReferenceContainerCustody.blocksCanonicalUse(state, record.id())
                        || ContainerPhysicalAuthorityComposition.pending(state, record.id())))
            throw new IllegalArgumentException("player operation needs a current available physical station instance");
        var recipe = state.bootstrap().ruleset().production().require(event.recipeId());
        var ledger = state.inventory().fungibleResources();
        ProductionOperation operation;
        var machines = state.productionStations();
        if (event.stage() == ProductionStationOperatorChanged.Stage.START) {
            if (machine.operation().isPresent()) throw new IllegalArgumentException("station already has an operation");
            var account = FungibleResourceCustodySupport.accountAtContainer(state, record.id()).orElseThrow(
                    () -> new IllegalArgumentException("station has no accounted ingredients"));
            var address = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(record.id(), station.inputSlot()));
            var binding = ledger.bindings().values().stream().filter(value -> value.accountId().equals(account.id())
                    && value.address().equals(address)).findFirst().orElseThrow(() -> new IllegalArgumentException("station input port is empty"));
            if (!recipe.ingredient().accepts(binding.itemKind())) throw new IllegalArgumentException("station input is not accepted by its selected recipe");
            int batches = Math.min(64 / recipe.product().quantity(),
                    ledger.unclaimedQuantity(account.id(), record.ownerId(), binding.itemKind()) / recipe.ingredient().quantity());
            if (recipe.heatPerBatch() > 0) batches = (int) Math.min(batches, machine.storedHeat() / recipe.heatPerBatch());
            if (batches == 0) throw new IllegalArgumentException("station lacks a complete ingredient/fuel batch");
            int inputQuantity = batches * recipe.ingredient().quantity();
            if (!ProductionStationProcessing.canStart(state, subject, recipe.id(), inputQuantity))
                throw new IllegalArgumentException("station output or processing capacity is occupied");
            var selected = FungibleResourceCustodySupport.selectAtAccount(ledger, account.id(), record.ownerId(), binding.itemKind(), inputQuantity).orElseThrow();
            if (!FungibleResourceCustodySupport.canReserve(state, selected))
                throw new IllegalArgumentException("station ingredients await their current physical binding");
            var id = new SubjectId("operation:player-station-" + UUID.nameUUIDFromBytes(
                    (subject.value() + "/" + machine.revision()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var claimId = new SubjectId("claim:station-" + UUID.nameUUIDFromBytes(id.value().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var outputId = new SubjectId("lot:station-output-" + UUID.nameUUIDFromBytes(id.value().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var claim = new ClaimAllocation(claimId, id, record.ownerId(), binding.itemKind(), inputQuantity, selected.lotQuantities(), ClaimPurpose.STATION_PROCESSING);
            ledger = ledger.reserveBound(claim, account.id(), binding.authorityEpoch());
            operation = new ProductionOperation(new ProductionOperation.Id(id), subject, id, recipe.id(),
                    new ProductionOperation.FungibleInput(account.id(), selected.lotQuantities(), Map.of(claimId, inputQuantity)), outputId,
                    inputQuantity, recipe.outputForInput(inputQuantity), recipe.heatPerBatch() * batches, Optional.empty(), false,
                    new ProductionOperation.PlayerInitiator(event.playerId(), ProductionOperation.PlayerAttendanceIntent.RUN_REQUESTED));
            machines = ProductionStationProcessing.start(state, station, operation);
        } else {
            operation = machine.operation().orElseThrow(() -> new IllegalArgumentException("station has no player operation"));
            if (!(operation.initiator() instanceof ProductionOperation.PlayerInitiator player) || !player.playerId().equals(event.playerId())
                    || !operation.recipeId().equals(recipe.id()) || operation.transformed())
                throw new IllegalArgumentException("player observation does not own the current station operation");
            if (event.stage() == ProductionStationOperatorChanged.Stage.ATTEND
                    && player.attendanceIntent() != ProductionOperation.PlayerAttendanceIntent.RUN_REQUESTED)
                throw new IllegalArgumentException("ordinary attendance cannot resume explicitly paused work");
            if (event.stage() != ProductionStationOperatorChanged.Stage.PAUSE
                    && operation.inputCustody() instanceof ProductionOperation.FungibleInput input
                    && !FungibleResourceCustodySupport.hasCurrentPhysicalPortion(state, input.accountId(), input.lots()))
                throw new IllegalArgumentException("station attendance awaits its current physical inputs");
        }
        if (recipe.processingMode() == ProductionRecipe.ProcessingMode.ATTENDED) {
            var previous = operation;
            var attendance = event.stage() == ProductionStationOperatorChanged.Stage.PAUSE ? Optional.<ProductionStationClock.Attendance>empty()
                    : Optional.of(new ProductionStationClock.Attendance(1_000, Math.addExact(event.atTick(), 40)));
            var next = ProductionStationClock.next(state, operation, event.atTick(), attendance);
            if (event.stage() == ProductionStationOperatorChanged.Stage.RESUME)
                operation = operation.withPlayerAttendanceIntent(ProductionOperation.PlayerAttendanceIntent.RUN_REQUESTED);
            operation = operation.withProgress(next);
            if (event.stage() == ProductionStationOperatorChanged.Stage.PAUSE)
                operation = operation.withPlayerAttendanceIntent(ProductionOperation.PlayerAttendanceIntent.PAUSED);
            machines = machines.with(machines.require(subject).replace(previous, operation));
        } else if (event.stage() != ProductionStationOperatorChanged.Stage.START) {
            throw new IllegalArgumentException("autonomous processing does not need player attendance");
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(ledger)).productionStations(machines));
    }
    private ProductionStationOperator() { }
}
