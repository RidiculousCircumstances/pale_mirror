package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;

/** Ruleset-owned immutable operation definitions. It owns no jobs, items or actors. */
public record WorkCatalog(List<Definition> definitions, int baseSpeedPermille,
                          int skillGainPermille, int minSpeedPermille, int maxSpeedPermille) {
    public record Definition(String resourceKind, WorkOperation operation, HumanCapability capability, long workUnits) {
        public Definition {
            Objects.requireNonNull(operation); Objects.requireNonNull(capability);
            if (resourceKind == null || !resourceKind.matches("[a-z0-9_]+:[a-z0-9_./-]+")
                    || workUnits < 1 || workUnits > 1_000_000)
                throw new IllegalArgumentException("invalid work definition");
        }
        String key() { return resourceKind + "/" + operation.wireTag(); }
    }
    public WorkCatalog {
        definitions = List.copyOf(definitions);
        if (definitions.isEmpty() || definitions.size() > 256
                || definitions.stream().map(Definition::key).distinct().count() != definitions.size()
                || minSpeedPermille < 1 || baseSpeedPermille < minSpeedPermille
                || maxSpeedPermille < baseSpeedPermille || maxSpeedPermille > 100_000
                || skillGainPermille < 0 || skillGainPermille > 1_000)
            throw new IllegalArgumentException("invalid closed work catalog");
    }
    public Definition require(String resourceKind, WorkOperation operation) {
        return definitions.stream().filter(value -> value.resourceKind().equals(resourceKind) && value.operation() == operation)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("unregistered resource operation"));
    }
    public static WorkCatalog initial() {
        return new WorkCatalog(List.of(
                new Definition("minecraft:wheat", WorkOperation.HARVEST, HumanCapability.AGRICULTURE, 200),
                new Definition("minecraft:wheat", WorkOperation.SOW, HumanCapability.AGRICULTURE, 100),
                new Definition("minecraft:wheat", WorkOperation.TILL_AND_SOW, HumanCapability.AGRICULTURE, 300)),
                1_000, 5, 100, 4_000);
    }
    public String canonicalText() {
        return baseSpeedPermille + "," + skillGainPermille + "," + minSpeedPermille + "," + maxSpeedPermille + "|"
                + definitions.stream().sorted(java.util.Comparator.comparing(Definition::key))
                .map(value -> value.key() + ":" + value.capability().wireTag() + ":" + value.workUnits())
                .collect(java.util.stream.Collectors.joining(";"));
    }
    public WorkCatalog withDefinition(Definition definition) {
        var next = new java.util.ArrayList<>(definitions); next.add(Objects.requireNonNull(definition));
        return new WorkCatalog(next, baseSpeedPermille, skillGainPermille, minSpeedPermille, maxSpeedPermille);
    }
}
