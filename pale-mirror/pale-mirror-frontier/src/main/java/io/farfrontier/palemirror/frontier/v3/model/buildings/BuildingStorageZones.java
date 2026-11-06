package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.*;

/**
 * Admission-time storage policy projection only. Does not own containers, resources or
 * transactions.
 */
public final class BuildingStorageZones {
    public record Selection(ResourceStorageFilter filter, int priority) {}

    public static Optional<Selection> at(Settlement settlement, BlockPosition container) {
        var matches = new ArrayList<BuildingDefinition.Zone>();
        for (var structure : settlement.structures()) {
            var local = structure.placement().local(container);
            for (var zone : structure.definition().zones().values())
                if (zone.use() == BuildingDefinition.ZoneUse.STORAGE && zone.volume().contains(local)) matches.add(zone);
        }
        return select(matches);
    }
    public static Optional<Selection> at(BuildingDefinition definition, BlockPosition local) {
        return select(definition.zones().values().stream().filter(zone -> zone.use() == BuildingDefinition.ZoneUse.STORAGE
                && zone.volume().contains(local)).toList());
    }
    private static Optional<Selection> select(Collection<BuildingDefinition.Zone> candidates) {
        var matches = new ArrayList<>(candidates);
        if (matches.isEmpty()) return Optional.empty();
        matches.sort(Comparator.comparingInt(BuildingDefinition.Zone::storagePriority).reversed());
        var selected = matches.getFirst();
        if (matches.size() > 1 && matches.get(1).storagePriority() == selected.storagePriority())
            throw new IllegalArgumentException("ambiguous overlapping building storage zones");
        var categories = new TreeMap<String, Set<String>>();
        for (var id : selected.resourceCategories())
            categories.put(
                    id,
                    Objects.requireNonNull(
                            BuildingCatalog.standard().categories().get(id),
                            "unknown building storage category"));
        return Optional.of(
                new Selection(
                        categories.isEmpty()
                                ? ResourceStorageFilter.all()
                                : new ResourceStorageFilter(false, Set.of(), categories),
                        selected.storagePriority()));
    }

    private BuildingStorageZones() {}
}
