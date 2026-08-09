package io.farfrontier.palemirror.internal.world;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.farfrontier.palemirror.domain.WorldObjectId;

/** Authoritative registry for physical location and template identity of managed objects. */
public final class WorldObjectRegistry {
    private final Map<WorldObjectId, WorldObjectRegistryEntry> entries = new LinkedHashMap<>();

    public void register(WorldObjectRegistryEntry entry) {
        if (entries.putIfAbsent(entry.id(), entry) != null) throw new IllegalStateException("Duplicate world object " + entry.id());
    }

    public Optional<WorldObjectRegistryEntry> find(WorldObjectId id) { return Optional.ofNullable(entries.get(id)); }
    public WorldObjectRegistryEntry require(WorldObjectId id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown world object " + id));
    }
    public Collection<WorldObjectRegistryEntry> entries() { return entries.values(); }
    public void clear() { entries.clear(); }
}
