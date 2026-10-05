package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Durable bounded progress for the one-cell-at-a-time work of a harvest.
 *
 * <p>Completed is a count, never a slot index. Selected/last slots retain selection history;
 * the exact immutable acceptance owns physical retirement independently of those cursors.
 * HOT and COLD advance the same canonical progress, never separate route cursors.</p>
 */
public record ResourceSiteHarvestProgress(int totalCropSlots, int completedCropSlots,
                                          int pendingCropSlotIndex, int selectedCropSlotIndex,
                                          int lastCompletedCropSlotIndex, java.util.Optional<WorkProgress> work,
                                          java.util.Optional<ResourceSiteHarvestWorkAcceptance> acceptance) {
    public static final int TOTAL_CROP_SLOTS = ResourceSiteKind.WHEAT_FIELD.cropSlotCount();

    public ResourceSiteHarvestProgress(int total, int completed, int pending, int selected, int last) {
        this(total, completed, pending, selected, last, java.util.Optional.empty());
    }

    public ResourceSiteHarvestProgress(int total, int completed, int pending, int selected, int last,
                                       java.util.Optional<WorkProgress> work) {
        this(total, completed, pending, selected, last, work, java.util.Optional.empty());
    }

    public ResourceSiteHarvestProgress {
        java.util.Objects.requireNonNull(work, "cell labour progress");
        java.util.Objects.requireNonNull(acceptance, "accepted physical field work");
        if (acceptance.isPresent() && (pendingCropSlotIndex >= 0 || work.isPresent()
                || acceptance.orElseThrow().receipt().completedCropSlots() > completedCropSlots))
            throw new IllegalArgumentException("accepted field work cannot be replaced by a new physical cell");
        if (work.isPresent() && (selectedCropSlotIndex < 0 || pendingCropSlotIndex >= 0 && !work.orElseThrow().complete()))
            throw new IllegalArgumentException("labour progress has no exact outstanding cell or prepared complete work");
        if (totalCropSlots < 1 || totalCropSlots > ResourceFieldLayout.MAX_CELLS
                || completedCropSlots < 0 || completedCropSlots > totalCropSlots
                || pendingCropSlotIndex < -1 || pendingCropSlotIndex >= totalCropSlots
                || selectedCropSlotIndex < -1 || selectedCropSlotIndex >= totalCropSlots
                || lastCompletedCropSlotIndex < -1 || lastCompletedCropSlotIndex >= totalCropSlots
                || (completedCropSlots == 0) != (lastCompletedCropSlotIndex == -1)
                || completedCropSlots == totalCropSlots && selectedCropSlotIndex != -1
                || pendingCropSlotIndex >= 0 && pendingCropSlotIndex != selectedCropSlotIndex
) {
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
    public ResourceSiteHarvestProgress withWork(WorkProgress value) {
        requireAcknowledged();
        return new ResourceSiteHarvestProgress(totalCropSlots, completedCropSlots, pendingCropSlotIndex,
                selectedCropSlotIndex, lastCompletedCropSlotIndex, java.util.Optional.of(value));
    }
    public boolean complete() { return selectedCropSlotIndex == -1; }
    public boolean hasPendingCrop() { return pendingCropSlotIndex >= 0; }
    public boolean hasPendingPhysicalWork() { return hasPendingCrop() || acceptance.isPresent(); }
    public ResourceSiteHarvestProgress retainAcceptance(ResourceSiteHarvestWorkAcceptance value) {
        requireAcknowledged();
        if (value.receipt().completedCropSlots() != completedCropSlots)
            throw new IllegalArgumentException("field acceptance has a foreign completion count");
        return new ResourceSiteHarvestProgress(totalCropSlots, completedCropSlots, pendingCropSlotIndex,
                selectedCropSlotIndex, lastCompletedCropSlotIndex, work, java.util.Optional.of(value));
    }
    public ResourceSiteHarvestProgress acknowledge(ResourceSiteHarvestWorkAcceptance value) {
        if (!acceptance.equals(java.util.Optional.of(value)))
            throw new IllegalArgumentException("field acknowledgement has a stale or foreign receipt");
        return new ResourceSiteHarvestProgress(totalCropSlots, completedCropSlots, pendingCropSlotIndex,
                selectedCropSlotIndex, lastCompletedCropSlotIndex, work);
    }
    private void requireAcknowledged() {
        if (acceptance.isPresent()) throw new IllegalStateException("field work retains an unacknowledged physical receipt");
    }
    public int nextCropSlotIndex() {
        if (complete()) throw new IllegalStateException("completed harvest has no next crop slot");
        return selectedCropSlotIndex;
    }
    public ResourceSiteHarvestProgress prepareNextCrop() {
        requireAcknowledged();
        if (complete() || hasPendingCrop()) throw new IllegalStateException("harvest cursor cannot prepare its next crop");
        return new ResourceSiteHarvestProgress(totalCropSlots, completedCropSlots, nextCropSlotIndex(), selectedCropSlotIndex,
                lastCompletedCropSlotIndex, work);
    }
    /** The field changed before its physical work witness began; no cell or yield was credited. */
    public ResourceSiteHarvestProgress cancelPreparedCrop() {
        requireAcknowledged();
        if (!hasPendingCrop() || pendingCropSlotIndex != selectedCropSlotIndex)
            throw new IllegalStateException("harvest cursor has no prepared crop to cancel");
        return new ResourceSiteHarvestProgress(totalCropSlots, completedCropSlots, -1, selectedCropSlotIndex,
                lastCompletedCropSlotIndex);
    }
    public ResourceSiteHarvestProgress confirmPreparedCrop() {
        return confirmPreparedCrop(completedCropSlots + 1 == totalCropSlots ? -1 : completedCropSlots + 1);
    }
    public ResourceSiteHarvestProgress confirmPreparedCrop(int nextSelectedCropSlotIndex) {
        requireAcknowledged();
        if (!hasPendingCrop() || pendingCropSlotIndex != selectedCropSlotIndex) {
            throw new IllegalStateException("harvest cursor has no exact prepared crop");
        }
        return new ResourceSiteHarvestProgress(totalCropSlots, Math.addExact(completedCropSlots, 1), -1,
                nextSelectedCropSlotIndex, selectedCropSlotIndex);
    }

    /** The owning cell ledger, not the completion count, chooses the next target. */
    public ResourceSiteHarvestProgress withSelectedCropSlot(int next) {
        requireAcknowledged();
        if (hasPendingCrop() || complete() || next < 0 || next >= totalCropSlots)
            throw new IllegalArgumentException("field cannot select a stale or foreign target");
        return new ResourceSiteHarvestProgress(totalCropSlots, completedCropSlots, -1, next,
                lastCompletedCropSlotIndex);
    }

    /** Reacquire from the current pool after a handoff; no crop outcome is credited. */
    public ResourceSiteHarvestProgress afterDeliverySelection(int next) {
        requireAcknowledged();
        if (hasPendingCrop() || complete() || next < -1 || next >= totalCropSlots)
            throw new IllegalArgumentException("field delivery has no valid successor selection");
        return new ResourceSiteHarvestProgress(totalCropSlots, completedCropSlots, -1, next,
                lastCompletedCropSlotIndex);
    }

    /** Account an unavailable selected target without inventing a physical crop effect. */
    public ResourceSiteHarvestProgress skipSelectedCrop(int nextSelectedCropSlotIndex) {
        requireAcknowledged();
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
                || completedCropSlots + 1 < totalCropSlots && nextSelectedCropSlotIndex == lostSlot
                || lostSlot != selectedCropSlotIndex && nextSelectedCropSlotIndex != selectedCropSlotIndex)
            throw new IllegalArgumentException("observed field loss has no consistent outstanding work target");
        // Unrelated loss preserves both selection history and the exact acceptance.
        int retainedWitnessSlot = lastCompletedCropSlotIndex >= 0 ? lastCompletedCropSlotIndex : lostSlot;
        return new ResourceSiteHarvestProgress(totalCropSlots, completedCropSlots + 1,
                pendingCropSlotIndex, nextSelectedCropSlotIndex, retainedWitnessSlot,
                lostSlot == selectedCropSlotIndex ? java.util.Optional.empty() : work, acceptance);
    }
}
