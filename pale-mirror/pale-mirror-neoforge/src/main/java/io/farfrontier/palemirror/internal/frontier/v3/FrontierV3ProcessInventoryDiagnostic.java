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
        String scope = id;
        int offset = 0;
        int separator = id.indexOf('@');
        if (separator >= 0) {
            scope = id.substring(0, separator);
            String cursor = id.substring(separator + 1);
            if (cursor.isEmpty() || !cursor.chars().allMatch(Character::isDigit))
                return FrontierV3DiagnosticJson.unavailable("process_inventory", id, checkpoint, "invalid_page");
            try { offset = Integer.parseInt(cursor); }
            catch (NumberFormatException invalid) {
                return FrontierV3DiagnosticJson.unavailable("process_inventory", id, checkpoint, "invalid_page");
            }
        }
        String selectedScope = scope;
        var sites = state.resourceSites().sites().values().stream()
                .filter(value -> selectedScope.equals("settlements") || value.siteId().value().equals(selectedScope))
                .sorted(java.util.Comparator.comparing(value -> value.siteId().value())).toList();
        if (sites.isEmpty()) return FrontierV3DiagnosticJson.unavailable("process_inventory", id, checkpoint, "not_found");
        var entries = sites.stream()
                .flatMap(value -> value.harvestJobs().isEmpty() ? java.util.stream.Stream.of(entry(value, null, checkpoint, state))
                        : value.harvestJobs().values().stream().sorted(java.util.Comparator.comparing(ResourceSiteHarvestJob::id))
                                .map(job -> entry(value, job, checkpoint, state))).toList();
        return renderPage(id, scope, offset, checkpoint, entries);
    }

    static String renderPage(String id, String scope, int offset, CheckpointImage checkpoint, java.util.List<Entry> entries) {
        if (offset < 0 || offset >= entries.size()) return FrontierV3DiagnosticJson.unavailable("process_inventory", id, checkpoint, "invalid_page");
        String header = FrontierV3DiagnosticJson.base("process_inventory", id, checkpoint) + ",\"status\":\"ok\",\"count\":" + entries.size()
                + ",\"coldEligible\":" + entries.stream().filter(Entry::coldEligible).count() + ",\"offset\":" + offset;
        var rows = new java.util.ArrayList<String>();
        String result = null;
        for (int index = offset; index < entries.size(); index++) {
            rows.add(entries.get(index).json());
            String candidate = page(header, scope, index + 1, entries.size(), rows);
            if ((FrontierV3DiagnosticJson.PREFIX + candidate).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    > FrontierV3DiagnosticJson.MAX_BYTES) break;
            result = candidate;
        }
        return result == null ? FrontierV3DiagnosticJson.unavailable("process_inventory", id, checkpoint, "response_limit") : result;
    }

    /** Every page reads its own declared checkpoint; callers must compare revisions before joining pages. */
    private static String page(String header, String scope, int next, int count, java.util.List<String> rows) {
        boolean complete = next == count;
        return header + ",\"returnedCount\":" + rows.size() + ",\"complete\":" + complete + ",\"nextId\":"
                + (complete ? "null" : "\"" + quote(scope + "@" + next) + "\"")
                + ",\"entries\":[" + String.join(",", rows) + "]}";
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
    record Entry(boolean coldEligible, String json) { }
}
