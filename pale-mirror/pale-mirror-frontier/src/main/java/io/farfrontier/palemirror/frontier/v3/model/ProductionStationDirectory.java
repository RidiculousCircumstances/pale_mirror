package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Read-only installation lookup over exact container declarations. It grants
 * no operability, service access, materials or runtime machine authority. */
public final class ProductionStationDirectory {
    private record Index(Map<SubjectId, ContainerRecord> stations, Map<SubjectId, List<ContainerRecord>> facilities) { }
    private static final ImmutableInputView<Index> VIEW = new ImmutableInputView<>();
    private static Index read(ExactInventory inventory) {
        return VIEW.get(List.of(inventory.containers()), () -> {
            var stations = new HashMap<SubjectId, ContainerRecord>(); var facilities = new HashMap<SubjectId, List<ContainerRecord>>();
            inventory.containers().values().forEach(record -> record.productionStation().ifPresent(station -> {
                if (stations.putIfAbsent(station.id(), record) != null)
                    throw new IllegalArgumentException("duplicate installed station identity");
                facilities.computeIfAbsent(station.facilityId(), ignored -> new ArrayList<>()).add(record);
            }));
            facilities.replaceAll((facility, records) -> List.copyOf(records));
            return new Index(Map.copyOf(stations), Map.copyOf(facilities));
        });
    }
    public static ContainerRecord require(ExactInventory inventory, SubjectId station) {
        var record = read(inventory).stations().get(station);
        if (record == null) throw new IllegalArgumentException("missing installed station identity");
        return record;
    }
    public static List<ContainerRecord> inFacility(ExactInventory inventory, SubjectId facility) {
        return read(inventory).facilities().getOrDefault(facility, List.of());
    }
    private ProductionStationDirectory() { }
}
