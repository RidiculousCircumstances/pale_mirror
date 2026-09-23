package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.*;

/** Bounded observations; the caller supplies successful write/read+sync fencing before commit. */
final class FrontierV3CargoFootprintCoverage {
    private final Map<Long, Set<UUID>> confirmed = new HashMap<>();
    private final Map<Long, Set<UUID>> pending = new HashMap<>();
    private final Set<Long> unknown = new HashSet<>();
    private boolean overflowed;
    private int presentEntries;

    void invalidate(long chunk) {
        var priorConfirmed = confirmed.remove(chunk); var priorPending = pending.remove(chunk);
        presentEntries -= priorConfirmed == null ? 0 : priorConfirmed.size();
        presentEntries -= priorPending == null ? 0 : priorPending.size();
        if (!unknown.contains(chunk) && unknown.size() >= FrontierV3EntitySaveBatch.MAX_CHUNKS) { overflowed = true; return; }
        unknown.add(chunk);
    }

    void observe(long chunk, Set<UUID> present) {
        invalidate(chunk);
        if (overflowed) return;
        if (confirmed.size() + pending.size() >= FrontierV3EntitySaveBatch.MAX_CHUNKS
                || present.size() > FrontierV3EntitySaveBatch.MAX_CHUNKS - presentEntries) { overflowed = true; return; }
        pending.put(chunk, Set.copyOf(present)); unknown.remove(chunk);
        presentEntries += present.size();
    }

    boolean absent(UUID entity, Collection<Long> chunks) {
        if (overflowed || chunks.isEmpty()) return false;
        for (long chunk : chunks) {
            if (unknown.contains(chunk)) return false;
            var contents = pending.containsKey(chunk) ? pending.get(chunk) : confirmed.get(chunk);
            if (contents == null || contents.contains(entity)) return false;
        }
        return true;
    }

    boolean hasPending() { return !pending.isEmpty(); }

    String diagnostic(UUID entity, Collection<Long> chunks) {
        long missing = 0, present = 0;
        for (long chunk : chunks) {
            var contents = pending.containsKey(chunk) ? pending.get(chunk) : confirmed.get(chunk);
            if (unknown.contains(chunk) || contents == null) missing++;
            else if (contents.contains(entity)) present++;
        }
        return "columns=" + chunks.size() + ",unknown=" + missing + ",present=" + present + ",overflow=" + overflowed;
    }

    /** Only after the exact save-batch ticket succeeded and is still current. */
    void saved() {
        if (overflowed) return;
        confirmed.putAll(pending); pending.clear();
    }
}
