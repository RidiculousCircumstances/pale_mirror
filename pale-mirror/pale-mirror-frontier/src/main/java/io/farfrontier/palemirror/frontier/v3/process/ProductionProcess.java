package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
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
public final class ProductionProcess {
    public static List<ProposedEvent> planRelationshipConflict(FrontierWorldState state, FrontierCommand command, SceneLease lease, ProductionWorkTraversalBlocked blocked) {
        SubjectId jobId = FrontierSceneBehaviors.productionWork(lease).jobId();
        ProductionJob job = state.productionJobs().get(jobId);
        if (job == null) throw new IllegalArgumentException("relationship conflict lease has no exact job");
        MarketWorkOrder order = state.companies().market().acceptedForJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("relationship conflict job has no exact order"));
        StrategicTask task = activeTask(state, job);
        if (!order.taskId().equals(job.taskId())) throw new IllegalArgumentException("relationship conflict job has a foreign accepted task");
        RelationshipIncident incident = new RelationshipIncident(FrontierDomainRelationships.Kind.ORDER_JOB, order.id(), order.id(),
                "lease-job=" + jobId.value(), "asserted-job=" + blocked.jobId().value(), FrontierDomainRelationships.IncidentReason.STALE_RELATION,
                FrontierDomainRelationships.Disposition.FAIL_CLOSED_LOCAL, command.expectedRevision().next().value(), "command:" + command.id().value());
        return List.of(new ProposedEvent(order.taskId(), new MarketRelationshipIncidentRecorded(order.id(), incident)),
                new ProposedEvent(job.settlementId(), ProductionDiagnosticProducer.RELATIONSHIP_CONFLICT.create(job.settlementId(), job.facilityId(), job.id(), task.id())), transition(task, StrategicTaskStatus.BLOCKED),
                new ProposedEvent(job.settlementId(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING)));
    }
    private static final String WHEAT = "minecraft:wheat";
    private static final String BREAD = "minecraft:bread";
    /** One COLD checkpoint may retain this many already-stationary processing ticks. */
    private ProductionProcess() { }

    public static ScheduledAction start(StrategicTask task, long dueAt) {
        if (task.kind() != StrategicTaskKind.PRODUCE_BREAD) throw new IllegalArgumentException("invalid production task schedule");
        return new ScheduledAction(new ScheduleId("schedule:production-task-start-" + task.id().value().replace(':', '-')), new SimInstant(dueAt), 0,
                task.id(), "frontier.settlement.production.task.start", 1);
    }

    /**
     * Recompiles only a not-yet-started workshop traversal from the body observed at the
     * durable ambient-to-scene transfer.  This is not a catch-up or teleport: the observation
     * becomes the first retained surface before HOT work begins.  Once work has used an edge or
     * advanced a stage, the original topology remains authoritative.
     */
    public static FrontierWorldState rebaseForAmbientHandoff(FrontierWorldState state, SubjectId subject,
                                                               ProductionWorkSceneLeaseHandoff handoff) {
        ProductionJob job = FrontierProductionWorkSceneSupport.require(state, FrontierSceneBehaviors.productionWork(handoff.lease()));
        Settlement settlement = settlement(state, job.settlementId()); SettlementStructure workshop = workshop(settlement);
        if (!subject.equals(job.settlementId()) || !workshop.id().equals(job.facilityId()) || handoff.ambientMembers().size() != 1
                || !handoff.ambientMembers().getFirst().actorId().equals(job.workerId())) {
            throw new IllegalArgumentException("production-work hand-off must capture its one exact workshop worker");
        }
        SceneMemberPosition capture = handoff.ambientMembers().getFirst();
        ActorLocation current = state.actorLocations().get(job.workerId());
        if (current == null || current.condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("production-work hand-off has no living worker");
        }
        if (job.bakeryWork().isPresent()) return state;
        if (job.traversalCursor() != 0 || !job.workProgress().equals(ProductionWorkProgress.notStarted()) || job.spatial().pending()) {
            if (!capture.body().equals(current.body()))
                throw new IllegalArgumentException("production hand-off requires independent current body inspection");
            SurfaceAnchor retained = job.spatial().current(job.workTraversal().linearCorridorSurfaces().get(job.traversalCursor()));
            return retained.equals(capture.body().supportingSurface()) ? state : replaceJob(state,
                    job.withSpatial(ProductionJourneyKnowledge.checkpoint(state, job, capture.body().supportingSurface())));
        }
        TraversalTopology rebased = ProductionWorkTraversal.compile(state, workshop, job.workerId(),
                new ActorLocation(capture.body(), current.condition(), current.kind()), job.id());
        return FrontierProductionWorkSceneSupport.replaceJob(state, job.rebaseUnstartedTraversal(rebased));
    }

    public static List<ProposedEvent> planStart(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = task(state, action.subject(), StrategicTaskStatus.PENDING); Settlement settlement = settlement(state, task.ownerId());
        if (state.strategicPlans().objectives().get(task.objectiveId()).kind() == StrategicObjectiveKind.SETTLEMENT_COMPANY_PRODUCTION)
            return CompanyBakeryPlanning.planStart(state, task, action);
        SettlementStructure workshop = workshop(settlement);
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) return blocked(task, settlement, workshop, workshop.id(), ProductionDiagnosticProducer.FACILITY_UNAVAILABLE);
        if (!SettlementCommitmentComposition.ADMISSION.facilityAvailable(state, workshop.id()))
            return List.of(reschedule(action, start(task, Math.addExact(action.dueAt().ticks(),
                    state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval()))));
        Optional<ResidentProfile> availableBaker = FrontierWorldStateSupport.availableWorkResident(
                state, settlement.id(), ResidentWorkKind.BAKING, HumanCapability.INDUSTRY);
        if (availableBaker.isEmpty()) {
            return blocked(task, settlement, workshop, workshop.id(), ProductionDiagnosticProducer.WORKER_UNAVAILABLE);
        }
        List<ResidentProfile> eligible = ResidentWorkSelection.eligible(state, settlement.id(),
                ResidentWorkKind.BAKING, HumanCapability.INDUSTRY, action.dueAt().ticks());
        if (eligible.isEmpty())
            return List.of(reschedule(action, start(task, Math.addExact(action.dueAt().ticks(),
                    state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval()))));
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        if (ReferenceContainerCustody.blocksCanonicalUse(state, depot)) {
            return blocked(task, settlement, workshop, depot, ProductionDiagnosticProducer.INPUT_UNAVAILABLE);
        }
        boolean physicalCustody = ReferenceContainerCustody.hasLiveCustody(state, depot);
        var availableInput = BakeryBatchSelection.available(state, settlement.id());
        if (physicalCustody && availableInput.isEmpty()
                && FungibleResourceCustodySupport.accountAtContainer(state, depot).map(account ->
                account.lotQuantities().entrySet().stream().filter(entry -> {
                    ResourceLot lot = state.inventory().fungibleResources().lots().get(entry.getKey());
                    return lot.economicOwnerId().equals(settlement.id()) && WHEAT.equals(lot.itemKind());
                }).mapToInt(java.util.Map.Entry::getValue).sum() > 0).orElse(false)) {
            return List.of(reschedule(action, start(task, Math.addExact(action.dueAt().ticks(), 20L))));
        }
        if (availableInput.isEmpty()) return blocked(task, settlement, workshop, workshop.id(), ProductionDiagnosticProducer.INPUT_UNAVAILABLE);
        var batch = BakeryBatchSelection.admissible(state, settlement.id());
        if (batch.isEmpty())
            return List.of(reschedule(action, start(task, Math.addExact(action.dueAt().ticks(),
                    state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval()))));
        Optional<ExactItemStack> input = batch.orElseThrow().exactInput();
        Optional<FungibleResourceCustodySupport.LotSelection> fungible = batch.orElseThrow().fungibleInput();
        if (physicalCustody && !ReferenceContainerCustody.hasOperationalCustody(state, depot)
                || fungible.isPresent() && !ProductionResourceCustody.canStart(state, fungible.orElseThrow(), batch.orElseThrow().quantity())) {
            return List.of(reschedule(action, start(task, Math.addExact(action.dueAt().ticks(), 20L))));
        }
        // Exact and fungible stock remain distinct representations. A resource job admitted
        // under physical custody reserves the current bound epoch, never a COLD mirror.
        Optional<ProductionJob> selectedJob = ResidentWorkSelection.offers(state, settlement.id(), action.dueAt().ticks(),
                BakeryJobAdmission.provider(task, settlement, workshop, input, fungible)).stream()
                .map(ResidentWorkOffer::execution)
                .filter(job -> CompanyWorkPaymentProcess.canReserve(state, job)).findFirst();
        // Finance is a start precondition.  A blocked task must not leave a durable job
        // occupying its workshop: otherwise a later objective review could create a
        // second job for the same facility and quarantine the canonical engine.
        if (selectedJob.isEmpty()) {
            // A funded but temporarily unavailable worker is not a settlement finance failure.
            if (SettlementWorkforce.candidates(state, settlement.id(), ResidentWorkKind.BAKING, HumanCapability.INDUSTRY).stream()
                    .map(worker -> BakeryJobAdmission.propose(state, task, settlement, workshop, input, fungible, worker))
                    .anyMatch(job -> CompanyWorkPaymentProcess.canReserve(state, job)))
                return List.of(reschedule(action, start(task, Math.addExact(action.dueAt().ticks(),
                        state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval()))));
            return blocked(task, settlement, workshop, workshop.id(), ProductionDiagnosticProducer.FINANCE_UNAVAILABLE);
        }
        ProductionJob job = selectedJob.orElseThrow();
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(settlement.id(), new ProductionStarted(job, job.consumedItemId())), schedule(complete(job, action.dueAt().ticks() + 100L)));
    }

    /** Restores the consumed completion action when an unstarted physical effect returns to COLD. */
    public static List<ProposedEvent> resumeReleasedEffects(FrontierWorldState before, FrontierWorldState after, long now) {
        return before.productionJobs().values().stream().sorted(Comparator.comparing(ProductionJob::id))
                .filter(job -> after.productionJobs().containsKey(job.id()))
                .filter(job -> before.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(job.id())
                        && !after.physicalIntents().containsKey(intent.id())))
                .map(job -> new ProposedEvent(job.settlementId(), new ScheduleEffect.Created(complete(job, Math.addExact(now, 1L)))))
                .toList();
    }

    public static List<ProposedEvent> planCompletion(FrontierWorldState state, ScheduledAction action) {
        ProductionJob job = state.productionJobs().get(action.subject());
        // A blocked scene retires its job durably before this one-shot action becomes due.
        // The consumed schedule must then be a harmless deterministic no-op, not a quarantine.
        if (job == null) return List.of();
        if (state.humanPopulation().meals().containsKey(job.workerId()))
            return List.of(reschedule(action, complete(job, Math.addExact(action.dueAt().ticks(), 20L))));
        if (!state.actorExecutions().owns(job.workerId(),
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRODUCTION, job.id())
                || !ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.workerId(), action.dueAt().ticks()))
            return List.of(reschedule(action, complete(job, ResidentActivityCoordinator.nextOrdinaryWorkCheck(
                    state, job.workerId(), action.dueAt().ticks()))));
        if (job.bakeryWork().isPresent()) return BakeryCompletionPlanning.plan(state, job, action);
        Settlement settlement = settlement(state, job.settlementId()); StrategicTask task = activeTask(state, job); SettlementStructure workshop = workshop(settlement);
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) return failActiveJob(state, task, settlement, workshop, job, ProductionDiagnosticProducer.FACILITY_UNAVAILABLE);
        if (ReferenceContainerCustody.blocksCanonicalUse(state, FrontierWorldState.depotId(settlement.id()))) {
            return failActiveJob(state, task, settlement, workshop, job, ProductionDiagnosticProducer.INPUT_UNAVAILABLE);
        }
        boolean marketBacked = state.companies().market().acceptedForJob(job.id()).isPresent();
        if (state.actorLocations().get(job.workerId()).condition().status() != ActorLifeStatus.ALIVE
                || (marketBacked && CompanyWorkPaymentProcess.contractFor(state, job).isEmpty())) {
            return failActiveJob(state, task, settlement, workshop, job, ProductionDiagnosticProducer.WORKER_UNAVAILABLE);
        }
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        boolean physicalCustody = ReferenceContainerCustody.hasLiveCustody(state, depot);
        if (job.workProgress().terminalEffectEligible() && !ActorExecutionCoordinator.coldAvailable(state, job.workerId())) {
            return List.of(reschedule(action, complete(job, Math.addExact(action.dueAt().ticks(), 20L))));
        }
        if (physicalCustody && !ReferenceContainerCustody.hasOperationalCustody(state, depot)) {
            return List.of(reschedule(action, complete(job, Math.addExact(action.dueAt().ticks(), 20L))));
        }
        if (job.inputHold() instanceof ProductionInputHold.Materialized) {
            // A materialized hold is exclusive only while the reference-container adapter has
            // a current lease.  Once that lease is gone, the retained route and exact input
            // return to COLD ownership; an old replica observation may not keep the recipe
            // semantically open.
            if (!job.workProgress().terminalEffectEligible()) {
                if (!physicalCustody) return planColdWorkAdvance(state, job, action);
                // A current lease owns the live traversal.  It must advance through its HOT
                // scene, never leap straight from an unworked materialized input to a final
                // physical transform.
                return List.of(reschedule(action, complete(job, Math.addExact(action.dueAt().ticks(), 20L))));
            }
            // The worker scene owns the live body until its release receipt is committed.  A
            // physical output receipt must never remove this job while the HOT/DRAINING lease
            // still names it. Keep this one durable completion review retained so the later
            // release receipt can move it to the next tick without creating a second schedule
            // with the same stable job identity.
            if (hasOpenWorkScene(state, job.id())) {
                return List.of(reschedule(action, complete(job, Math.addExact(action.dueAt().ticks(), 20L))));
            }
            if (!physicalCustody && hasStartedProductionTransformation(state, job.id())) {
                // RUNNING is still a real-world ambiguity boundary.  A merely PREPARED
                // transform has performed no effect and is retired atomically by the COLD
                // completion reducer below.
                return List.of(reschedule(action, complete(job, Math.addExact(action.dueAt().ticks(), 20L))));
            }
        }
        if (job.inputHold() instanceof ProductionInputHold.FungibleBound held) {
            if (!job.workProgress().terminalEffectEligible() || hasOpenWorkScene(state, job.id())) {
                return List.of(reschedule(action, complete(job, Math.addExact(action.dueAt().ticks(), 20L))));
            }
            PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:production-resource-" + job.id().value().replace(':', '-')),
                    PhysicalIntentKind.PRODUCTION_TRANSFORMATION, PhysicalIntentStatus.PREPARED, job.id(),
                    PhysicalIntentRoleBinding.productionResources(job.id(), held.itemId(), job.outputItemId(), held.claimId(), held.accountId(), depot),
                    fixed(state.actorLocations().get(job.workerId()).supportingSurface().support()), 0,
                    PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.PRODUCTION_WORK);
            return List.of(new ProposedEvent(settlement.id(), new PhysicalIntentPrepared(intent)));
        }
        if (job.inputHold() instanceof ProductionInputHold.FungibleCold held) {
            if (ReferenceContainerCustody.hasLiveCustody(state, FrontierWorldState.depotId(settlement.id()))) {
                return List.of(reschedule(action, complete(job, Math.addExact(action.dueAt().ticks(), 20L))));
            }
            if (!job.workProgress().terminalEffectEligible()) return planColdWorkAdvance(state, job, action);
            if (!retainsFungibleInput(state, job, held.accountId(), held.inputLots())) {
                return failActiveJob(state, task, settlement, workshop, job, ProductionDiagnosticProducer.INPUT_UNAVAILABLE);
            }
            ResourceLot output = new ResourceLot(job.outputItemId(), job.rights().resourceOwner().id(), job.outputItemKind(), job.outputCount(), "recipe:bread", inputLineage(held.inputLots()));
            return List.of(new ProposedEvent(settlement.id(), new FungibleProductionCompleted(job.id(), output)), transition(task, StrategicTaskStatus.COMPLETED));
        }
        if (job.inputHold() instanceof ProductionInputHold.Cold held) {
            // Releasing a materialized input returns the existing route to COLD; it does not
            // turn the first subsequent schedule review into an implicit transformation.  The
            // retained cursor/progress remains the sole semantic-work authority until the
            // worker reaches OUTPUT_READY.
            if (!job.workProgress().terminalEffectEligible()) {
                return planColdWorkAdvance(state, job, action);
            }
            ExactItemStack input = held.item();
            if (!(input.custody() instanceof InventoryCustody.ContainerSlot source) || !source.containerId().equals(FrontierWorldState.depotId(settlement.id()))) {
                throw new IllegalStateException("cold production hold has no settlement depot source slot");
            }
            ExactItemStack output = new ExactItemStack(job.outputItemId(), job.rights().resourceOwner().id(), job.outputItemKind(), job.outputCount(), source);
            return List.of(new ProposedEvent(settlement.id(), new ProductionCompleted(job.id(), output)), transition(task, StrategicTaskStatus.COMPLETED));
        }
        if (job.inputHold() instanceof ProductionInputHold.Materialized && !physicalCustody) {
            ExactItemStack input = state.inventory().items().get(job.consumedItemId());
            if (input == null || !(input.custody() instanceof InventoryCustody.ContainerSlot source) || !source.containerId().equals(depot)) {
                return failActiveJob(state, task, settlement, workshop, job, ProductionDiagnosticProducer.INPUT_UNAVAILABLE);
            }
            ExactItemStack output = new ExactItemStack(job.outputItemId(), job.rights().resourceOwner().id(), job.outputItemKind(), job.outputCount(), source);
            return List.of(new ProposedEvent(settlement.id(), new ProductionCompleted(job.id(), output)), transition(task, StrategicTaskStatus.COMPLETED));
        }
        ExactItemStack input = state.inventory().items().get(job.consumedItemId());
        if (input == null || !(input.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(FrontierWorldState.depotId(settlement.id()))) {
            return failActiveJob(state, task, settlement, workshop, job, ProductionDiagnosticProducer.INPUT_UNAVAILABLE);
        }
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:production-transform-" + job.id().value().substring("job:".length())),
                PhysicalIntentKind.PRODUCTION_TRANSFORMATION, PhysicalIntentStatus.PREPARED, job.id(), PhysicalIntentRoleBinding.production(job.id(), job.consumedItemId(), job.outputItemId()),
                fixed(state.actorLocations().get(job.workerId()).supportingSurface().support()), 0, PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.PRODUCTION_WORK);
        return List.of(new ProposedEvent(settlement.id(), new PhysicalIntentPrepared(intent)));
    }

    /**
     * A death stops any work that has not crossed the executor's durable RUNNING barrier. A
     * RUNNING intent remains an exact recovery question: Minecraft may already contain its
     * irreversible effect, so its receipt—not a later death observation—decides settlement.
     */
    public static List<ProposedEvent> failPreEffectWorkForDeath(FrontierWorldState state, SubjectId workerId) {
        return state.productionJobs().values().stream().filter(job -> job.workerId().equals(workerId))
                .sorted(Comparator.comparing(ProductionJob::id)).flatMap(job -> {
                    boolean preEffect = state.physicalIntents().values().stream().filter(intent -> intent.causeSubjectId().equals(job.id()))
                            .allMatch(intent -> intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION
                                    && intent.status() == PhysicalIntentStatus.PREPARED);
                    if (!preEffect) return java.util.stream.Stream.empty();
                    Settlement settlement = settlement(state, job.settlementId());
                    return failActiveJob(state, activeTask(state, job), settlement, workshop(settlement), job,
                            ProductionDiagnosticProducer.WORKER_UNAVAILABLE).stream();
                }).toList();
    }

    /**
     * Only COLD market-backed work is safely releasable for immediate settlement defence.
     * A materialized input or even a prepared physical transform remains its own recovery
     * problem and therefore keeps the worker unavailable.
     */
    public static Optional<ProductionJob> interruptibleForSettlementDefence(FrontierWorldState state, SubjectId workerId) {
        return state.productionJobs().values().stream().filter(job -> job.workerId().equals(workerId))
                .filter(job -> coldHold(job.inputHold()))
                .filter(job -> state.physicalIntents().values().stream().noneMatch(intent -> intent.causeSubjectId().equals(job.id())))
                .filter(job -> state.companies().market().acceptedForJob(job.id()).isPresent())
                .filter(job -> CompanyWorkPaymentProcess.contractFor(state, job).isPresent())
                .filter(job -> state.strategicPlans().tasks().get(job.taskId()) instanceof StrategicTask task
                        && task.ownerId().equals(job.settlementId()) && task.kind() == StrategicTaskKind.PRODUCE_BREAD
                        && task.status() == StrategicTaskStatus.ACTIVE)
                .reduce((left, right) -> { throw new IllegalArgumentException("one worker cannot retain two interruptible production jobs"); });
    }

    /** Emits only the explicit worker records that the admitted defender unit must release. */
    public static List<ProposedEvent> planSettlementDefenceInterruptions(FrontierWorldState state, SettlementAssault assault) {
        return assault.defenderIds().stream().sorted().flatMap(worker -> interruptibleForSettlementDefence(state, worker).stream()
                .map(job -> new ProposedEvent(job.settlementId(), new ProductionInterrupted(job.id(), worker, assault.taskId(), assault.sighting())))).toList();
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, ProductionStarted started) {
        return BakeryJobAdmission.admitStarted(state, subject, started);
    }

    public static FrontierWorldState reduceBakeryColdStep(FrontierWorldState state, SubjectId subject, BakeryColdStep step) {
        return BakeryProcess.reduceColdStep(state, subject, step);
    }
    public static FrontierWorldState reduceBakeryHotBlockChanged(FrontierWorldState state, SubjectId subject,
                                                                   BakeryHotBlockChanged changed) {
        return BakeryProcess.hotBlockChanged(state, subject, changed);
    }
    public static FrontierWorldState reduceBakeryHotEffectPrepared(FrontierWorldState state, SubjectId subject, BakeryHotEffectPrepared prepared) {
        return BakeryProcess.prepareHotEffect(state, subject, prepared);
    }
    public static FrontierWorldState reduceBakeryHotEffectObserved(FrontierWorldState state, SubjectId subject, BakeryHotEffectObserved observed) {
        return BakeryProcess.observeHotEffect(state, subject, observed);
    }
    public static FrontierWorldState reduceBakeryHotWorkTick(FrontierWorldState state, SubjectId subject, BakeryHotWorkTick tick) {
        return BakeryProcess.hotWorkTick(state, subject, tick);
    }
    public static FrontierWorldState reduceBakeryHotHandRelease(FrontierWorldState state, SubjectId subject, BakeryHotHandRelease released) {
        return BakeryProcess.releaseHotHand(state, subject, released);
    }
    public static FrontierWorldState reduceBakeryHotHandMaterialized(FrontierWorldState state, SubjectId subject,
                                                                      BakeryHotHandMaterialized observed) {
        return BakeryProcess.materializeHotHand(state, subject, observed);
    }
    public static FrontierWorldState reduceBakeryInputReallocated(FrontierWorldState state, SubjectId subject,
                                                                   BakeryInputReallocated reallocated) {
        return BakeryProcess.reduceInputReallocated(state, subject, reallocated);
    }

    public static FrontierWorldState reduceCompleted(FrontierWorldState state, SubjectId subject, ProductionCompleted completed) {
        ProductionJob job = state.productionJobs().get(completed.jobId());
        if (job == null) throw new IllegalArgumentException("production completion has no active job");
        job.requireColdCompletion(state);
        requireOwner(subject, job.settlementId()); Settlement settlement = settlement(state, job.settlementId()); SettlementStructure workshop = workshop(settlement);
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) throw new IllegalArgumentException("production completion facility is unavailable");
        if (!(completed.output().custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(FrontierWorldState.depotId(settlement.id()))) {
            throw new IllegalArgumentException("production output is not stored in its settlement depot");
        }
        if (ReferenceContainerCustody.blocksCanonicalUse(state, slot.containerId())) throw new IllegalArgumentException("production completion cannot use conflicted depot evidence");
        if (!completed.output().economicOwnerId().equals(job.rights().resourceOwner().id())) throw new IllegalArgumentException("production output claim does not belong to its settlement");
        boolean coldHeld = job.inputHold() instanceof ProductionInputHold.Cold && !state.inventory().items().containsKey(job.consumedItemId());
        boolean releasedMaterialized = job.inputHold() instanceof ProductionInputHold.Materialized
                && !ReferenceContainerCustody.hasLiveCustody(state, slot.containerId())
                && materializedInputMatches(state, job) && !hasStartedProductionTransformation(state, job.id());
        if (!coldHeld && !releasedMaterialized) {
            throw new IllegalArgumentException("production completion requires COLD custody or an unstarted released materialized hold");
        }
        if (state.actorLocations().get(job.workerId()).condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("cold production cannot complete after its worker has died");
        }
        StrategicTask task = activeTask(state, job); validateMarketOrder(state, task, job);
        FrontierWorldState paid = CompanyWorkPaymentProcess.settle(state, job);
        java.util.Optional<MarketWorkOrder> order = paid.companies().market().acceptedForJob(job.id());
        if (order.isPresent()) paid = paid.withCompanies(paid.companies().withMarket(paid.companies().market().complete(order.orElseThrow().id(), job)));
        return paid.completeProductionJob(completed.jobId(), completed.output());
    }

    public static FrontierWorldState reduceFungibleCompleted(FrontierWorldState state, SubjectId subject, FungibleProductionCompleted completed) {
        ProductionJob job = state.productionJobs().get(completed.jobId());
        if (job == null || !(job.inputHold() instanceof ProductionInputHold.FungibleCold held) || !subject.equals(job.settlementId())) {
            throw new IllegalArgumentException("fungible production completion has no owned COLD job");
        }
        job.requireColdCompletion(state);
        Settlement settlement = settlement(state, job.settlementId()); SettlementStructure workshop = workshop(settlement);
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT
                || ReferenceContainerCustody.blocksCanonicalUse(state, FrontierWorldState.depotId(settlement.id()))
                || ReferenceContainerCustody.hasLiveCustody(state, FrontierWorldState.depotId(settlement.id()))) {
            throw new IllegalArgumentException("fungible production completion cannot bypass current physical custody");
        }
        ClaimAllocation claim = state.inventory().fungibleResources().claims().get(held.claimId());
        if (!retainsFungibleInput(state, job, held.accountId(), held.inputLots()) || claim == null
                || claim.quantity() != job.outputCount() || claim.purpose() != ClaimPurpose.PRODUCTION_WORK
                || !claim.claimantId().equals(job.id())
                || !completed.output().lineage().equals(inputLineage(held.inputLots()))) {
            throw new IllegalArgumentException("fungible production completion has no exact reserved wheat input");
        }
        StrategicTask task = activeTask(state, job); validateMarketOrder(state, task, job);
        FrontierWorldState paid = CompanyWorkPaymentProcess.settle(state, job);
        java.util.Optional<MarketWorkOrder> order = paid.companies().market().acceptedForJob(job.id());
        if (order.isPresent()) paid = paid.withCompanies(paid.companies().withMarket(paid.companies().market().complete(order.orElseThrow().id(), job)));
        return paid.completeFungibleProductionJob(job.id(), completed.output());
    }

    /** The scene may progress only this job's retained worker/station state machine. */
    public static FrontierWorldState reduceWorkProgressed(FrontierWorldState state, SubjectId subject, ProductionWorkProgressed progressed) {
        ProductionJob job = state.productionJobs().get(progressed.jobId());
        if (job == null || !subject.equals(job.settlementId())) throw new IllegalArgumentException("production work progress has no owned active job");
        Settlement settlement = settlement(state, job.settlementId()); SettlementStructure workshop = workshop(settlement);
        if (!job.facilityId().equals(workshop.id()) || state.structureConditions().get(workshop.id()) != StructureCondition.INTACT
                || state.actorLocations().get(job.workerId()).condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("production work progress has unavailable worker or facility");
        }
        if (ReferenceContainerCustody.blocksCanonicalUse(state, FrontierWorldState.depotId(job.settlementId()))) {
            throw new IllegalArgumentException("production work progress cannot use conflicted depot evidence");
        }
        ProductionWorkProgress current = job.workProgress(), next = progressed.next();
        int inputCursor = job.workTraversal().linearCorridorSurfaces().size() - 2;
        int workCursor = job.workTraversal().linearCorridorSurfaces().size() - 1;
        SurfaceAnchor observedStation = job.workTraversal().linearCorridorSurfaces().get(job.traversalCursor());
        SceneLease lease = FrontierProductionWorkSceneSupport.requireHotLease(state, job, progressed.leaseId());
        progressed.observation().require(state, job, lease, progressed.observedWorker());
        if (!progressed.observedWorker().equals(observedStation.standingBody())) {
            throw new IllegalArgumentException("production work progress must name the observed retained worker station");
        }
        if (!lease.memberBody(state.actorLocations(), job.workerId()).equals(observedStation.standingBody())) {
            throw new IllegalArgumentException("production work progress must retain the HOT worker at its current station");
        }
        boolean legal = switch (current.stage()) {
            case APPROACH -> next.equals(ProductionWorkProgress.inputReady()) && job.traversalCursor() == inputCursor;
            case INPUT_READY -> next.equals(ProductionWorkProgress.processing(0)) && job.traversalCursor() == workCursor;
            case PROCESSING -> next.stage() == ProductionWorkProgress.Stage.PROCESSING && next.completedTicks() == current.completedTicks() + 1
                    || next.equals(ProductionWorkProgress.outputReady()) && current.completedTicks() == ProductionWorkProgress.REQUIRED_PROCESSING_TICKS - 1;
            case OUTPUT_READY -> false;
        };
        if (!legal) throw new IllegalArgumentException("production work stage transition is not the retained next step");
        return replaceJob(state, job.withWorkProgress(next));
    }

    public static FrontierWorldState reduceWorkTraversalAdvanced(FrontierWorldState state, SubjectId subject, ProductionWorkTraversalAdvanced advanced) {
        ProductionJob job = state.productionJobs().get(advanced.jobId());
        if (job == null || !subject.equals(job.settlementId()) || job.workProgress().stage() == ProductionWorkProgress.Stage.OUTPUT_READY
                || advanced.nextCursor() != job.traversalCursor() + 1 || advanced.nextCursor() >= job.workTraversal().linearCorridorSurfaces().size()) {
            throw new IllegalArgumentException("production work traversal advance is not one retained open edge");
        }
        if (ReferenceContainerCustody.blocksCanonicalUse(state, FrontierWorldState.depotId(job.settlementId()))) {
            throw new IllegalArgumentException("production traversal cannot use conflicted depot evidence");
        }
        FrontierProductionWorkSceneSupport.requireHotLease(state, job, advanced.leaseId());
        if (!advanced.observedWorker().equals(job.workTraversal().linearCorridorSurfaces().get(advanced.nextCursor()).standingBody())) {
            throw new IllegalArgumentException("production work traversal must name its observed next retained station");
        }
        return FrontierProductionWorkSceneSupport.advanceWorker(state, job, job.withWorkTraversal(job.workTraversal(), advanced.nextCursor()),
                advanced.leaseId(), advanced.observedWorker(), advanced.observation());
    }

    /**
     * COLD owns the same retained work route and semantic dwell whenever no physical scene owns
     * its worker.  The final chest transformation remains a separately observed physical intent;
     * this transition never reads or writes Minecraft.  It prevents ordinary first ingress from
     * becoming the event that starts an industrial worker's route.
     */
    public static FrontierWorldState reduceColdWorkAdvanced(FrontierWorldState state, SubjectId subject, ProductionColdWorkAdvanced advanced) {
        ProductionJob job = state.productionJobs().get(advanced.jobId());
        if (job == null || !subject.equals(job.settlementId()) || !ActorExecutionCoordinator.coldAvailable(state, job.workerId())
                || hasOpenWorkScene(state, job.id())) throw new IllegalArgumentException("production cold work has a physical owner");
        var actor = state.actorLocations().get(job.workerId());
        state.actorExecutions().requireCurrent(advanced.execution());
        if (!advanced.execution().actorId().equals(job.workerId()) || actor == null
                || !advanced.expectedBody().equals(actor.body()) || advanced.expectedCursor() != job.traversalCursor()
                || !advanced.expectedProgress().equals(job.workProgress()) || advanced.spatialRevision() != job.spatial().revision())
            throw new IllegalArgumentException("production COLD receipt has stale execution, body or owner predecessor");
        var step = ProductionColdJourney.next(state, job).orElseThrow(
                () -> new IllegalArgumentException("production COLD approach is not known traversable"));
        if (advanced.nextCursor() != step.cursor() || !advanced.next().equals(step.progress()))
            throw new IllegalArgumentException("production COLD receipt is not its exact retained successor");
        ProductionJob replacement = step.cursor() == job.traversalCursor() && step.progress().equals(job.workProgress())
                ? job.withSpatial(step.spatial()) : job.withWorkTraversal(job.workTraversal(), step.cursor())
                    .withWorkProgress(step.progress()).withSpatial(step.spatial());
        ExactInventory inventory = state.inventory();
        java.util.Map<PhysicalIntentId, PhysicalIntent> intents = state.physicalIntents();
        if (job.inputHold() instanceof ProductionInputHold.Materialized) {
            SubjectId depot = FrontierWorldState.depotId(job.settlementId());
            if (ReferenceContainerCustody.hasLiveCustody(state, depot)) {
                throw new IllegalArgumentException("production cold work cannot take a currently leased materialized input");
            }
            ExactItemStack input = inventory.items().get(job.consumedItemId());
            if (input == null || !materializedInputMatches(state, job)) {
                throw new IllegalArgumentException("production cold work has no exact released materialized input");
            }
            java.util.Map<PhysicalIntentId, PhysicalIntent> nextIntents = new java.util.LinkedHashMap<>(intents);
            for (PhysicalIntent intent : intents.values()) if (intent.causeSubjectId().equals(job.id())) {
                if (intent.kind() != PhysicalIntentKind.PRODUCTION_TRANSFORMATION || intent.status() != PhysicalIntentStatus.PREPARED) {
                    throw new IllegalArgumentException("production cold work cannot supersede a started physical transformation");
                }
                nextIntents.remove(intent.id());
            }
            intents = java.util.Map.copyOf(nextIntents);
            inventory = inventory.withoutItem(input.id());
            replacement = replacement.withInputHold(new ProductionInputHold.Cold(input));
        }
        java.util.Map<SubjectId, ActorLocation> actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(job.workerId(), actor.withBody(step.body()));
        java.util.Map<SubjectId, ProductionJob> jobs = new java.util.LinkedHashMap<>(state.productionJobs()); jobs.put(replacement.id(), replacement);
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).physicalIntents(intents)
                .productionJobs(jobs).actorLocations(actors));
    }

    private static List<ProposedEvent> planColdWorkAdvance(FrontierWorldState state, ProductionJob job, ScheduledAction action) {
        if (!ActorExecutionCoordinator.coldAvailable(state, job.workerId()) || hasOpenWorkScene(state, job.id())) {
            return List.of(reschedule(action, complete(job, Math.addExact(action.dueAt().ticks(), 20L))));
        }
        var execution = state.actorExecutions().current(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRODUCTION)
                .get(job.workerId());
        long nextDue = Math.addExact(action.dueAt().ticks(), ProductionWorkProgress.SIMULATION_TICKS_PER_WORK_UNIT);
        if (execution == null || !execution.activityOwnerId().equals(job.id()))
            return List.of(reschedule(action, complete(job, nextDue)));
        var step = ProductionColdJourney.next(state, job);
        if (step.isEmpty()) return List.of(reschedule(action, complete(job, nextDue)));
        var next = step.orElseThrow();
        return List.of(new ProposedEvent(job.settlementId(), new ProductionColdWorkAdvanced(job.id(), next.cursor(), next.progress(),
                execution, state.actorLocations().get(job.workerId()).body(), job.traversalCursor(), job.workProgress(), job.spatial().revision())),
                reschedule(action, complete(job, nextDue)));
    }

    /**
     * A loaded collision is an observation, not authority to invent a detour.  The immutable
     * topology supplies the only legal next surface; a valid report blocks this same work and
     * drains its exact HOT lease through the ordinary release/finalization path.
     */
    public static List<ProposedEvent> planWorkTraversalBlocked(FrontierWorldState state, SubjectId subject,
                                                                 ProductionWorkTraversalBlocked blocked) {
        reduceWorkTraversalBlocked(state, subject, blocked);
        ProductionJob job = state.productionJobs().get(blocked.jobId());
        Settlement settlement = settlement(state, job.settlementId()); StrategicTask task = activeTask(state, job);
        return List.of(new ProposedEvent(settlement.id(), blocked),
                new ProposedEvent(settlement.id(), ProductionDiagnosticProducer.ROUTE_BLOCKED.create(settlement.id(), job.facilityId(), job.id(), task.id())),
                transition(task, StrategicTaskStatus.BLOCKED), new ProposedEvent(settlement.id(), new SceneLeaseTransition(blocked.leaseId(), SceneLeaseStatus.DRAINING)));
    }

    /** Validates immutable worker/cursor evidence.  State changes are represented by the following durable effects. */
    public static FrontierWorldState reduceWorkTraversalBlocked(FrontierWorldState state, SubjectId subject, ProductionWorkTraversalBlocked blocked) {
        ProductionJob job = state.productionJobs().get(blocked.jobId());
        SceneLease assertedLease = state.sceneLeases().get(blocked.leaseId());
        if (assertedLease != null && FrontierSceneBehaviors.isProductionWork(assertedLease)
                && !FrontierSceneBehaviors.productionWork(assertedLease).jobId().equals(blocked.jobId())) {
            throw new IllegalArgumentException("production traversal asserted job differs from exact lease cause");
        }
        if (job == null || !subject.equals(job.settlementId()) || job.workProgress().terminalEffectEligible()
                || blocked.blockedNextCursor() != job.traversalCursor() + 1
                || blocked.blockedNextCursor() >= job.workTraversal().linearCorridorSurfaces().size()) {
            throw new IllegalArgumentException("production work traversal block is not one retained next edge");
        }
        SceneLease lease = FrontierProductionWorkSceneSupport.requireHotLease(state, job, blocked.leaseId());
        blocked.observation().require(state, job, lease, blocked.observedWorker());
        BodyPosition current = job.workTraversal().linearCorridorSurfaces().get(job.traversalCursor()).standingBody();
        if (!blocked.observedWorker().equals(current) || !lease.memberBody(state.actorLocations(), job.workerId()).equals(current)) {
            throw new IllegalArgumentException("production work traversal block must retain its worker at the current cursor");
        }
        return state;
    }

    /**
     * Releases one COLD job only when the current active hive assault names the same observed
     * settlement. This is one atomic canonical transition: the original input is restored,
     * its invoice reservation is released and both market order and production task become
     * terminal before the defender unit can claim the resident.
     */
    public static FrontierWorldState reduceInterrupted(FrontierWorldState state, SubjectId subject, long now, ProductionInterrupted interrupted) {
        ProductionJob job = state.productionJobs().get(interrupted.jobId());
        if (job == null || !subject.equals(job.settlementId()) || !job.workerId().equals(interrupted.workerId())
                || !job.settlementId().equals(interrupted.sighting().settlementId()) || !coldHold(job.inputHold())
                || state.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(job.id()))) {
            throw new IllegalArgumentException("production interruption must retain one untouched COLD job at its defended settlement");
        }
        StrategicTask defence = state.strategicPlans().tasks().get(interrupted.assaultTaskId());
        if (defence == null || defence.kind() != StrategicTaskKind.ASSAULT_SETTLEMENT || defence.status() != StrategicTaskStatus.ACTIVE
                || !defence.ownerId().equals(state.bootstrap().hive().id()) || interrupted.sighting().observedAt() < Math.subtractExact(now,
                state.bootstrap().ruleset().cadence().hiveSettlementKnowledgeMaxAge()) || !interrupted.sighting().equals(
                state.strategicPlans().hiveSettlementKnowledge().entries().get(interrupted.sighting().settlementId()))) {
            throw new IllegalArgumentException("production interruption must retain one current active settlement assault cause");
        }
        StrategicTask production = activeTask(state, job);
        MarketWorkOrder order = state.companies().market().acceptedForJob(job.id()).orElseThrow(() ->
                new IllegalArgumentException("interrupted production must retain one accepted market work order"));
        EmploymentContract contract = CompanyWorkPaymentProcess.contractFor(state, job).orElseThrow(() ->
                new IllegalArgumentException("interrupted production must retain its active worker contract"));
        FinancialReservation reservation = CompanyWorkPaymentProcess.reservation(job, contract);
        if (!order.reservationId().equals(reservation.id()) || !order.sellerId().equals(contract.companyId())
                || !state.inventory().economics().reservations().containsKey(reservation.id())) {
            throw new IllegalArgumentException("production interruption has no matching exact financial reservation");
        }
        FrontierWorldState released = state.cancelProductionJob(job.id());
        ExactInventory inventory = released.inventory().withEconomics(released.inventory().economics().release(reservation.id()));
        StrategicPlanState plans = released.strategicPlans().transitionTask(production.id(), StrategicTaskStatus.BLOCKED);
        return released.withInventory(inventory).withCompanies(released.companies().withMarket(
                released.companies().market().cancel(order.id(), MarketWorkOrderStatus.CANCELLED))).withStrategicPlans(plans);
    }

    public static FrontierWorldState reduceBlocked(FrontierWorldState state, SubjectId subject, ProductionBlocked blocked) {
        requireOwner(subject, blocked.settlementId()); Settlement settlement = settlement(state, blocked.settlementId()); SettlementStructure workshop = workshop(settlement);
        if (!workshop.id().equals(blocked.facilityId())) throw new IllegalArgumentException("production block refers to a foreign facility");
        ProductionJob retainedJob = state.productionJobs().get(blocked.workId());
        StrategicTask declaredTask = task(state, blocked.taskId(), retainedJob == null ? StrategicTaskStatus.PENDING : StrategicTaskStatus.ACTIVE);
        if (!declaredTask.ownerId().equals(settlement.id()) || retainedJob != null && !retainedJob.taskId().equals(declaredTask.id())) {
            throw new IllegalArgumentException("production block must name the exact owner task of its job or pending work");
        }
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        boolean wheatPresent = BakeryBatchSelection.available(state, settlement.id()).isPresent();
        switch (blocked.reason()) {
            case INPUT_UNAVAILABLE -> {
                ProductionJob job = state.productionJobs().get(blocked.workId());
                if (job != null) {
                    boolean unavailable = ReferenceContainerCustody.blocksCanonicalUse(state, depot)
                            || (job.inputHold() instanceof ProductionInputHold.Materialized && !materializedInputMatches(state, job));
                    if (!job.settlementId().equals(settlement.id()) || !unavailable) {
                        throw new IllegalArgumentException("materialized production input block precondition does not hold");
                    }
                    break;
                }
                // A changed/foreign/missing depot replica is a distinct, local input
                // unavailability: canonical wheat is deliberately retained, but this exact
                // depot must not admit it until its evidence is reconciled.  Bind that form to
                // the depot identity so it cannot be forged as an ordinary no-input block.
                boolean conflictedDepot = blocked.workId().equals(depot) && ReferenceContainerCustody.blocksCanonicalUse(state, depot);
                if (!conflictedDepot && (!blocked.workId().equals(workshop.id()) || wheatPresent
                        || state.structureConditions().get(workshop.id()) != StructureCondition.INTACT)) {
                    throw new IllegalArgumentException("production input block precondition does not hold");
                }
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
                        || FrontierWorldStateSupport.availableWorkResident(state, settlement.id(), ResidentWorkKind.BAKING, HumanCapability.INDUSTRY).isPresent()) {
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
                StrategicTask pending = declaredTask;
                var prospective = BakeryBatchSelection.admissible(state, settlement.id());
                if (!blocked.workId().equals(workshop.id()) || prospective.isEmpty()
                        || FrontierWorldStateSupport.availableWorkResident(state, settlement.id(), ResidentWorkKind.BAKING, HumanCapability.INDUSTRY).isEmpty()) {
                    throw new IllegalArgumentException("production finance start block precondition does not hold");
                }
                if (SettlementWorkforce.candidates(state, settlement.id(), ResidentWorkKind.BAKING, HumanCapability.INDUSTRY).stream()
                        .map(worker -> BakeryJobAdmission.propose(state, pending, settlement, workshop,
                                prospective.orElseThrow().exactInput(), prospective.orElseThrow().fungibleInput(), worker))
                        .anyMatch(candidate -> CompanyWorkPaymentProcess.canReserve(state, candidate))) {
                    throw new IllegalArgumentException("production finance start block has available funds");
                }
            }
            case ROUTE_BLOCKED -> {
                ProductionJob job = state.productionJobs().get(blocked.workId());
                if (job == null || !job.settlementId().equals(settlement.id()) || job.workProgress().terminalEffectEligible()
                        || !hasOpenWorkScene(state, job.id())) {
                    throw new IllegalArgumentException("production route block lacks its retained active worker scene");
                }
            }
            case RELATIONSHIP_CONFLICT -> {
                ProductionJob job = state.productionJobs().get(blocked.workId());
                MarketWorkOrder order = job == null ? null : state.companies().market().workOrders().values().stream()
                        .filter(value -> value.jobId().equals(job.id()) && value.status() == MarketWorkOrderStatus.CONFLICT).findFirst().orElse(null);
                if (job == null || !job.settlementId().equals(settlement.id()) || order == null
                        || order.relationshipIncident().isEmpty() || !order.relationshipIncident().orElseThrow().ownerId().equals(order.id())) {
                    throw new IllegalArgumentException("production relationship conflict lacks its exact retained order incident");
                }
            }
        }
        ProductionJob job = state.productionJobs().get(blocked.workId());
        if (job == null || state.companies().market().acceptedForJob(job.id()).isPresent() || hasOpenWorkScene(state, job.id())) return state;
        if (job.bakeryWork().isPresent()) return state;
        return state.cancelProductionJob(job.id());
    }

    /** Final cancellation is deliberately after SceneLeaseReleased: the closed lease is the durable body-exit receipt. */
    public static FrontierWorldState reduceWorkSceneFinalized(FrontierWorldState state, SubjectId subject, ProductionWorkSceneFinalized finalized) {
        SceneLease lease = state.sceneLeases().get(finalized.leaseId()); ProductionJob job = state.productionJobs().get(finalized.jobId());
        if (lease == null || lease.status() != SceneLeaseStatus.CLOSED || !FrontierSceneBehaviors.isProductionWork(lease)
                || !FrontierSceneBehaviors.productionWork(lease).jobId().equals(finalized.jobId()) || job == null || !subject.equals(job.settlementId())) {
            throw new IllegalArgumentException("production scene finalization lacks its closed exact job scene");
        }
        if (job.bakeryWork().isPresent()) {
            // Releasing a failed scene does not retire actor/station cargo. The blocked job
            // remains its exact custodian until a later explicit recovery/compensation flow.
            task(state, job.taskId(), StrategicTaskStatus.BLOCKED);
            return state;
        }
        Optional<MarketWorkOrder> order = state.companies().market().acceptedForJob(job.id());
        if (order.isEmpty()) order = state.companies().market().workOrders().values().stream()
                .filter(value -> value.jobId().equals(job.id()) && value.status() == MarketWorkOrderStatus.CONFLICT && value.relationshipIncident().isPresent()).findFirst();
        StrategicTask blockedTask = task(state, job.taskId(), StrategicTaskStatus.BLOCKED);
        if (!blockedTask.ownerId().equals(job.settlementId())
                || order.isPresent() && !order.orElseThrow().taskId().equals(job.taskId())) {
            throw new IllegalArgumentException("production scene finalization lacks its exact job/task/order relation");
        }
        if (order.isEmpty()) return state.cancelProductionJob(job.id());
        FinancialReservation reservation = state.inventory().economics().reservations().get(order.orElseThrow().reservationId());
        if (reservation == null || !reservation.reasonId().equals(job.id()) || !reservation.payerId().equals(job.settlementId())) {
            throw new IllegalArgumentException("production scene finalization has no exact market reservation");
        }
        FrontierWorldState released = state.cancelProductionJob(job.id());
        FrontierWorldState reservationReleased = released.withInventory(released.inventory().withEconomics(released.inventory().economics().release(reservation.id())));
        if (order.orElseThrow().status() == MarketWorkOrderStatus.CONFLICT) {
            return reservationReleased;
        }
        return reservationReleased.withCompanies(reservationReleased.companies().withMarket(
                reservationReleased.companies().market().cancel(order.orElseThrow().id(), MarketWorkOrderStatus.CANCELLED)));
    }

    private static List<ProposedEvent> blocked(StrategicTask task, Settlement settlement, SettlementStructure workshop, SubjectId work, ProductionDiagnosticProducer producer) {
        return List.of(new ProposedEvent(settlement.id(), producer.create(settlement.id(), workshop.id(), work, task.id())), transition(task, StrategicTaskStatus.BLOCKED));
    }
    static List<ProposedEvent> failActiveJob(FrontierWorldState state, StrategicTask task, Settlement settlement, SettlementStructure workshop,
                                                      ProductionJob job, ProductionDiagnosticProducer producer) {
        if (job.bakeryWork().isPresent())
            return blocked(task, settlement, workshop, job.id(), producer);
        Optional<MarketWorkOrder> order = state.companies().market().acceptedForJob(job.id());
        if (order.isEmpty() || hasOpenWorkScene(state, job.id())) return blocked(task, settlement, workshop, job.id(), producer);
        return List.of(new ProposedEvent(settlement.id(), producer.create(settlement.id(), workshop.id(), job.id(), task.id())),
                new ProposedEvent(settlement.id(), new MarketWorkOrderCancelled(order.orElseThrow().id(), job.id(), producer.reason())));
    }
    /**
     * A player/world custody observation may remove the exact input before a materialized
     * transform begins.  A COLD job may retire in that transaction. A loaded worker scene
     * instead enters its normal DRAINING → CLOSED → finalized protocol: its named body must
     * release before the job and market reservation retire. Once an intent exists, the effect
     * owns recovery rather than fabricating a refund.
     */
    public static List<ProposedEvent> planMaterializedInputDeparture(FrontierWorldState state, SubjectId itemId, ProposedEvent observation) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(itemId, "item id"); Objects.requireNonNull(observation, "observation");
        ProductionJob job = state.productionJobs().values().stream().filter(candidate -> candidate.consumedItemId().equals(itemId)
                && candidate.inputHold() instanceof ProductionInputHold.Materialized).reduce((left, right) -> {
                    throw new IllegalStateException("one exact materialized input cannot belong to two production jobs");
                }).orElse(null);
        if (job == null || state.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(job.id()))) {
            return List.of(observation);
        }
        // Bakery owns a retained order and replaces an uncollected depot input. The
        // physical observation atomically records its local source block in the reducer.
        if (job.bakeryWork().isPresent()
                && job.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.DEPOT_PICKUP)
            return List.of(observation);
        MarketWorkOrder order = state.companies().market().acceptedForJob(job.id()).orElseThrow(() ->
                new IllegalArgumentException("a materialized production input without an effect must retain its accepted market order"));
        Settlement settlement = settlement(state, job.settlementId()); StrategicTask task = activeTask(state, job);
        SceneLease liveScene = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isProductionWork)
                .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED && FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id()))
                .reduce((left, right) -> { throw new IllegalArgumentException("materialized production input has multiple live worker scenes"); }).orElse(null);
        if (liveScene != null) {
            java.util.List<ProposedEvent> events = new java.util.ArrayList<>();
            events.add(observation);
            events.add(new ProposedEvent(settlement.id(), ProductionDiagnosticProducer.INPUT_UNAVAILABLE.create(settlement.id(), job.facilityId(), job.id(), task.id())));
            events.add(transition(task, StrategicTaskStatus.BLOCKED));
            if (liveScene.status() == SceneLeaseStatus.PREPARED) {
                // No scene body was authoritative yet. Close the retained lease by a typed
                // pre-effect receipt, then apply the same job/order finalizer as a HOT release.
                events.add(new ProposedEvent(settlement.id(), new ProductionWorkScenePreparationAborted(liveScene.id(), job.id())));
                events.add(new ProposedEvent(settlement.id(), new ProductionWorkSceneFinalized(liveScene.id(), job.id())));
                return java.util.List.copyOf(events);
            }
            if (liveScene.status() != SceneLeaseStatus.HOT && liveScene.status() != SceneLeaseStatus.DRAINING) {
                throw new IllegalArgumentException("materialized production input departed from an unresolved worker scene");
            }
            if (liveScene.status() == SceneLeaseStatus.HOT) {
                events.add(new ProposedEvent(settlement.id(), new SceneLeaseTransition(liveScene.id(), SceneLeaseStatus.DRAINING)));
            }
            return java.util.List.copyOf(events);
        }
        return List.of(observation, new ProposedEvent(settlement.id(), ProductionDiagnosticProducer.INPUT_UNAVAILABLE.create(settlement.id(), job.facilityId(), job.id(), task.id())),
                new ProposedEvent(settlement.id(), new MarketWorkOrderCancelled(order.id(), job.id(), ProductionBlockReason.INPUT_UNAVAILABLE)));
    }

    /**
     * Turns an already accepted physical loss of a workshop into the same durable cancellation
     * protocol as any other pre-effect production failure.  In particular, a HOT worker is not
     * silently left working until a later strategic timer notices the damage: the job is blocked
     * first and its exact scene then drains and releases normally.
     *
     * <p>Post-effect intents deliberately remain their own recovery question.  A destroyed
     * workshop cannot retrospectively decide whether an already durable-before-effect output
     * replacement happened.</p>
     */
    public static List<ProposedEvent> planFacilityUnavailable(FrontierWorldState state, SubjectId facilityId) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(facilityId, "facility id");
        List<ProposedEvent> events = new java.util.ArrayList<>();
        for (ProductionJob job : state.productionJobs().values().stream().filter(candidate -> candidate.facilityId().equals(facilityId))
                .sorted(Comparator.comparing(ProductionJob::id)).toList()) {
            if (state.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(job.id()))) continue;
            Settlement settlement = settlement(state, job.settlementId());
            StrategicTask task = state.strategicPlans().tasks().get(job.taskId());
            if (task == null || task.kind() != StrategicTaskKind.PRODUCE_BREAD || !task.ownerId().equals(job.settlementId())) {
                throw new IllegalArgumentException("production facility loss has no exact job task");
            }
            if (task.status() != StrategicTaskStatus.ACTIVE) continue;
            events.add(new ProposedEvent(settlement.id(), ProductionDiagnosticProducer.FACILITY_UNAVAILABLE.create(settlement.id(), job.facilityId(), job.id(), task.id())));
            events.add(transition(task, StrategicTaskStatus.BLOCKED));
            SceneLease scene = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isProductionWork)
                    .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED && FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id()))
                    .reduce((left, right) -> { throw new IllegalArgumentException("production facility loss has multiple live worker scenes"); }).orElse(null);
            if (scene != null) {
                if (scene.status() == SceneLeaseStatus.PREPARED) {
                    events.add(new ProposedEvent(settlement.id(), new ProductionWorkScenePreparationAborted(scene.id(), job.id())));
                    events.add(new ProposedEvent(settlement.id(), new ProductionWorkSceneFinalized(scene.id(), job.id())));
                } else if (scene.status() == SceneLeaseStatus.HOT) {
                    events.add(new ProposedEvent(settlement.id(), new SceneLeaseTransition(scene.id(), SceneLeaseStatus.DRAINING)));
                } else if (scene.status() != SceneLeaseStatus.DRAINING) {
                    throw new IllegalArgumentException("production facility loss has an unresolved worker scene");
                }
                continue;
            }
            if (job.bakeryWork().isPresent())
                continue; // A bakery allocation remains owned by its accepted job even before pickup.
            state.companies().market().acceptedForJob(job.id()).ifPresent(order -> events.add(new ProposedEvent(settlement.id(),
                    new MarketWorkOrderCancelled(order.id(), job.id(), ProductionBlockReason.FACILITY_UNAVAILABLE))));
        }
        return List.copyOf(events);
    }

    public static FrontierWorldState reduceWorkScenePreparationAborted(FrontierWorldState state, SubjectId subject,
                                                                        ProductionWorkScenePreparationAborted aborted) {
        SceneLease lease = state.sceneLeases().get(aborted.leaseId()); ProductionJob job = state.productionJobs().get(aborted.jobId());
        if (lease == null || lease.status() != SceneLeaseStatus.PREPARED || !FrontierSceneBehaviors.isProductionWork(lease)
                || !FrontierSceneBehaviors.productionWork(lease).jobId().equals(aborted.jobId()) || job == null
                || !subject.equals(job.settlementId()) || job.workProgress().terminalEffectEligible()
                || state.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(job.id()))) {
            throw new IllegalArgumentException("production preparation abort lacks one exact pre-effect job scene");
        }
        return FrontierSceneLeaseStateSupport.abortPrepared(state, aborted.leaseId());
    }
    private static StrategicTask task(FrontierWorldState state, SubjectId id, StrategicTaskStatus status) {
        StrategicTask task = state.strategicPlans().tasks().get(id);
        if (task == null || task.kind() != StrategicTaskKind.PRODUCE_BREAD || task.status() != status) throw new IllegalStateException("production schedule has no matching strategic task");
        return task;
    }
    static StrategicTask activeTask(FrontierWorldState state, ProductionJob job) {
        StrategicTask task = task(state, job.taskId(), StrategicTaskStatus.ACTIVE);
        if (!task.ownerId().equals(job.settlementId()))
            throw new IllegalArgumentException("production job declares a foreign strategic task owner");
        state.companies().market().acceptedForJob(job.id()).ifPresent(order -> {
            if (!order.taskId().equals(task.id()))
                throw new IllegalArgumentException("production market order contradicts the job's declared task");
        });
        return task;
    }
    private static boolean materializedInputMatches(FrontierWorldState state, ProductionJob job) {
        ExactItemStack input = state.inventory().items().get(job.consumedItemId());
        return input != null && input.economicOwnerId().equals(job.rights().resourceOwner().id()) && WHEAT.equals(input.itemKind()) && input.count() == job.outputCount()
                && input.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(FrontierWorldState.depotId(job.settlementId()));
    }
    public static boolean completionHeld(FrontierWorldState state, ScheduledAction action) {
        ProductionJob job = state.productionJobs().get(action.subject());
        return job != null && action.equals(complete(job, action.dueAt().ticks())) && hasOpenWorkScene(state, job.id());
    }

    private static boolean hasOpenWorkScene(FrontierWorldState state, SubjectId jobId) {
        return state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isProductionWork)
                .anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED && FrontierSceneBehaviors.productionWork(lease).jobId().equals(jobId));
    }
    private static boolean hasStartedProductionTransformation(FrontierWorldState state, SubjectId jobId) {
        return state.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(jobId)
                && intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION && intent.status() != PhysicalIntentStatus.PREPARED);
    }
    private static FrontierWorldState replaceJob(FrontierWorldState state, ProductionJob replacement) {
        return FrontierProductionWorkSceneSupport.replaceJob(state, replacement);
    }
    static Settlement settlement(FrontierWorldState state, SubjectId id) { return FrontierWorldStateSupport.settlement(state.bootstrap(), id); }
    private static boolean retainsFungibleInput(FrontierWorldState state, ProductionJob job, SubjectId accountId,
                                                java.util.Map<SubjectId, Integer> inputLots) {
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        CustodyAccount account = resources.accounts().get(accountId);
        return account != null && inputLots.values().stream().mapToInt(Integer::intValue).sum() == job.outputCount()
                && inputLots.entrySet().stream().allMatch(entry -> {
                    ResourceLot lot = resources.lots().get(entry.getKey());
                    return lot != null && lot.economicOwnerId().equals(job.rights().resourceOwner().id()) && WHEAT.equals(lot.itemKind())
                            && account.lotQuantities().getOrDefault(lot.id(), 0) >= entry.getValue();
                });
    }
    private static List<SubjectId> inputLineage(java.util.Map<SubjectId, Integer> inputLots) {
        return inputLots.keySet().stream().sorted().toList();
    }
    static SettlementStructure workshop(Settlement settlement) { return settlement.structures().stream().filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst()
            .orElseThrow(() -> new IllegalStateException("settlement lacks workshop")); }
    private static boolean coldHold(ProductionInputHold hold) {
        return hold instanceof ProductionInputHold.Cold || hold instanceof ProductionInputHold.FungibleCold;
    }
    public static SubjectId jobId(StrategicTask task) {
        if (task.kind() != StrategicTaskKind.PRODUCE_BREAD) {
            throw new IllegalArgumentException("production job identity requires a bread task");
        }
        // Review ordinals are local to schedule families. A stock wake can reuse
        // one while a previous commercial order remains in the bounded audit.
        return new SubjectId("job:production-" + task.id().value().substring("task:".length()));
    }
    static void validateMarketOrder(FrontierWorldState state, StrategicTask task, ProductionJob job) {
        if (job.rights().mode() == ProductionRights.Mode.COMPANY_OWN_ACCOUNT) {
            job.rights().validate(state, job);
            if (state.strategicPlans().objectives().get(task.objectiveId()).kind() != StrategicObjectiveKind.SETTLEMENT_COMPANY_PRODUCTION
                    || CompanyWorkPaymentProcess.contractFor(state, job).isEmpty()
                    || state.companies().market().acceptedForJob(job.id()).isPresent())
                throw new IllegalArgumentException("own-account production requires a separate company proposal and exact employment, not a public invoice");
            return;
        }
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
    /**
     * The one durable completion review for an admitted exact job.  Test fixtures may use this
     * public scheduler boundary, but may not invent an alternate completion kind or schedule
     * identity for the same job.
     */
    public static ScheduledAction complete(ProductionJob job, long due) { return new ScheduledAction(new ScheduleId("schedule:production-task-complete-" + job.id().value().substring("job:".length())),
            new SimInstant(due), 0, job.id(), "frontier.settlement.production.task.complete", 1); }
    private static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) { return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status)); }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    static ProposedEvent reschedule(ScheduledAction current, ScheduledAction replacement) {
        if (!current.id().equals(replacement.id())) throw new IllegalArgumentException("production completion reschedule must retain its stable identity");
        return new ProposedEvent(current.subject(), new ScheduleEffect.Rescheduled(current.id(), replacement));
    }
    private static FixedPosition fixed(BlockPosition position) { return new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z())); }
    private static void requireOwner(SubjectId actual, SubjectId expected) { if (!expected.equals(actual)) throw new IllegalArgumentException("production event subject does not own the work"); }
}
