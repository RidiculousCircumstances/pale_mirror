package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.*;
import java.util.function.ToLongFunction;

/** Disposable scheduling only: alternating nearby and oldest pending preparation. */
final class FrontierV3PreparationWindow<K> {
    private final LinkedHashSet<K> pending = new LinkedHashSet<>();
    private boolean nearest;

    static FrontierV3FirstVisibilityBudget handoffBudget() {
        return new FrontierV3FirstVisibilityBudget(32, 1,
                (long) (io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.RUNTIME_BUDGET_MILLIS.get() * 1_000_000D),
                System::nanoTime);
    }

    static <K> K poll(LinkedHashSet<K> pending, ToLongFunction<K> distance, boolean nearest) {
        if (pending == null || pending.isEmpty()) return null;
        K key = nearest ? pending.stream().min(Comparator.comparingLong(distance)).orElseThrow()
                : pending.iterator().next();
        pending.remove(key);
        return key;
    }

    static net.minecraft.world.level.ChunkPos pollChunk(LinkedHashSet<net.minecraft.world.level.ChunkPos> pending,
            List<net.minecraft.world.level.ChunkPos> observers, boolean nearest) {
        return poll(pending, candidate -> observers.stream().mapToLong(observer -> {
            long dx = (long) candidate.x - observer.x, dz = (long) candidate.z - observer.z;
            return dx * dx + dz * dz;
        }).min().orElse(Long.MAX_VALUE), nearest);
    }

    List<K> next(Collection<K> current, ToLongFunction<K> distance, int limit) {
        if (limit < 1) throw new IllegalArgumentException("positive preparation window required");
        var membership = new HashSet<>(current);
        pending.retainAll(membership);
        pending.addAll(current);
        var selected = new ArrayList<K>();
        int count = Math.min(limit, pending.size());
        while (selected.size() < count) {
            nearest = !nearest;
            selected.add(poll(pending, distance, nearest));
        }
        pending.addAll(selected);
        return List.copyOf(selected);
    }
}
