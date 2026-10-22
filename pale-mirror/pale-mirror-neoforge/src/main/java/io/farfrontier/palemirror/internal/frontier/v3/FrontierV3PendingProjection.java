package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

/** Ephemeral work queue, never a physical receipt. Settled facts leave; revisions re-enter locally. */
final class FrontierV3PendingProjection {
    private static final class Slice {
        List<WorksiteBlock> version = List.of();
        final Map<BlockPosition, WorksiteBlock> cells = new HashMap<>();
        final LinkedHashMap<BlockPosition, WorksiteBlock> pending = new LinkedHashMap<>();
        Object availability;
        void update(List<WorksiteBlock> next) {
            if (version == next) return;
            var retained = new HashSet<BlockPosition>();
            for (var block : next) {
                retained.add(block.position());
                if (!block.equals(cells.put(block.position(), block))) pending.put(block.position(), block);
            }
            cells.keySet().retainAll(retained); pending.keySet().retainAll(retained); version = next;
        }
    }
    private final LinkedHashMap<Long, Slice> slices = new LinkedHashMap<>();
    private Map<Long, List<WorksiteBlock>> version;
    private boolean priorityTurn;
    private Long focus;
    void update(Map<Long, List<WorksiteBlock>> current) {
        if (version == current) return;
        slices.keySet().retainAll(current.keySet());
        current.forEach((key, cells) -> slices.computeIfAbsent(key, ignored -> new Slice()).update(cells));
        version = current;
    }
    void invalidate(long chunk, BlockPosition position) {
        var slice = slices.get(chunk);
        if (slice != null && slice.cells.containsKey(position)) slice.pending.put(position, slice.cells.get(position));
    }
    void complete(long chunk, WorksiteBlock block) {
        var slice = slices.get(chunk);
        if (slice != null) slice.pending.remove(block.position(), block);
    }
    int pending(long chunk) { var slice = slices.get(chunk); return slice == null ? 0 : slice.pending.size(); }
    /** A whole chunk gets a bounded contiguous slice; alternating preferred/fair turns prevent starvation. */
    List<WorksiteBlock> batch(LongFunction<Object> availability, LongUnaryOperator priority, int limit) {
        return batch(availability, priority, limit, java.util.Set.of());
    }
    /** In-flight preparation owns its cells until completion; it does not hide chunk availability. */
    List<WorksiteBlock> batch(LongFunction<Object> availability, LongUnaryOperator priority, int limit,
            java.util.Set<BlockPosition> preparing) {
        if (limit < 1) throw new IllegalArgumentException("positive projection budget required");
        for (var entry : slices.entrySet()) {
            var slice = entry.getValue(); var token = availability.apply(entry.getKey());
            if (!Objects.equals(token, slice.availability)) {
                slice.availability = token;
                if (token != null) for (var block : slice.version) slice.pending.put(block.position(), block);
            }
        }
        priorityTurn = !priorityTurn;
        var keys = new ArrayList<>(slices.keySet());
        if (focus != null && (!slices.containsKey(focus) || slices.get(focus).pending.isEmpty()
                || slices.get(focus).availability == null || priority.applyAsLong(focus) == Long.MAX_VALUE)) focus = null;
        var nearest = keys.stream().filter(key -> priority.applyAsLong(key) != Long.MAX_VALUE
                && slices.get(key).availability != null && !slices.get(key).pending.isEmpty())
                .min(Comparator.comparingLong(priority::applyAsLong)).orElse(null);
        if (focus == null || nearest != null && priority.applyAsLong(nearest) < priority.applyAsLong(focus)) focus = nearest;
        if (priorityTurn) keys.sort(Comparator.<Long>comparingInt(key -> key.equals(focus) ? 0 : 1)
                .thenComparingLong(priority::applyAsLong));
        var result = new ArrayList<WorksiteBlock>();
        for (long key : keys) {
            var slice = slices.get(key);
            if (slice.availability == null || slice.pending.isEmpty()) continue;
            var chosen = slice.pending.values().stream().filter(block -> !preparing.contains(block.position()))
                    .limit(limit - result.size()).toList();
            result.addAll(chosen);
            // Unresolved facts retry behind their peers, not sixty-four times in the same turn.
            for (var block : chosen) { slice.pending.remove(block.position()); slice.pending.put(block.position(), block); }
            slices.remove(key); slices.put(key, slice);
            if (result.size() == limit) break;
        }
        return result;
    }
}
