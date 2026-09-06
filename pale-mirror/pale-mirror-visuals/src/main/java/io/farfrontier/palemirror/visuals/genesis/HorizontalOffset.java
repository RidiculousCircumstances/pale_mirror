package io.farfrontier.palemirror.visuals.genesis;

/** One deterministic, chunk-aligned horizontal refinement around an approximate terrain candidate. */
public record HorizontalOffset(int x, int z) {
    public static final HorizontalOffset ORIGIN = new HorizontalOffset(0, 0);
}
