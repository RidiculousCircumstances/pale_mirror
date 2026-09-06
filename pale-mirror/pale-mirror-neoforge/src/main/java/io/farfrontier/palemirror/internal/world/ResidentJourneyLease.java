package io.farfrontier.palemirror.internal.world;

/** Exclusive persisted right for one stable resident to be projected by one journey. */
public final class ResidentJourneyLease {
    private final String residentId;
    private final String journeyId;
    private ResidentJourneyLeasePhase phase;
    private int checkpointIndex;
    private long revision;

    public ResidentJourneyLease(String residentId, String journeyId, ResidentJourneyLeasePhase phase,
                                int checkpointIndex, long revision) {
        if (residentId == null || residentId.isBlank() || journeyId == null || journeyId.isBlank()
                || phase == null || checkpointIndex < 0 || revision < 0) {
            throw new IllegalArgumentException("Invalid resident journey lease");
        }
        this.residentId = residentId; this.journeyId = journeyId; this.phase = phase;
        this.checkpointIndex = checkpointIndex; this.revision = revision;
    }
    public String residentId() { return residentId; }
    public String journeyId() { return journeyId; }
    public ResidentJourneyLeasePhase phase() { return phase; }
    public int checkpointIndex() { return checkpointIndex; }
    public long revision() { return revision; }
    public void ready() {
        if (phase == ResidentJourneyLeasePhase.RETIRE_ORIGIN) { phase = ResidentJourneyLeasePhase.READY; revision++; }
    }
    public void release() {
        if (phase != ResidentJourneyLeasePhase.RELEASED) { phase = ResidentJourneyLeasePhase.RELEASED; revision++; }
    }
    public void checkpoint(int value) { if (value > checkpointIndex) { checkpointIndex = value; revision++; } }
}
