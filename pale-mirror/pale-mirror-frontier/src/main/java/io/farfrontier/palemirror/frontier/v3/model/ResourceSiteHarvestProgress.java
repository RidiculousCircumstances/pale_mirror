package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Durable bounded progress for the one-cell-at-a-time work of a harvest.
 *
 * <p>Completed is a count, never a slot index. The selected and retained witness slots name
 * stable CellIds through the admitted layout revision; the cell ledger validates both.
 * HOT and COLD advance the same canonical progress, never separate route cursors.</p>
 */
public record ResourceSiteHarvestProgress(int totalCropSlots, int completedCropSlots,
                                          int pendingCropSlotIndex, int selectedCropSlotIndex,
                                          int lastCompletedCropSlotIndex) {
    public static final int TOTAL_CROP_SLOTS = ResourceSiteKind.WHEAT_FIELD.cropSlotCount();

    public ResourceSiteHarvestProgress {
        if (totalCropSlots < 1 || totalCropSlots > ResourceFieldLayout.MAX_CELLS
                || completedCropSlots < 0 || completedCropSlots > totalCropSlots
                || pendingCropSlotIndex < -1 || pendingCropSlotIndex >= totalCropSlots
                || selectedCropSlotIndex < -1 || selectedCropSlotIndex >= totalCropSlots
                || lastCompletedCropSlotIndex < -1 || lastCompletedCropSlotIndex >= totalCropSlots
                || (completedCropSlots == 0) != (lastCompletedCropSlotIndex == -1)
                || (completedCropSlots == totalCropSlots) != (selectedCropSlotIndex == -1)
                || pendingCropSlotIndex >= 0 && pendingCropSlotIndex != selectedCropSlotIndex
                || pendingCropSlotIndex >= 0 && pendingCropSlotIndex == lastCompletedCropSlotIndex
                || selectedCropSlotIndex == lastCompletedCropSlotIndex) {
            throw new IllegalArgumentException("resource-site harvest progress is outside its bounded field");
        }
    }

    /** Fixture convenience for the historically ordered initial layout only. */
    public ResourceSiteHarvestProgress(int totalCropSlots, int completedCropSlots, int pendingCropSlotIndex) {
        this(totalCropSlots, completedCropSlots, pendingCropSlotIndex,
                completedCropSlots == totalCropSlots ? -1 : completedCropSlots,
                completedCropSlots == 0 ? -1 : completedCropSlots - 1);
    }

    public static ResourceSiteHarvestProgress notStarted(int totalCropSlots) {
        return new ResourceSiteHarvestProgress(totalCropSlots, 0, -1, 0, -1);
    }
    public boolean complete() { return completedCropSlots == totalCropSlots; }
    public boolean hasPendingCrop() { return pendingCropSlotIndex >= 0; }
    public int nextCropSlotIndex() {
        if (complete()) throw new IllegalStateException("completed harvest has no next crop slot");
        return selectedCropSlotIndex;
    }
    public ResourceSiteHarvestProgress prepareNextCrop() {
        if (complete() || hasPendingCrop()) throw new IllegalStateException("harvest cursor cannot prepare its next crop");
        return new ResourceSiteHarvestProgress(totalCropSlots, completedCropSlots, nextCropSlotIndex(), selectedCropSlotIndex,
                lastCompletedCropSlotIndex);
    }
    /** The field changed before its physical work witness began; no cell or yield was credited. */
    public ResourceSiteHarvestProgress cancelPreparedCrop() {
        if (!hasPendingCrop() || pendingCropSlotIndex != selectedCropSlotIndex)
            throw new IllegalStateException("harvest cursor has no prepared crop to cancel");
        return new ResourceSiteHarvestProgress(totalCropSlots, completedCropSlots, -1, selectedCropSlotIndex,
                lastCompletedCropSlotIndex);
    }
    public ResourceSiteHarvestProgress confirmPreparedCrop() {
        return confirmPreparedCrop(completedCropSlots + 1 == totalCropSlots ? -1 : completedCropSlots + 1);
    }
    public ResourceSiteHarvestProgress confirmPreparedCrop(int nextSelectedCropSlotIndex) {
        if (!hasPendingCrop() || pendingCropSlotIndex != selectedCropSlotIndex) {
            throw new IllegalStateException("harvest cursor has no exact prepared crop");
        }
        return new ResourceSiteHarvestProgress(totalCropSlots, Math.addExact(completedCropSlots, 1), -1,
                nextSelectedCropSlotIndex, selectedCropSlotIndex);
    }

    /** The owning cell ledger, not the completion count, chooses the next target. */
    public ResourceSiteHarvestProgress withSelectedCropSlot(int next) {
        if (hasPendingCrop() || complete() || next < 0 || next >= totalCropSlots)
            throw new IllegalArgumentException("field cannot select a stale or foreign target");
        return new ResourceSiteHarvestProgress(totalCropSlots, completedCropSlots, -1, next,
                lastCompletedCropSlotIndex);
    }

    /** Account an unavailable selected target without inventing a physical crop effect. */
    public ResourceSiteHarvestProgress skipSelectedCrop(int nextSelectedCropSlotIndex) {
        if (complete() || hasPendingCrop())
            throw new IllegalArgumentException("field cannot skip a completed or prepared target");
        return new ResourceSiteHarvestProgress(totalCropSlots, Math.addExact(completedCropSlots, 1), -1,
                nextSelectedCropSlotIndex, selectedCropSlotIndex);
    }

    /** The cell owner may close a different outstanding CellId without moving the farmer. */
    public ResourceSiteHarvestProgress accountObservedLoss(int lostSlot, int nextSelectedCropSlotIndex) {
        if (complete() || lostSlot < 0 || lostSlot >= totalCropSlots
                || lostSlot == selectedCropSlotIndex && hasPendingCrop()
                || completedCropSlots + 1 == totalCropSlots && nextSelectedCropSlotIndex != -1
                || completedCropSlots + 1 < totalCropSlots
                    && (nextSelectedCropSlotIndex < 0 || nextSelectedCropSlotIndex == lostSlot)
                || lostSlot != selectedCropSlotIndex && nextSelectedCropSlotIndex != selectedCropSlotIndex)
            throw new IllegalArgumentException("observed field loss has no consistent outstanding work target");
        // The physical executor may still be acknowledging the preceding farmer
        // effect. An unrelated lost cell must not replace that witness cursor.
        int retainedWitnessSlot = lastCompletedCropSlotIndex >= 0 ? lastCompletedCropSlotIndex : lostSlot;
        return new ResourceSiteHarvestProgress(totalCropSlots, completedCropSlots + 1,
                pendingCropSlotIndex, nextSelectedCropSlotIndex, retainedWitnessSlot);
    }
}
