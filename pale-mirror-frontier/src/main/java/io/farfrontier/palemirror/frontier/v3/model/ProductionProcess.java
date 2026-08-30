package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Executes exact settlement production only as a durable strategic task. */
final class ProductionProcess {
    private static final String WHEAT = "minecraft:wheat";
    private static final String BREAD = "minecraft:bread";
    private ProductionProcess() { }

    static ScheduledAction start(StrategicTask task, long dueAt) {
        if (task.kind() != StrategicTaskKind.PRODUCE_BREAD) throw new IllegalArgumentException("invalid production task schedule");
        return new ScheduledAction(new ScheduleId("schedule:production-task-start-" + task.id().value().replace(':', '-')), new SimInstant(dueAt), 0,
                task.id(), "frontier.settlement.production.task.start", 1);
    }

    static List<ProposedEvent> planStart(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = task(state, action.subject(), StrategicTaskStatus.PENDING); Settlement settlement = settlement(state, task.ownerId());
        SettlementStructure workshop = workshop(settlement);
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) return blocked(task, settlement, workshop, workshop.id(), ProductionBlockReason.FACILITY_UNAVAILABLE);
        if (FrontierWorldStateSupport.availableWorkResident(state, settlement.id(), ResidentRole.CRAFTER).isEmpty()) {
            return blocked(task, settlement, workshop, workshop.id(), ProductionBlockReason.WORKER_UNAVAILABLE);
        }
        Optional<ExactItemStack> input = wheat(state, settlement);
        if (input.isEmpty()) return blocked(task, settlement, workshop, workshop.id(), ProductionBlockReason.INPUT_UNAVAILABLE);
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        boolean physicallyActive = state.inventory().surfaces().get(depot).status() == ContainerSurfaceStatus.ACTIVE;
        if (!physicallyActive && state.inventory().firstFreeSlot(depot).isEmpty()) {
            return blocked(task, settlement, workshop, workshop.id(), ProductionBlockReason.OUTPUT_STORAGE_UNAVAILABLE);
        }
        int ordinal = state.strategicPlans().objectives().get(task.objectiveId()).decisionOrdinal();
        ProductionJob job = job(state, settlement, workshop, input.orElseThrow(), ordinal);
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(settlement.id(), new ProductionStarted(job, input.orElseThrow().id())), schedule(complete(job, action.dueAt().ticks() + 100L)));
    }

    static List<ProposedEvent> planCompletion(FrontierWorldState state, ScheduledAction action) {
        ProductionJob job = state.productionJobs().get(action.subject());
        if (job == null) throw new IllegalStateException("production completion has no active job: " + action.subject().value());
        Settlement settlement = settlement(state, job.settlementId()); StrategicTask task = activeTask(state, settlement.id()); SettlementStructure workshop = workshop(settlement);
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) return blocked(task, settlement, workshop, job.id(), ProductionBlockReason.FACILITY_UNAVAILABLE);
        if (!CompanyWorkPaymentProcess.canSettle(state, job)) return blocked(task, settlement, workshop, job.id(), ProductionBlockReason.FINANCE_UNAVAILABLE);
        ExactItemStack input = state.inventory().items().get(job.consumedItemId());
        if (input == null) {
            SubjectId depot = FrontierWorldState.depotId(settlement.id()); OptionalInt slot = state.inventory().firstFreeSlot(depot);
            if (slot.isEmpty()) return blocked(task, settlement, workshop, job.id(), ProductionBlockReason.OUTPUT_STORAGE_UNAVAILABLE);
            ExactItemStack output = new ExactItemStack(job.outputItemId(), settlement.id(), job.outputItemKind(), job.outputCount(), new InventoryCustody.ContainerSlot(depot, slot.getAsInt()));
            return List.of(new ProposedEvent(settlement.id(), new ProductionCompleted(job.id(), output)), transition(task, StrategicTaskStatus.COMPLETED));
        }
        if (!(input.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(FrontierWorldState.depotId(settlement.id()))) {
            return blocked(task, settlement, workshop, job.id(), ProductionBlockReason.INPUT_UNAVAILABLE);
        }
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:production-transform-" + job.id().value().substring("job:".length())),
                PhysicalIntentKind.PRODUCTION_TRANSFORMATION, PhysicalIntentStatus.PREPARED, job.id(), List.of(job.id(), job.consumedItemId(), job.outputItemId()),
                fixed(state.actorLocations().get(job.workerId()).position()), 0, PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED);
        return List.of(new ProposedEvent(settlement.id(), new PhysicalIntentPrepared(intent)));
    }

    static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, ProductionStarted started) {
        ProductionJob job = started.job(); requireOwner(subject, job.settlementId()); Settlement settlement = settlement(state, job.settlementId());
        SettlementStructure workshop = workshop(settlement);
        if (!workshop.id().equals(job.facilityId()) || state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) throw new IllegalArgumentException("production start facility is unavailable");
        if (!crafter(state, settlement).id().equals(job.workerId())) throw new IllegalArgumentException("production start worker is not the deterministic facility crafter");
        ExactItemStack input = state.inventory().items().get(started.inputItemId());
        if (input == null || !WHEAT.equals(input.itemKind()) || !(input.custody() instanceof InventoryCustody.ContainerSlot slot)
                || !slot.containerId().equals(FrontierWorldState.depotId(settlement.id()))) throw new IllegalArgumentException("production start input is unavailable or not in its depot");
        if (input.count() != job.outputCount() || !BREAD.equals(job.outputItemKind())) throw new IllegalArgumentException("production output is not a verified wheat conversion");
        boolean physicallyActive = state.inventory().surfaces().get(FrontierWorldState.depotId(settlement.id())).status() == ContainerSurfaceStatus.ACTIVE;
        activeTask(state, settlement.id()); return physicallyActive ? state.withProductionJob(job) : state.startProductionJob(job, started.inputItemId());
    }

    static FrontierWorldState reduceCompleted(FrontierWorldState state, SubjectId subject, ProductionCompleted completed) {
        ProductionJob job = state.productionJobs().get(completed.jobId());
        if (job == null) throw new IllegalArgumentException("production completion has no active job");
        requireOwner(subject, job.settlementId()); Settlement settlement = settlement(state, job.settlementId()); SettlementStructure workshop = workshop(settlement);
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) throw new IllegalArgumentException("production completion facility is unavailable");
        if (!(completed.output().custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(FrontierWorldState.depotId(settlement.id()))) {
            throw new IllegalArgumentException("production output is not stored in its settlement depot");
        }
        if (!completed.output().economicOwnerId().equals(settlement.id())) throw new IllegalArgumentException("production output claim does not belong to its settlement");
        if (state.inventory().items().containsKey(job.consumedItemId())) {
            throw new IllegalArgumentException("materialized production output requires a physical transformation receipt");
        }
        activeTask(state, settlement.id());
        if (!CompanyWorkPaymentProcess.canSettle(state, job)) throw new IllegalArgumentException("production completion has unavailable company finance");
        return CompanyWorkPaymentProcess.settle(state, job).completeProductionJob(completed.jobId(), completed.output());
    }

    static FrontierWorldState reduceBlocked(FrontierWorldState state, SubjectId subject, ProductionBlocked blocked) {
        requireOwner(subject, blocked.settlementId()); Settlement settlement = settlement(state, blocked.settlementId()); SettlementStructure workshop = workshop(settlement);
        if (!workshop.id().equals(blocked.facilityId())) throw new IllegalArgumentException("production block refers to a foreign facility");
        SubjectId depot = FrontierWorldState.depotId(settlement.id()); boolean wheatPresent = wheat(state, settlement).isPresent();
        switch (blocked.reason()) {
            case INPUT_UNAVAILABLE -> {
                if (!blocked.workId().equals(workshop.id()) || wheatPresent || state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) throw new IllegalArgumentException("production input block precondition does not hold");
            }
            case OUTPUT_STORAGE_UNAVAILABLE -> {
                boolean pending = blocked.workId().equals(workshop.id()) && state.productionJobs().isEmpty();
                if ((!pending && !state.productionJobs().containsKey(blocked.workId())) || state.inventory().firstFreeSlot(depot).isPresent()) throw new IllegalArgumentException("production storage block precondition does not hold");
            }
            case FACILITY_UNAVAILABLE -> {
                if (state.structureConditions().get(workshop.id()) == StructureCondition.INTACT) throw new IllegalArgumentException("production facility block precondition does not hold");
            }
            case WORKER_UNAVAILABLE -> {
                if (!blocked.workId().equals(workshop.id()) || state.structureConditions().get(workshop.id()) != StructureCondition.INTACT
                        || FrontierWorldStateSupport.availableWorkResident(state, settlement.id(), ResidentRole.CRAFTER).isPresent()) {
                    throw new IllegalArgumentException("production worker block precondition does not hold");
                }
            }
            case FINANCE_UNAVAILABLE -> {
                ProductionJob job = state.productionJobs().get(blocked.workId());
                if (job == null || !job.settlementId().equals(settlement.id()) || CompanyWorkPaymentProcess.canSettle(state, job)) {
                    throw new IllegalArgumentException("production finance block precondition does not hold");
                }
            }
        }
        return state;
    }

    private static List<ProposedEvent> blocked(StrategicTask task, Settlement settlement, SettlementStructure workshop, SubjectId work, ProductionBlockReason reason) {
        return List.of(new ProposedEvent(settlement.id(), new ProductionBlocked(settlement.id(), workshop.id(), work, reason)), transition(task, StrategicTaskStatus.BLOCKED));
    }
    private static StrategicTask task(FrontierWorldState state, SubjectId id, StrategicTaskStatus status) {
        StrategicTask task = state.strategicPlans().tasks().get(id);
        if (task == null || task.kind() != StrategicTaskKind.PRODUCE_BREAD || task.status() != status) throw new IllegalStateException("production schedule has no matching strategic task");
        return task;
    }
    private static StrategicTask activeTask(FrontierWorldState state, SubjectId settlement) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.ownerId().equals(settlement)
                && task.kind() == StrategicTaskKind.PRODUCE_BREAD && task.status() == StrategicTaskStatus.ACTIVE).reduce((left, right) -> {
                    throw new IllegalArgumentException("production task binding is ambiguous");
                }).orElseThrow(() -> new IllegalArgumentException("production has no active strategic task"));
    }
    private static Optional<ExactItemStack> wheat(FrontierWorldState state, Settlement settlement) {
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        return state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id)).filter(item -> WHEAT.equals(item.itemKind())
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot)).findFirst();
    }
    private static Settlement settlement(FrontierWorldState state, SubjectId id) { return FrontierWorldStateSupport.settlement(state.bootstrap(), id); }
    private static SettlementStructure workshop(Settlement settlement) { return settlement.structures().stream().filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst()
            .orElseThrow(() -> new IllegalStateException("settlement lacks workshop")); }
    private static ResidentProfile crafter(FrontierWorldState state, Settlement settlement) { return FrontierWorldStateSupport.availableWorkResident(state, settlement.id(), ResidentRole.CRAFTER)
            .orElseThrow(() -> new IllegalStateException("settlement lacks crafter")); }
    private static ProductionJob job(FrontierWorldState state, Settlement settlement, SettlementStructure workshop, ExactItemStack input, int ordinal) {
        ResidentProfile worker = crafter(state, settlement); String number = settlement.id().value().substring("settlement:".length());
        return new ProductionJob(new SubjectId("job:production-" + number + "-" + ordinal), settlement.id(), workshop.id(), worker.id(), input.id(),
                new SubjectId("item:production-" + number + "-" + ordinal + "-bread"), BREAD, input.count());
    }
    private static ScheduledAction complete(ProductionJob job, long due) { return new ScheduledAction(new ScheduleId("schedule:production-task-complete-" + job.id().value().substring("job:".length())),
            new SimInstant(due), 0, job.id(), "frontier.settlement.production.task.complete", 1); }
    private static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) { return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status)); }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    private static FixedPosition fixed(BlockPosition position) { return new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z())); }
    private static void requireOwner(SubjectId actual, SubjectId expected) { if (!expected.equals(actual)) throw new IllegalArgumentException("production event subject does not own the work"); }
}
