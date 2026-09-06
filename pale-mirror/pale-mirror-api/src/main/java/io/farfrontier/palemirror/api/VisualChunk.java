package io.farfrontier.palemirror.api;

/** Horizontal chunk identity used by immutable visual manifests without exposing Minecraft types. */
public record VisualChunk(int x, int z) implements Comparable<VisualChunk> {
    public static VisualChunk containing(VisualPoint point) {
        return new VisualChunk(Math.floorDiv(point.x(), 16), Math.floorDiv(point.z(), 16));
    }

    @Override
    public int compareTo(VisualChunk other) {
        int byZ = Integer.compare(z, other.z);
        return byZ != 0 ? byZ : Integer.compare(x, other.x);
    }
}
