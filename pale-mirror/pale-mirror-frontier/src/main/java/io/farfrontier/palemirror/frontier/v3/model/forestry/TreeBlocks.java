package io.farfrontier.palemirror.frontier.v3.model.forestry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import java.util.*;

/** Immutable four-byte entries: three signed relative coordinates and one palette index.
 * No retained BlockPosition/Map entry/boxed integer per log. */
public final class TreeBlocks {
    public static final int MAX_LOGS = 4096;
    private final List<BlockExtraction.Block> palette;
    private final int[] entries;
    private final int hash;

    public TreeBlocks(List<BlockExtraction.Block> palette, int[] entries) {
        this.palette = List.copyOf(palette); this.entries = entries.clone();
        if (palette.isEmpty() || palette.size() > 256 || entries.length < 1 || entries.length > MAX_LOGS
                || new HashSet<>(palette).size() != palette.size())
            throw new IllegalArgumentException("invalid compact tree block image");
        var positions = new HashSet<Integer>();
        for (int entry : entries) {
            if ((entry >>> 24) >= palette.size() || !positions.add(entry & 0xFFFFFF))
                throw new IllegalArgumentException("tree has an unknown material or duplicate log position");
        }
        hash = 31 * this.palette.hashCode() + Arrays.hashCode(this.entries);
    }
    public static TreeBlocks capture(BlockPosition origin, Map<BlockPosition, BlockExtraction.Block> logs) {
        var materials = new ArrayList<BlockExtraction.Block>();
        int[] result = new int[logs.size()]; int next = 0;
        for (var entry : logs.entrySet().stream().sorted(Map.Entry.comparingByKey(
                Comparator.comparingInt(BlockPosition::y).thenComparingInt(BlockPosition::x).thenComparingInt(BlockPosition::z))).toList()) {
            int material = materials.indexOf(entry.getValue());
            if (material < 0) { material = materials.size(); materials.add(entry.getValue()); }
            var p = entry.getKey();
            result[next++] = encode(p.x() - origin.x(), p.y() - origin.y(), p.z() - origin.z(), material);
        }
        return new TreeBlocks(materials, result);
    }
    public static int encode(int x, int y, int z, int material) {
        if (x < -128 || x > 127 || y < -128 || y > 127 || z < -128 || z > 127 || material < 0 || material > 255)
            throw new IllegalArgumentException("tree block exceeds its compact bounded footprint");
        return (x & 255) | (y & 255) << 8 | (z & 255) << 16 | material << 24;
    }
    public int size() { return entries.length; }
    public List<BlockExtraction.Block> palette() { return palette; }
    public int[] packed() { return entries.clone(); }
    public BlockPosition position(BlockPosition origin, int index) {
        int entry = entries[index];
        return origin.offset((byte) entry, (byte) (entry >>> 8), (byte) (entry >>> 16));
    }
    public BlockExtraction.Block block(int index) { return palette.get(entries[index] >>> 24); }
    public int indexOf(BlockPosition origin, BlockPosition position) {
        int x = position.x() - origin.x(), y = position.y() - origin.y(), z = position.z() - origin.z();
        if (x < -128 || x > 127 || y < -128 || y > 127 || z < -128 || z > 127) return -1;
        int key = encode(x, y, z, 0);
        for (int i = 0; i < entries.length; i++) if ((entries[i] & 0xFFFFFF) == key) return i;
        return -1;
    }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof TreeBlocks blocks && palette.equals(blocks.palette) && Arrays.equals(entries, blocks.entries);
    }
    @Override public int hashCode() { return hash; }
}
