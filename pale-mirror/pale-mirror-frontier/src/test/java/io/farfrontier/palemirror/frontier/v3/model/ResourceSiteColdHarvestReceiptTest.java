package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSiteColdHarvestReceiptTest {
    @Test
    void terminalOwnerAtomicallyRetiresItsExactIntentAndContinuationWhileUnknownOrExpiredRenewalTailsFailClosed() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState state = matureField();
        List<ProposedEvent> opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 22_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, new SubjectId("settlement:1"),
                (StrategicTaskPlanned) opportunity.get(1).payload());
        StrategicTask task = state.strategicPlans().tasks().values().stream()
                .filter(candidate -> candidate.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
        List<ProposedEvent> planned = ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(task, 22_100L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        state = ResourceSiteHarvestProcess.reduceStarted(state, site, started);
        state = ResourceSiteHarvestProcess.reducePrepared(state, site, ((PhysicalIntentPrepared) planned.get(2).payload()).intent());
        ScheduledAction firstAction = ((ScheduleEffect.Created) planned.get(3).payload()).action();
        ColdHarvestPendingTerminal pending = advanceBeforeTerminal(state, site, firstAction);

        var base = FrontierWorldRuntimeDefinition.configuration(pending.state().bootstrap().worldId(), 125L);
        var configuration = new FrontierEngineConfiguration<>(pending.state().bootstrap().worldId(), pending.state(), base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(pending.action()), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var engine = FrontierEngines.create(configuration);
        var result = engine.advanceTo(pending.action().dueAt(), new WorkBudget(1, pending.action().weight()));
        FrontierWorldState terminal = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec()
                .decode(engine.checkpoint().canonicalState());

        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse(""));
        assertFalse(terminal.physicalIntents().containsKey(started.job().intentId()),
                "the owner transition cannot retire its harvest job while retaining the associated physical request");
        assertFalse(engine.checkpoint().schedules().stream().anyMatch(action -> action.id().equals(pending.action().id())),
                "the same WAL transaction must cancel the retired job's durable continuation");
        assertTrue(engine.checkpoint().schedules().stream().anyMatch(action -> action.kind().equals("frontier.resource_site.growth")),
                "terminal retirement retains only the next lifecycle owner's growth continuation");
        assertEquals(List.of(new ScheduleEffect.Consumed(pending.action().id())),
                ResourceSiteHarvestProcess.planColdProgress(terminal, pending.action()).stream().map(ProposedEvent::payload).toList(),
                "a recovery tail is consumable only through the retained resolved terminal lineage");
        ScheduledAction unknown = new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:resource-site-harvest-cold-progress-site-harvest-forged"),
                pending.action().dueAt(), 0, new SubjectId("job:site-harvest-forged"), ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, 1);
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.planColdProgress(terminal, unknown),
                "an unclassified durable action must not be silently consumed as a late materialization tail");

        FrontierWorldState renewed = terminal;
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle lifecycle = renewed.resourceSites().site(site);
            renewed = ResourceSiteProcess.reduceGrowth(renewed, site,
                    new ResourceSiteGrowthAdvanced(site, lifecycle.growthEpoch(), lifecycle.growthStage()));
        }
        List<ProposedEvent> successorOpportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(renewed,
                StrategicObjectiveProcess.resourceHarvestOpportunity(renewed, renewed.resourceSites().site(site), 40_000L));
        renewed = StrategicObjectiveProcess.reduceObjective(renewed, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) successorOpportunity.getFirst().payload());
        renewed = StrategicObjectiveProcess.reduceTask(renewed, new SubjectId("settlement:1"),
                (StrategicTaskPlanned) successorOpportunity.get(1).payload());
        StrategicTask successorTask = renewed.strategicPlans().tasks().values().stream()
                .filter(candidate -> candidate.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE && candidate.status() == StrategicTaskStatus.PENDING).findFirst().orElseThrow();
        List<ProposedEvent> successorPlan = ResourceSiteHarvestProcess.plan(renewed, ResourceSiteHarvestProcess.start(successorTask, 40_100L));
        ResourceSiteHarvestStarted successor = (ResourceSiteHarvestStarted) successorPlan.stream().map(ProposedEvent::payload)
                .filter(ResourceSiteHarvestStarted.class::isInstance).findFirst().orElseThrow();
        renewed = StrategicObjectiveProcess.reduceTaskTransition(renewed, new SubjectId("settlement:1"),
                (StrategicTaskTransition) successorPlan.getFirst().payload());
        renewed = ResourceSiteHarvestProcess.reduceStarted(renewed, site, successor);
        renewed = ResourceSiteHarvestProcess.reducePrepared(renewed, site, ((PhysicalIntentPrepared) successorPlan.stream().map(ProposedEvent::payload)
                .filter(PhysicalIntentPrepared.class::isInstance).findFirst().orElseThrow()).intent());
        ScheduledAction successorAction = ((ScheduleEffect.Created) successorPlan.stream().map(ProposedEvent::payload)
                .filter(ScheduleEffect.Created.class::isInstance).findFirst().orElseThrow()).action();
        renewed = completeColdHarvest(renewed, site, successorAction);
        FrontierWorldState renewedTerminal = renewed;
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.planColdProgress(renewedTerminal, pending.action()),
                "after a renewable owner is replaced, the older action has no retained bounded disposition and fails closed");
    }

    @Test
    void zeroPlayerColdHarvestContinuesBeyondTheFirstDurableCropReceiptBeforeAnyHotLease() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState state = matureField();
        List<ProposedEvent> opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 22_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, new SubjectId("settlement:1"),
                (StrategicTaskPlanned) opportunity.get(1).payload());
        StrategicTask task = state.strategicPlans().tasks().values().stream()
                .filter(candidate -> candidate.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
        List<ProposedEvent> planned = ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(task, 22_100L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        state = ResourceSiteHarvestProcess.reduceStarted(state, site, started);
        state = ResourceSiteHarvestProcess.reducePrepared(state, site, ((PhysicalIntentPrepared) planned.get(2).payload()).intent());
        ScheduledAction[] action = { ((ScheduleEffect.Created) planned.get(3).payload()).action() };

        while (((ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow()).progress().completedCropSlots() == 0) {
            for (ProposedEvent event : ResourceSiteHarvestProcess.planColdProgress(state, action[0])) {
                if (event.payload() instanceof ResourceSiteHarvestColdTraversalAdvanced traversal) state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site, traversal);
                else if (event.payload() instanceof ResourceSiteHarvestCropPrepared crop) state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site, crop);
                else if (event.payload() instanceof ResourceSiteHarvestProgressed secondProgress) state = ResourceSiteHarvestProcess.reduceProgressed(state, site, secondProgress);
                else if (event.payload() instanceof ScheduleEffect.Rescheduled rescheduled) action[0] = rescheduled.replacement();
                else throw new AssertionError("unexpected COLD harvest event: " + event.payload());
            }
        }

        ResourceSiteHarvestJob progressed = (ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow();
        assertEquals(1, progressed.progress().completedCropSlots());
        assertEquals(-1, progressed.progress().pendingCropSlotIndex());
        assertEquals(PhysicalIntentStatus.PREPARED, state.physicalIntents().get(progressed.intentId()).status());
        assertFalse(state.inventory().items().containsKey(progressed.outputItemId()));
        // A prior implementation deliberately retained the same action after crop one.  That
        // made elapsed zero-player time a semantic no-op and parked every first ingress at the
        // same slot.  Prove the next complete COLD station/receipt cycle is durable too.
        while (((ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow()).progress().completedCropSlots() == 1) {
            for (ProposedEvent event : ResourceSiteHarvestProcess.planColdProgress(state, action[0])) {
                if (event.payload() instanceof ResourceSiteHarvestColdTraversalAdvanced traversal) state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site, traversal);
                else if (event.payload() instanceof ResourceSiteHarvestCropPrepared crop) state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site, crop);
                else if (event.payload() instanceof ResourceSiteHarvestProgressed continuingProgress) state = ResourceSiteHarvestProcess.reduceProgressed(state, site, continuingProgress);
                else if (event.payload() instanceof ScheduleEffect.Rescheduled rescheduled) action[0] = rescheduled.replacement();
                else throw new AssertionError("unexpected continuing COLD harvest event: " + event.payload());
            }
        }
        ResourceSiteHarvestJob continued = (ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow();
        assertEquals(2, continued.progress().completedCropSlots());
        assertEquals(2, continued.progress().nextCropSlotIndex(), "the next ingress is no longer parked at crop one");
    }

    @Test
    void coldHarvestOwnsOutputAndAdmitsItsExactSuccessorBeforeLaterPhysicalReceipt() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState state = matureField();
        List<ProposedEvent> opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 22_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, new SubjectId("settlement:1"),
                (StrategicTaskPlanned) opportunity.get(1).payload());
        StrategicTask task = state.strategicPlans().tasks().values().stream()
                .filter(candidate -> candidate.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
        List<ProposedEvent> planned = ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(task, 22_100L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        state = ResourceSiteHarvestProcess.reduceStarted(state, site, started);
        state = ResourceSiteHarvestProcess.reducePrepared(state, site, ((PhysicalIntentPrepared) planned.get(2).payload()).intent());
        ScheduledAction[] action = { ((ScheduleEffect.Created) planned.get(3).payload()).action() };

        while (state.resourceSites().site(site).phase() == ResourceSitePhase.HARVESTING) {
            for (ProposedEvent event : ResourceSiteHarvestProcess.planColdProgress(state, action[0])) {
                if (event.payload() instanceof ResourceSiteHarvestColdTraversalAdvanced traversal) state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site, traversal);
                else if (event.payload() instanceof ResourceSiteHarvestCropPrepared crop) state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site, crop);
                else if (event.payload() instanceof ResourceSiteHarvestProgressed progressed) state = ResourceSiteHarvestProcess.reduceProgressed(state, site, progressed);
                else if (event.payload() instanceof StrategicTaskTransition transition) {
                    state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"), transition);
                }
                else if (event.payload() instanceof ScheduleEffect.Rescheduled rescheduled) action[0] = rescheduled.replacement();
                else if (event.payload() instanceof ScheduleEffect.Cancelled || event.payload() instanceof ScheduleEffect.Created) { }
                else throw new AssertionError("unexpected terminal COLD harvest event: " + event.payload());
            }
        }

        ResourceSiteHarvestLineage complete = state.resourceSites().site(site).harvestLineage().orElseThrow();
        assertEquals(ResourceSitePhase.GROWING, state.resourceSites().site(site).phase());
        assertFalse(complete.receiptPending(), "COLD composition closes the old physical request instead of retaining an unbounded pending receipt");
        assertEquals(started.job().traversal().linearCorridorSurfaces().getLast().standingBody(),
                state.actorLocations().get(complete.workerId()).body(), "first visibility retains the exact completed field station");
        assertEquals(state.actorLocations().get(complete.workerId()).body(), complete.terminalBody(),
                "the deferred receipt retains its exact terminal worker station across the lifecycle boundary");
        assertFalse(state.physicalIntents().containsKey(complete.predecessorIntentId()),
                "COLD completion retires its composed unstarted physical effect rather than leaving an orphan PREPARED subject set");
        assertEquals(FencedRecoveryDisposition.REJECT_STALE,
                state.fencedRecovery().tombstones().values().stream()
                        .filter(tombstone -> tombstone.ownerId().equals(site) && tombstone.reason().equals("canonical-composition"))
                        .findFirst().orElseThrow().disposition(),
                "the exact composition owner fences any late old-wheat materialization without calling it observed");
        assertEquals(StrategicTaskStatus.COMPLETED, state.strategicPlans().tasks().get(started.job().taskId()).status());
        ExactItemStack output = new ExactItemStack(complete.outputItemId(), new SubjectId("settlement:1"), "minecraft:wheat", 64, complete.outputSlot());
        assertEquals(output, state.inventory().items().get(complete.outputItemId()),
                "COLD closes the terminal crop into one exact canonical depot claim before any player loads the field");
        FrontierWorldState restored = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state));
        assertEquals(complete, restored.resourceSites().site(site).harvestLineage().orElseThrow(), "restart retains the same exact terminal COLD receipt lineage");
        assertFalse(restored.physicalIntents().containsKey(complete.predecessorIntentId()),
                "restart retains the retired composition boundary rather than replaying or falsely starting harvest work");
        assertEquals(output, restored.inventory().items().get(complete.outputItemId()),
                "restart retains the same output custody independently from later materialization");

        StrategicObjective breadObjective = new StrategicObjective(new SubjectId("objective:deferred-harvest-bread"),
                new SubjectId("settlement:1"), StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, java.util.Optional.empty(), 98,
                StrategicObjectiveStatus.ACTIVE);
        StrategicTask breadTask = new StrategicTask(new SubjectId("task:deferred-harvest-bread"), breadObjective.id(),
                new SubjectId("settlement:1"), StrategicTaskKind.PRODUCE_BREAD, java.util.Optional.empty(),
                List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT), List.of(), StrategicTaskStatus.PENDING);
        FrontierWorldState beforeReceipt = state.withStrategicPlans(state.strategicPlans().addObjective(breadObjective).addTask(breadTask));
        List<ProposedEvent> downstream = ProductionProcess.planStart(beforeReceipt, ProductionProcess.start(breadTask, 30_000L));
        assertFalse(downstream.stream().map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                        .map(ProductionStarted.class::cast).anyMatch(startedProduction -> startedProduction.inputItemId().equals(output.id())),
                "this agricultural-only fixture has no admitted industrial worker; it must not manufacture a downstream owner");
        assertEquals(output, beforeReceipt.inventory().items().get(complete.outputItemId()),
                "the composed terminal output stays owned and projectable while the same farmer may admit a successor");

        FrontierWorldState composed = state;
        assertThrows(IllegalArgumentException.class,
                () -> composed.transitionPhysicalIntent(complete.predecessorIntentId(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty()),
                "the composed request has no non-terminal transition path after its exact output is canonical");

        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle lifecycle = state.resourceSites().site(site);
            state = ResourceSiteProcess.reduceGrowth(state, site,
                    new ResourceSiteGrowthAdvanced(site, lifecycle.growthEpoch(), lifecycle.growthStage()));
        }
        List<ProposedEvent> successorOpportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 40_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) successorOpportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, new SubjectId("settlement:1"),
                (StrategicTaskPlanned) successorOpportunity.get(1).payload());
        StrategicTask successorTask = state.strategicPlans().tasks().values().stream().filter(candidate -> candidate.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE
                && candidate.status() == StrategicTaskStatus.PENDING).findFirst().orElseThrow();
        List<ProposedEvent> successorPlan = ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(successorTask, 40_100L));
        ResourceSiteHarvestStarted successor = (ResourceSiteHarvestStarted) successorPlan.stream()
                .map(ProposedEvent::payload).filter(ResourceSiteHarvestStarted.class::isInstance).findFirst().orElseThrow();
        assertEquals(complete.workerId(), successor.job().workerId(),
                "the already-owned output admits only the exact predecessor farmer to the successor job without a loaded receipt");
        assertFalse(successor.job().outputSlot().equals(complete.outputSlot()),
                "the canonical predecessor output reserves its depot slot while the successor owns a distinct future receipt");
        state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"),
                (StrategicTaskTransition) successorPlan.getFirst().payload());
        state = ResourceSiteHarvestProcess.reduceStarted(state, site, successor);
        state = ResourceSiteHarvestProcess.reducePrepared(state, site,
                ((PhysicalIntentPrepared) successorPlan.stream().map(ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance)
                        .findFirst().orElseThrow()).intent());
        FrontierWorldState successorRestarted = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state));
        assertEquals(successor.job().id(), ((ResourceSiteHarvestJob) successorRestarted.resourceSites().site(site).activeWork().orElseThrow()).id(),
                "a subsequent epoch and restart retain the new active job without orphaning the retired predecessor request");
        ScheduledAction successorAction = ((ScheduleEffect.Created) successorPlan.stream().map(ProposedEvent::payload)
                .filter(ScheduleEffect.Created.class::isInstance).findFirst().orElseThrow()).action();
        state = completeColdHarvest(state, site, successorAction);
        ResourceSiteHarvestLineage successorComplete = state.resourceSites().site(site).harvestLineage().orElseThrow();
        assertFalse(successorComplete.receiptPending());
        assertFalse(state.physicalIntents().containsKey(complete.predecessorIntentId()));
        assertFalse(state.physicalIntents().containsKey(successorComplete.predecessorIntentId()));
        FrontierWorldState twiceRestarted = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state));
        assertEquals(successorComplete, twiceRestarted.resourceSites().site(site).harvestLineage().orElseThrow(),
                "two zero-player harvest epochs survive restart with no orphan physical intent");
        assertEquals(output, twiceRestarted.inventory().items().get(complete.outputItemId()),
                "the first composed output remains exact canonical custody while a later epoch completes");
    }

    private static FrontierWorldState completeColdHarvest(FrontierWorldState state, SubjectId site, ScheduledAction action) {
        for (int step = 0; step < 256 && state.resourceSites().site(site).phase() == ResourceSitePhase.HARVESTING; step++) {
            List<ProposedEvent> planned = ResourceSiteHarvestProcess.planColdProgress(state, action);
            assertFalse(planned.isEmpty(), "COLD harvest must make one bounded durable disposition at step " + step);
            for (ProposedEvent event : planned) {
                switch (event.payload()) {
                    case ResourceSiteHarvestColdTraversalAdvanced traversal -> state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site, traversal);
                    case ResourceSiteHarvestCropPrepared crop -> state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site, crop);
                    case ResourceSiteHarvestProgressed progressed -> state = ResourceSiteHarvestProcess.reduceProgressed(state, site, progressed);
                    case StrategicTaskTransition transition -> state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"), transition);
                    case ScheduleEffect.Rescheduled rescheduled -> action = rescheduled.replacement();
                    case ScheduleEffect.Cancelled ignored -> { }
                    case ScheduleEffect.Created ignored -> { }
                    default -> throw new AssertionError("unexpected COLD harvest event: " + event.payload().type());
                }
            }
        }
        assertEquals(ResourceSitePhase.GROWING, state.resourceSites().site(site).phase());
        return state;
    }

    private static ColdHarvestPendingTerminal advanceBeforeTerminal(FrontierWorldState state, SubjectId site, ScheduledAction action) {
        while (((ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow()).progress().completedCropSlots()
                < ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS - 1) {
            List<ProposedEvent> planned = ResourceSiteHarvestProcess.planColdProgress(state, action);
            assertFalse(planned.isEmpty(), "COLD harvest must retain a bounded action before its final crop");
            for (ProposedEvent event : planned) {
                switch (event.payload()) {
                    case ResourceSiteHarvestColdTraversalAdvanced traversal -> state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site, traversal);
                    case ResourceSiteHarvestCropPrepared crop -> state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site, crop);
                    case ResourceSiteHarvestProgressed progressed -> state = ResourceSiteHarvestProcess.reduceProgressed(state, site, progressed);
                    case ScheduleEffect.Rescheduled rescheduled -> action = rescheduled.replacement();
                    default -> throw new AssertionError("unexpected pre-terminal COLD harvest event: " + event.payload().type());
                }
            }
        }
        return new ColdHarvestPendingTerminal(state, action);
    }

    private record ColdHarvestPendingTerminal(FrontierWorldState state, ScheduledAction action) { }

    private static FrontierWorldState matureField() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:cold-harvest-receipt"), 125L));
        List<ProposedEvent> preparation = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(site, 4_000L));
        state = ResourceSiteProcess.reducePreparationStarted(state, site, (ResourceSitePreparationStarted) preparation.getFirst().payload());
        state = ResourceSiteProcess.reducePrepared(state, site, (ResourceSitePrepared) preparation.get(1).payload());
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle lifecycle = state.resourceSites().site(site);
            state = ResourceSiteProcess.reduceGrowth(state, site,
                    new ResourceSiteGrowthAdvanced(site, lifecycle.growthEpoch(), lifecycle.growthStage()));
        }
        return state;
    }
}
