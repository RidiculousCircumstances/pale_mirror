package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A prepared resource effect, not a job, route, block writer or resource ledger. */
public record BlockExtraction(String operationId,
                              io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution,
                              BlockPosition target, Definition definition,
                              List<Output> output) {
    public static final int MAX_OUTPUTS = 32;

    /** Complete nominal block state; support/feet coordinates never select a block type. */
    public record Block(String kind, Map<String, String> properties) {
        public Block {
            requireKey(kind);
            properties = Map.copyOf(properties);
            if (properties.size() > 32 || properties.entrySet().stream().anyMatch(entry ->
                    !entry.getKey().matches("[a-z0-9_]+") || !entry.getValue().matches("[a-z0-9_-]+")))
                throw new IllegalArgumentException("invalid extraction block properties");
        }
    }

    public record Output(String itemKind, int quantity) {
        public Output {
            requireKey(itemKind);
            if (quantity < 1 || quantity > 64) throw new IllegalArgumentException("unbounded extraction output");
        }
    }

    /** Family-issued policy. Loot owns output; this declaration bounds its COLD equivalent. */
    public record Definition(String id, Block before, Block after, String toolKind,
                             String lootTable, List<Output> coldOutput) {
        public Definition {
            requireKey(id); requireKey(toolKind); requireKey(lootTable);
            Objects.requireNonNull(before); Objects.requireNonNull(after);
            coldOutput = outputs(coldOutput);
            if (before.equals(after)) throw new IllegalArgumentException("extraction cannot leave its source unchanged");
        }
    }

    public BlockExtraction {
        if (operationId == null || operationId.isBlank() || operationId.length() > 512)
            throw new IllegalArgumentException("extraction needs an exact retained operation identity");
        Objects.requireNonNull(execution); Objects.requireNonNull(target); Objects.requireNonNull(definition);
        output = outputs(output);
        if (!output.equals(definition.coldOutput()))
            throw new IllegalArgumentException("native extraction output differs from the declared COLD rule: " + definition.id());
    }

    /** COLD uses known authoritative source state, never queries or loads Minecraft terrain. */
    public static BlockExtraction prepareKnown(String operationId,
                                                io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution,
                                                BlockPosition target,
                                                Definition definition, Block knownSource) {
        if (!definition.before().equals(knownSource))
            throw new IllegalArgumentException("extraction source changed before preparation");
        return new BlockExtraction(operationId, execution, target, definition, definition.coldOutput());
    }

    public int quantity(String itemKind) {
        return output.stream().filter(value -> value.itemKind().equals(itemKind)).mapToInt(Output::quantity).sum();
    }

    private static List<Output> outputs(List<Output> values) {
        values = List.copyOf(values);
        if (values.isEmpty() || values.size() > MAX_OUTPUTS
                || values.stream().map(Output::itemKind).distinct().count() != values.size())
            throw new IllegalArgumentException("extraction needs bounded, uniquely declared output kinds");
        return values.stream().sorted(java.util.Comparator.comparing(Output::itemKind)).toList();
    }

    private static void requireKey(String key) {
        if (key == null || !key.matches("[a-z0-9_]+:[a-z0-9_./-]+") || key.length() > 256)
            throw new IllegalArgumentException("invalid extraction registry key");
    }
}
