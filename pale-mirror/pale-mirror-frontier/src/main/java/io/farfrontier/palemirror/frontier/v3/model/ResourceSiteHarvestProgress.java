package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Durable bounded cursor for the one-cell-at-a-time work of a harvest.
 *
 * <p>The field's immutable plan fixes the serpentine work ordering of its crop slots.  This record
 * therefore stores a count rather than a second copy of field geometry: slot {@code n} is always
 * {@code ResourceSite.cropSlots().get(n)}.  It is canonical progress, not a render timer.  A
 * HOT scene and a COLD continuation must both advance this same cursor before an output receipt
 * can be admitted.</p>
 */
public record ResourceSiteHarvestProgress(int completedCropSlots, int pendingCropSlotIndex) {
    public static final int TOTAL_CROP_SLOTS = ResourceSiteKind.WHEAT_FIELD.cropSlotCount();

    public ResourceSiteHarvestProgress {
        if (completedCropSlots < 0 || completedCropSlots > TOTAL_CROP_SLOTS
                || pendingCropSlotIndex < -1 || pendingCropSlotIndex >= TOTAL_CROP_SLOTS
                || pendingCropSlotIndex >= 0 && (completedCropSlots == TOTAL_CROP_SLOTS || pendingCropSlotIndex != completedCropSlots)) {
            throw new IllegalArgumentException("resource-site harvest progress is outside its bounded field");
        }
    }

    public static ResourceSiteHarvestProgress notStarted() { return new ResourceSiteHarvestProgress(0, -1); }
    public boolean complete() { return completedCropSlots == TOTAL_CROP_SLOTS; }
    public boolean hasPendingCrop() { return pendingCropSlotIndex >= 0; }
    public int nextCropSlotIndex() {
        if (complete()) throw new IllegalStateException("completed harvest has no next crop slot");
        return completedCropSlots;
    }
    public ResourceSiteHarvestProgress prepareNextCrop() {
        if (complete() || hasPendingCrop()) throw new IllegalStateException("harvest cursor cannot prepare its next crop");
        return new ResourceSiteHarvestProgress(completedCropSlots, nextCropSlotIndex());
    }
    public ResourceSiteHarvestProgress confirmPreparedCrop() {
        if (!hasPendingCrop() || pendingCropSlotIndex != completedCropSlots) {
            throw new IllegalStateException("harvest cursor has no exact prepared crop");
        }
        return new ResourceSiteHarvestProgress(Math.addExact(completedCropSlots, 1), -1);
    }
}
