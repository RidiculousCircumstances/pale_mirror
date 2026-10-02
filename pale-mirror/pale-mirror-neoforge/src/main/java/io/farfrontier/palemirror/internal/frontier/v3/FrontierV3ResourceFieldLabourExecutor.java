package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestWorkProcess;
import net.minecraft.server.level.ServerLevel;
import java.util.Optional;

/** Loaded field adapter; no private timer, speed formula, crop mutation or movement. */
final class FrontierV3ResourceFieldLabourExecutor {
    private FrontierV3ResourceFieldLabourExecutor() { }
    static boolean pause(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, SceneLease lease, ResourceSiteHarvestJob job) {
        if (job.progress().work().filter(WorkProgress::running).isEmpty()) return false;
        return submit(level, runtime, state, lease, job, false);
    }
    static boolean advance(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, SceneLease lease, ResourceSiteHarvestJob job) {
        var work = job.progress().work();
        if (work.filter(WorkProgress::complete).isPresent()) return false;
        long now = runtime.canonicalState().orElseThrow().instant().ticks();
        if (work.filter(WorkProgress::running).isPresent()) {
            if (now < work.orElseThrow().activeUntilTick()) return true;
            return submit(level, runtime, state, lease, job, false);
        }
        return submit(level, runtime, state, lease, job, true);
    }
    private static boolean submit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, SceneLease lease, ResourceSiteHarvestJob job, boolean run) {
        var binding = FrontierV3TraversalScheduleGate.binding(runtime.executionView().orElseThrow(), job.siteId());
        if (binding.isEmpty()) return true;
        var value = ResourceSiteHarvestWorkProcess.change(state, job, runtime.canonicalState().orElseThrow().instant().ticks(),
                run, binding.orElseThrow(), Optional.of(lease.id()));
        var accepted = FrontierV3CommandSubmission.submitBound(runtime, "resource-site-harvest-labour", lease.id().value(), value,
                binding.orElseThrow());
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_work_changed", lease, accepted);
        return true;
    }
}
