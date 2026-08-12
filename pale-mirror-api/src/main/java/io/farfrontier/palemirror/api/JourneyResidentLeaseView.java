package io.farfrontier.palemirror.api;

/** Read-only persisted ownership hand-off for one stable resident identity. */
public record JourneyResidentLeaseView(String residentId, String phase, int checkpointIndex, long revision) {
    public JourneyResidentLeaseView {
        if (residentId == null || residentId.isBlank()) throw new IllegalArgumentException("Resident ID is required");
        if (phase == null || phase.isBlank()) throw new IllegalArgumentException("Lease phase is required");
        if (checkpointIndex < 0 || revision < 0) throw new IllegalArgumentException("Invalid lease cursor");
    }
}
