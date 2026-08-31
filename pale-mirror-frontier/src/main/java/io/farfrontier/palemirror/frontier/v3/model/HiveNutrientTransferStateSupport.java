package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;

import java.util.List;
import java.util.Map;

/** Exact custody/state transitions for the hive organ-network nutrient corridor. */
final class HiveNutrientTransferStateSupport {
    private HiveNutrientTransferStateSupport() { }

    static void validate(FrontierBootstrap bootstrap, ExactInventory inventory, HiveColony colony, StrategicPlanState strategicPlans) {
        Map<SubjectId, HiveNutrientTransfer> transfers = colony.nutrientTransfers();
        for (HiveNutrientTransfer transfer : transfers.values()) {
            validateTopology(bootstrap, inventory, colony, strategicPlans, transfer);
            ExactItemStack item = inventory.items().get(transfer.itemId()); CargoBatch cargo = inventory.cargo().get(transfer.cargoId());
            boolean cargoPhase = transfer.phase() == HiveNutrientTransferPhase.IN_TRANSIT || transfer.phase() == HiveNutrientTransferPhase.ARRIVAL_PENDING || transfer.phase() == HiveNutrientTransferPhase.BLOCKED;
            if (transfer.phase() == HiveNutrientTransferPhase.DEPARTURE_PENDING) {
                if (item == null || !item.custody().equals(transfer.sourceSlot()) || cargo != null || !item.economicOwnerId().equals(transfer.hiveId()))
                    throw new IllegalArgumentException("pending hive nutrient departure must retain its exact source item");
            } else if (cargoPhase && transfer.phase() != HiveNutrientTransferPhase.BLOCKED || transfer.phase() == HiveNutrientTransferPhase.BLOCKED && cargo != null) {
                if (item == null || cargo == null || !cargo.itemIds().equals(List.of(item.id())) || !cargo.ownerId().equals(transfer.hiveId())
                        || !item.custody().equals(new InventoryCustody.Cargo(cargo.id())) || !item.economicOwnerId().equals(transfer.hiveId()))
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
        validateTopology(state.bootstrap(), state.inventory(), state.hiveColony(), state.strategicPlans(), transfer);
        ExactItemStack item = state.inventory().items().get(transfer.itemId());
        if (item == null || !item.custody().equals(transfer.sourceSlot()) || !item.economicOwnerId().equals(transfer.hiveId())) {
            throw new IllegalArgumentException("hive nutrient departure has no exact source item");
        }
        boolean physicalDeparture = state.inventory().surfaces().get(transfer.sourceStoreId()).status() != ContainerSurfaceStatus.UNMATERIALIZED;
        ExactInventory inventory = physicalDeparture ? state.inventory() : state.inventory().loadCargo(new CargoBatch(transfer.cargoId(), transfer.hiveId(), List.of(transfer.itemId())));
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), inventory, state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().startNutrientTransfer(transfer), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState advance(FrontierWorldState state, SubjectId transferId, int cursor) {
        HiveNutrientTransfer transfer = requireTransit(state, transferId); validateTransit(state, transfer);
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().advanceNutrientTransfer(transferId, cursor), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState complete(FrontierWorldState state, HiveNutrientReceipt receipt) {
        HiveNutrientTransfer transfer = requireTransit(state, receipt.transferId()); validateTransit(state, transfer);
        if (!receipt.matches(transfer) || transfer.cursor() != transfer.corridor().size() - 1 || state.inventory().itemAt(transfer.targetStoreId(), transfer.targetSlot().slot()).isPresent()) {
            throw new IllegalArgumentException("hive nutrient arrival does not match its retained corridor or target slot");
        }
        ExactInventory inventory = state.inventory().completeCargoHandoff(transfer.cargoId(), List.of(new CargoHandoffPlacement(transfer.itemId(), transfer.targetSlot())));
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), inventory, state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().completeNutrientTransfer(receipt), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState block(FrontierWorldState state, SubjectId transferId, HiveNutrientTransferBlockReason reason) {
        requireUnblocked(state, transferId);
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().blockNutrientTransfer(transferId, reason), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static HiveNutrientTransfer requireUnblocked(FrontierWorldState state, SubjectId transferId) {
        HiveNutrientTransfer transfer = state.hiveColony().nutrientTransfers().get(transferId);
        if (transfer == null || transfer.phase() == HiveNutrientTransferPhase.BLOCKED) throw new IllegalArgumentException("hive nutrient transfer is not active");
        return transfer;
    }

    static HiveNutrientTransfer requireTransit(FrontierWorldState state, SubjectId transferId) {
        HiveNutrientTransfer transfer = requireUnblocked(state, transferId);
        if (transfer.phase() != HiveNutrientTransferPhase.IN_TRANSIT) throw new IllegalArgumentException("hive nutrient transfer is not in transit");
        return transfer;
    }

    static boolean ownsIntentSubject(HiveColony colony, PhysicalIntent intent, SubjectId subject) {
        return isEndpointIntent(intent) && colony.nutrientTransfers().values().stream()
                .anyMatch(transfer -> subject.equals(transfer.id()) || subject.equals(transfer.cargoId()) || subject.equals(transfer.itemId()));
    }

    static boolean isEndpointIntent(PhysicalIntent intent) {
        return intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE || intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL;
    }

    static void validateTransit(FrontierWorldState state, HiveNutrientTransfer transfer) {
        validateTopology(state.bootstrap(), state.inventory(), state.hiveColony(), state.strategicPlans(), transfer);
        if (state.inventory().itemAt(transfer.targetStoreId(), transfer.targetSlot().slot()).isPresent()) {
            throw new IllegalArgumentException("hive nutrient transfer target slot is no longer available");
        }
    }

    static PhysicalIntent departureIntent(FrontierWorldState state, HiveNutrientTransfer transfer) {
        if (transfer.phase() != HiveNutrientTransferPhase.DEPARTURE_PENDING) throw new IllegalArgumentException("hive nutrient departure is not pending");
        return endpointIntent(transfer, transfer.sourceStoreId(), transfer.endpointIntentId().orElseThrow(), PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE,
                PhysicalPostcondition.HIVE_NUTRIENT_DEPARTED_OBSERVED, state.inventory().surfaces().get(transfer.sourceStoreId()).position());
    }

    static PhysicalIntent arrivalIntent(FrontierWorldState state, HiveNutrientTransfer transfer) {
        if (transfer.phase() != HiveNutrientTransferPhase.ARRIVAL_PENDING) throw new IllegalArgumentException("hive nutrient arrival is not pending");
        return endpointIntent(transfer, transfer.targetStoreId(), transfer.endpointIntentId().orElseThrow(), PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL,
                PhysicalPostcondition.HIVE_NUTRIENT_ARRIVED_OBSERVED, state.inventory().surfaces().get(transfer.targetStoreId()).position());
    }

    static FrontierWorldState completePhysicalDeparture(FrontierWorldState state, PhysicalIntent intent, HiveNutrientDepartureObservation observed,
                                                        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        HiveNutrientTransfer transfer = requireUnblocked(state, observed.transferId());
        ExactItemStack item = state.inventory().items().get(observed.itemId());
        if (transfer.phase() != HiveNutrientTransferPhase.DEPARTURE_PENDING || !transfer.endpointIntentId().equals(java.util.Optional.of(intent.id()))
                || !observed.intentId().equals(intent.id()) || !transfer.cargoId().equals(observed.cargoId()) || !transfer.itemId().equals(observed.itemId())
                || item == null || item.count() != observed.itemCount() || !item.custody().equals(transfer.sourceSlot())) {
            throw new IllegalArgumentException("hive nutrient departure observation does not match its retained source claim");
        }
        intents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(observed.id())));
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new java.util.LinkedHashMap<>(state.physicalObservations()); observations.put(observed.id(), observed);
        ExactInventory inventory = state.inventory().loadCargo(new CargoBatch(transfer.cargoId(), transfer.hiveId(), List.of(transfer.itemId())));
        HiveColony colony = state.hiveColony().advanceNutrientTransferState(transfer.id(), transfer.departed());
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), inventory, state.productionJobs(), state.contracts(), state.operations(), intents,
                observations, state.sceneLeases(), colony, state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState completePhysicalArrival(FrontierWorldState state, PhysicalIntent intent, HiveNutrientArrivalObservation observed,
                                                      Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        HiveNutrientTransfer transfer = requireUnblocked(state, observed.transferId());
        ExactItemStack item = state.inventory().items().get(observed.itemId());
        if (transfer.phase() != HiveNutrientTransferPhase.ARRIVAL_PENDING || !transfer.endpointIntentId().equals(java.util.Optional.of(intent.id()))
                || !observed.intentId().equals(intent.id()) || !transfer.cargoId().equals(observed.cargoId()) || !transfer.itemId().equals(observed.itemId())
                || item == null || item.count() != observed.itemCount() || !item.custody().equals(new InventoryCustody.Cargo(transfer.cargoId()))) {
            throw new IllegalArgumentException("hive nutrient arrival observation does not match its retained cargo");
        }
        HiveNutrientReceipt receipt = new HiveNutrientReceipt(transfer.id(), transfer.hiveId(), transfer.cargoId(), transfer.itemId(), transfer.sourceSlot(), transfer.targetSlot());
        intents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(observed.id())));
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new java.util.LinkedHashMap<>(state.physicalObservations()); observations.put(observed.id(), observed);
        ExactInventory inventory = state.inventory().completeCargoHandoff(transfer.cargoId(), List.of(new CargoHandoffPlacement(transfer.itemId(), transfer.targetSlot())));
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), inventory, state.productionJobs(), state.contracts(), state.operations(), intents,
                observations, state.sceneLeases(), state.hiveColony().completeNutrientTransfer(receipt), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState completeEndpoint(FrontierWorldState state, PhysicalIntent intent, PhysicalEffectObservation evidence,
                                               Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        if (intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE) {
            if (!(evidence instanceof HiveNutrientDepartureObservation departure)) throw new IllegalArgumentException("hive nutrient departure requires exact source removal evidence");
            return completePhysicalDeparture(state, intent, departure, intents);
        }
        if (intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL) {
            if (!(evidence instanceof HiveNutrientArrivalObservation arrival)) throw new IllegalArgumentException("hive nutrient arrival requires exact target insertion evidence");
            return completePhysicalArrival(state, intent, arrival, intents);
        }
        throw new IllegalArgumentException("physical intent is not a hive nutrient endpoint");
    }

    static FrontierWorldState unknownEndpoint(FrontierWorldState state, PhysicalIntent intent,
                                              Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        HiveNutrientTransfer transfer = state.hiveColony().nutrientTransfers().values().stream()
                .filter(value -> value.endpointIntentId().equals(java.util.Optional.of(intent.id()))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("hive endpoint intent has no transfer"));
        intents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty()));
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), state.operations(), intents,
                state.physicalObservations(), state.sceneLeases(), state.hiveColony().blockNutrientTransfer(transfer.id(), HiveNutrientTransferBlockReason.CARGO_CUSTODY_LOST),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    private static PhysicalIntent endpointIntent(HiveNutrientTransfer transfer, SubjectId endpoint, PhysicalIntentId intentId,
                                                  PhysicalIntentKind kind, PhysicalPostcondition postcondition, BlockPosition position) {
        return new PhysicalIntent(intentId, kind, PhysicalIntentStatus.PREPARED, transfer.hiveId(), List.of(transfer.id(), transfer.cargoId(), transfer.itemId()),
                new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z())), 0, postcondition);
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
