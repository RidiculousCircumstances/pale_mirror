package io.farfrontier.palemirror.frontier.v3.model.forestry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.*;

/** Immutable successful generation fact, never a certificate that every log still exists. */
public record TreeDescriptor(TreeId id, long generation, String provider, String species,
                             BlockPosition origin, TreeBlocks logs) {
    public TreeDescriptor {
        Objects.requireNonNull(id); Objects.requireNonNull(origin); Objects.requireNonNull(logs);
        if (generation < 1 || !key(provider) || !key(species) || logs.indexOf(origin, origin) < 0)
            throw new IllegalArgumentException("tree needs complete generation provenance and a declared trunk origin");
    }
    private static boolean key(String value) { return value != null && value.matches("[a-z0-9_]+:[a-z0-9_./-]+"); }
    public ForestRegion owningRegion() { return ForestRegion.containing(origin); }
    public Set<ForestRegion> footprint() {
        var result = new TreeSet<ForestRegion>();
        for (int i = 0; i < logs.size(); i++) result.add(ForestRegion.containing(logs.position(origin, i)));
        return Collections.unmodifiableSet(result);
    }
}
