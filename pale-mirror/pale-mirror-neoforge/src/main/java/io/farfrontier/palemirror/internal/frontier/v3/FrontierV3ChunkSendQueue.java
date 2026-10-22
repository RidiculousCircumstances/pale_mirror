package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.*;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import net.minecraft.world.level.ChunkPos;

/** Per-connection disposable discovery index. Native pending membership remains authoritative. */
public final class FrontierV3ChunkSendQueue {
    private static final int MAX_PROBES = 64;
    private ChunkPos origin = new ChunkPos(0, 0);
    private NavigableSet<Long> fresh = ordered(origin);
    private final LinkedHashSet<Long> waiting = new LinkedHashSet<>();
    private boolean retryTurn;

    public void add(long chunk) { if (!waiting.contains(chunk)) fresh.add(chunk); }
    public void remove(long chunk) { fresh.remove(chunk); waiting.remove(chunk); }

    /** No retained physical verdict: admission is rechecked immediately before native send. */
    public <T> List<T> collect(ChunkPos observer, int quota, LongFunction<T> loaded, Predicate<T> presentable) {
        if (quota <= 0) return List.of();
        if (!origin.equals(observer)) {
            origin = observer;
            var reordered = ordered(origin); reordered.addAll(fresh); fresh = reordered;
        }
        int attempts = Math.min(MAX_PROBES, fresh.size() + waiting.size());
        var result = new ArrayList<T>();
        var deferred = new ArrayList<Long>();
        while (attempts-- > 0 && result.size() < quota) {
            retryTurn = !retryTurn;
            Long key;
            if (!waiting.isEmpty() && (retryTurn || fresh.isEmpty())) {
                var iterator = waiting.iterator(); key = iterator.next(); iterator.remove();
            } else key = fresh.pollFirst();
            if (key == null) break;
            T candidate = loaded.apply(key);
            if (candidate != null && presentable.test(candidate)) result.add(candidate);
            else deferred.add(key);
        }
        // A held candidate is checked at most once per call, and cannot starve its neighbours.
        waiting.addAll(deferred);
        return List.copyOf(result);
    }

    private static NavigableSet<Long> ordered(ChunkPos observer) {
        return new TreeSet<>(Comparator.<Long>comparingLong(key -> {
            long dx = (long) ChunkPos.getX(key) - observer.x, dz = (long) ChunkPos.getZ(key) - observer.z;
            return dx * dx + dz * dz;
        }).thenComparingLong(Long::longValue));
    }
}
