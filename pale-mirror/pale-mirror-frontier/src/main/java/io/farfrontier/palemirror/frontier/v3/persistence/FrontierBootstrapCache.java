package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrap;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRuleset;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.TerrainSurfacePlan;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded process-local reuse of genesis derived exactly from a snapshot header. */
final class FrontierBootstrapCache {
    private static final int CAPACITY = 8;
    private static final Map<Key, FrontierBootstrap> VALUES = new LinkedHashMap<>(CAPACITY, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, FrontierBootstrap> eldest) { return size() > CAPACITY; }
    };

    private FrontierBootstrapCache() { }

    static FrontierBootstrap resolve(WorldId worldId, long seed, FrontierRuleset ruleset, TerrainSurfacePlan terrain) {
        return resolve(worldId, seed, ruleset, terrain, Map.of());
    }

    static FrontierBootstrap resolve(WorldId worldId, long seed, FrontierRuleset ruleset, TerrainSurfacePlan terrain,
                                     Map<SubjectId, ResourceFieldLayout> initialFieldLayouts) {
        Key key = new Key(worldId, seed, ruleset, terrain, Map.copyOf(initialFieldLayouts));
        synchronized (VALUES) {
            FrontierBootstrap known = VALUES.get(key);
            if (known != null) return known;
            FrontierBootstrap baseline = FrontierBootstrapper.create(worldId, seed, ruleset, terrain);
            FrontierBootstrap created = initialFieldLayouts.isEmpty() ? baseline : new FrontierBootstrap(
                    baseline.worldId(), baseline.seed(), baseline.bounds(), baseline.settlements(), baseline.hive(),
                    baseline.ruleset(), baseline.terrain(), initialFieldLayouts);
            VALUES.put(key, created);
            return created;
        }
    }

    private record Key(WorldId worldId, long seed, FrontierRuleset ruleset, TerrainSurfacePlan terrain,
                       Map<SubjectId, ResourceFieldLayout> initialFieldLayouts) { }
}
