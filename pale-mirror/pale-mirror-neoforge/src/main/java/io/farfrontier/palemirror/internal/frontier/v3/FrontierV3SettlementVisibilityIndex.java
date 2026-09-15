package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
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
        Map<SubjectId, ResourceSite> sites = FrontierResourceSitePlan.compile(state.bootstrap());
        for (Settlement settlement : state.bootstrap().settlements()) {
            java.util.Set<SubjectId> owners = new java.util.HashSet<>();
            owners.add(settlement.id()); settlement.structures().forEach(structure -> owners.add(structure.id()));
            java.util.LinkedHashSet<ChunkPos> packageChunks = plan.cells().values().stream().filter(cell -> owners.contains(cell.ownerId()))
                    .map(cell -> new ChunkPos(cell.position().x() >> 4, cell.position().z() >> 4))
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            plan.cells().values().stream().filter(cell -> cell.ownerId().equals(FrontierRouteNetwork.OWNER))
                    .map(cell -> new ChunkPos(cell.position().x() >> 4, cell.position().z() >> 4))
                    .filter(route -> packageChunks.stream().anyMatch(local -> Math.abs(local.x - route.x) <= 1 && Math.abs(local.z - route.z) <= 1))
                    .forEach(packageChunks::add);
            sites.values().stream().filter(site -> site.settlementId().equals(settlement.id())).flatMap(site -> site.managedSlots().stream())
                    .map(position -> new ChunkPos(position.x() >> 4, position.z() >> 4)).forEach(packageChunks::add);
            if (packageChunks.isEmpty()) continue;
            java.util.LinkedHashSet<ChunkPos> ingressChunks = new java.util.LinkedHashSet<>(packageChunks);
            packageChunks.forEach(local -> {
                for (int x = local.x - 1; x <= local.x + 1; x++) for (int z = local.z - 1; z <= local.z + 1; z++) ingressChunks.add(new ChunkPos(x, z));
            });
            List<ChunkPos> immutablePackage = List.copyOf(packageChunks);
            ingressChunks.forEach(ingress -> byIngress.computeIfAbsent(ingress, ignored -> new java.util.LinkedHashSet<>()).addAll(immutablePackage));
        }
        return byIngress.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
