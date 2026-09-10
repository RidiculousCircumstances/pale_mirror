package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Canonical COLD consequence with a later, non-replaying physical footprint.  It deliberately
 * stores the cause instant separately from physical inspection time and never holds a projectile,
 * block state, loaded-world reference, or presentation lease.
 */
public record DeferredAftermath(SubjectId id, SubjectId ownerId, SubjectId causeId,
                                long eventAt, OptionalLong observedAt, String provenance,
                                DeferredAftermathKnowledge knowledge, long expectedEpoch,
                                List<DeferredAftermathCell> cells, int resolutionCursor) {
    public static final int MAX_CELLS = 32;

    public DeferredAftermath {
        Objects.requireNonNull(id, "aftermath id");
        Objects.requireNonNull(ownerId, "aftermath owner");
        Objects.requireNonNull(causeId, "aftermath cause");
        observedAt = Objects.requireNonNull(observedAt, "aftermath observation instant");
        if (eventAt < 0 || observedAt.isPresent() && observedAt.getAsLong() < eventAt || provenance == null || provenance.isBlank()
                || knowledge == null || expectedEpoch < 0) throw new IllegalArgumentException("invalid deferred aftermath boundary");
        cells = List.copyOf(Objects.requireNonNull(cells, "aftermath cells"));
        if (cells.isEmpty() || cells.size() > MAX_CELLS || cells.stream().map(DeferredAftermathCell::position).distinct().count() != cells.size()
                || resolutionCursor < 0 || resolutionCursor > cells.size()) throw new IllegalArgumentException("invalid bounded deferred aftermath footprint");
        List<DeferredAftermathCell> ordered = new ArrayList<>(cells);
        ordered.sort(Comparator.comparingLong((DeferredAftermathCell cell) -> chunkKey(cell.position()))
                .thenComparingInt(cell -> cell.position().x()).thenComparingInt(cell -> cell.position().y()).thenComparingInt(cell -> cell.position().z()));
        if (!ordered.equals(cells)) throw new IllegalArgumentException("aftermath cells must retain chunk-indexed deterministic order");
        int firstUnresolved = firstUnresolved(cells);
        if (resolutionCursor != firstUnresolved || cells.stream().filter(cell -> cell.status() == DeferredAftermathCellStatus.RUNNING).count() > 1) {
            throw new IllegalArgumentException("aftermath cursor or running boundary is invalid");
        }
    }

    public boolean terminal() { return resolutionCursor == cells.size(); }
    /** Lowest unresolved cell, retained for deterministic diagnostics and durable recovery. */
    public DeferredAftermathCell nextPending() { return terminal() ? null : cells.get(resolutionCursor); }
    public DeferredAftermathCell cellAt(int cursor) { return cursor < 0 || cursor >= cells.size() ? null : cells.get(cursor); }
    public int runningCursor() {
        for (int index = 0; index < cells.size(); index++) if (cells.get(index).status() == DeferredAftermathCellStatus.RUNNING) return index;
        return -1;
    }
    public DeferredAftermath resolve(long observationAt, int expectedCursor, long authorityRevision, DeferredAftermathCellStatus result) {
        DeferredAftermathCell current = cellAt(expectedCursor);
        boolean pendingConflict = current != null && current.status() == DeferredAftermathCellStatus.PENDING
                && result == DeferredAftermathCellStatus.CONFLICTED && authorityRevision == -1L;
        if (terminal() || current == null || result == DeferredAftermathCellStatus.PENDING || observationAt < eventAt
                || current.status() != DeferredAftermathCellStatus.RUNNING && result != DeferredAftermathCellStatus.RUNNING && !pendingConflict
                || current.status() != DeferredAftermathCellStatus.PENDING && result == DeferredAftermathCellStatus.RUNNING
                || result == DeferredAftermathCellStatus.RUNNING && (runningCursor() >= 0 || authorityRevision < 0L)
                || result != DeferredAftermathCellStatus.RUNNING && !pendingConflict && current.authorityRevision() != authorityRevision) {
            throw new IllegalArgumentException("stale or invalid aftermath resolution");
        }
        ArrayList<DeferredAftermathCell> next = new ArrayList<>(cells);
        if (result == DeferredAftermathCellStatus.RUNNING) {
            next.set(expectedCursor, current.begin(authorityRevision));
            return new DeferredAftermath(id, ownerId, causeId, eventAt, observedAt, provenance, knowledge, expectedEpoch, next, firstUnresolved(next));
        }
        if (pendingConflict) {
            next.set(expectedCursor, new DeferredAftermathCell(current.position(), current.expectedOwner(), current.expectedMaterial(), current.expectedPart(),
                    -1L, DeferredAftermathCellStatus.CONFLICTED));
        } else next.set(expectedCursor, current.resolved(result));
        return new DeferredAftermath(id, ownerId, causeId, eventAt, OptionalLong.of(observationAt), provenance, knowledge, expectedEpoch,
                next, firstUnresolved(next));
    }

    public static long chunkKey(BlockPosition position) {
        return ((long) Math.floorDiv(position.x(), 16) << 32) ^ (Math.floorDiv(position.z(), 16) & 0xffffffffL);
    }
    private static int firstUnresolved(List<DeferredAftermathCell> values) {
        for (int index = 0; index < values.size(); index++) {
            DeferredAftermathCellStatus status = values.get(index).status();
            if (status == DeferredAftermathCellStatus.PENDING || status == DeferredAftermathCellStatus.RUNNING) return index;
        }
        return values.size();
    }
}
