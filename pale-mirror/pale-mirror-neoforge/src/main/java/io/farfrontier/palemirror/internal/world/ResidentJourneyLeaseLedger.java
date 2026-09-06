package io.farfrontier.palemirror.internal.world;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ResidentJourneyLeaseLedger {
    private final Map<String, ResidentJourneyLease> leases;
    public ResidentJourneyLeaseLedger() { this(new LinkedHashMap<>()); }
    public ResidentJourneyLeaseLedger(Map<String, ResidentJourneyLease> leases) { this.leases = new LinkedHashMap<>(leases); }
    public Collection<ResidentJourneyLease> leases() { return java.util.List.copyOf(leases.values()); }
    public ResidentJourneyLease acquire(String residentId, String journeyId) {
        ResidentJourneyLease existing = leases.get(residentId);
        if (existing != null && existing.phase() == ResidentJourneyLeasePhase.RELEASED
                && !existing.journeyId().equals(journeyId)) {
            leases.remove(residentId); existing = null;
        }
        if (existing != null && !existing.journeyId().equals(journeyId)) {
            throw new IllegalStateException("Resident " + residentId + " is already leased by " + existing.journeyId());
        }
        if (existing != null) return existing;
        ResidentJourneyLease created = new ResidentJourneyLease(residentId, journeyId,
                ResidentJourneyLeasePhase.RETIRE_ORIGIN, 0, 0);
        leases.put(residentId, created); return created;
    }
    public ResidentJourneyLease lease(String residentId) { return leases.get(residentId); }
    public boolean releaseJourney(String journeyId) {
        boolean changed = false;
        for (ResidentJourneyLease lease : leases.values()) if (lease.journeyId().equals(journeyId)
                && lease.phase() != ResidentJourneyLeasePhase.RELEASED) { lease.release(); changed = true; }
        return changed;
    }
    public void clear() { leases.clear(); }
}
