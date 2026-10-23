package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRouteNetwork;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.Settlement;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Map;

/** Immutable ordinary-ingress lookup for one complete authored settlement package. */
final class FrontierV3SettlementVisibilityIndex {
    private FrontierV3SettlementVisibilityIndex() { }

    static Map<ChunkPos, List<ChunkPos>> compile(FrontierWorldState state, FrontierGrayboxPlan plan) {
        if (state == null || plan == null) return Map.of();
        Map<ChunkPos, java.util.LinkedHashSet<ChunkPos>> byIngress = new java.util.HashMap<>();
        Map<SubjectId, ResourceSite> sites = state.resourceSiteDescriptors();
        Map<SubjectId, java.util.Set<ChunkPos>> byOwner = new java.util.HashMap<>();
        for (var cell : plan.cells().values()) byOwner.computeIfAbsent(cell.ownerId(), ignored -> new java.util.HashSet<>())
                .add(new ChunkPos(cell.position().x() >> 4, cell.position().z() >> 4));
        var roads = byOwner.getOrDefault(FrontierRouteNetwork.OWNER, java.util.Set.of());
        for (Settlement settlement : state.bootstrap().settlements()) {
            java.util.Set<SubjectId> owners = new java.util.HashSet<>();
            owners.add(settlement.id()); settlement.structures().forEach(structure -> owners.add(structure.id()));
            java.util.LinkedHashSet<ChunkPos> packageChunks = new java.util.LinkedHashSet<>();
            owners.forEach(owner -> packageChunks.addAll(byOwner.getOrDefault(owner, java.util.Set.of())));
            // The initial settlement footprint owns the halo. A road admitted to
            // it cannot recruit the next road and expand visibility across the world.
            halo(packageChunks).stream().filter(roads::contains).forEach(packageChunks::add);
            sites.values().stream().filter(site -> site.settlementId().equals(settlement.id())).flatMap(site -> site.managedSlots().stream())
                    .map(position -> new ChunkPos(position.x() >> 4, position.z() >> 4)).forEach(packageChunks::add);
            if (packageChunks.isEmpty()) continue;
            var ingressChunks = halo(packageChunks);
            List<ChunkPos> immutablePackage = packageChunks.stream()
                    .sorted(java.util.Comparator.comparingInt((ChunkPos chunk) -> chunk.x).thenComparingInt(chunk -> chunk.z)).toList();
            ingressChunks.forEach(ingress -> byIngress.computeIfAbsent(ingress, ignored -> new java.util.LinkedHashSet<>()).addAll(immutablePackage));
        }
        return byIngress.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
    private static java.util.Set<ChunkPos> halo(java.util.Set<ChunkPos> chunks) {
        var halo = new java.util.HashSet<ChunkPos>();
        for (var local : chunks)
            for (int x = local.x - 1; x <= local.x + 1; x++)
                for (int z = local.z - 1; z <= local.z + 1; z++) halo.add(new ChunkPos(x, z));
        return halo;
    }

}
