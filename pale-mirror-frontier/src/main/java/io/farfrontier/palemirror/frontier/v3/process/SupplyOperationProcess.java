package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

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
public final class SupplyOperationProcess {
    private SupplyOperationProcess() { }

    public static ScheduledAction start(StrategicTask task, long due) {
        if (task.kind() != StrategicTaskKind.PREPARE_BREAD_CARGO) throw new IllegalArgumentException("invalid supply preparation task schedule");
        return new ScheduledAction(new ScheduleId("schedule:supply-task-start-" + task.id().value().replace(':', '-')), new SimInstant(due), 0,
                task.id(), "frontier.supply.task.start", 1);
    }

    public static List<ProposedEvent> planStart(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = preparationTask(state, action.subject(), StrategicTaskStatus.PENDING);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), task.ownerId());
        if (state.humanPopulation().quarantined(settlement.id()) || !dependenciesCompleted(state, task) || bread(state, settlement).isEmpty()
                || !participantsAvailable(state, settlement)) {
            return blockPreparation(state, task);
        }
        SupplyContract contract = contract(state, task, settlement, bread(state, settlement).orElseThrow());
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(settlement.id(), new SupplyContractCreated(contract)),
                schedule(cargoLoad(contract, action.dueAt().ticks() + 50L)));
    }

    public static List<ProposedEvent> planCargoLoad(FrontierWorldState state, ScheduledAction action) {
        return planCargoLoad(state, action, true);
    }

    public static List<ProposedEvent> planCargoLoad(FrontierWorldState state, ScheduledAction action, boolean autonomousInterception) {
        SupplyContract contract = state.contracts().get(action.subject());
        if (contract == null || contract.status() != ContractStatus.ORDERED) throw new IllegalStateException("cargo load has no ordered contract");
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), contract.settlementId());
        StrategicTask preparation = preparationTaskForContract(state, contract, StrategicTaskStatus.ACTIVE);
        StrategicTask delivery = deliveryTask(state, preparation, StrategicTaskStatus.PENDING);
        if (state.humanPopulation().quarantined(settlement.id())) return abandonPreparation(state, preparation, contract);
        ExactItemStack item = state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id)).filter(value -> value.itemKind().equals(contract.itemKind())
                && value.count() == contract.itemCount() && value.custody() instanceof InventoryCustody.ContainerSlot slot
                && slot.containerId().equals(FrontierWorldState.depotId(settlement.id()))).findFirst().orElse(null);
        if (item == null || !participantsAvailable(state, settlement)) return abandonPreparation(state, preparation, contract);
        ContainerSurface surface = state.inventory().surfaces().get(FrontierWorldState.depotId(settlement.id()));
        if (surface != null && surface.status() == ContainerSurfaceStatus.ACTIVE) {
            return List.of(new ProposedEvent(contract.settlementId(), new PhysicalIntentPrepared(cargoLoadingIntent(contract, item, surface))));
        }
        return cargoLoadedEvents(state, contract, item, action.dueAt().ticks(), autonomousInterception);
    }

    /** Continues a physical active-depot cargo loading only after its exact removal receipt. */
    public static List<ProposedEvent> planCargoLoadingTransition(FrontierWorldState state, PhysicalIntent intent,
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
        // Cargo loading is a settlement fact. It is not an observation by the hive; an
        // explicit scout/perception slice must create any future intercept opportunity.
        events.add(schedule(operationAssembly(operation, now + 20L)));
        return List.copyOf(events);
    }

    public static List<ProposedEvent> planAssembly(FrontierWorldState state, ScheduledAction action) {
        RouteOperation operation = state.operations().get(action.subject());
        if (operation == null || operation.stage() != OperationStage.ASSEMBLING || operation.activeAssembly().isEmpty()) {
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        }
        OperationAssembly assembly = operation.activeAssembly().orElseThrow();
        // The durable HOT observation owns recovery.  COLD retains only a sparse, bounded
        // recheck so an unloaded world never becomes a busy poll or silently skips the block.
        if (assembly.deferral().isPresent()) return List.of(schedule(operationAssembly(operation, action.dueAt().ticks() + 100L)));
        if (assembly.complete()) {
            return List.of(new ProposedEvent(operation.settlementId(), new OperationTravelStarted(operation.id(), travelForNextSegment(state, operation))),
                    schedule(operationProgress(operation, action.dueAt().ticks() + 20L)));
        }
        // A formation may have more than the historical hauler/guard pair.  Advance exactly
        // one COLD member one adjacent cell per turn, retaining the same deterministic order;
        // this prevents two independently compiled approaches from passing through the same
        // canonical floor in one transaction. HOT members keep their durable observed cursor.
        OperationAssembly next = assembly.safeAdvances().stream().map(actor -> {
            AmbientActorLease lease = state.ambientLeases().get(actor);
            return lease != null && lease.status() != AmbientLeaseStatus.CLOSED ? null : assembly.advance(actor);
        }).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (next == null) return List.of(schedule(operationAssembly(operation, action.dueAt().ticks() + 20L)));
        if (next.complete()) {
            return List.of(new ProposedEvent(operation.settlementId(), new OperationAssemblyAdvanced(operation.id(), next)),
                    new ProposedEvent(operation.settlementId(), new OperationTravelStarted(operation.id(), travelForCompletedAssembly(state, operation, next))),
                    schedule(operationProgress(operation, action.dueAt().ticks() + 20L)));
        }
        return List.of(new ProposedEvent(operation.settlementId(), new OperationAssemblyAdvanced(operation.id(), next)),
                schedule(operationAssembly(operation, action.dueAt().ticks() + 20L)));
    }

    public static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action) {
        RouteOperation operation = state.operations().get(action.subject());
        if (operation != null && operation.stage() == OperationStage.ARRIVED
                && contractForOperation(state, operation).status() == ContractStatus.DELIVERED) {
            return List.of(new ProposedEvent(operation.settlementId(), new OperationTravelStarted(operation.id(), travelForNextSegment(state, operation))),
                    schedule(operationProgress(operation, Math.addExact(action.dueAt().ticks(), 20L))));
        }
        if (operation != null && operation.stage() == OperationStage.ARRIVED) {
            List<ProposedEvent> arrival = arrivalEvents(state, operation, action.dueAt().ticks());
            return arrival.isEmpty() ? List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id()))) : arrival;
        }
        if (operation == null || (operation.stage() != OperationStage.EN_ROUTE && operation.stage() != OperationStage.RETURNING)) {
            // A terminal operation can retain an older persisted progress action after recovery.
            // It is not harmless to return no events: record the exact cancellation rather than
            // pretending the action never existed or allowing the kernel to quarantine.
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        }
        boolean heldAtIntercept = operation.stage() == OperationStage.EN_ROUTE && state.strategicPlans().routeEngagements().values().stream()
                .anyMatch(engagement -> engagement.operationId().equals(operation.id()) && engagement.status() != RouteEngagementStatus.RESOLVED
                        && operation.activeTravel().map(travel -> travel.cargoAnchor().surface().support().equals(engagement.intercept()))
                        .orElseGet(() -> operation.currentPosition().equals(engagement.intercept())));
        if (heldAtIntercept) return List.of(schedule(operationProgress(operation, action.dueAt().ticks() + 100L)));
        Optional<SceneLease> unknownLease = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isLogistics).filter(value -> FrontierSceneBehaviors.logistics(value).operationId().equals(operation.id())
                && value.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART).findFirst();
        if (unknownLease.isPresent()) {
            return unknownLease.orElseThrow().recoveryEvidence().isPresent()
                    ? failed(state, operation, "scene-recovery-unresolved")
                    : List.of(schedule(operationProgress(operation, action.dueAt().ticks() + 100L)));
        }
        Optional<SceneLease> lease = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isLogistics).filter(value -> FrontierSceneBehaviors.logistics(value).operationId().equals(operation.id())
                && value.status() != SceneLeaseStatus.CLOSED).findFirst();
        if (lease.isPresent()) return List.of(new ProposedEvent(operation.settlementId(), new OperationColdSuspended(operation.id(), lease.orElseThrow().id())));
        if (!state.routeTopology().supplyPassable(state.bootstrap(), operation.settlementId())) {
            return failed(state, operation, "route-obstructed", action.dueAt().ticks());
        }
        if (operation.activeTravel().isEmpty() || operation.activeTravel().orElseThrow().arrived()
                && operation.activeTravel().orElseThrow().corridor().getLast().equals(operation.route().get(operation.routeIndex()))) {
            return List.of(new ProposedEvent(operation.settlementId(), new OperationTravelStarted(operation.id(), travelForNextSegment(state, operation))),
                    schedule(operationProgress(operation, action.dueAt().ticks() + 20L)));
        }
        OperationTravel travel = operation.activeTravel().orElseThrow();
        if (!travel.arrived()) {
            if (!travel.canAdvanceNextEdge()) return failed(state, operation, "route-obstructed", action.dueAt().ticks());
            OperationTravel advanced = translateTravel(travel, travel.nextColdCursor());
            return List.of(new ProposedEvent(operation.settlementId(), new OperationTravelAdvanced(operation.id(), advanced)),
                    schedule(operationProgress(operation, action.dueAt().ticks() + 20L)));
        }
        int completedIndex = operation.stage() == OperationStage.RETURNING ? operation.routeIndex() - 1 : operation.routeIndex() + 1;
        List<ProposedEvent> events = new ArrayList<>(List.of(new ProposedEvent(operation.settlementId(), new OperationTravelSegmentCompleted(operation.id()))));
        if (operation.stage() == OperationStage.RETURNING) {
            if (completedIndex > 0) events.add(schedule(operationProgress(operation, action.dueAt().ticks() + 20L)));
        } else if (completedIndex == operation.route().size() - 1) {
            events.addAll(arrivalEvents(state, operation, action.dueAt().ticks()));
        }
        else events.add(schedule(operationProgress(operation, action.dueAt().ticks() + 20L)));
        return List.copyOf(events);
    }

    static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition, long now) {
        if (intent.kind() != PhysicalIntentKind.CARGO_HANDOFF) throw new IllegalArgumentException("supply transition has an invalid physical intent kind");
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null || operation.stage() != OperationStage.ARRIVED || !intent.subjectIds().equals(List.of(operation.id(), operation.cargoId()))) {
            throw new IllegalArgumentException("supply transition lacks its arrived route operation");
        }
        StrategicTask task = deliveryTaskForOperation(state, operation, StrategicTaskStatus.ACTIVE);
        ProposedEvent physical = new ProposedEvent(operation.settlementId(), transition);
        if (transition.status() == PhysicalIntentStatus.CONFIRMED) return List.of(physical, transition(task, StrategicTaskStatus.COMPLETED),
                schedule(operationProgress(operation, Math.addExact(now, 20L))));
        if (transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return List.of(physical, transition(task, StrategicTaskStatus.BLOCKED));
        return List.of(physical);
    }

    public static FrontierWorldState reduceDelivered(FrontierWorldState state, SubjectId subject, CargoDelivered delivered) {
        RouteOperation operation = state.operations().get(delivered.operationId());
        if (operation == null || !subject.equals(operation.settlementId()) || !operation.cargoId().equals(delivered.cargoId())) {
            throw new IllegalArgumentException("cold cargo delivery has a foreign operation owner");
        }
        return state.completeColdCargoHandoff(delivered.operationId(), delivered.cargoId(), delivered.placements());
    }

    public static List<ProposedEvent> failed(FrontierWorldState state, RouteOperation operation, String reason) {
        return List.of(new ProposedEvent(operation.settlementId(), new OperationFailed(operation.id(), reason)),
                transition(deliveryTaskForOperation(state, operation, StrategicTaskStatus.ACTIVE), StrategicTaskStatus.BLOCKED));
    }

    /**
     * The route-loss observation may happen while this delivery owns the settlement's only
     * strategic lane.  A fresh reconsideration belongs at the terminal failure boundary,
     * after its delivery task has released that lane, rather than as a retrying side queue.
     */
    private static List<ProposedEvent> failed(FrontierWorldState state, RouteOperation operation, String reason, long now) {
        List<ProposedEvent> events = new ArrayList<>(failed(state, operation, reason));
        if (reason.equals("route-obstructed")) {
            firstObservedRouteLoss(state, operation).ifPresent(loss -> events.add(schedule(
                    StrategicObjectiveProcess.routeReconsideration(operation.settlementId(), loss, "failure", Math.addExact(now, 1L)))));
        }
        return List.copyOf(events);
    }

    private static Optional<BlockPosition> firstObservedRouteLoss(FrontierWorldState state, RouteOperation operation) {
        return state.physicalDeltas().values().stream()
                .filter(delta -> delta.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS)
                .filter(delta -> delta.ownerId().filter(FrontierRouteNetwork.OWNER::equals).isPresent())
                .filter(delta -> delta.semanticPart().filter(GrayboxSemanticPart.ROUTE_SURFACE::equals).isPresent())
                .map(PhysicalDelta::position)
                .filter(position -> FrontierRouteNetwork.isSurfaceCell(state.bootstrap(), state.routeTopology(), position))
                .filter(position -> !state.routeTopology().supplyPassable(state.bootstrap(), operation.settlementId()))
                .sorted(Comparator.comparingInt(BlockPosition::x).thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z))
                .findFirst();
    }

    public static ScheduledAction operationProgress(RouteOperation operation, long due) { return new ScheduledAction(new ScheduleId("schedule:operation-progress-" + operation.id().value().substring("operation:".length())),
            new SimInstant(due), 0, operation.id(), "frontier.operation.progress", 1); }
    public static ScheduledAction operationAssembly(RouteOperation operation, long due) { return new ScheduledAction(new ScheduleId("schedule:operation-assembly-" + operation.id().value().substring("operation:".length())),
            new SimInstant(due), 0, operation.id(), "frontier.operation.assembly", 1); }
    private static ScheduledAction cargoLoad(SupplyContract contract, long due) { return new ScheduledAction(new ScheduleId("schedule:cargo-load-" + contract.id().value().substring("contract:".length())),
            new SimInstant(due), 0, contract.id(), "frontier.supply.cargo.load", 1); }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }

    private static OperationTravel travelForNextSegment(FrontierWorldState state, RouteOperation operation) {
        TransportAnchor cargoAnchor = operation.activeAssembly().map(OperationAssembly::cargoAnchor)
                .orElseGet(() -> operation.activeTravel().orElseThrow(() -> new IllegalArgumentException("operation has no prior cargo anchor")).cargoAnchor());
        return travelForNextSegment(state, operation, participantBodies(state, operation), cargoAnchor);
    }
    private static OperationTravel travelForNextSegment(FrontierWorldState state, RouteOperation operation, java.util.Map<SubjectId, BodyPosition> formation, TransportAnchor cargoAnchor) {
        int next = operation.stage() == OperationStage.ARRIVED || operation.stage() == OperationStage.RETURNING ? operation.routeIndex() - 1 : operation.routeIndex() + 1;
        if (next < 0 || next >= operation.route().size()) throw new IllegalArgumentException("operation has no next travel segment");
        TraversalTopology topology = state.routeTopology().supplyTraversalSegment(state.bootstrap(), operation.settlementId(),
                operation.route().get(operation.routeIndex()), operation.route().get(next),
                new TraversalTopologyId("topology:operation:" + operation.id().value() + ":segment:" + operation.routeIndex()));
        return new OperationTravel(topology, 0, formation, cargoAnchor);
    }

    /**
     * The final HOT observed arrival is an authoritative assembly completion, so it starts the
     * first segment in that same canonical transaction instead of waiting for a stale COLD poll.
     */
    public static OperationTravel travelForCompletedAssembly(FrontierWorldState state, RouteOperation operation, OperationAssembly assembly) {
        if (!assembly.complete()) throw new IllegalArgumentException("only a complete assembly may start operation travel");
        java.util.Map<SubjectId, BodyPosition> formation = new java.util.LinkedHashMap<>();
        assembly.positions().forEach((actor, position) -> formation.put(actor, BodyPosition.above(position)));
        return travelForNextSegment(state, operation, java.util.Map.copyOf(formation), assembly.cargoAnchor());
    }
    private static java.util.Map<SubjectId, BodyPosition> participantBodies(FrontierWorldState state, RouteOperation operation) {
        java.util.Map<SubjectId, BodyPosition> formation = new java.util.LinkedHashMap<>();
        operation.participantIds().forEach(actor -> formation.put(actor, state.actorLocations().get(actor).body()));
        return java.util.Map.copyOf(formation);
    }
    private static OperationTravel translateTravel(OperationTravel travel, int nextCursor) {
        BlockPosition from = travel.currentPosition(), to = travel.corridor().get(nextCursor);
        int deltaX = to.x() - from.x(), deltaY = to.y() - from.y(), deltaZ = to.z() - from.z(); java.util.Map<SubjectId, BodyPosition> formation = new java.util.LinkedHashMap<>();
        travel.formation().forEach((actor, position) -> formation.put(actor, position.offset(deltaX, deltaY, deltaZ)));
        return travel.advance(nextCursor, formation, travel.cargoAnchor().offset(deltaX, deltaY, deltaZ));
    }
    private static List<BlockPosition> adjacentSegment(BlockPosition from, BlockPosition to) {
        if (Math.abs(from.y() - to.y()) > 1 || (from.x() != to.x() && from.z() != to.z())) throw new IllegalArgumentException("operation route segment must be axis aligned with grade at most one");
        if (from.y() != to.y()) return List.of(from, to);
        List<BlockPosition> corridor = new ArrayList<>(); int deltaX = Integer.compare(to.x(), from.x()), deltaZ = Integer.compare(to.z(), from.z());
        for (BlockPosition cursor = from;; cursor = cursor.offset(deltaX, 0, deltaZ)) { corridor.add(cursor); if (cursor.equals(to)) return List.copyOf(corridor); }
    }
    private static List<ProposedEvent> arrivalEvents(FrontierWorldState state, RouteOperation operation, long now) {
        SubjectId receiver = FrontierCargoValidation.receiverStore(state.bootstrap(), operation);
        if (state.inventory().surfaces().get(receiver).status() == ContainerSurfaceStatus.ACTIVE) {
            PhysicalIntentId intentId = cargoHandoffIntent(operation).id();
            return state.physicalIntents().containsKey(intentId) ? List.of() : List.of(new ProposedEvent(operation.settlementId(), new PhysicalIntentPrepared(cargoHandoffIntent(operation))));
        }
        CargoDelivered delivery = coldDelivery(state, operation, receiver);
        if (delivery == null) return List.of(schedule(operationProgress(operation, Math.addExact(now, 100L))));
        return List.of(new ProposedEvent(operation.settlementId(), delivery), transition(deliveryTaskForOperation(state, operation, StrategicTaskStatus.ACTIVE), StrategicTaskStatus.COMPLETED),
                schedule(operationProgress(operation, Math.addExact(now, 20L))));
    }

    private static CargoDelivered coldDelivery(FrontierWorldState state, RouteOperation operation, SubjectId receiver) {
        CargoBatch cargo = state.inventory().cargo().get(operation.cargoId());
        if (cargo == null) throw new IllegalStateException("arrived operation has no exact cargo");
        java.util.ArrayList<CargoHandoffPlacement> placements = new java.util.ArrayList<>(); int nextSlot = 0;
        for (SubjectId itemId : cargo.itemIds().stream().sorted().toList()) {
            while (nextSlot < state.inventory().containers().get(receiver).slotCount()
                    && !state.containerSlotAvailable(new InventoryCustody.ContainerSlot(receiver, nextSlot))) nextSlot++;
            if (nextSlot >= state.inventory().containers().get(receiver).slotCount()) return null;
            placements.add(new CargoHandoffPlacement(itemId, new InventoryCustody.ContainerSlot(receiver, nextSlot++)));
        }
        return new CargoDelivered(operation.id(), cargo.id(), placements);
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
    private static List<ProposedEvent> abandonPreparation(FrontierWorldState state, StrategicTask preparation, SupplyContract contract) {
        return List.of(new ProposedEvent(contract.settlementId(), new SupplyContractAbandoned(contract.id())),
                transition(preparation, StrategicTaskStatus.BLOCKED), transition(deliveryTask(state, preparation, StrategicTaskStatus.PENDING), StrategicTaskStatus.BLOCKED));
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
    public static StrategicTask deliveryTaskForOperation(FrontierWorldState state, RouteOperation operation, StrategicTaskStatus status) {
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
        return SettlementProvisionProcess.exportableBread(state, settlement.id());
    }
    private static boolean participantsAvailable(FrontierWorldState state, Settlement settlement) {
        return FrontierWorldStateSupport.availableRouteResident(state, settlement.id(), ResidentProfession.LOGISTICIAN).isPresent()
                && FrontierWorldStateSupport.availableRouteResidents(state, settlement.id(), ResidentProfession.SECURITY_WORKER).size() >= 2;
    }
    private static boolean dependenciesCompleted(FrontierWorldState state, StrategicTask task) {
        return task.dependencies().stream().map(state.strategicPlans().tasks()::get).allMatch(value -> value.status() == StrategicTaskStatus.COMPLETED);
    }
    private static RouteOperation routeOperation(FrontierWorldState state, SupplyContract contract, Settlement settlement) {
        SubjectId hauler = FrontierWorldStateSupport.availableRouteResident(state, settlement.id(), ResidentProfession.LOGISTICIAN).orElseThrow().id();
        List<SubjectId> escorts = FrontierWorldStateSupport.availableRouteResidents(state, settlement.id(), ResidentProfession.SECURITY_WORKER).stream()
                .limit(2).map(ResidentProfile::id).toList();
        int ordinal = FrontierWorldScheduleSupport.ordinal(contract.id().value());
        SubjectId operationId = new SubjectId("operation:supply-" + settlement.id().value().substring("settlement:".length()) + "-" + ordinal);
        RouteUnitManifest unit = RouteUnitManifest.cargoEscort(operationId, hauler, escorts.getFirst(), escorts);
        SettlementAccessPort access = SettlementAccessPort.forHall(settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("supply settlement lacks a Hall")));
        java.util.Map<SubjectId, OperationAssembly.Member> members = new java.util.LinkedHashMap<>();
        members.put(hauler, new OperationAssembly.Member(OperationAssemblyCorridor.compile(state, operationId, hauler, access.assemblySurface()), 0));
        // The lead escort holds the outward port beside the cargo crew. Further escorts use
        // lateral assembly slots rather than queuing through that same one-cell throat.
        List<SurfaceAnchor> escortSlots = List.of(access.routeSurface(), access.assemblySurface().offset(0, 0, 1), access.assemblySurface().offset(0, 0, -1),
                access.interiorSurface().offset(0, 0, 1));
        for (int index = 0; index < escorts.size(); index++) {
            SubjectId escort = escorts.get(index);
            members.put(escort, new OperationAssembly.Member(OperationAssemblyCorridor.compile(state, operationId, escort, escortSlots.get(index)), 0));
        }
        OperationAssembly assembly = new OperationAssembly(members, hauler);
        return new RouteOperation(operationId, settlement.id(), contract.cargoId(), contract.recipientId(), unit,
                state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id()), 0, OperationStage.ASSEMBLING, java.util.Optional.of(assembly), java.util.Optional.empty());
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

    /** Normal supply IDs form one stable exact pair; fixtures may retain an arbitrary pair. */
    private static SupplyContract contractForOperation(FrontierWorldState state, RouteOperation operation) {
        String cargo = operation.cargoId().value();
        if (cargo.startsWith("cargo:supply-")) {
            SupplyContract direct = state.contracts().get(new SubjectId("contract:" + cargo.substring("cargo:".length())));
            if (direct != null && direct.cargoId().equals(operation.cargoId())) return direct;
        }
        return state.contracts().values().stream().filter(contract -> contract.cargoId().equals(operation.cargoId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("route operation has no matching supply contract"));
    }
    private static PhysicalIntent cargoHandoffIntent(RouteOperation operation) {
        BlockPosition target = operation.route().getLast(); FixedPosition origin = new FixedPosition(FixedScalar.whole(target.x()), FixedScalar.whole(target.y()), FixedScalar.whole(target.z()));
        return new PhysicalIntent(new PhysicalIntentId("intent:cargo-handoff-" + operation.id().value().substring("operation:".length())), PhysicalIntentKind.CARGO_HANDOFF,
                PhysicalIntentStatus.PREPARED, operation.id(), List.of(operation.id(), operation.cargoId()), origin, 0, PhysicalPostcondition.CARGO_HANDOFF_OBSERVED);
    }
}
