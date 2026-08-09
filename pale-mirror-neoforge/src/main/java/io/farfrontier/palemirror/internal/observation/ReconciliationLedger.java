package io.farfrontier.palemirror.internal.observation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Bounded durable deduplication window for observations replayed after reload or restart. */
public final class ReconciliationLedger {
    private static final int RETENTION_LIMIT = 512;
    private final LinkedHashSet<String> appliedIds;

    public ReconciliationLedger() { this(List.of()); }

    public ReconciliationLedger(List<String> appliedIds) {
        this.appliedIds = new LinkedHashSet<>(appliedIds);
        trim();
    }

    public boolean recordIfNew(String id) {
        if (!appliedIds.add(id)) return false;
        trim();
        return true;
    }

    public List<String> appliedIds() { return List.copyOf(appliedIds); }
    public void clear() { appliedIds.clear(); }

    private void trim() {
        while (appliedIds.size() > RETENTION_LIMIT) appliedIds.remove(appliedIds.getFirst());
    }
}
