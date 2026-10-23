package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Explicit immutable genesis placement. All geometry, recovery and worldgen
 * consumers receive the same ruleset value; coordinates are not inferred from IDs. */
public record FrontierWorldLayout(WorldBounds bounds, List<BlockPosition> settlements) {
    public FrontierWorldLayout {
        Objects.requireNonNull(bounds); settlements = List.copyOf(settlements);
        if (settlements.size() != 12 || settlements.stream().distinct().count() != settlements.size()
                || settlements.stream().anyMatch(p -> p.y() != 0 || !bounds.contains(p)))
            throw new IllegalArgumentException("invalid graybox settlement layout");
    }
    public static FrontierWorldLayout compact() {
        return new FrontierWorldLayout(new WorldBounds(-512, -512, 1024, 1024), positions(new int[][] {
                {-360,-340},{-120,-340},{120,-340},{360,-340},
                {-360,0},{-120,0},{120,0},{360,0},
                {-360,340},{-120,340},{120,340},{360,340}}));
    }
    public static FrontierWorldLayout distant() {
        return new FrontierWorldLayout(new WorldBounds(-2048, -2048, 4096, 4096), positions(new int[][] {
                {-1480,-1360},{-600,-1360},{440,-1360},{1600,-1360},
                {-1480,0},{-600,0},{440,0},{1600,0},
                {-1480,1480},{-600,1480},{440,1480},{1600,1480}}));
    }
    private static List<BlockPosition> positions(int[][] coordinates) {
        return Arrays.stream(coordinates).map(p -> new BlockPosition(p[0], 0, p[1])).toList();
    }
    String canonicalText() { return bounds + ":" + settlements; }
}
