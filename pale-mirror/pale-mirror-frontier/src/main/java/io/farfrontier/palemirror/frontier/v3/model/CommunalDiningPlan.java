package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.buildings.*;
import java.util.*;

/** Content composition only. Ordinary storage, shipments and meals retain their own authority. */
public final class CommunalDiningPlan {
    public record Pantry(ContainerRecord container, ContainerSurface surface) { }
    private CommunalDiningPlan() { }
    public static List<Pantry> compile(FrontierBootstrap bootstrap) {
        var result = new ArrayList<Pantry>();
        for (var home : bootstrap.settlements()) for (var building : home.structures()) {
            if (building.kind() != StructureKind.KITCHEN) continue;
            var definition = building.definition();
            var pantry = definition.anchors().get("pantry");
            var serving = definition.anchors().get("serving");
            if (pantry == null || serving == null || definition.zones().values().stream().noneMatch(z -> z.use() == BuildingDefinition.ZoneUse.DINING))
                throw new IllegalArgumentException("kitchen needs declared pantry, serving and dining area");
            var id = new SubjectId("container:pantry-" + UUID.nameUUIDFromBytes(building.id().value().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var station = building.placement().support(serving);
            var exterior = BuildingSpatialPlan.of(building).primaryEntrance().exteriorApproach().getFirst();
            var endpoint = new ShipmentEndpoint.Storage(home.id(), id, station, Optional.of(building.id()), exterior);
            var filter = new ResourceStorageFilter(false, bootstrap.ruleset().residentLife().foods().foods().keySet(), Map.of());
            result.add(new Pantry(ContainerRecord.storage(id, home.id(), 27, StorageBlockKind.BARREL,
                    new ResourceStoragePolicy(endpoint, filter, 100, ResourceStorageRole.WAREHOUSE)),
                    new ContainerSurface(id, building.placement().world(pantry).offset(0, 1, 0), ContainerSurfaceStatus.UNMATERIALIZED)));
        }
        return List.copyOf(result);
    }
    public static ResourceStorageFilter generalFilter(FrontierBootstrap bootstrap) {
        return ResourceStorageFilter.all().excluding(bootstrap.ruleset().residentLife().foods().foods().keySet());
    }
    public static SubjectId initialFoodStorage(FrontierBootstrap bootstrap, SubjectId home) {
        return compile(bootstrap).stream().filter(p -> p.container().ownerId().equals(home)).map(p -> p.container().id())
                .findFirst().orElseThrow(() -> new IllegalArgumentException("settlement lacks its declared initial pantry"));
    }
}
