package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestGoal;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;

/** Bounded read-only settlement aggregate distinguishing COLD work from observer admission. */
final class FrontierV3ProcessInventoryDiagnostic {
    private FrontierV3ProcessInventoryDiagnostic() { }

    static String render(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        if (!id.equals("settlements")) return FrontierV3DiagnosticJson.unavailable("process_inventory", id, checkpoint, "not_found");
        var entries = state.resourceSites().sites().values().stream().sorted(java.util.Comparator.comparing(value -> value.siteId().value()))
                .flatMap(value -> value.harvestJobs().isEmpty() ? java.util.stream.Stream.of(entry(value, null, checkpoint, state))
                        : value.harvestJobs().values().stream().sorted(java.util.Comparator.comparing(ResourceSiteHarvestJob::id))
                                .map(job -> entry(value, job, checkpoint, state))).toList();
        String rendered = entries.stream().map(Entry::json).reduce((left, right) -> left + "," + right).map(value -> "[" + value + "]").orElse("[]");
        return FrontierV3DiagnosticJson.base("process_inventory", id, checkpoint) + ",\"status\":\"ok\",\"count\":" + entries.size()
                + ",\"coldEligible\":" + entries.stream().filter(Entry::coldEligible).count() + ",\"entries\":" + rendered + "}";
    }

    private static Entry entry(ResourceSiteLifecycle lifecycle, ResourceSiteHarvestJob job, CheckpointImage checkpoint, FrontierWorldState state) {
        if (job == null) return new Entry(false, "{\"site\":\"" + quote(lifecycle.siteId().value()) + "\",\"phase\":\"" + lifecycle.phase()
                + "\",\"waitReason\":\"" + waitReason(lifecycle, null) + "\"}");
        long dueAt = checkpoint.schedules().stream()
                .filter(value -> ResourceSiteHarvestProcess.coldProgress(job, value.dueAt().ticks()).equals(value))
                .mapToLong(value -> value.dueAt().ticks()).min().orElse(-1L);
        boolean hotOwned = state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                && FrontierSceneBehaviors.isResourceSiteHarvest(lease)
                && FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(job.siteId())
                && FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id()));
        boolean coldEligible = lifecycle.phase() == ResourceSitePhase.HARVESTING && !job.progress().complete() && !hotOwned;
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        var actor = state.actorLocations().get(job.workerId());
        String actorBody = actor == null ? "null" : "{\"x\":" + actor.body().x()
                + ",\"y\":" + actor.body().y() + ",\"z\":" + actor.body().z() + "}";
        return new Entry(coldEligible, "{\"site\":\"" + quote(lifecycle.siteId().value()) + "\",\"phase\":\"" + lifecycle.phase()
                + "\",\"job\":\"" + quote(job.id().value()) + "\",\"worker\":\"" + quote(job.workerId().value())
                + "\",\"goal\":{\"kind\":\"" + goal.kind() + "\",\"nextWorkSlot\":" + goal.nextWorkSlot()
                + ",\"layoutRevision\":" + goal.layoutRevision() + "},\"actorBody\":" + actorBody
                + ",\"completedCropSlots\":" + job.progress().completedCropSlots() + ",\"pendingCropSlot\":" + job.progress().pendingCropSlotIndex()
                + ",\"nextCropSlot\":" + (job.progress().complete() ? -1 : job.progress().nextCropSlotIndex())
                + ",\"deferredMaterializationSlots\":" + job.progress().completedCropSlots()
                + ",\"coldEligible\":" + coldEligible
                + ",\"dueAt\":" + dueAt + ",\"waitReason\":\"" + waitReason(lifecycle, job, hotOwned) + "\"}");
    }

    private static String waitReason(ResourceSiteLifecycle lifecycle, ResourceSiteHarvestJob job) { return waitReason(lifecycle, job, false); }
    private static String waitReason(ResourceSiteLifecycle lifecycle, ResourceSiteHarvestJob job, boolean hotOwned) {
        if (job != null && lifecycle.phase() == ResourceSitePhase.HARVESTING) return job.progress().complete()
                ? "AWAITING_HOT_OUTPUT_EFFECT" : hotOwned ? "HOT_OWNED_CURRENT_HARVEST" : "COLD_ELIGIBLE_SEMANTIC_HARVEST";
        return switch (lifecycle.phase()) {
            case UNPREPARED -> "AWAITING_PREPARATION"; case GROWING -> "AWAITING_GROWTH"; case READY -> "AWAITING_HARVEST_ASSIGNMENT";
            case CONFLICT -> "CONFLICT_DISPOSITION"; case DESTROYED -> "DESTROYED"; case HARVESTING -> "MISSING_HARVEST_JOB";
        };
    }

    private static String quote(String value) { return FrontierV3DiagnosticJson.quote(value); }
    private record Entry(boolean coldEligible, String json) { }
}
