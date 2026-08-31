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
import java.util.Objects;
import java.util.Optional;

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
        int ordinal = state.strategicPlans().objectives().get(task.objectiveId()).decisionOrdinal();
        ProductionJob job = job(state, settlement, workshop, input.orElseThrow(), ordinal, !physicallyActive);
        // Finance is a start precondition.  A blocked task must not leave a durable job
        // occupying its workshop: otherwise a later objective review could create a
        // second job for the same facility and quarantine the canonical engine.
        if (!CompanyWorkPaymentProcess.canReserve(state, job)) {
            return blocked(task, settlement, workshop, workshop.id(), ProductionBlockReason.FINANCE_UNAVAILABLE);
        }
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(settlement.id(), new ProductionStarted(job, input.orElseThrow().id())), schedule(complete(job, action.dueAt().ticks() + 100L)));
    }

    static List<ProposedEvent> planCompletion(FrontierWorldState state, ScheduledAction action) {
        ProductionJob job = state.productionJobs().get(action.subject());
        if (job == null) throw new IllegalStateException("production completion has no active job: " + action.subject().value());
        Settlement settlement = settlement(state, job.settlementId()); StrategicTask task = activeTask(state, settlement.id()); SettlementStructure workshop = workshop(settlement);
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) return failActiveJob(state, task, settlement, workshop, job, ProductionBlockReason.FACILITY_UNAVAILABLE);
        boolean marketBacked = state.companies().market().acceptedForJob(job.id()).isPresent();
        if (state.actorLocations().get(job.workerId()).condition().status() != ActorLifeStatus.ALIVE
                || (marketBacked && CompanyWorkPaymentProcess.contractFor(state, job).isEmpty())) {
            return failActiveJob(state, task, settlement, workshop, job, ProductionBlockReason.WORKER_UNAVAILABLE);
        }
        if (job.inputHold() instanceof ProductionInputHold.Cold held) {
            ExactItemStack input = held.item();
            if (!(input.custody() instanceof InventoryCustody.ContainerSlot source) || !source.containerId().equals(FrontierWorldState.depotId(settlement.id()))) {
                throw new IllegalStateException("cold production hold has no settlement depot source slot");
            }
            ExactItemStack output = new ExactItemStack(job.outputItemId(), settlement.id(), job.outputItemKind(), job.outputCount(), source);
            return List.of(new ProposedEvent(settlement.id(), new ProductionCompleted(job.id(), output)), transition(task, StrategicTaskStatus.COMPLETED));
        }
        ExactItemStack input = state.inventory().items().get(job.consumedItemId());
        if (input == null || !(input.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(FrontierWorldState.depotId(settlement.id()))) {
            return failActiveJob(state, task, settlement, workshop, job, ProductionBlockReason.INPUT_UNAVAILABLE);
        }
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:production-transform-" + job.id().value().substring("job:".length())),
                PhysicalIntentKind.PRODUCTION_TRANSFORMATION, PhysicalIntentStatus.PREPARED, job.id(), List.of(job.id(), job.consumedItemId(), job.outputItemId()),
                fixed(state.actorLocations().get(job.workerId()).position()), 0, PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED);
        return List.of(new ProposedEvent(settlement.id(), new PhysicalIntentPrepared(intent)));
    }

    /**
     * A death stops any work that has not crossed the executor's durable RUNNING barrier. A
     * RUNNING intent remains an exact recovery question: Minecraft may already contain its
     * irreversible effect, so its receipt—not a later death observation—decides settlement.
     */
    static List<ProposedEvent> failPreEffectWorkForDeath(FrontierWorldState state, SubjectId workerId) {
        return state.productionJobs().values().stream().filter(job -> job.workerId().equals(workerId))
                .sorted(Comparator.comparing(ProductionJob::id)).flatMap(job -> {
                    boolean preEffect = state.physicalIntents().values().stream().filter(intent -> intent.causeSubjectId().equals(job.id()))
                            .allMatch(intent -> intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION
                                    && intent.status() == PhysicalIntentStatus.PREPARED);
                    if (!preEffect) return java.util.stream.Stream.empty();
                    Settlement settlement = settlement(state, job.settlementId());
                    return failActiveJob(state, activeTask(state, settlement.id()), settlement, workshop(settlement), job,
                            ProductionBlockReason.WORKER_UNAVAILABLE).stream();
                }).toList();
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
        if (physicallyActive != (job.inputHold() instanceof ProductionInputHold.Materialized)) {
            throw new IllegalArgumentException("production input hold does not match its admitted HOT/COLD boundary");
        }
        StrategicTask task = activeTask(state, settlement.id()); validateMarketOrder(state, task, job);
        FrontierWorldState startedState = physicallyActive ? state.withProductionJob(job) : state.startProductionJob(job, started.inputItemId());
        return CompanyWorkPaymentProcess.reserve(startedState, job);
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
        if (!(job.inputHold() instanceof ProductionInputHold.Cold) || state.inventory().items().containsKey(job.consumedItemId())) {
            throw new IllegalArgumentException("materialized production output requires a physical transformation receipt");
        }
        if (state.actorLocations().get(job.workerId()).condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("cold production cannot complete after its worker has died");
        }
        StrategicTask task = activeTask(state, settlement.id()); validateMarketOrder(state, task, job);
        FrontierWorldState paid = CompanyWorkPaymentProcess.settle(state, job);
        java.util.Optional<MarketWorkOrder> order = paid.companies().market().acceptedForJob(job.id());
        if (order.isPresent()) paid = paid.withCompanies(paid.companies().withMarket(paid.companies().market().complete(order.orElseThrow().id())));
        return paid.completeProductionJob(completed.jobId(), completed.output());
    }

    static FrontierWorldState reduceBlocked(FrontierWorldState state, SubjectId subject, ProductionBlocked blocked) {
        requireOwner(subject, blocked.settlementId()); Settlement settlement = settlement(state, blocked.settlementId()); SettlementStructure workshop = workshop(settlement);
        if (!workshop.id().equals(blocked.facilityId())) throw new IllegalArgumentException("production block refers to a foreign facility");
        SubjectId depot = FrontierWorldState.depotId(settlement.id()); boolean wheatPresent = wheat(state, settlement).isPresent();
        switch (blocked.reason()) {
            case INPUT_UNAVAILABLE -> {
                ProductionJob job = state.productionJobs().get(blocked.workId());
                if (job != null) {
                    if (!job.settlementId().equals(settlement.id()) || !(job.inputHold() instanceof ProductionInputHold.Materialized)
                            || materializedInputMatches(state, job)) {
                        throw new IllegalArgumentException("materialized production input block precondition does not hold");
                    }
                    break;
                }
                if (!blocked.workId().equals(workshop.id()) || wheatPresent || state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) throw new IllegalArgumentException("production input block precondition does not hold");
            }
            case OUTPUT_STORAGE_UNAVAILABLE -> {
                boolean pending = blocked.workId().equals(workshop.id()) && state.productionJobs().isEmpty();
                if ((!pending && !state.productionJobs().containsKey(blocked.workId())) || state.firstFreeContainerSlot(depot).isPresent()) throw new IllegalArgumentException("production storage block precondition does not hold");
            }
            case FACILITY_UNAVAILABLE -> {
                if (state.structureConditions().get(workshop.id()) == StructureCondition.INTACT) throw new IllegalArgumentException("production facility block precondition does not hold");
            }
            case WORKER_UNAVAILABLE -> {
                ProductionJob job = state.productionJobs().get(blocked.workId());
                if (job != null) {
                    if (!job.settlementId().equals(settlement.id()) || (state.actorLocations().get(job.workerId()).condition().status() == ActorLifeStatus.ALIVE
                            && (!state.companies().market().acceptedForJob(job.id()).isPresent() || CompanyWorkPaymentProcess.contractFor(state, job).isPresent()))) {
                        throw new IllegalArgumentException("active production worker block precondition does not hold");
                    }
                    break;
                }
                if (!blocked.workId().equals(workshop.id()) || state.structureConditions().get(workshop.id()) != StructureCondition.INTACT
                        || FrontierWorldStateSupport.availableWorkResident(state, settlement.id(), ResidentRole.CRAFTER).isPresent()) {
                    throw new IllegalArgumentException("production worker block precondition does not hold");
                }
            }
            case FINANCE_UNAVAILABLE -> {
                ProductionJob job = state.productionJobs().get(blocked.workId());
                if (job != null) {
                    if (!job.settlementId().equals(settlement.id()) || CompanyWorkPaymentProcess.canReserve(state, job)) {
                        throw new IllegalArgumentException("production finance block precondition does not hold");
                    }
                    break;
                }
                // A finance failure is normally admitted before ProductionStarted.  Bind a
                // start-time block to the exact pending task and prospective job so a forged
                // block cannot free a facility that actually has available funds.
                StrategicTask pending = task(state, state.strategicPlans().tasks().values().stream()
                        .filter(value -> value.ownerId().equals(settlement.id()) && value.kind() == StrategicTaskKind.PRODUCE_BREAD
                                && value.status() == StrategicTaskStatus.PENDING)
                        .map(StrategicTask::id).reduce((left, right) -> {
                            throw new IllegalArgumentException("production finance block has ambiguous pending task");
                        }).orElseThrow(() -> new IllegalArgumentException("production finance block has no pending task")), StrategicTaskStatus.PENDING);
                Optional<ExactItemStack> prospectiveInput = wheat(state, settlement);
                if (!blocked.workId().equals(workshop.id()) || prospectiveInput.isEmpty()
                        || FrontierWorldStateSupport.availableWorkResident(state, settlement.id(), ResidentRole.CRAFTER).isEmpty()) {
                    throw new IllegalArgumentException("production finance start block precondition does not hold");
                }
                int ordinal = state.strategicPlans().objectives().get(pending.objectiveId()).decisionOrdinal();
                boolean cold = state.inventory().surfaces().get(depot).status() != ContainerSurfaceStatus.ACTIVE;
                if (CompanyWorkPaymentProcess.canReserve(state, job(state, settlement, workshop, prospectiveInput.orElseThrow(), ordinal, cold))) {
                    throw new IllegalArgumentException("production finance start block has available funds");
                }
            }
        }
        return state;
    }

    private static List<ProposedEvent> blocked(StrategicTask task, Settlement settlement, SettlementStructure workshop, SubjectId work, ProductionBlockReason reason) {
        return List.of(new ProposedEvent(settlement.id(), new ProductionBlocked(settlement.id(), workshop.id(), work, reason)), transition(task, StrategicTaskStatus.BLOCKED));
    }
    private static List<ProposedEvent> failActiveJob(FrontierWorldState state, StrategicTask task, Settlement settlement, SettlementStructure workshop,
                                                      ProductionJob job, ProductionBlockReason reason) {
        Optional<MarketWorkOrder> order = state.companies().market().acceptedForJob(job.id());
        if (order.isEmpty()) return blocked(task, settlement, workshop, job.id(), reason);
        return List.of(new ProposedEvent(settlement.id(), new ProductionBlocked(settlement.id(), workshop.id(), job.id(), reason)),
                new ProposedEvent(settlement.id(), new MarketWorkOrderCancelled(order.orElseThrow().id(), job.id(), reason)));
    }
    /**
     * A player/world custody observation may remove the exact input before a materialized
     * transform begins.  It must release the job in the same WAL transaction: retaining a
     * job that now lacks its only input would make the final canonical snapshot invalid.
     * Once an intent exists, the effect owns the recovery question and the job deliberately
     * remains unresolved for its physical postcondition path instead of fabricating a refund.
     */
    static List<ProposedEvent> planMaterializedInputDeparture(FrontierWorldState state, SubjectId itemId, ProposedEvent observation) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(itemId, "item id"); Objects.requireNonNull(observation, "observation");
        ProductionJob job = state.productionJobs().values().stream().filter(candidate -> candidate.consumedItemId().equals(itemId)
                && candidate.inputHold() instanceof ProductionInputHold.Materialized).reduce((left, right) -> {
                    throw new IllegalStateException("one exact materialized input cannot belong to two production jobs");
                }).orElse(null);
        if (job == null || state.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(job.id()))) {
            return List.of(observation);
        }
        MarketWorkOrder order = state.companies().market().acceptedForJob(job.id()).orElseThrow(() ->
                new IllegalArgumentException("a materialized production input without an effect must retain its accepted market order"));
        Settlement settlement = settlement(state, job.settlementId()); StrategicTask task = activeTask(state, settlement.id());
        return List.of(observation, new ProposedEvent(settlement.id(), new ProductionBlocked(settlement.id(), job.facilityId(), job.id(), ProductionBlockReason.INPUT_UNAVAILABLE)),
                new ProposedEvent(settlement.id(), new MarketWorkOrderCancelled(order.id(), job.id(), ProductionBlockReason.INPUT_UNAVAILABLE)));
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
    private static boolean materializedInputMatches(FrontierWorldState state, ProductionJob job) {
        ExactItemStack input = state.inventory().items().get(job.consumedItemId());
        return input != null && input.economicOwnerId().equals(job.settlementId()) && WHEAT.equals(input.itemKind()) && input.count() == job.outputCount()
                && input.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(FrontierWorldState.depotId(job.settlementId()));
    }
    private static Settlement settlement(FrontierWorldState state, SubjectId id) { return FrontierWorldStateSupport.settlement(state.bootstrap(), id); }
    private static SettlementStructure workshop(Settlement settlement) { return settlement.structures().stream().filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst()
            .orElseThrow(() -> new IllegalStateException("settlement lacks workshop")); }
    private static ResidentProfile crafter(FrontierWorldState state, Settlement settlement) { return FrontierWorldStateSupport.availableWorkResident(state, settlement.id(), ResidentRole.CRAFTER)
            .orElseThrow(() -> new IllegalStateException("settlement lacks crafter")); }
    private static ProductionJob job(FrontierWorldState state, Settlement settlement, SettlementStructure workshop, ExactItemStack input, int ordinal, boolean cold) {
        ResidentProfile worker = crafter(state, settlement); String number = settlement.id().value().substring("settlement:".length());
        ProductionInputHold hold = cold ? new ProductionInputHold.Cold(input) : new ProductionInputHold.Materialized(input.id());
        return new ProductionJob(jobId(settlement, ordinal), settlement.id(), workshop.id(), worker.id(), input.id(), hold,
                new SubjectId("item:production-" + number + "-" + ordinal + "-bread"), BREAD, input.count());
    }
    static SubjectId jobId(Settlement settlement, int ordinal) {
        return new SubjectId("job:production-" + settlement.id().value().substring("settlement:".length()) + "-" + ordinal);
    }
    private static void validateMarketOrder(FrontierWorldState state, StrategicTask task, ProductionJob job) {
        boolean demandExists = state.companies().market().demands().values().stream().anyMatch(demand -> demand.reasonId().equals(task.id()));
        if (!demandExists) return; // Explicit compatibility for old fixtures/snapshots that predate the v3 market boundary.
        MarketWorkOrder order = state.companies().market().acceptedForJob(job.id()).orElseThrow(() ->
                new IllegalArgumentException("market-backed production has no accepted exact work order"));
        EmploymentContract contract = CompanyWorkPaymentProcess.contractFor(state, job).orElseThrow(() ->
                new IllegalArgumentException("market-backed production has no exact employment contract"));
        FinancialReservation expected = CompanyWorkPaymentProcess.reservation(job, contract);
        if (!order.taskId().equals(task.id()) || !order.sellerId().equals(contract.companyId()) || !order.reservationId().equals(expected.id())
                || !order.acceptedTotalPrice().equals(contract.invoicePerCompletedJob())) {
            throw new IllegalArgumentException("market-backed production order no longer matches its durable job terms");
        }
    }
    private static ScheduledAction complete(ProductionJob job, long due) { return new ScheduledAction(new ScheduleId("schedule:production-task-complete-" + job.id().value().substring("job:".length())),
            new SimInstant(due), 0, job.id(), "frontier.settlement.production.task.complete", 1); }
    private static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) { return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status)); }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    private static FixedPosition fixed(BlockPosition position) { return new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z())); }
    private static void requireOwner(SubjectId actual, SubjectId expected) { if (!expected.equals(actual)) throw new IllegalArgumentException("production event subject does not own the work"); }
}
