package io.farfrontier.palemirror.frontier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic daily order: local production and consumption, then bounded route clearing. */
final class FrontierSimulationEngine {
    private final FrontierHiveSimulation hives = new FrontierHiveSimulation();
    private final FrontierAssaultSimulation assaults = new FrontierAssaultSimulation();
    private final FrontierFieldOperationSimulation fieldOperations = new FrontierFieldOperationSimulation();
    private final FrontierCampaignSimulation campaigns = new FrontierCampaignSimulation();
    private final FrontierSettlementPolicy settlementPolicy = new FrontierSettlementPolicy();
    List<FrontierEvent> advance(FrontierWorldState state, String causationId) {
        state.advanceDay();
        List<FrontierEvent> events = new ArrayList<>();
        // The Python reference observes ecosystem regeneration before hive planning.  Keep that
        // ordering explicit: human production cannot accidentally become hive substrate later in
        // this daily pass.
        state.ecology().regenerate();
        for (FrontierOperation operation : state.operations().stream().sorted(Comparator.comparing(FrontierOperation::id)).toList()) {
            if (state.reconcileOperation(operation)) {
                events.add(state.event(FrontierEvent.Type.OPERATION_STATE_CHANGED, operation.id(), causationId));
            }
        }
        for (FrontierCargo shipment : state.cargo().stream().sorted(Comparator.comparing(FrontierCargo::id)).toList()) {
            if (shipment.dispatchedDay() < state.day()) {
                state.settlement(shipment.destinationSettlementId()).orElseThrow().addStock(shipment.resource(), shipment.amount());
                state.removeCargo(shipment.id());
                events.add(state.event(FrontierEvent.Type.CARGO_DELIVERED, shipment.id(), causationId));
            }
        }
        // Existing directed field actions act on the previous day's available bodies. Brood
        // production follows them, so a newborn cannot be spent on a raid in the same canonical
        // day merely because the iteration order happened to put hives first.
        assaults.advance(state, causationId, events);
        fieldOperations.advance(state, causationId, events);
        hives.advance(state, causationId, events);
        campaigns.advance(state, causationId, events);
        // Civilian policy sees the day's actual raids and losses before it sets the food issue.
        // It is a state transition, not a Narrator cue, and its output remains visible in every
        // settlement label through the immutable projection.
        settlementPolicy.advance(state, causationId, events);
        for (FrontierSettlement settlement : state.settlements().stream().sorted(Comparator.comparing(FrontierSettlement::id)).toList()) {
            int farmers = roleCount(state, settlement, FrontierResidentRole.FARMER);
            int miners = roleCount(state, settlement, FrontierResidentRole.MINER);
            int foresters = roleCount(state, settlement, FrontierResidentRole.FORESTER);
            int engineers = roleCount(state, settlement, FrontierResidentRole.ENGINEER);
            int medics = roleCount(state, settlement, FrontierResidentRole.MEDIC);
            if (running(state, settlement.id(), FrontierOperationKind.FARMING)) {
                settlement.addStock(FrontierResource.FOOD, farmers * 2L + Math.max(1, state.alivePopulation(settlement.id()) / 12));
                events.add(state.event(FrontierEvent.Type.RESOURCE_PRODUCED, operationId(state, settlement.id(), FrontierOperationKind.FARMING), causationId));
            }
            if (running(state, settlement.id(), FrontierOperationKind.MINING) && miners > 0) {
                settlement.addStock(FrontierResource.ORE, miners);
                events.add(state.event(FrontierEvent.Type.RESOURCE_PRODUCED, operationId(state, settlement.id(), FrontierOperationKind.MINING), causationId));
            }
            if (running(state, settlement.id(), FrontierOperationKind.FORESTRY) && foresters > 0) {
                settlement.addStock(FrontierResource.WOOD, foresters);
                events.add(state.event(FrontierEvent.Type.RESOURCE_PRODUCED, operationId(state, settlement.id(), FrontierOperationKind.FORESTRY), causationId));
            }
            if (running(state, settlement.id(), FrontierOperationKind.POWER_GENERATION) && engineers > 0) {
                settlement.addStock(FrontierResource.POWER, engineers * 2L);
                events.add(state.event(FrontierEvent.Type.RESOURCE_PRODUCED, operationId(state, settlement.id(), FrontierOperationKind.POWER_GENERATION), causationId));
            }
            long workshopBatch = Math.min(engineers, Math.min(settlement.stock(FrontierResource.ORE),
                    Math.min(settlement.stock(FrontierResource.WOOD), settlement.stock(FrontierResource.POWER))));
            if (running(state, settlement.id(), FrontierOperationKind.CRAFTING) && workshopBatch > 0
                    && settlement.removeStock(FrontierResource.ORE, workshopBatch)
                    && settlement.removeStock(FrontierResource.WOOD, workshopBatch)
                    && settlement.removeStock(FrontierResource.POWER, workshopBatch)) {
                settlement.addStock(FrontierResource.WEAPONS, workshopBatch);
                settlement.addStock(FrontierResource.AMMO, workshopBatch * 4L);
                events.add(state.event(FrontierEvent.Type.RESOURCE_PRODUCED, operationId(state, settlement.id(), FrontierOperationKind.CRAFTING), causationId));
            }
            long clinicBatch = Math.min(medics, Math.min(settlement.stock(FrontierResource.FOOD) / 2L,
                    settlement.stock(FrontierResource.POWER)));
            if (running(state, settlement.id(), FrontierOperationKind.MEDICAL) && clinicBatch > 0
                    && settlement.removeStock(FrontierResource.FOOD, clinicBatch * 2L)
                    && settlement.removeStock(FrontierResource.POWER, clinicBatch)) {
                settlement.addStock(FrontierResource.MEDICINE, clinicBatch);
                events.add(state.event(FrontierEvent.Type.RESOURCE_PRODUCED, operationId(state, settlement.id(), FrontierOperationKind.MEDICAL), causationId));
            }
            long foodNeed = FrontierBalance.civilianFoodNeed(state.alivePopulation(settlement.id()));
            long rationedNeed = Math.max(1, (foodNeed * settlement.rationPermille() + 999L) / 1_000L);
            long issued = Math.min(rationedNeed, settlement.stock(FrontierResource.FOOD));
            settlement.removeStock(FrontierResource.FOOD, issued);
            if (issued < rationedNeed) {
                events.add(state.event(FrontierEvent.Type.FOOD_SHORTAGE, settlement.id(), causationId));
            }
        }
        FrontierMarket.clear(state, causationId, events);
        state.assertCreditBalanced();
        events.add(state.event(FrontierEvent.Type.DAY_ADVANCED, state.profile().id(), causationId));
        return List.copyOf(events);
    }

    private static int roleCount(FrontierWorldState state, FrontierSettlement settlement, FrontierResidentRole role) {
        return (int) settlement.residentIds().stream().map(state::resident).flatMap(java.util.Optional::stream)
                .filter(FrontierResident::alive).filter(value -> value.role() == role).count();
    }
    private static boolean running(FrontierWorldState state, String settlementId, FrontierOperationKind kind) {
        return state.operations().stream().anyMatch(value -> value.settlementId().equals(settlementId)
                && value.kind() == kind && value.state() == FrontierOperation.State.RUNNING);
    }
    private static String operationId(FrontierWorldState state, String settlementId, FrontierOperationKind kind) {
        return state.operations().stream().filter(value -> value.settlementId().equals(settlementId) && value.kind() == kind)
                .map(FrontierOperation::id).findFirst().orElseThrow();
    }
}
