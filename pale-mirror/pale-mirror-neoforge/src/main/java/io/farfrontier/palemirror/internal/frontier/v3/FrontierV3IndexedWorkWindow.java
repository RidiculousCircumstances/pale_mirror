package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.*;

/** Fair bounded discovery over an immutable owner map, not another progress queue. */
final class FrontierV3IndexedWorkWindow<Key extends Comparable<? super Key>, Value> {
    private Map<Key, Value> source;
    private List<Key> keys = List.of();
    private int cursor;
    private FrontierV3IndexedWorkWindow<Key, Value> preferred;
    private boolean preferredTurn;

    /** Alternating demanded service cannot suppress the caller's ordinary fair sweep. */
    List<Value> nextPreferred(Map<Key, Value> current, Collection<Key> demanded, int limit) {
        if (limit < 1) throw new IllegalArgumentException("positive discovery bound required");
        preferredTurn = !preferredTurn;
        if (!preferredTurn || demanded.isEmpty()) return List.of();
        var selected = new HashMap<Key, Value>();
        for (var key : demanded) if (current.containsKey(key)) selected.put(key, current.get(key));
        if (preferred == null) preferred = new FrontierV3IndexedWorkWindow<>();
        return preferred.next(selected, limit);
    }

    List<Value> next(Map<Key, Value> current, int limit) {
        if (limit < 1) throw new IllegalArgumentException("positive discovery bound required");
        if (source != current) {
            if (source == null || !source.keySet().equals(current.keySet())) {
                keys = current.keySet().stream().sorted().toList();
                cursor = keys.isEmpty() ? 0 : Math.floorMod(cursor, keys.size());
            }
            source = current;
        }
        int count = Math.min(limit, keys.size());
        var result = new ArrayList<Value>(count);
        for (int offset = 0; offset < count; offset++) {
            result.add(current.get(keys.get((cursor + offset) % keys.size())));
        }
        // The first eligible owner gets service. Move the discovery start by one, not
        // by the window width: otherwise a permanently ready first entry starves its neighbours.
        if (!keys.isEmpty()) cursor = (cursor + 1) % keys.size();
        return List.copyOf(result);
    }
}
