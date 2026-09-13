package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;

/** Bounded read-only settlement aggregate distinguishing COLD work from observer admission. */
final class FrontierV3ProcessInventoryDiagnostic {
    private FrontierV3ProcessInventoryDiagnostic() { }

    static String render(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        if (!id.equals("settlements")) return FrontierV3DiagnosticJson.unavailable("process_inventory", id, checkpoint, "not_found");
        var entries = state.resourceSites().sites().values().stream().sorted(java.util.Comparator.comparing(value -> value.siteId().value()))
                .map(value -> entry(value, checkpoint)).toList();
        String rendered = entries.stream().map(Entry::json).reduce((left, right) -> left + "," + right).map(value -> "[" + value + "]").orElse("[]");
        return FrontierV3DiagnosticJson.base("process_inventory", id, checkpoint) + ",\"status\":\"ok\",\"count\":" + entries.size()
                + ",\"coldEligible\":" + entries.stream().filter(Entry::coldEligible).count() + ",\"entries\":" + rendered + "}";
    }

    private static Entry entry(ResourceSiteLifecycle lifecycle, CheckpointImage checkpoint) {
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast).orElse(null);
        if (job == null) return new Entry(false, "{\"site\":\"" + quote(lifecycle.siteId().value()) + "\",\"phase\":\"" + lifecycle.phase()
                + "\",\"waitReason\":\"" + waitReason(lifecycle, null) + "\"}");
        long dueAt = checkpoint.schedules().stream().filter(value -> value.subject().equals(job.id())).mapToLong(value -> value.dueAt().ticks()).min().orElse(-1L);
        boolean coldEligible = lifecycle.phase() == ResourceSitePhase.HARVESTING && job.traversalCursor() < job.cropCursor();
        return new Entry(coldEligible, "{\"site\":\"" + quote(lifecycle.siteId().value()) + "\",\"phase\":\"" + lifecycle.phase()
                + "\",\"job\":\"" + quote(job.id().value()) + "\",\"worker\":\"" + quote(job.workerId().value()) + "\",\"cursor\":"
                + job.traversalCursor() + ",\"cursorLength\":" + job.traversal().linearCorridorSurfaces().size() + ",\"cropCursor\":" + job.cropCursor()
                + ",\"dueAt\":" + dueAt + ",\"waitReason\":\"" + waitReason(lifecycle, job) + "\"}");
    }

    private static String waitReason(ResourceSiteLifecycle lifecycle, ResourceSiteHarvestJob job) {
        if (job != null && lifecycle.phase() == ResourceSitePhase.HARVESTING) return job.traversalCursor() < job.cropCursor()
                ? "COLD_ELIGIBLE_TRAVERSAL" : "AWAITING_HOT_CROP_EFFECT";
        return switch (lifecycle.phase()) {
            case UNPREPARED -> "AWAITING_PREPARATION"; case GROWING -> "AWAITING_GROWTH"; case READY -> "AWAITING_HARVEST_ASSIGNMENT";
            case CONFLICT -> "CONFLICT_DISPOSITION"; case DESTROYED -> "DESTROYED"; case HARVESTING -> "MISSING_HARVEST_JOB";
        };
    }

    private static String quote(String value) { return FrontierV3DiagnosticJson.quote(value); }
    private record Entry(boolean coldEligible, String json) { }
}
