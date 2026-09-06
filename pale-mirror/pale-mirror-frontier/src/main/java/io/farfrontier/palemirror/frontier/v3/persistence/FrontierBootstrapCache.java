package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrap;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRuleset;
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
        Key key = new Key(worldId, seed, ruleset, terrain);
        synchronized (VALUES) {
            FrontierBootstrap known = VALUES.get(key);
            if (known != null) return known;
            FrontierBootstrap created = FrontierBootstrapper.create(worldId, seed, ruleset, terrain);
            VALUES.put(key, created);
            return created;
        }
    }

    private record Key(WorldId worldId, long seed, FrontierRuleset ruleset, TerrainSurfacePlan terrain) { }
}
