package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Durable bounded cursor for the one-cell-at-a-time work of a harvest.
 *
 * <p>The admitted layout fixes the work ordering of its cells. This record retains its exact
 * work count rather than a second copy of geometry; slot {@code n} belongs to that admitted
 * layout revision, not a global wheat-field constant. It is canonical progress, not a render timer. A
 * HOT scene and a COLD continuation must both advance this same cursor before an output receipt
 * can be admitted.</p>
 */
public record ResourceSiteHarvestProgress(int totalCropSlots, int completedCropSlots, int pendingCropSlotIndex) {
    public static final int TOTAL_CROP_SLOTS = ResourceSiteKind.WHEAT_FIELD.cropSlotCount();

    public ResourceSiteHarvestProgress {
        if (totalCropSlots < 1 || totalCropSlots > ResourceFieldLayout.MAX_CELLS
                || completedCropSlots < 0 || completedCropSlots > totalCropSlots
                || pendingCropSlotIndex < -1 || pendingCropSlotIndex >= totalCropSlots
                || pendingCropSlotIndex >= 0 && (completedCropSlots == totalCropSlots || pendingCropSlotIndex != completedCropSlots)) {
            throw new IllegalArgumentException("resource-site harvest progress is outside its bounded field");
        }
    }

    public static ResourceSiteHarvestProgress notStarted(int totalCropSlots) {
        return new ResourceSiteHarvestProgress(totalCropSlots, 0, -1);
    }
    public boolean complete() { return completedCropSlots == totalCropSlots; }
    public boolean hasPendingCrop() { return pendingCropSlotIndex >= 0; }
    public int nextCropSlotIndex() {
        if (complete()) throw new IllegalStateException("completed harvest has no next crop slot");
        return completedCropSlots;
    }
    public ResourceSiteHarvestProgress prepareNextCrop() {
        if (complete() || hasPendingCrop()) throw new IllegalStateException("harvest cursor cannot prepare its next crop");
        return new ResourceSiteHarvestProgress(totalCropSlots, completedCropSlots, nextCropSlotIndex());
    }
    public ResourceSiteHarvestProgress confirmPreparedCrop() {
        if (!hasPendingCrop() || pendingCropSlotIndex != completedCropSlots) {
            throw new IllegalStateException("harvest cursor has no exact prepared crop");
        }
        return new ResourceSiteHarvestProgress(totalCropSlots, Math.addExact(completedCropSlots, 1), -1);
    }
}
