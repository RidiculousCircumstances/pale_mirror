package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Sole persisted owner of bounded deferred physical aftermath. */
public record DeferredAftermathState(Map<SubjectId, DeferredAftermath> entries) {
    public static final int MAX_ENTRIES = 256;
    public DeferredAftermathState {
        entries = Map.copyOf(Objects.requireNonNull(entries, "deferred aftermath entries"));
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("deferred aftermath retention limit exceeded");
        for (Map.Entry<SubjectId, DeferredAftermath> entry : entries.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("aftermath index differs from retained identity");
        }
    }
    public static DeferredAftermathState empty() { return new DeferredAftermathState(Map.of()); }
    public DeferredAftermathState prepare(DeferredAftermath aftermath) {
        Objects.requireNonNull(aftermath, "aftermath");
        DeferredAftermathState retained = compactTerminal();
        if (retained.entries.containsKey(aftermath.id()) || retained.entries.values().stream().anyMatch(value -> value.causeId().equals(aftermath.causeId()))) {
            throw new IllegalArgumentException("duplicate deferred aftermath causal identity");
        }
        Map<SubjectId, DeferredAftermath> next = new LinkedHashMap<>(retained.entries); next.put(aftermath.id(), aftermath);
        return new DeferredAftermathState(next);
    }

    /**
     * The durable owner retains active evidence only.  This explicit deterministic transition
     * is applied as part of the next prepared event, so terminal history cannot consume the
     * finite admission bound forever or require a global maintenance sweep.
     */
    public DeferredAftermathState compactTerminal() {
        Map<SubjectId, DeferredAftermath> next = new LinkedHashMap<>();
        entries.entrySet().stream().filter(entry -> !entry.getValue().terminal()).sorted(Map.Entry.comparingByKey())
                .forEach(entry -> next.put(entry.getKey(), entry.getValue()));
        return next.size() == entries.size() ? this : new DeferredAftermathState(next);
    }
    public DeferredAftermathState resolve(SubjectId id, long expectedEpoch, long observationAt, int expectedCursor, long authorityRevision, DeferredAftermathCellStatus result) {
        DeferredAftermath current = entries.get(id);
        if (current == null) throw new IllegalArgumentException("unknown deferred aftermath");
        if (current.expectedEpoch() != expectedEpoch) throw new IllegalArgumentException("stale deferred aftermath epoch");
        Map<SubjectId, DeferredAftermath> next = new LinkedHashMap<>(entries);
        next.put(id, current.resolve(observationAt, expectedCursor, authorityRevision, result));
        return new DeferredAftermathState(next);
    }
    public DeferredAftermathState resolve(SubjectId id, long expectedEpoch, long observationAt, int expectedCursor, DeferredAftermathCellStatus result) {
        DeferredAftermath current = entries.get(id); DeferredAftermathCell cell = current == null ? null : current.cellAt(expectedCursor);
        long revision = result == DeferredAftermathCellStatus.RUNNING ? 0L : cell == null ? -1L : cell.authorityRevision();
        return resolve(id, expectedEpoch, observationAt, expectedCursor, revision, result);
    }
}
