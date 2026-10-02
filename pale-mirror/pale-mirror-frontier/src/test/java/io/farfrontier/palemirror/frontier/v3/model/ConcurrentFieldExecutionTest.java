package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrentFieldExecutionTest {
    @Test void availableSecondFarmerJoinsTheExistingTaskThroughRegisteredSettlementPolicy() {
        var state = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial(126L));
        var site = new SubjectId("site:1-wheat-field");
        var settlement = new SubjectId("settlement:1");
        var permissions = SettlementWorkPolicy.permissions(state, settlement);
        var first = SettlementWorkforce.candidates(state, settlement,
                ResidentWorkKind.AGRICULTURE, HumanCapability.AGRICULTURE).getFirst();
        var limited = new EnumMap<ResidentWorkKind, Set<SubjectId>>(ResidentWorkKind.class);
        limited.putAll(permissions.workers());
        limited.put(ResidentWorkKind.AGRICULTURE, Set.of(first.id()));
        state = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(settlement,
                new ResidentWorkPermissions(limited)));
        var opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 27_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, settlement,
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, settlement,
                (StrategicTaskPlanned) opportunity.get(1).payload());
        var task = state.strategicPlans().tasks().values().stream()
                .filter(value -> value.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
        var admission = ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(task, 27_085L));
        var started = admission.stream().map(ProposedEvent::payload).filter(ResourceSiteHarvestStarted.class::isInstance)
                .map(ResourceSiteHarvestStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, settlement,
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ResourceSiteHarvestProcess.reduceStarted(state, site, started);
        var intent = admission.stream().map(ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).map(PhysicalIntentPrepared::intent).findFirst().orElseThrow();
        state = state.preparePhysicalIntent(intent);
        // Availability is a fixture precondition; the actual second admission is an ordinary engine action.
        state = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(settlement, permissions));
        var wake = StrategicObjectiveProcess.playerStockReconsideration(settlement,
                UUID.fromString("00000000-0000-0000-0000-000000000002"), 27_086L);
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 126L);
        var journal = new ArrayList<TransactionRecord>();
        var config = new FrontierEngineConfiguration<>(base.worldId(), state, new SimInstant(27_085L),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(wake, ResourceSiteHarvestProcess.coldProgress(started.job(), 30_000L)),
                (TransactionCommitter) (transaction, durability) -> journal.add(transaction), base.stateValidator());
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        var initial = engine.checkpoint();
        requireActive(engine.advanceTo(wake.dueAt(), new WorkBudget(1, 64)).status());
        var jobs = engine.canonicalState().state().resourceSites().site(site).harvestJobs();
        assertEquals(2, jobs.size());
        assertEquals(started.job(), jobs.get(started.job().id()), "joining cannot rewrite the first execution");
        assertEquals(Set.of(task.id()), jobs.values().stream().map(ResourceSiteHarvestJob::taskId)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, jobs.values().stream().map(ResourceSiteHarvestJob::target).distinct().count());
        var recovered = FrontierEngines.recoverCanonicalStateAccess(config,
                new RecoveryImage(base.worldId(), Optional.of(new SnapshotRecord(initial, 0L)), journal));
        assertEquals(engine.canonicalState().state(), recovered.canonicalState().state());
        assertEquals(engine.checkpoint().schedules(), recovered.checkpoint().schedules());
    }

    @Test void registeredAdmissionAdvancesBothExactWorkersAndReplaysTheirIndependentCustody() {
        FrontierWorldState state = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial(126L));
        SubjectId site = new SubjectId("site:1-wheat-field"), settlement = new SubjectId("settlement:1");
        var opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 27_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, settlement,
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, settlement,
                (StrategicTaskPlanned) opportunity.get(1).payload());
        StrategicTask task = state.strategicPlans().tasks().values().stream()
                .filter(value -> value.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 126L);
        var journal = new ArrayList<TransactionRecord>();
        var config = new FrontierEngineConfiguration<>(base.worldId(), state, new SimInstant(27_084L),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(ResourceSiteHarvestProcess.start(task, 27_085L)),
                (TransactionCommitter) (transaction, durability) -> journal.add(transaction), base.stateValidator());
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        var initial = engine.checkpoint();
        requireActive(engine.advanceTo(new SimInstant(27_085L), new WorkBudget(1, 64)).status());
        var admitted = engine.canonicalState().state().resourceSites().site(site).harvestJobs();
        assertEquals(2, admitted.size());
        assertEquals(2, admitted.values().stream().map(ResourceSiteHarvestJob::workerId).distinct().count());
        assertEquals(2, admitted.values().stream().map(ResourceSiteHarvestJob::target).distinct().count());
        assertIndependentTerminalTraces(engine.canonicalState().state(), site, admitted);

        for (int turn = 0; turn < 256; turn++) {
            var jobs = engine.canonicalState().state().resourceSites().site(site).harvestJobs();
            if (jobs.size() == 2 && jobs.values().stream().allMatch(job -> job.harvestedYieldQuantity() > 0)) break;
            var next = engine.checkpoint().schedules().stream()
                    .min(Comparator.comparing(ScheduledAction::dueAt).thenComparing(ScheduledAction::id)).orElseThrow();
            requireActive(engine.advanceTo(next.dueAt(), new WorkBudget(1, 64)).status());
        }
        FrontierWorldState worked = engine.canonicalState().state();
        var jobs = worked.resourceSites().site(site).harvestJobs();
        assertEquals(admitted.keySet(), jobs.keySet());
        for (var job : jobs.values()) {
            assertTrue(job.harvestedYieldQuantity() > 0, "both admitted workers must produce their own confirmed crop");
            assertEquals(job.undeliveredYieldQuantity(), ResourceSiteHarvestCargo.quantity(worked, job));
            assertTrue(job.target().current(worked.resourceSites().cycle(site)));
        }
        var recovered = FrontierEngines.recoverCanonicalStateAccess(config,
                new RecoveryImage(base.worldId(), Optional.of(new SnapshotRecord(initial, 0L)), journal));
        assertEquals(worked, recovered.canonicalState().state());
        assertEquals(engine.checkpoint().schedules(), recovered.checkpoint().schedules());
        assertEquals(worked, base.stateCodec().decode(base.stateCodec().encode(worked)));
    }

    @Test void replacingOnePlantInvalidatesOnlyThatGenerationAndSnapshotRetainsIt() {
        var state = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial(126L));
        var site = new SubjectId("site:1-wheat-field");
        var before = state.resourceSites().cycle(site);
        var first = before.layout().cells().getFirst().id();
        var other = before.layout().cells().get(1).id();
        var oldClaim = before.target(first);
        var otherClaim = before.target(other);
        var next = before.harvested(first);
        assertFalse(oldClaim.current(next));
        assertTrue(otherClaim.current(next));
        for (int growth = 0; growth < 7; growth++) next = next.advanceGrowthStage();
        assertFalse(next.cell(first).accounted(), "renewed maturity is new available work, not an old batch outcome");
        assertEquals(before.generation(first) + 1L, next.generation(first));
        state = state.withResourceSites(state.resourceSites().replace(
                state.resourceSites().site(site).withPlantReadiness(next), next));
        var codec = new FrontierWorldStateCodec();
        assertEquals(state, codec.decode(codec.encode(state)));
    }

    private static void assertIndependentTerminalTraces(FrontierWorldState state, SubjectId site,
            Map<SubjectId, ResourceSiteHarvestJob> admitted) {
        var histories = new LinkedHashMap<PhysicalIntentId, ResourceSiteHarvestLineage>();
        var lifecycle = state.resourceSites().site(site);
        for (var job : admitted.values()) {
            histories.put(job.intentId(), ResourceSiteHarvestLineage.completed(job, lifecycle.growthEpoch(), true,
                    ResourceSiteHarvestCausality.notCaptured(job), state.actorLocations().get(job.workerId()).body()));
        }
        var retained = new ResourceSiteLifecycle(site, ResourceSitePhase.GROWING, lifecycle.growthEpoch(), 0,
                Optional.empty(), Optional.empty(), Map.of(), histories, lifecycle.harvestSequence());
        var jobs = admitted.values().stream().sorted(Comparator.comparing(ResourceSiteHarvestJob::id)).toList();
        var first = jobs.getFirst();
        var second = jobs.getLast();
        var trace = RetainedDiagnosticTrace.pending(first, "terminal-delivery");
        var updated = retained.withHarvestTrace(first.intentId(), trace);
        assertEquals(trace, updated.harvestLineage(first.intentId()).orElseThrow().causality().trace());
        assertEquals(histories.get(second.intentId()), updated.harvestLineage(second.intentId()).orElseThrow(),
                "updating one terminal delivery must not select or rewrite its sibling");
        assertThrows(IllegalArgumentException.class, () -> retained.withHarvestTrace(second.intentId(), trace));
    }

    private static void requireActive(EngineStatus status) {
        assertEquals(EngineStatus.Kind.ACTIVE, status.kind(), status.failureDetail().orElse(""));
    }
}
