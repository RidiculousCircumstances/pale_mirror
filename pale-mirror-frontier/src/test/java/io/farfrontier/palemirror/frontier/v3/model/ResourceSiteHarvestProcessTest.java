package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSiteHarvestProcessTest {
    @Test
    void exactMatureFieldCreatesOneNamedWheatStackOnlyAfterObservedReceipt() {
        FrontierWorldState ready = activeDepot(ready(initial())); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));

        assertEquals(3, planned.size()); assertEquals(new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE), planned.getFirst().payload());
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        assertEquals(task.id(), started.job().taskId()); assertFalse(tasked.inventory().items().containsKey(started.job().outputItemId()));
        assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
        FrontierWorldState active = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"), (StrategicTaskTransition) planned.getFirst().payload());
        FrontierWorldState harvesting = ResourceSiteHarvestProcess.reduceStarted(active, site, started);
        PhysicalIntent intent = ((PhysicalIntentPrepared) planned.get(2).payload()).intent();
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, intent);
        harvesting = harvesting.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        ExactItemStack output = new ExactItemStack(started.job().outputItemId(), new SubjectId("settlement:1"), "minecraft:wheat", 64, started.job().outputSlot());
        ResourceSiteHarvestObservation receipt = new ResourceSiteHarvestObservation(new PhysicalObservationId("observation:site-harvest-1"), intent.id(), site,
                started.job().workerId(), output, 64);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> completion = ResourceSiteHarvestProcess.planTransition(harvesting, intent,
                new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)), 22_010L);

        assertEquals(3, completion.size()); assertEquals(new StrategicTaskTransition(task.id(), StrategicTaskStatus.COMPLETED), completion.get(1).payload());
        FrontierWorldState complete = harvesting.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        complete = StrategicObjectiveProcess.reduceTaskTransition(complete, new SubjectId("settlement:1"), (StrategicTaskTransition) completion.get(1).payload());
        assertEquals(ResourceSitePhase.GROWING, complete.resourceSites().site(site).phase());
        assertEquals(output, complete.inventory().items().get(output.id()));
        assertEquals(StrategicTaskStatus.COMPLETED, complete.strategicPlans().tasks().get(task.id()).status());
        assertTrue(new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(complete)).inventory().items().containsKey(output.id()));
    }

    @Test
    void inactiveOrFullDepotLeavesReadyFieldWithoutInventingAHarvest() {
        FrontierWorldState state = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(state, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));

        assertEquals(1, planned.size()); assertEquals(new StrategicTaskTransition(task.id(), StrategicTaskStatus.BLOCKED), planned.getFirst().payload());
        assertEquals(ResourceSitePhase.READY, state.resourceSites().site(site).phase());
    }

    @Test
    void receiptCannotRedirectTheNamedHarvestToAnotherDepotSlot() {
        FrontierWorldState ready = activeDepot(ready(initial())); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        FrontierWorldState active = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"), (StrategicTaskTransition) planned.getFirst().payload());
        FrontierWorldState harvesting = ResourceSiteHarvestProcess.reduceStarted(active, site, started);
        PhysicalIntent intent = ((PhysicalIntentPrepared) planned.get(2).payload()).intent();
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, intent);
        harvesting = harvesting.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        InventoryCustody.ContainerSlot otherSlot = new InventoryCustody.ContainerSlot(started.job().outputSlot().containerId(),
                started.job().outputSlot().slot() + 1);
        ExactItemStack redirected = new ExactItemStack(started.job().outputItemId(), new SubjectId("settlement:1"), "minecraft:wheat", 64, otherSlot);
        ResourceSiteHarvestObservation receipt = new ResourceSiteHarvestObservation(new PhysicalObservationId("observation:redirected-harvest"),
                intent.id(), site, started.job().workerId(), redirected, 64);

        FrontierWorldState state = harvesting;
        assertThrows(IllegalArgumentException.class, () -> state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)));
    }

    @Test
    void restartUnknownHarvestConflictsTheExactFieldAndBlocksItsOwningTask() {
        FrontierWorldState ready = activeDepot(ready(initial())); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        FrontierWorldState harvesting = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        harvesting = ResourceSiteHarvestProcess.reduceStarted(harvesting, site, started);
        PhysicalIntent intent = ((PhysicalIntentPrepared) planned.get(2).payload()).intent();
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, intent);
        harvesting = harvesting.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> transition = ResourceSiteHarvestProcess.planTransition(harvesting, intent,
                new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty()), 22_101L);

        assertEquals(2, transition.size());
        FrontierWorldState conflicted = harvesting.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        assertEquals(ResourceSitePhase.CONFLICT, conflicted.resourceSites().site(site).phase());
        FrontierWorldState blocked = StrategicObjectiveProcess.reduceTaskTransition(conflicted, new SubjectId("settlement:1"),
                (StrategicTaskTransition) transition.get(1).payload());
        assertEquals(StrategicTaskStatus.BLOCKED, blocked.strategicPlans().tasks().get(task.id()).status());
        assertEquals(StrategicObjectiveStatus.BLOCKED, blocked.strategicPlans().objectives().get(task.objectiveId()).status());
    }

    @Test
    void publicPhysicalBoundaryConfirmsOneObservedHarvestWithoutQuarantiningTheEngine() {
        WorldId world = new WorldId("frontier:resource-site-public-harvest");
        FrontierWorldState ready = activeDepot(ready(FrontierWorldState.initial(FrontierBootstrapper.create(world, 125L))));
        SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L);
        StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        FrontierWorldState harvesting = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        harvesting = ResourceSiteHarvestProcess.reduceStarted(harvesting, site, started);
        PhysicalIntent intent = ((PhysicalIntentPrepared) planned.get(2).payload()).intent();
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, intent);

        var base = FrontierWorldRuntimeDefinition.configuration(world, 125L);
        var configuration = new FrontierEngineConfiguration<>(world, harvesting, new SimInstant(22_100L), base.commandPlanner(), base.scheduledPlanner(),
                base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), List.of(), TransactionCommitter.noOp());
        var engine = FrontierEngines.create(configuration);
        assertTrue(submit(engine, world, "running", intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty()) instanceof CommandResult.Accepted);

        ExactItemStack output = new ExactItemStack(started.job().outputItemId(), new SubjectId("settlement:1"), "minecraft:wheat", 64, started.job().outputSlot());
        ResourceSiteHarvestObservation receipt = new ResourceSiteHarvestObservation(new PhysicalObservationId("observation:site-harvest-public"),
                intent.id(), site, started.job().workerId(), output, 64);
        CommandResult result = submit(engine, world, "confirmed", intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));

        assertTrue(result instanceof CommandResult.Accepted, () -> "harvest confirmation must be accepted: " + result);
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind());
        FrontierWorldState complete = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(ResourceSitePhase.GROWING, complete.resourceSites().site(site).phase());
        assertEquals(output, complete.inventory().items().get(output.id()));
    }

    private static FrontierWorldState activeDepot(FrontierWorldState state) {
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        ExactInventory inventory = state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE);
        return state.withInventory(inventory);
    }

    private static FrontierWorldState ready(FrontierWorldState state) {
        SubjectId site = new SubjectId("site:1-wheat-field");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> preparation = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(site, 4_000L));
        state = ResourceSiteProcess.reducePreparationStarted(state, site, (ResourceSitePreparationStarted) preparation.getFirst().payload());
        PhysicalIntent intent = ((PhysicalIntentPrepared) preparation.get(1).payload()).intent(); state = ResourceSiteProcess.reducePrepared(state, site, intent);
        state = state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        state = state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(new ResourceSitePreparationObservation(
                new PhysicalObservationId("observation:site-prepare-1"), intent.id(), site, 64, 64)));
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle current = state.resourceSites().site(site);
            state = ResourceSiteProcess.reduceGrowth(state, site, new ResourceSiteGrowthAdvanced(site, current.growthEpoch(), current.growthStage()));
        }
        return state;
    }

    private static FrontierWorldState harvestTask(FrontierWorldState state, SubjectId site, long dueAt) {
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), dueAt));
        assertEquals(3, planned.size());
        assertEquals(planned.getFirst().payload(), FrontierWorldRuntimeDefinition.payloadCodecs().decode(planned.getFirst().payload().type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(planned.getFirst().payload())));
        assertEquals(planned.get(1).payload(), FrontierWorldRuntimeDefinition.payloadCodecs().decode(planned.get(1).payload().type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(planned.get(1).payload())));
        FrontierWorldState selected = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) planned.getFirst().payload());
        return StrategicObjectiveProcess.reduceTask(selected, new SubjectId("settlement:1"), (StrategicTaskPlanned) planned.get(1).payload());
    }

    private static StrategicTask onlyHarvestTask(FrontierWorldState state) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
    }

    private static CommandResult submit(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine, WorldId world,
                                        String phase, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId intentId,
                                        PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation) {
        CommandId command = new CommandId("command:resource-site-harvest-" + phase);
        var checkpoint = engine.checkpoint();
        return engine.submit(new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(intentId, status, observation)));
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-harvest"), 125L));
    }
}
