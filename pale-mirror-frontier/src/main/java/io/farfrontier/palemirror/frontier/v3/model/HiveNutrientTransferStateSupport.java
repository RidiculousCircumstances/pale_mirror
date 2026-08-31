package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Map;

/** Exact custody/state transitions for the hive organ-network nutrient corridor. */
final class HiveNutrientTransferStateSupport {
    private HiveNutrientTransferStateSupport() { }

    static void validate(FrontierBootstrap bootstrap, ExactInventory inventory, HiveColony colony, StrategicPlanState strategicPlans) {
        Map<SubjectId, HiveNutrientTransfer> transfers = colony.nutrientTransfers();
        for (HiveNutrientTransfer transfer : transfers.values()) {
            validateTopology(bootstrap, inventory, colony, strategicPlans, transfer);
            ExactItemStack item = inventory.items().get(transfer.itemId());
            CargoBatch cargo = inventory.cargo().get(transfer.cargoId());
            if (item == null || cargo == null || !cargo.itemIds().equals(List.of(item.id())) || !cargo.ownerId().equals(transfer.hiveId())
                    || !item.custody().equals(new InventoryCustody.Cargo(cargo.id())) || !item.economicOwnerId().equals(transfer.hiveId())) {
                throw new IllegalArgumentException("hive nutrient transfer must retain its one exact cargo item");
            }
        }
        for (HiveNutrientReceipt receipt : colony.nutrientReceipts().values()) {
            if (!receipt.hiveId().equals(bootstrap.hive().id()) || transfers.containsKey(receipt.transferId())) {
                throw new IllegalArgumentException("hive nutrient receipt has a foreign or still-active transfer");
            }
            ExactItemStack item = inventory.items().get(receipt.itemId());
            if (receipt.status() == HiveNutrientReceiptStatus.STORED
                    && (item == null || !item.economicOwnerId().equals(receipt.hiveId()) || !item.custody().equals(receipt.targetSlot()))) {
                throw new IllegalArgumentException("stored hive nutrient receipt must retain its exact delivered item once");
            }
            if (receipt.status() == HiveNutrientReceiptStatus.CONSUMED && item != null) {
                throw new IllegalArgumentException("consumed hive nutrient receipt must retain its exact local growth provenance");
            }
            if (inventory.cargo().containsKey(receipt.cargoId())) throw new IllegalArgumentException("hive nutrient receipt cargo must be terminal");
        }
    }

    static FrontierWorldState start(FrontierWorldState state, HiveNutrientTransfer transfer) {
        validateColdEndpoints(state, transfer);
        ExactItemStack item = state.inventory().items().get(transfer.itemId());
        if (item == null || !item.custody().equals(transfer.sourceSlot()) || !item.economicOwnerId().equals(transfer.hiveId())) {
            throw new IllegalArgumentException("hive nutrient departure has no exact source item");
        }
        ExactInventory inventory = state.inventory().loadCargo(new CargoBatch(transfer.cargoId(), transfer.hiveId(), List.of(transfer.itemId())));
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), inventory, state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().startNutrientTransfer(transfer), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState advance(FrontierWorldState state, SubjectId transferId, int cursor) {
        HiveNutrientTransfer transfer = requireActive(state, transferId); validateColdEndpoints(state, transfer);
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().advanceNutrientTransfer(transferId, cursor), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState complete(FrontierWorldState state, HiveNutrientReceipt receipt) {
        HiveNutrientTransfer transfer = requireActive(state, receipt.transferId()); validateColdEndpoints(state, transfer);
        if (!receipt.matches(transfer) || transfer.cursor() != transfer.corridor().size() - 1 || state.inventory().itemAt(transfer.targetStoreId(), transfer.targetSlot().slot()).isPresent()) {
            throw new IllegalArgumentException("hive nutrient arrival does not match its retained corridor or target slot");
        }
        ExactInventory inventory = state.inventory().completeCargoHandoff(transfer.cargoId(), List.of(new CargoHandoffPlacement(transfer.itemId(), transfer.targetSlot())));
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), inventory, state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().completeNutrientTransfer(receipt), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState block(FrontierWorldState state, SubjectId transferId, HiveNutrientTransferBlockReason reason) {
        requireActive(state, transferId);
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().blockNutrientTransfer(transferId, reason), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static HiveNutrientTransfer requireActive(FrontierWorldState state, SubjectId transferId) {
        HiveNutrientTransfer transfer = state.hiveColony().nutrientTransfers().get(transferId);
        if (transfer == null || transfer.phase() != HiveNutrientTransferPhase.IN_TRANSIT) throw new IllegalArgumentException("hive nutrient transfer is not in transit");
        return transfer;
    }

    static void validateColdEndpoints(FrontierWorldState state, HiveNutrientTransfer transfer) {
        validateTopology(state.bootstrap(), state.inventory(), state.hiveColony(), state.strategicPlans(), transfer);
        if (state.inventory().surfaces().get(transfer.sourceStoreId()).status() != ContainerSurfaceStatus.UNMATERIALIZED
                || state.inventory().surfaces().get(transfer.targetStoreId()).status() != ContainerSurfaceStatus.UNMATERIALIZED) {
            throw new IllegalArgumentException("COLD hive nutrient transfer cannot cross a materialized organ endpoint");
        }
        if (state.inventory().itemAt(transfer.targetStoreId(), transfer.targetSlot().slot()).isPresent()) {
            throw new IllegalArgumentException("hive nutrient transfer target slot is no longer available");
        }
    }

    private static void validateTopology(FrontierBootstrap bootstrap, ExactInventory inventory, HiveColony colony, StrategicPlanState strategicPlans, HiveNutrientTransfer transfer) {
        java.util.stream.Stream<HiveOrgan> organs = java.util.stream.Stream.concat(bootstrap.hive().organs().stream(), colony.addedOrgans().values().stream());
        List<HiveOrgan> allOrgans = organs.toList();
        HiveOrgan sourceOrgan = allOrgans.stream().filter(organ -> organ.kind() == HiveOrganKind.STORE && organ.containerId().equals(java.util.Optional.of(transfer.sourceStoreId()))).findFirst().orElse(null);
        HiveOrgan targetOrgan = allOrgans.stream().filter(organ -> organ.kind() == HiveOrganKind.STORE && organ.containerId().equals(java.util.Optional.of(transfer.targetStoreId()))).findFirst().orElse(null);
        if (!transfer.hiveId().equals(bootstrap.hive().id()) || !strategicPlans.tasks().containsKey(transfer.requesterTaskId())
                || sourceOrgan == null || targetOrgan == null) {
            throw new IllegalArgumentException("hive nutrient transfer has a foreign organ-network endpoint");
        }
        HiveNest sourceNest = bootstrap.hive().seedNests().stream().filter(nest -> nest.id().equals(sourceOrgan.nestId())).findFirst().orElseThrow();
        HiveNest targetNest = bootstrap.hive().seedNests().stream().filter(nest -> nest.id().equals(targetOrgan.nestId())).findFirst().orElseThrow();
        if (sourceNest.id().equals(targetNest.id()) || !inventory.surfaces().get(transfer.sourceStoreId()).position().equals(transfer.corridor().getFirst())
                || !inventory.surfaces().get(transfer.targetStoreId()).position().equals(transfer.corridor().getLast())) {
            throw new IllegalArgumentException("hive nutrient transfer must cross one exact inter-nest corridor");
        }
    }

}
