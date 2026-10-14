package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable derived regional knowledge, never task/arrival/custody authority. */
public record PedestrianGeometryRevision(Object scope, Object baseline, Map<Region, Object> revisions) {
    public static final int MAX_REGIONS = 16_384;
    public record Region(int x, int z) {
        public static Region at(BlockPosition position) {
            return new Region(Math.floorDiv(position.x(), 16), Math.floorDiv(position.z(), 16));
        }
    }
    public PedestrianGeometryRevision {
        Objects.requireNonNull(scope); Objects.requireNonNull(baseline);
        revisions = Map.copyOf(revisions);
        if (revisions.size() > MAX_REGIONS) throw new IllegalArgumentException("regional knowledge exceeds its safety bound");
    }
    public static PedestrianGeometryRevision initial() {
        return initial(new Object());
    }
    public static PedestrianGeometryRevision initial(Object scope) { return new PedestrianGeometryRevision(scope, new Object(), Map.of()); }
    public Object at(int regionX, int regionZ) { return revisions.getOrDefault(new Region(regionX, regionZ), baseline); }
    public PedestrianGeometryRevision changed(Set<Region> regions) {
        if (regions.isEmpty()) return this;
        var next = new java.util.LinkedHashMap<>(revisions);
        Object revision = new Object(); regions.forEach(region -> next.put(region, revision));
        return new PedestrianGeometryRevision(scope, baseline, next);
    }
    public Set<Region> changesSince(PedestrianGeometryRevision prior) {
        if (!scope.equals(prior.scope) || baseline != prior.baseline)
            throw new IllegalArgumentException("regional comparison needs the same declared knowledge scope");
        var regions = new java.util.HashSet<>(revisions.keySet()); regions.addAll(prior.revisions.keySet());
        // Profile envelopes combine several immutable terrain tokens. Rebuilding
        // an equal tuple is not a changed region; plain Object epochs still use
        // their identity equality. This is calculation evidence, not actor custody.
        regions.removeIf(region -> Objects.equals(at(region.x(), region.z()), prior.at(region.x(), region.z())));
        return Set.copyOf(regions);
    }
}
