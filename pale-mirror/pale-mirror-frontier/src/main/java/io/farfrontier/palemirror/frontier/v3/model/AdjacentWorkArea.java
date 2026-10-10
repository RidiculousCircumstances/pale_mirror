package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.LongPredicate;

/** Pure spatial selection. The family supplies eligibility; this mechanism knows no resource or job. */
public final class AdjacentWorkArea {
    public record Cell(long id, BlockPosition position) {
        public Cell { Objects.requireNonNull(position); if (id < 1) throw new IllegalArgumentException("invalid area cell"); }
    }
    private AdjacentWorkArea() { }
    public static Set<Long> extend(List<Cell> cells, WorkAreaDevelopment frontier, LongPredicate eligible, int limit) {
        if (cells.isEmpty() || cells.size() > WorkAreaDevelopment.MAX_CELLS || limit < 1 || limit > WorkAreaDevelopment.MAX_CELLS)
            throw new IllegalArgumentException("unbounded adjacent-area selection");
        var positions = new HashMap<BlockPosition, Long>(); var byId = new HashMap<Long, BlockPosition>();
        for (var cell : cells) if (positions.putIfAbsent(cell.position(), cell.id()) != null
                || byId.putIfAbsent(cell.id(), cell.position()) != null)
            throw new IllegalArgumentException("competing work-area cells");
        if (!byId.keySet().containsAll(frontier.openedCells())) throw new IllegalArgumentException("foreign developed area");
        var pending = new TreeSet<Long>(); var visited = new HashSet<Long>(frontier.openedCells());
        for (long id : frontier.openedCells()) adjacent(byId.get(id), positions).forEach(pending::add);
        var additions = new LinkedHashSet<Long>();
        while (!pending.isEmpty() && additions.size() < limit) {
            long id = pending.pollFirst();
            if (!visited.add(id) || !eligible.test(id)) continue;
            additions.add(id); adjacent(byId.get(id), positions).forEach(pending::add);
        }
        return Set.copyOf(additions);
    }
    private static List<Long> adjacent(BlockPosition at, Map<BlockPosition, Long> positions) {
        var result = new ArrayList<Long>();
        for (var position : List.of(at.offset(1, 0, 0), at.offset(-1, 0, 0), at.offset(0, 1, 0),
                at.offset(0, -1, 0), at.offset(0, 0, 1), at.offset(0, 0, -1))) {
            Long id = positions.get(position); if (id != null) result.add(id);
        }
        return result;
    }
}
