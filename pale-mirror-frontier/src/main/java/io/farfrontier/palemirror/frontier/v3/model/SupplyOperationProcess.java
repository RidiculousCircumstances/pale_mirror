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

    static ScheduledAction start(StrategicTask task, long due) {
        if (task.kind() != StrategicTaskKind.PREPARE_BREAD_CARGO) throw new IllegalArgumentException("invalid supply preparation task schedule");
        return new ScheduledAction(new ScheduleId("schedule:supply-task-start-" + task.id().value().replace(':', '-')), new SimInstant(due), 0,
                task.id(), "frontier.supply.task.start", 1);
    }

    static List<ProposedEvent> planStart(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = preparationTask(state, action.subject(), StrategicTaskStatus.PENDING);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), task.ownerId());
        if (state.humanPopulation().quarantined(settlement.id()) || !dependenciesCompleted(state, task) || bread(state, settlement).isEmpty()) {
            return blockPreparation(state, task);
        }
        SupplyContract contract = contract(state, task, settlement, bread(state, settlement).orElseThrow());
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(settlement.id(), new SupplyContractCreated(contract)),
                schedule(cargoLoad(contract, action.dueAt().ticks() + 50L)));
    }

    static List<ProposedEvent> planCargoLoad(FrontierWorldState state, ScheduledAction action) {
        return planCargoLoad(state, action, true);
    }

    static List<ProposedEvent> planCargoLoad(FrontierWorldState state, ScheduledAction action, boolean autonomousInterception) {
        SupplyContract contract = state.contracts().get(action.subject());
        if (contract == null || contract.status() != ContractStatus.ORDERED) throw new IllegalStateException("cargo load has no ordered contract");
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), contract.settlementId());
        StrategicTask preparation = preparationTaskForContract(state, contract, StrategicTaskStatus.ACTIVE);
        StrategicTask delivery = deliveryTask(state, preparation, StrategicTaskStatus.PENDING);
        if (state.humanPopulation().quarantined(settlement.id())) return blockPreparation(state, preparation);
        ExactItemStack item = state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id)).filter(value -> value.itemKind().equals(contract.itemKind())
                && value.count() == contract.itemCount() && value.custody() instanceof InventoryCustody.ContainerSlot slot
                && slot.containerId().equals(FrontierWorldState.depotId(settlement.id()))).findFirst().orElse(null);
        if (item == null || !participantsAvailable(state, settlement)) return blockPreparation(state, preparation);
        ContainerSurface surface = state.inventory().surfaces().get(FrontierWorldState.depotId(settlement.id()));
        if (surface != null && surface.status() == ContainerSurfaceStatus.ACTIVE) {
            return List.of(new ProposedEvent(contract.settlementId(), new PhysicalIntentPrepared(cargoLoadingIntent(contract, item, surface))));
        }
        return cargoLoadedEvents(state, contract, item, action.dueAt().ticks(), autonomousInterception);
    }

    /** Continues a physical active-depot cargo loading only after its exact removal receipt. */
    static List<ProposedEvent> planCargoLoadingTransition(FrontierWorldState state, PhysicalIntent intent,
                                                          PhysicalIntentTransition transition, long now) {
        CargoLoadingStateSupport.validateIntent(state, intent);
        SupplyContract contract = state.contracts().get(intent.causeSubjectId());
        ExactItemStack item = state.inventory().items().get(intent.subjectIds().get(2));
        StrategicTask preparation = preparationTaskForContract(state, contract, StrategicTaskStatus.ACTIVE);
        if (transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            return List.of(new ProposedEvent(contract.settlementId(), transition), transition(preparation, StrategicTaskStatus.BLOCKED),
                    transition(deliveryTask(state, preparation, StrategicTaskStatus.PENDING), StrategicTaskStatus.BLOCKED));
        }
        if (transition.status() != PhysicalIntentStatus.CONFIRMED) return List.of(new ProposedEvent(contract.settlementId(), transition));
        if (!(transition.observation().orElseThrow() instanceof CargoLoadObservation receipt)) {
            throw new IllegalArgumentException("cargo loading confirmation requires its exact removal receipt");
        }
        CargoLoadingStateSupport.validateReceipt(intent, receipt);
        return cargoLoadedEvents(state, contract, item, now, true, transition);
    }

    private static List<ProposedEvent> cargoLoadedEvents(FrontierWorldState state, SupplyContract contract, ExactItemStack item,
                                                          long now, boolean autonomousInterception) {
        return cargoLoadedEvents(state, contract, item, now, autonomousInterception, null);
    }

    private static List<ProposedEvent> cargoLoadedEvents(FrontierWorldState state, SupplyContract contract, ExactItemStack item,
                                                          long now, boolean autonomousInterception, PhysicalIntentTransition physicalTransition) {
        StrategicTask preparation = preparationTaskForContract(state, contract, StrategicTaskStatus.ACTIVE);
        StrategicTask delivery = deliveryTask(state, preparation, StrategicTaskStatus.PENDING);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), contract.settlementId());
        RouteOperation operation = routeOperation(state, contract, settlement);
        List<ProposedEvent> events = new ArrayList<>();
        if (physicalTransition != null) events.add(new ProposedEvent(contract.settlementId(), physicalTransition));
        events.addAll(List.of(new ProposedEvent(contract.settlementId(), new CargoLoaded(contract.id(), new CargoBatch(contract.cargoId(), contract.settlementId(), List.of(item.id())))),
                transition(preparation, StrategicTaskStatus.COMPLETED), transition(delivery, StrategicTaskStatus.ACTIVE),
                new ProposedEvent(contract.settlementId(), new OperationCreated(operation))));
        if (autonomousInterception) events.add(schedule(StrategicObjectiveProcess.interceptOpportunity(state.bootstrap().hive().id(), operation, now + 20L)));
        events.add(schedule(operationAssembly(operation, now + 20L)));
        return List.copyOf(events);
    }

    static List<ProposedEvent> planAssembly(FrontierWorldState state, ScheduledAction action) {
        RouteOperation operation = state.operations().get(action.subject());
        if (operation == null || operation.stage() != OperationStage.ASSEMBLING || operation.activeAssembly().isEmpty()) {
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        }
        OperationAssembly assembly = operation.activeAssembly().orElseThrow();
        if (assembly.complete()) {
            return List.of(new ProposedEvent(operation.settlementId(), new OperationTravelStarted(operation.id(), travelForNextSegment(state, operation))),
                    schedule(operationProgress(operation, action.dueAt().ticks() + 20L)));
        }
        java.util.Map<SubjectId, OperationAssembly.Member> advanced = new java.util.LinkedHashMap<>();
        assembly.members().forEach((actor, member) -> {
            AmbientActorLease lease = state.ambientLeases().get(actor);
            int cursor = lease == null || lease.status() == AmbientLeaseStatus.CLOSED ? member.nextColdCursor() : member.cursor();
            advanced.put(actor, new OperationAssembly.Member(member.corridor(), cursor));
        });
        OperationAssembly next = new OperationAssembly(advanced, assembly.cargoCarrierId());
        if (next.equals(assembly)) return List.of(schedule(operationAssembly(operation, action.dueAt().ticks() + 20L)));
        if (next.complete()) {
            return List.of(new ProposedEvent(operation.settlementId(), new OperationAssemblyAdvanced(operation.id(), next)),
                    new ProposedEvent(operation.settlementId(), new OperationTravelStarted(operation.id(), travelForNextSegment(operation, next.positions()))),
                    schedule(operationProgress(operation, action.dueAt().ticks() + 20L)));
        }
        return List.of(new ProposedEvent(operation.settlementId(), new OperationAssemblyAdvanced(operation.id(), next)),
                schedule(operationAssembly(operation, action.dueAt().ticks() + 20L)));
    }

    static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action) {
        RouteOperation operation = state.operations().get(action.subject());
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE) {
            // A terminal operation can retain an older persisted progress action after recovery.
            // It is not harmless to return no events: record the exact cancellation rather than
            // pretending the action never existed or allowing the kernel to quarantine.
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        }
        boolean heldAtIntercept = state.strategicPlans().routeEngagements().values().stream()
                .anyMatch(engagement -> engagement.operationId().equals(operation.id()) && engagement.status() != RouteEngagementStatus.RESOLVED
                        && operation.currentPosition().equals(engagement.intercept()));
        if (heldAtIntercept) return List.of(schedule(operationProgress(operation, action.dueAt().ticks() + 100L)));
        Optional<SceneLease> unknownLease = state.sceneLeases().values().stream().filter(value -> value.operationId().equals(operation.id())
                && value.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART).findFirst();
        if (unknownLease.isPresent()) {
            return unknownLease.orElseThrow().recoveryEvidence().isPresent()
                    ? failed(state, operation, "scene-recovery-unresolved")
                    : List.of(schedule(operationProgress(operation, action.dueAt().ticks() + 100L)));
        }
        Optional<SceneLease> lease = state.sceneLeases().values().stream().filter(value -> value.operationId().equals(operation.id())
                && value.status() != SceneLeaseStatus.CLOSED).findFirst();
        if (lease.isPresent()) return List.of(new ProposedEvent(operation.settlementId(), new OperationColdSuspended(operation.id(), lease.orElseThrow().id())));
        if (!FrontierRouteNetwork.isPassable(state.bootstrap(), operation.route(), state.physicalDeltas())) return failed(state, operation, "route-obstructed");
        if (operation.activeTravel().isEmpty() || operation.activeTravel().orElseThrow().arrived()
                && operation.activeTravel().orElseThrow().corridor().getLast().equals(operation.route().get(operation.routeIndex()))) {
            return List.of(new ProposedEvent(operation.settlementId(), new OperationTravelStarted(operation.id(), travelForNextSegment(operation, participantPositions(state, operation)))),
                    schedule(operationProgress(operation, action.dueAt().ticks() + 20L)));
        }
        OperationTravel travel = operation.activeTravel().orElseThrow();
        if (!travel.arrived()) {
            OperationTravel advanced = translateTravel(travel, travel.nextColdCursor());
            return List.of(new ProposedEvent(operation.settlementId(), new OperationTravelAdvanced(operation.id(), advanced)),
                    schedule(operationProgress(operation, action.dueAt().ticks() + 20L)));
        }
        int completedIndex = operation.routeIndex() + 1;
        List<ProposedEvent> events = new ArrayList<>(List.of(new ProposedEvent(operation.settlementId(), new OperationTravelSegmentCompleted(operation.id()))));
        if (completedIndex == operation.route().size() - 1) events.add(new ProposedEvent(operation.settlementId(), new PhysicalIntentPrepared(cargoHandoffIntent(operation))));
        else events.add(schedule(operationProgress(operation, action.dueAt().ticks() + 20L)));
        return List.copyOf(events);
    }

    static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition) {
        if (intent.kind() != PhysicalIntentKind.CARGO_HANDOFF) throw new IllegalArgumentException("supply transition has an invalid physical intent kind");
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null || operation.stage() != OperationStage.ARRIVED || !intent.subjectIds().equals(List.of(operation.id(), operation.cargoId()))) {
            throw new IllegalArgumentException("supply transition lacks its arrived route operation");
        }
        StrategicTask task = deliveryTaskForOperation(state, operation, StrategicTaskStatus.ACTIVE);
        ProposedEvent physical = new ProposedEvent(operation.settlementId(), transition);
        if (transition.status() == PhysicalIntentStatus.CONFIRMED) return List.of(physical, transition(task, StrategicTaskStatus.COMPLETED));
        if (transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return List.of(physical, transition(task, StrategicTaskStatus.BLOCKED));
        return List.of(physical);
    }

    static List<ProposedEvent> failed(FrontierWorldState state, RouteOperation operation, String reason) {
        return List.of(new ProposedEvent(operation.settlementId(), new OperationFailed(operation.id(), reason)),
                transition(deliveryTaskForOperation(state, operation, StrategicTaskStatus.ACTIVE), StrategicTaskStatus.BLOCKED));
    }

    static ScheduledAction operationProgress(RouteOperation operation, long due) { return new ScheduledAction(new ScheduleId("schedule:operation-progress-" + operation.id().value().substring("operation:".length())),
            new SimInstant(due), 0, operation.id(), "frontier.operation.progress", 1); }
    static ScheduledAction operationAssembly(RouteOperation operation, long due) { return new ScheduledAction(new ScheduleId("schedule:operation-assembly-" + operation.id().value().substring("operation:".length())),
            new SimInstant(due), 0, operation.id(), "frontier.operation.assembly", 1); }
    private static ScheduledAction cargoLoad(SupplyContract contract, long due) { return new ScheduledAction(new ScheduleId("schedule:cargo-load-" + contract.id().value().substring("contract:".length())),
            new SimInstant(due), 0, contract.id(), "frontier.supply.cargo.load", 1); }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }

    private static OperationTravel travelForNextSegment(FrontierWorldState state, RouteOperation operation) {
        return travelForNextSegment(operation, participantPositions(state, operation));
    }
    private static OperationTravel travelForNextSegment(RouteOperation operation, java.util.Map<SubjectId, BlockPosition> formation) {
        if (operation.routeIndex() >= operation.route().size() - 1) throw new IllegalArgumentException("arrived operation has no next travel segment");
        List<BlockPosition> corridor = adjacentSegment(operation.route().get(operation.routeIndex()), operation.route().get(operation.routeIndex() + 1));
        return new OperationTravel(corridor, 0, formation, formation.get(operation.cargoCarrierId()));
    }

    /**
     * The final HOT observed arrival is an authoritative assembly completion, so it starts the
     * first segment in that same canonical transaction instead of waiting for a stale COLD poll.
     */
    static OperationTravel travelForCompletedAssembly(RouteOperation operation, OperationAssembly assembly) {
        if (!assembly.complete()) throw new IllegalArgumentException("only a complete assembly may start operation travel");
        return travelForNextSegment(operation, assembly.positions());
    }
    private static java.util.Map<SubjectId, BlockPosition> participantPositions(FrontierWorldState state, RouteOperation operation) {
        java.util.Map<SubjectId, BlockPosition> formation = new java.util.LinkedHashMap<>();
        operation.participantIds().forEach(actor -> formation.put(actor, state.actorLocations().get(actor).position()));
        return java.util.Map.copyOf(formation);
    }
    private static OperationTravel translateTravel(OperationTravel travel, int nextCursor) {
        BlockPosition from = travel.currentPosition(), to = travel.corridor().get(nextCursor);
        int deltaX = to.x() - from.x(), deltaZ = to.z() - from.z(); java.util.Map<SubjectId, BlockPosition> formation = new java.util.LinkedHashMap<>();
        travel.formation().forEach((actor, position) -> formation.put(actor, position.offset(deltaX, 0, deltaZ)));
        return travel.advance(nextCursor, formation, travel.cargoAnchor().offset(deltaX, 0, deltaZ));
    }
    private static List<BlockPosition> adjacentSegment(BlockPosition from, BlockPosition to) {
        if (from.y() != to.y() || (from.x() != to.x() && from.z() != to.z())) throw new IllegalArgumentException("operation route segment must be horizontal and axis aligned");
        List<BlockPosition> corridor = new ArrayList<>(); int deltaX = Integer.compare(to.x(), from.x()), deltaZ = Integer.compare(to.z(), from.z());
        for (BlockPosition cursor = from;; cursor = cursor.offset(deltaX, 0, deltaZ)) { corridor.add(cursor); if (cursor.equals(to)) return List.copyOf(corridor); }
    }

    private static PhysicalIntent cargoLoadingIntent(SupplyContract contract, ExactItemStack item, ContainerSurface surface) {
        BlockPosition position = surface.position();
        return new PhysicalIntent(new PhysicalIntentId("intent:cargo-load-" + contract.id().value().substring("contract:".length())),
                PhysicalIntentKind.CARGO_LOADING, PhysicalIntentStatus.PREPARED, contract.id(), List.of(contract.id(), contract.cargoId(), item.id()),
                new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z())), 0,
                PhysicalPostcondition.CARGO_LOADED_FROM_DEPOT_OBSERVED);
    }
    private static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) {
        return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status));
    }
    private static List<ProposedEvent> blockPreparation(FrontierWorldState state, StrategicTask preparation) {
        return List.of(transition(preparation, StrategicTaskStatus.BLOCKED), transition(deliveryTask(state, preparation, StrategicTaskStatus.PENDING), StrategicTaskStatus.BLOCKED));
    }
    private static StrategicTask preparationTask(FrontierWorldState state, SubjectId taskId, StrategicTaskStatus status) {
        StrategicTask task = state.strategicPlans().tasks().get(taskId);
        if (task == null || task.kind() != StrategicTaskKind.PREPARE_BREAD_CARGO || task.status() != status) {
            throw new IllegalStateException("supply preparation has no matching " + status.name().toLowerCase(java.util.Locale.ROOT) + " strategic task");
        }
        return task;
    }
    private static StrategicTask preparationTaskForContract(FrontierWorldState state, SupplyContract contract, StrategicTaskStatus status) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.ownerId().equals(contract.settlementId())
                && task.kind() == StrategicTaskKind.PREPARE_BREAD_CARGO && task.status() == status && contract(state, task).id().equals(contract.id()))
                .reduce((left, right) -> { throw new IllegalArgumentException("supply contract task binding is ambiguous"); })
                .orElseThrow(() -> new IllegalArgumentException("supply contract has no active strategic task"));
    }
    static StrategicTask deliveryTaskForOperation(FrontierWorldState state, RouteOperation operation, StrategicTaskStatus status) {
        SupplyContract contract = state.contracts().values().stream().filter(value -> value.cargoId().equals(operation.cargoId())).reduce((left, right) -> {
            throw new IllegalArgumentException("supply operation cargo binding is ambiguous");
        }).orElseThrow(() -> new IllegalArgumentException("supply operation has no contract"));
        if (!contract.settlementId().equals(operation.settlementId())) throw new IllegalArgumentException("supply operation contract has a foreign owner");
        return deliveryTask(state, preparationTaskForContract(state, contract, StrategicTaskStatus.COMPLETED), status);
    }
    private static StrategicTask deliveryTask(FrontierWorldState state, StrategicTask preparation, StrategicTaskStatus status) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.kind() == StrategicTaskKind.DELIVER_BREAD_TO_HIVE
                && task.objectiveId().equals(preparation.objectiveId()) && task.status() == status && task.dependencies().equals(List.of(preparation.id())))
                .reduce((left, right) -> { throw new IllegalArgumentException("supply delivery task binding is ambiguous"); })
                .orElseThrow(() -> new IllegalArgumentException("supply preparation has no matching delivery task"));
    }
    private static Optional<ExactItemStack> bread(FrontierWorldState state, Settlement settlement) {
        SubjectId depot = FrontierWorldState.depotId(settlement.id()); return state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id))
                .filter(item -> item.itemKind().equals("minecraft:bread") && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot)).findFirst();
    }
    private static boolean participantsAvailable(FrontierWorldState state, Settlement settlement) {
        return FrontierWorldStateSupport.availableRouteResident(state, settlement.id(), ResidentRole.HAULER).isPresent()
                && FrontierWorldStateSupport.availableRouteResident(state, settlement.id(), ResidentRole.GUARD).isPresent();
    }
    private static boolean dependenciesCompleted(FrontierWorldState state, StrategicTask task) {
        return task.dependencies().stream().map(state.strategicPlans().tasks()::get).allMatch(value -> value.status() == StrategicTaskStatus.COMPLETED);
    }
    private static RouteOperation routeOperation(FrontierWorldState state, SupplyContract contract, Settlement settlement) {
        SubjectId hauler = FrontierWorldStateSupport.availableRouteResident(state, settlement.id(), ResidentRole.HAULER).orElseThrow().id();
        SubjectId guard = FrontierWorldStateSupport.availableRouteResident(state, settlement.id(), ResidentRole.GUARD).orElseThrow().id();
        int ordinal = FrontierWorldScheduleSupport.ordinal(contract.id().value());
        SettlementAccessPort access = SettlementAccessPort.forHall(settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("supply settlement lacks a Hall")));
        OperationAssembly assembly = new OperationAssembly(java.util.Map.of(
                hauler, new OperationAssembly.Member(OperationAssemblyCorridor.compile(state, hauler, access.assemblyFloor()), 0),
                guard, new OperationAssembly.Member(OperationAssemblyCorridor.compile(state, guard, access.routeFloor()), 0)), hauler);
        return new RouteOperation(new SubjectId("operation:supply-" + settlement.id().value().substring("settlement:".length()) + "-" + ordinal), settlement.id(), contract.cargoId(), contract.recipientId(),
                List.of(hauler, guard), state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id()), 0, OperationStage.ASSEMBLING, java.util.Optional.of(assembly), java.util.Optional.empty());
    }
    private static SupplyContract contract(FrontierWorldState state, StrategicTask task, Settlement settlement, ExactItemStack bread) {
        SupplyContract contract = contract(state, task);
        return new SupplyContract(contract.id(), settlement.id(), state.bootstrap().hive().id(), contract.cargoId(), bread.itemKind(), bread.count(), ContractStatus.ORDERED);
    }
    private static SupplyContract contract(FrontierWorldState state, StrategicTask task) {
        StrategicObjective objective = state.strategicPlans().objectives().get(task.objectiveId());
        if (objective == null || !objective.ownerId().equals(task.ownerId())) throw new IllegalArgumentException("supply task has no canonical objective");
        String settlement = task.ownerId().value().substring("settlement:".length()); String suffix = settlement + "-" + objective.decisionOrdinal();
        return new SupplyContract(new SubjectId("contract:supply-" + suffix), task.ownerId(), state.bootstrap().hive().id(),
                new SubjectId("cargo:supply-" + suffix), "minecraft:bread", 1, ContractStatus.ORDERED);
    }
    private static PhysicalIntent cargoHandoffIntent(RouteOperation operation) {
        BlockPosition target = operation.route().getLast(); FixedPosition origin = new FixedPosition(FixedScalar.whole(target.x()), FixedScalar.whole(target.y()), FixedScalar.whole(target.z()));
        return new PhysicalIntent(new PhysicalIntentId("intent:cargo-handoff-" + operation.id().value().substring("operation:".length())), PhysicalIntentKind.CARGO_HANDOFF,
                PhysicalIntentStatus.PREPARED, operation.id(), List.of(operation.id(), operation.cargoId()), origin, 0, PhysicalPostcondition.CARGO_HANDOFF_OBSERVED);
    }
}
