package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Rebuildable spatial discovery only. Physical readiness is always observed afresh. */
final class FrontierV3HandoffIndex {
    private static final Map<ServerLevel, FrontierV3HandoffIndex> INDEXES = new WeakHashMap<>();
    private Map<SubjectId, ResourceFieldCycle> cycles;
    private Map<SubjectId, ResourceFieldLayout> layouts = Map.of();
    private Map<ChunkPos, List<SubjectId>> fieldOwners = Map.of();
    private Map<SubjectId, ContainerSurface> surfaces;
    private Map<ChunkPos, List<SubjectId>> containerOwners = Map.of();
    private FrontierV3HandoffIndex() { }

    static List<SubjectId> fields(ServerLevel level, ResourceSiteState sites, ChunkPos chunk) {
        var index = INDEXES.computeIfAbsent(level, ignored -> new FrontierV3HandoffIndex());
        if (index.cycles != sites.cycles()) {
            var layouts = new HashMap<SubjectId, ResourceFieldLayout>();
            sites.cycles().forEach((id, cycle) -> layouts.put(id, cycle.layout()));
            // Crop age/work changes do not rebuild spatial topology.
            if (!index.layouts.keySet().equals(layouts.keySet())
                    || layouts.entrySet().stream().anyMatch(entry -> index.layouts.get(entry.getKey()) != entry.getValue())) {
                var owners = new HashMap<ChunkPos, List<SubjectId>>();
                layouts.forEach((id, layout) -> layout.occupiedChunks().forEach(column ->
                        owners.computeIfAbsent(new ChunkPos(column.x(), column.z()), ignored -> new ArrayList<>()).add(id)));
                index.fieldOwners = freeze(owners);
                index.layouts = Map.copyOf(layouts);
            }
            index.cycles = sites.cycles();
        }
        return index.fieldOwners.getOrDefault(chunk, List.of());
    }

    static List<SubjectId> containers(ServerLevel level, Map<SubjectId, ContainerSurface> surfaces, ChunkPos chunk) {
        var index = INDEXES.computeIfAbsent(level, ignored -> new FrontierV3HandoffIndex());
        if (index.surfaces != surfaces) {
            var owners = new HashMap<ChunkPos, List<SubjectId>>();
            // Chunk terrain presentation owns fixed sockets only. A mobile container
            // belongs to exact body admission; indexing it at a saved pose would both
            // become stale as it moves and make chunk delivery depend on body insertion.
            surfaces.forEach((id, surface) -> {
                if (surface.fixed()) owners.computeIfAbsent(
                        new ChunkPos(surface.position().x() >> 4, surface.position().z() >> 4),
                        ignored -> new ArrayList<>()).add(id);
            });
            index.containerOwners = freeze(owners);
            index.surfaces = surfaces;
        }
        return index.containerOwners.getOrDefault(chunk, List.of());
    }

    private static Map<ChunkPos, List<SubjectId>> freeze(Map<ChunkPos, List<SubjectId>> owners) {
        owners.replaceAll((chunk, ids) -> ids.stream().sorted().toList());
        return Map.copyOf(owners);
    }
}
