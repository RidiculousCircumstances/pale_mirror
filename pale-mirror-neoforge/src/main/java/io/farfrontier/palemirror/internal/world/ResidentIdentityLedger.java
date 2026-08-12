package io.farfrontier.palemirror.internal.world;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Persists stable authored identities that canonical reconciliation has permanently retired. */
public final class ResidentIdentityLedger {
    private final Set<String> retired;
    public ResidentIdentityLedger() { this(Set.of()); }
    ResidentIdentityLedger(Collection<String> retired) { this.retired = new LinkedHashSet<>(retired); }
    public boolean retire(String residentId) {
        if (residentId == null || residentId.isBlank()) throw new IllegalArgumentException("Resident identity is required");
        return retired.add(residentId);
    }
    public boolean retired(String residentId) { return retired.contains(residentId); }
    public Set<String> retiredIds() { return Set.copyOf(retired); }
    public void clear() { retired.clear(); }
}
