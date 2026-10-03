package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable sparse chunk directory with direct 16x16 column lookup inside each chunk. */
public final class ChunkSurfaceIndex {
    private record Chunk(int x, int z) {
        @Override public int hashCode() {
            int hash = x * 0x9e3779b9 + Integer.rotateLeft(z * 0x85ebca6b, 16);
            hash ^= hash >>> 16;
            hash *= 0x85ebca6b;
            return hash ^ (hash >>> 13);
        }
    }

    private final Map<Chunk, SurfaceAnchor[]> chunks;

    private ChunkSurfaceIndex(Map<Chunk, SurfaceAnchor[]> chunks) {
        // Arrays are built privately and never exposed. HashMap avoids MapN's clustered probing.
        this.chunks = Collections.unmodifiableMap(chunks);
    }

    public static ChunkSurfaceIndex of(Iterable<SurfaceAnchor> surfaces) {
        Map<Chunk, SurfaceAnchor[]> chunks = new HashMap<>();
        for (SurfaceAnchor surface : surfaces) {
            SurfaceAnchor[] cells = chunks.computeIfAbsent(new Chunk(surface.x() >> 4, surface.z() >> 4),
                    ignored -> new SurfaceAnchor[256]);
            int slot = slot(surface.x(), surface.z());
            SurfaceAnchor previous = cells[slot];
            if (previous == null || previous.y() < surface.y()) cells[slot] = surface;
        }
        return new ChunkSurfaceIndex(chunks);
    }

    public SurfaceAnchor at(int x, int z) {
        SurfaceAnchor[] cells = chunks.get(new Chunk(x >> 4, z >> 4));
        return cells == null ? null : cells[slot(x, z)];
    }

    private static int slot(int x, int z) { return (z & 15) * 16 + (x & 15); }
}
