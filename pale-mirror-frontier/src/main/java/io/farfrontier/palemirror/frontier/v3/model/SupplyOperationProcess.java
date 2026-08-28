package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Executes one exact supply contract, cargo batch and COLD route operation. */
final class SupplyOperationProcess {
    private SupplyOperationProcess() { }

    static List<ProposedEvent> planDemand(FrontierWorldState state, ScheduledAction action) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), action.subject());
        ExactItemStack bread = bread(state, settlement).orElseThrow(() -> new IllegalStateException("supply demand has no exact bread output"));
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        SupplyContract contract = new SupplyContract(new SubjectId("contract:supply-" + settlement.id().value().substring("settlement:".length()) + "-" + ordinal),
                settlement.id(), state.bootstrap().hive().id(), new SubjectId("cargo:supply-" + settlement.id().value().substring("settlement:".length()) + "-" + ordinal),
                bread.itemKind(), bread.count(), ContractStatus.ORDERED);
        return List.of(new ProposedEvent(settlement.id(), new SupplyContractCreated(contract)), schedule(cargoLoad(contract, action.dueAt().ticks() + 50L)));
    }

    static List<ProposedEvent> planCargoLoad(FrontierWorldState state, ScheduledAction action) {
        SupplyContract contract = state.contracts().get(action.subject());
        if (contract == null || contract.status() != ContractStatus.ORDERED) throw new IllegalStateException("cargo load has no ordered contract");
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), contract.settlementId());
        ExactItemStack item = state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id)).filter(value -> value.itemKind().equals(contract.itemKind())
                && value.count() == contract.itemCount() && value.custody() instanceof InventoryCustody.ContainerSlot slot
                && slot.containerId().equals(FrontierWorldState.depotId(settlement.id()))).findFirst().orElseThrow(() -> new IllegalStateException("contract cargo is unavailable in its depot"));
        RouteOperation operation = routeOperation(state, contract, settlement);
        return List.of(new ProposedEvent(contract.settlementId(), new CargoLoaded(contract.id(), new CargoBatch(contract.cargoId(), contract.settlementId(), List.of(item.id())))),
                new ProposedEvent(contract.settlementId(), new OperationCreated(operation)), schedule(operationProgress(operation, action.dueAt().ticks() + 100L)));
    }

    static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action) {
        RouteOperation operation = state.operations().get(action.subject());
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE) return List.of();
        Optional<SceneLease> lease = state.sceneLeases().values().stream().filter(value -> value.operationId().equals(operation.id()) && value.status() != SceneLeaseStatus.CLOSED).findFirst();
        if (lease.isPresent()) return List.of(new ProposedEvent(operation.settlementId(), new OperationColdSuspended(operation.id(), lease.orElseThrow().id())));
        if (!FrontierRouteNetwork.isPassable(state.bootstrap(), operation.route(), state.physicalDeltas())) return List.of(new ProposedEvent(operation.settlementId(), new OperationFailed(operation.id(), "route-obstructed")));
        int index = operation.routeIndex() + 1; OperationStage stage = index == operation.route().size() - 1 ? OperationStage.ARRIVED : OperationStage.EN_ROUTE;
        List<ProposedEvent> events = new ArrayList<>(List.of(new ProposedEvent(operation.settlementId(), new OperationAdvanced(operation.id(), index, stage))));
        if (stage == OperationStage.ARRIVED) events.add(new ProposedEvent(operation.settlementId(), new PhysicalIntentPrepared(cargoHandoffIntent(operation))));
        if (stage == OperationStage.EN_ROUTE) events.add(schedule(operationProgress(operation, action.dueAt().ticks() + 100L)));
        return List.copyOf(events);
    }

    static ScheduledAction demand(SubjectId settlement, int ordinal, long due) {
        return new ScheduledAction(new ScheduleId("schedule:contract-demand-" + settlement.value().substring("settlement:".length()) + "-" + ordinal),
                new SimInstant(due), 0, settlement, "frontier.supply.contract.demand", 1);
    }
    static ScheduledAction operationProgress(RouteOperation operation, long due) { return new ScheduledAction(new ScheduleId("schedule:operation-progress-" + operation.id().value().substring("operation:".length())),
            new SimInstant(due), 0, operation.id(), "frontier.operation.progress", 1); }
    private static ScheduledAction cargoLoad(SupplyContract contract, long due) { return new ScheduledAction(new ScheduleId("schedule:cargo-load-" + contract.id().value().substring("contract:".length())),
            new SimInstant(due), 0, contract.id(), "frontier.supply.cargo.load", 1); }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    private static Optional<ExactItemStack> bread(FrontierWorldState state, Settlement settlement) {
        SubjectId depot = FrontierWorldState.depotId(settlement.id()); return state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id))
                .filter(item -> item.itemKind().equals("minecraft:bread") && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot)).findFirst();
    }
    private static RouteOperation routeOperation(FrontierWorldState state, SupplyContract contract, Settlement settlement) {
        SubjectId hauler = settlement.residents().stream().filter(value -> value.role() == ResidentRole.HAULER).sorted(Comparator.comparing(Resident::id)).findFirst().orElseThrow().id();
        SubjectId guard = settlement.residents().stream().filter(value -> value.role() == ResidentRole.GUARD).sorted(Comparator.comparing(Resident::id)).findFirst().orElseThrow().id();
        int ordinal = FrontierWorldScheduleSupport.ordinal(contract.id().value());
        return new RouteOperation(new SubjectId("operation:supply-" + settlement.id().value().substring("settlement:".length()) + "-" + ordinal), settlement.id(), contract.cargoId(), contract.recipientId(),
                List.of(hauler, guard), state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id()), 0, OperationStage.EN_ROUTE);
    }
    private static PhysicalIntent cargoHandoffIntent(RouteOperation operation) {
        BlockPosition target = operation.route().getLast(); FixedPosition origin = new FixedPosition(FixedScalar.whole(target.x()), FixedScalar.whole(target.y()), FixedScalar.whole(target.z()));
        return new PhysicalIntent(new PhysicalIntentId("intent:cargo-handoff-" + operation.id().value().substring("operation:".length())), PhysicalIntentKind.CARGO_HANDOFF,
                PhysicalIntentStatus.PREPARED, operation.id(), List.of(operation.id(), operation.cargoId()), origin, 0, PhysicalPostcondition.CARGO_HANDOFF_OBSERVED);
    }
}
