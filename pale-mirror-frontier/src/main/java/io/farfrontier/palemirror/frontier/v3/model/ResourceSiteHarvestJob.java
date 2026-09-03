package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact harvest: one named farmer advances one retained field cursor into one named 64-wheat output stack. */
public record ResourceSiteHarvestJob(SubjectId id, SubjectId taskId, SubjectId siteId, SubjectId workerId, SubjectId outputItemId,
                                     InventoryCustody.ContainerSlot outputSlot, PhysicalIntentId intentId,
                                     ResourceSiteHarvestProgress progress, TraversalTopology traversal, int traversalCursor) implements ResourceSiteWork {
    public ResourceSiteHarvestJob {
        Objects.requireNonNull(id, "resource-site harvest id"); Objects.requireNonNull(taskId, "resource-site harvest task id");
        Objects.requireNonNull(siteId, "resource-site harvest site id");
        Objects.requireNonNull(workerId, "resource-site harvest worker id"); Objects.requireNonNull(outputItemId, "resource-site harvest output item id");
        Objects.requireNonNull(outputSlot, "resource-site harvest output slot"); Objects.requireNonNull(intentId, "resource-site harvest intent id");
        Objects.requireNonNull(progress, "resource-site harvest progress"); traversal = Objects.requireNonNull(traversal, "resource-site harvest traversal");
        if (!id.value().startsWith("job:site-harvest-") || !taskId.value().startsWith("task:") || !siteId.value().startsWith("site:") || !workerId.value().startsWith("resident:")
                || !outputItemId.value().startsWith("item:site-harvest-") || !intentId.value().startsWith("intent:site-harvest-")) {
            throw new IllegalArgumentException("resource-site harvest identities must use canonical namespaces");
        }
        if (!traversal.provenance().equals(siteId) || traversal.edges().stream().anyMatch(edge -> edge.kind() != TraversalKind.PEDESTRIAN
                || !edge.traversableBy(TraversalCapability.PEDESTRIAN)) || traversal.linearCorridorSurfaces().size() < ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS
                || traversalCursor < 0 || traversalCursor >= traversal.linearCorridorSurfaces().size()) {
            throw new IllegalArgumentException("resource-site harvest must retain one open pedestrian work traversal and cursor");
        }
    }

    public ResourceSiteHarvestJob withProgress(ResourceSiteHarvestProgress next) {
        return new ResourceSiteHarvestJob(id, taskId, siteId, workerId, outputItemId, outputSlot, intentId, next, traversal, traversalCursor);
    }

    /**
     * Pins the still-unstarted field corridor to the one body position observed during the
     * ambient-to-scene transfer.  This is the only legal rebase: after the first retained edge
     * or crop receipt, the original topology/cursor remains the operation's progress truth.
     */
    public ResourceSiteHarvestJob rebaseUnstartedTraversal(TraversalTopology next) {
        if (progress.completedCropSlots() != 0 || progress.hasPendingCrop() || traversalCursor != 0) {
            throw new IllegalArgumentException("only an unstarted field worker may rebase its traversal at HOT hand-off");
        }
        return new ResourceSiteHarvestJob(id, taskId, siteId, workerId, outputItemId, outputSlot, intentId, progress,
                Objects.requireNonNull(next, "rebased field-work traversal"), 0);
    }

    public int firstCropCursor() { return traversal.linearCorridorSurfaces().size() - ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS; }
    public int cropCursor() { return firstCropCursor() + progress.completedCropSlots(); }
    public boolean atCurrentCropStation() { return traversalCursor == cropCursor(); }
    public boolean hasNextTraversalStep() { return !progress.complete() && traversalCursor < cropCursor(); }
    public SurfaceAnchor nextTraversalSurface() {
        if (!hasNextTraversalStep()) throw new IllegalStateException("field worker has no retained next traversal surface");
        return traversal.linearCorridorSurfaces().get(traversalCursor + 1);
    }
    public ResourceSiteHarvestJob advanceTraversal(int nextCursor) {
        if (nextCursor != traversalCursor + 1 || nextCursor > cropCursor()) {
            throw new IllegalArgumentException("field worker may advance only one retained approach/work edge");
        }
        return new ResourceSiteHarvestJob(id, taskId, siteId, workerId, outputItemId, outputSlot, intentId, progress, traversal, nextCursor);
    }
}
