package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Horizontal projection over the common immutable regional set. Zero Y is an
 * index coordinate, never a terrain height. Local tokens retain no world directory. */
public final class RegionalColumnSet extends AbstractSet<TerrainColumn> {
    private final RegionalBlockSet columns;
    private volatile Integer hash;
    private RegionalColumnSet(RegionalBlockSet columns) {
        this.columns = columns;
    }
    public static RegionalColumnSet fromPositions(Collection<BlockPosition> positions) {
        var projected = new HashSet<BlockPosition>();
        positions.forEach(p -> projected.add(new BlockPosition(p.x(), 0, p.z())));
        return new RegionalColumnSet(RegionalBlockSet.copyOf(projected));
    }
    public static RegionalColumnSet union(Collection<RegionalColumnSet> sources) {
        return new RegionalColumnSet(RegionalBlockSet.union(sources.stream().map(s -> s.columns).toList()));
    }
    public Object token(Region region) { return columns.token(region); }
    @Override public int size() { return columns.size(); }
    @Override public int hashCode() {
        // Most readers need contains/local tokens only. Do not walk the world
        // merely to construct another thin union directory.
        var retained = hash;
        if (retained != null) return retained;
        int value = columns.stream().mapToInt(p -> new TerrainColumn(p.x(), p.z()).hashCode()).sum();
        hash = value; return value;
    }
    @Override public boolean contains(Object value) {
        return value instanceof TerrainColumn column && columns.contains(new BlockPosition(column.x(), 0, column.z()));
    }
    @Override public Iterator<TerrainColumn> iterator() {
        return columns.stream().map(p -> new TerrainColumn(p.x(), p.z())).iterator();
    }
}
