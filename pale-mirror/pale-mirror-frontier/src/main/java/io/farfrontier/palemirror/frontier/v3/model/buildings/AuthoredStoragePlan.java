package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;

/** Content-time inventory admission. Existing fabric supplies blocks; ordinary storage owns goods. */
public final class AuthoredStoragePlan {
    public record Entry(ContainerRecord container, ContainerSurface surface) { }
    private record Provider(StorageBlockKind kind, int slots) { }
    public record InventoryPort(BlockPosition block, SurfaceAnchor station, StorageBlockKind kind, int slots) { }
    private static final Map<String, Provider> PROVIDERS = Map.of(
            "minecraft:chest", new Provider(StorageBlockKind.CHEST, 27),
            "minecraft:barrel", new Provider(StorageBlockKind.BARREL, 27),
            "minecolonies:blockminecoloniesrack", new Provider(StorageBlockKind.RACK, 27));
    // Keys can only be references admitted by the finite, immutable installed building catalogue.
    private static final Map<BuildingPlanRef, List<InventoryPort>> LOCAL = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean authored(SettlementStructure building) {
        return building.definition().representation() == BuildingDefinition.Representation.AUTHORED;
    }
    private static final ImmutableInputView<List<Entry>> CATALOGUE = new ImmutableInputView<>();
    public static List<Entry> compile(FrontierBootstrap bootstrap) {
        return CATALOGUE.get(List.of(bootstrap), () -> compileFresh(bootstrap));
    }
    private static List<Entry> compileFresh(FrontierBootstrap bootstrap) {
        var entries = new ArrayList<Entry>();
        for (var home : bootstrap.settlements()) for (var building : home.structures()) {
            if (!authored(building)) continue;
            for (var local : LOCAL.computeIfAbsent(building.building(), ignored -> connectedInventoryPorts(building.definition()))) {
                var position = building.placement().world(local.block());
                var id = id(building, local);
                var exterior = building.placement().support(building.definition().primaryEntranceDefinition().supports().getFirst());
                var endpoint = new ShipmentEndpoint.Storage(home.id(), id, building.placement().support(local.station().support()),
                        Optional.of(building.id()), exterior);
                var zone = BuildingStorageZones.at(building.definition(), local.block());
                var filter = building.kind() == StructureKind.KITCHEN
                        ? new ResourceStorageFilter(false, bootstrap.ruleset().residentLife().foods().foods().keySet(), Map.of())
                        : zone.map(BuildingStorageZones.Selection::filter).orElseGet(ResourceStorageFilter::all)
                                .excluding(bootstrap.ruleset().residentLife().foods().foods().keySet());
                var policy = new ResourceStoragePolicy(endpoint, filter,
                        building.kind() == StructureKind.KITCHEN ? 100 : zone.map(BuildingStorageZones.Selection::priority).orElse(0),
                        ResourceStorageRole.WAREHOUSE);
                entries.add(new Entry(ContainerRecord.storage(id, home.id(), local.slots(), local.kind(), policy),
                        new ContainerSurface(id, position, ContainerSurfaceStatus.UNMATERIALIZED)));
            }
        }
        return List.copyOf(entries);
    }
    private static SubjectId id(SettlementStructure building, InventoryPort local) {
        var first = LOCAL.get(building.building()).getFirst();
        if (local.equals(first) && building.kind() == StructureKind.DEPOT) return FrontierWorldState.depotId(building.settlementId());
        return new SubjectId("container:authored-" + UUID.nameUUIDFromBytes(
                (building.id().value() + "|" + local.block()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    public static List<InventoryPort> connectedInventoryPorts(BuildingDefinition definition) {
        var candidates = definition.blocks().entrySet().stream()
                .filter(e -> PROVIDERS.containsKey(e.getValue().state().kind()) && BuildingStorageZones.at(definition, e.getKey()).isPresent())
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(BlockPosition::y)
                        .thenComparingInt(BlockPosition::x).thenComparingInt(BlockPosition::z))).toList();
        if (candidates.isEmpty()) return List.of();
        var geometry = BuildingGeometryValidation.geometry(definition,
                state -> BuildingCollisionCatalog.standard(state).flatMap(NavigationObservation::cell),
                workplace -> NavigationCell.SOLID).walking();
        var start = new SurfaceAnchor(definition.primaryEntranceDefinition().supports().getFirst());
        var area = ReachableSurfaceArea.select(geometry, List.of(start), start, 128, 65536, 65536, geometry::clearStation);
        if (area.exhausted()) throw new IllegalArgumentException("authored storage connectivity exceeds content bound: " + definition.id());
        var result = new ArrayList<InventoryPort>();
        for (var entry : candidates) area.selected().stream()
                .filter(s -> BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN.contains(s, entry.getKey()))
                .min(Comparator.comparingLong((SurfaceAnchor s) -> distance(s, entry.getKey()))
                        .thenComparingInt(SurfaceAnchor::y).thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z))
                .ifPresent(station -> result.add(new InventoryPort(entry.getKey(), station, PROVIDERS.get(entry.getValue().state().kind()).kind(), PROVIDERS.get(entry.getValue().state().kind()).slots())));
        if (definition.functions().contains(StructureKind.DEPOT) && result.isEmpty())
            throw new IllegalArgumentException("authored warehouse has no connected inventory: " + definition.id());
        return List.copyOf(result);
    }
    private static long distance(SurfaceAnchor station, BlockPosition block) {
        long x = station.x()-block.x(), y = station.y()+1-block.y(), z = station.z()-block.z();
        return x*x+y*y+z*z;
    }
    private AuthoredStoragePlan() { }
}
