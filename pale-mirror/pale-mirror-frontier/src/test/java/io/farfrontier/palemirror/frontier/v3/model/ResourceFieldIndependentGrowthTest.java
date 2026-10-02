package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.EngineStatus;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ResourceFieldIndependentGrowthTest {
    @Test void completedHarvestGrowsWhileItsExactWorkerStillCarriesUndeliveredWheatAcrossRecovery() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        var state = ResourceSiteHarvestProcessTest.completeHarvestWorkHot(hot.state(), hot.site(), hot.job(), hot.lease().id());
        var job = (ResourceSiteHarvestJob) state.resourceSites().site(hot.site()).activeWork().orElseThrow();
        assertEquals(64, job.carriedYieldQuantity(state.resourceSites().cycle(hot.site()).harvestedCount()));
        var clock = ResourceSiteProcess.nextGrowth(state.resourceSites().site(hot.site()), 22_301L);
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), state.bootstrap().seed());
        var journal = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord>();
        var config = new FrontierEngineConfiguration<>(base.worldId(), state, new SimInstant(22_300L),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(clock, ResourceSiteHarvestProcess.coldProgress(job, 22_302L)),
                (io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter) (transaction, durability) -> journal.add(transaction),
                base.stateValidator());
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        var initial = engine.checkpoint();
        var result = engine.advanceTo(clock.dueAt(), new WorkBudget(1, 64));
        assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse(""));
        var grown = engine.canonicalState().state();
        assertEquals(job, grown.resourceSites().site(hot.site()).activeWork().orElseThrow());
        assertEquals(1, grown.resourceSites().cycle(hot.site()).plantGrowthStage());
        assertEquals(64, grown.resourceSites().cycle(hot.site()).harvestedCount());
        assertEquals(state.actorLocations(), grown.actorLocations());
        assertEquals(state.inventory(), grown.inventory());
        var recovered = FrontierEngines.recoverCanonicalStateAccess(config,
                new RecoveryImage(base.worldId(), Optional.of(new SnapshotRecord(initial, 0L)), journal));
        assertEquals(grown, recovered.canonicalState().state());
        var next = recovered.checkpoint().schedules().stream().filter(action -> action.id().equals(clock.id())).findFirst().orElseThrow();
        result = recovered.advanceTo(next.dueAt(), new WorkBudget(1, 64));
        assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse(""));
        assertEquals(2, recovered.canonicalState().state().resourceSites().cycle(hot.site()).plantGrowthStage());
        assertEquals(1, recovered.checkpoint().schedules().stream().filter(action -> action.id().equals(clock.id())).count());
    }

    @Test void retiringWorkAccountingPreservesPlantAgeAndTheSameClockIdentity() {
        var cold = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var cycle = cold.state().resourceSites().cycle(cold.site());
        for (var cell : cycle.layout().cells()) cycle = cycle.harvested(cell.id());
        for (int stage = 0; stage < 3; stage++) cycle = cycle.advanceGrowthStage();
        var successor = cycle.nextEpoch();
        var terminal = new ResourceSiteLifecycle(cold.site(), ResourceSitePhase.GROWING, successor.epoch(), 0,
                Optional.empty(), Optional.empty()).withPlantReadiness(successor);
        assertEquals(3, terminal.growthStage());
        assertEquals(3, successor.plantGrowthStage());
        assertEquals(0, successor.accountedCount());
        assertEquals(ResourceSiteProcess.nextGrowth(cold.state().resourceSites().site(cold.site()), 50_000L).id(),
                ResourceSiteProcess.nextGrowth(terminal, 50_000L).id());
    }
}
