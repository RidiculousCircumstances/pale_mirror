package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestWorkProcess;
import java.util.*;

/** Initial-state fixture for the shared native work/fatality boundary, never a runtime repair. */
public final class HarvestPendingWorkFixture {
    private HarvestPendingWorkFixture() { }
    public record Fixture(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job,
            SceneLease lease, SimInstant instant, ScheduledAction due) { }
    public static Fixture create(FrontierBootstrap bootstrap, ResourceFieldCycle.WorkOutcome outcome) {
        var cold = FrontierResourceSiteHarvestFixture.create(FrontierWorldState.initial(bootstrap));
        var state = cold.state(); var site = cold.siteId();
        var job = state.resourceSites().site(site).harvestJob(cold.jobId()).orElseThrow();
        var cycle = state.resourceSites().cycle(site); var cell = job.target().cellId();
        cycle = switch (outcome) {
            case HARVESTED -> cycle;
            case PLANTED -> cycle.cropRemoved(cell);
            case TILLED -> cycle.soilBecameDirt(cell);
            default -> throw new IllegalArgumentException("fixture requires a physical field operation");
        };
        state = state.withResourceSites(state.resourceSites().replace(
                state.resourceSites().site(site).bindHarvestTarget(job, cycle), cycle));
        job = state.resourceSites().site(site).harvestJob(job.id()).orElseThrow();
        var lease = SceneLease.forCause(new SceneLeaseId("lease:pending-field-work"), bootstrap.worldId(),
                new ResourceSiteHarvestSceneCause(site, job.id()), cycle.layout().requireCell(cell).crop(), cold.instant(), 1,
                SceneLeaseStatus.PREPARED, List.of(new SceneMember(job.workerId(),
                        SceneLease.deterministicEntityId(bootstrap.worldId(), job.workerId()))), Set.of(job.workerId()), Optional.empty());
        state = ModeledActorBodyFacts.present(state.prepareSceneLease(lease), job.workerId())
                .transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        state = ModeledActorBodyFacts.inspected(state, job.workerId(),
                ResourceSiteHarvestGoal.current(state, job).representative().standingBody());
        long tick = cold.instant().ticks();
        for (int interval = 0; interval < 8 && !job.progress().work().map(WorkProgress::complete).orElse(false); interval++) {
            boolean running = job.progress().work().map(WorkProgress::running).orElse(false);
            if (running) tick = job.progress().work().orElseThrow().activeUntilTick();
            else if (!ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.workerId(), tick))
                tick = ResidentActivityCoordinator.nextOrdinaryWorkCheck(state, job.workerId(), tick);
            state = ResourceSiteHarvestWorkProcess.reduce(state, site, ResourceSiteHarvestWorkProcess.change(
                    state, job, tick, !running, ResourceSiteHarvestProcess.coldProgress(job, tick), Optional.of(lease.id())));
            job = state.resourceSites().site(site).harvestJob(job.id()).orElseThrow();
        }
        state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site,
                new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex(), job.target().generation()));
        job = state.resourceSites().site(site).harvestJob(job.id()).orElseThrow();
        return new Fixture(state, site, job, state.sceneLeases().get(lease.id()), new SimInstant(tick),
                ResourceSiteHarvestProcess.coldProgress(job, tick + 1));
    }
}
