package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.WeakHashMap;

/** Volatile invalidation of no-load snapshots whenever vanilla starts a newer entity write. */
final class FrontierV3EntityWriteEpochs {
    private static final Epochs<ServerLevel> WRITES = new Epochs<>(8_192);
    private FrontierV3EntityWriteEpochs() { }

    static void began(ServerLevel level, ChunkPos chunk) {
        WRITES.began(level, chunk);
    }

    static OptionalLong stamp(ServerLevel level, ChunkPos chunk) {
        return WRITES.stamp(level, chunk);
    }

    static OptionalLong globalStamp(ServerLevel level) {
        return WRITES.globalStamp(level);
    }

    /** A missing stamp means overflow; the caller must leave recovery unresolved. */
    static final class Epochs<K> {
        private final int maxChunks;
        private final Map<K, Counter> levels = new WeakHashMap<>();

        Epochs(int maxChunks) {
            if (maxChunks < 1) throw new IllegalArgumentException("positive entity-write index bound required");
            this.maxChunks = maxChunks;
        }

        synchronized void began(K level, ChunkPos chunk) {
            var counter = levels.computeIfAbsent(level, ignored -> new Counter());
            if (counter.overflowed) return;
            if (counter.global == Long.MAX_VALUE) {
                counter.overflowed = true;
                counter.chunks.clear();
                return;
            }
            counter.global++;
            long key = chunk.toLong();
            if (!counter.chunks.containsKey(key) && counter.chunks.size() == maxChunks) {
                counter.overflowed = true;
                counter.chunks.clear();
                return;
            }
            long previous = counter.chunks.getOrDefault(key, 0L);
            if (previous == Long.MAX_VALUE) {
                counter.overflowed = true;
                counter.chunks.clear();
            } else counter.chunks.put(key, previous + 1);
        }

        synchronized OptionalLong stamp(K level, ChunkPos chunk) {
            var counter = levels.get(level);
            return counter != null && counter.overflowed ? OptionalLong.empty()
                    : OptionalLong.of(counter == null ? 0L : counter.chunks.getOrDefault(chunk.toLong(), 0L));
        }

        synchronized OptionalLong globalStamp(K level) {
            var counter = levels.get(level);
            return counter != null && counter.overflowed ? OptionalLong.empty()
                    : OptionalLong.of(counter == null ? 0L : counter.global);
        }

        private static final class Counter {
            final Map<Long, Long> chunks = new HashMap<>();
            long global;
            boolean overflowed;
        }
    }
}
