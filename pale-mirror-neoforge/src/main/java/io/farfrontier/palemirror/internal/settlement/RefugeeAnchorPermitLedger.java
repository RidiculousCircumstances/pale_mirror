package io.farfrontier.palemirror.internal.settlement;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Bounded retained permits prevent copied/old client stacks from becoming authority. */
public final class RefugeeAnchorPermitLedger {
    private static final int MAX_RECORDS = 64;
    private final Map<String, RefugeeAnchorPermit> permits;

    public RefugeeAnchorPermitLedger() { this(new LinkedHashMap<>()); }
    public RefugeeAnchorPermitLedger(Map<String, RefugeeAnchorPermit> permits) {
        if (permits.size() > MAX_RECORDS) throw new IllegalStateException("Refugee anchor permit ledger exceeds its bound");
        this.permits = new LinkedHashMap<>(permits);
    }
    public Collection<RefugeeAnchorPermit> permits() { return java.util.List.copyOf(permits.values()); }
    public Optional<RefugeeAnchorPermit> find(String id) { return Optional.ofNullable(permits.get(id)); }
    public void issue(RefugeeAnchorPermit permit) {
        if (permits.size() >= MAX_RECORDS) throw new IllegalStateException("Refugee anchor permit ledger is full");
        if (permits.putIfAbsent(permit.id(), permit) != null) throw new IllegalStateException("Duplicate refugee anchor permit");
    }
    public boolean compact(long step) {
        int before = permits.size();
        permits.values().stream().filter(value -> value.consumed() || value.expiresAtStep() < step)
                .map(RefugeeAnchorPermit::id).toList().forEach(permits::remove);
        return before != permits.size();
    }
    public void clear() { permits.clear(); }
}
