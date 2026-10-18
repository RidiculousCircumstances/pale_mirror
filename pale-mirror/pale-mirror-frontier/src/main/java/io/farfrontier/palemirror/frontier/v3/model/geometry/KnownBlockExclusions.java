package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.*;

/** Exact revoked baseline facts, partitioned by spatial region. Never grants mining or restoration rights.
 * World bounds validate coverage; an arbitrary lifetime edit counter must not stop a valid world. */
public record KnownBlockExclusions(Set<BlockPosition> positions) {
    public KnownBlockExclusions {
        positions = RegionalBlockSet.copyOf(Objects.requireNonNull(positions));
    }
    public static KnownBlockExclusions empty() { return new KnownBlockExclusions(Set.of()); }
    public KnownBlockExclusions invalidate(BlockPosition position) { return invalidate(Set.of(position)); }
    /** A bounded ingress batch copies only touched regions, not every position in the world. */
    public KnownBlockExclusions invalidate(Collection<BlockPosition> changes) {
        var before = (RegionalBlockSet) positions;
        var after = before.overlay(Set.of(), Set.copyOf(changes));
        return after == before ? this : new KnownBlockExclusions(after);
    }

    /** Exact append proof across shared regions and recovery; no parent snapshots are retained. */
    public Optional<Set<BlockPosition>> additionsSince(KnownBlockExclusions prior) {
        var current = (RegionalBlockSet) positions;
        var previous = (RegionalBlockSet) Objects.requireNonNull(prior).positions;
        var added = new HashSet<BlockPosition>();
        for (var region : current.changesFrom(previous)) {
            var now = current.in(region); var before = previous.in(region);
            if (!now.containsAll(before)) return Optional.empty();
            for (var position : now) if (!before.contains(position)) added.add(position);
        }
        return Optional.of(Set.copyOf(added));
    }
}
