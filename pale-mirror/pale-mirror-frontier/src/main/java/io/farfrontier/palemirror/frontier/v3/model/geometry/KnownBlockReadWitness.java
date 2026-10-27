package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.*;

/** Bounded facts read by a completed derived query. Never admission or effect authority.
 * Observation revision alone does not change a block's meaning; consumers must
 * obtain fresh samples before constructing any new canonical admission proof. */
public final class KnownBlockReadWitness<T> {
    private final Map<BlockPosition, Optional<KnownBlockGeometry.Sample<T>>> facts;
    private final java.util.Set<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region> regions;
    private final Optional<Object> matchingVersion;
    private KnownBlockReadWitness(Map<BlockPosition, Optional<KnownBlockGeometry.Sample<T>>> facts, KnownBlockGeometry<T> source) {
        this.regions = facts.keySet().stream().map(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region::at)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        matchingVersion = source.regionalReadVersion(regions);
        // A complete provider token replaces the block image, rather than retaining
        // thousands of duplicated samples per settlement. Changed tokens recompute.
        this.facts = matchingVersion.isPresent() ? Map.of() : Map.copyOf(facts);
    }
    public int size() { return matchingVersion.isPresent() ? regions.size() : facts.size(); }
    public boolean matches(KnownBlockGeometry<T> current) {
        var version = current.regionalReadVersion(regions);
        if (matchingVersion.isPresent()) return matchingVersion.equals(version);
        for (var entry : facts.entrySet()) {
            var before = entry.getValue(); var after = current.at(entry.getKey());
            if (before.isEmpty() != after.isEmpty()) return false;
            if (before.isPresent() && (!before.orElseThrow().block().equals(after.orElseThrow().block())
                    || before.orElseThrow().permission() != after.orElseThrow().permission())) return false;
        }
        return true;
    }
    public static final class Recorder<T> implements KnownBlockGeometry<T> {
        private final KnownBlockGeometry<T> source;
        private final int maximum;
        private final Map<BlockPosition, Optional<Sample<T>>> facts = new HashMap<>();
        private boolean overflow;
        public Recorder(KnownBlockGeometry<T> source, int maximum) {
            this.source = Objects.requireNonNull(source);
            if (maximum < 1) throw new IllegalArgumentException("positive read-witness capacity required");
            this.maximum = maximum;
        }
        @Override public Optional<Sample<T>> at(BlockPosition position) {
            var retained = facts.get(position);
            if (retained != null) return retained;
            var value = source.at(position);
            if (facts.size() < maximum) facts.put(position, value);
            else overflow = true; // Continue the real query; overflow forbids reuse, not discovery.
            return value;
        }
        public Optional<KnownBlockReadWitness<T>> completed() {
            return overflow ? Optional.empty() : Optional.of(new KnownBlockReadWitness<>(facts, source));
        }
    }
}
