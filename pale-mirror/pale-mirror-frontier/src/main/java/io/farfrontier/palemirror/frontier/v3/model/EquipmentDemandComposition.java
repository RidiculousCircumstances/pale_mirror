package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.EquipmentWorkDemand;
import java.util.*;

/** One declared adapter per enabled equipment consumer. No producer policy or mutation here. */
public final class EquipmentDemandComposition {
    private static final ImmutableKeyedInputView<SubjectId, List<EquipmentWorkDemand>> VIEWS = new ImmutableKeyedInputView<>(256);
    private static final Map<EquipmentDemandPort.Key, EquipmentDemandPort> PORTS = register(List.of(
            new FieldEquipmentDemand(), new ExtractionEquipmentDemand(), new ForestryEquipmentDemand()));
    private static Map<EquipmentDemandPort.Key, EquipmentDemandPort> register(List<EquipmentDemandPort> values) {
        var result = new EnumMap<EquipmentDemandPort.Key, EquipmentDemandPort>(EquipmentDemandPort.Key.class);
        for (var value : values) if (result.put(value.key(), value) != null) throw new IllegalArgumentException("duplicate equipment demand owner");
        if (result.size() != EquipmentDemandPort.Key.values().length) throw new IllegalArgumentException("missing equipment demand owner");
        return Map.copyOf(result);
    }
    public static List<EquipmentWorkDemand> read(FrontierWorldState state, SubjectId home) {
        var parts = Arrays.stream(EquipmentDemandPort.Key.values()).map(key -> PORTS.get(key).read(state, home)).toList();
        return VIEWS.get(state.bootstrap(), home, new ArrayList<Object>(parts),
                () -> parts.stream().flatMap(List::stream).toList());
    }
    private EquipmentDemandComposition() { }
}
