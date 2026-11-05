package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState;

import java.util.*;

/**
 * Exact structural appearance and repair input. Encoding carries its own explicit stable type tag.
 */
public sealed interface StructureMaterial permits GrayboxMaterial, StructureMaterial.Authored {
    String repairItemKind();

    String wireIdentity();

    /** Explicit authored excavation may receive separately declared public paving. */
    default boolean emptySpace() { return false; }

    record Authored(NavigationBlockState block, String repairItemKind, Optional<AuthoredBlockData> data)
            implements StructureMaterial {
        public Authored {
            Objects.requireNonNull(block);
            data = Objects.requireNonNull(data);
            if (!block.kind().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || repairItemKind == null
                    || !repairItemKind.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || block.properties().entrySet().stream()
                            .anyMatch(
                                    e ->
                                            !e.getKey().matches("[a-z0-9_]+")
                                                    || !e.getValue().matches("[a-z0-9_-]+")))
                throw new IllegalArgumentException("invalid authored structural material");
        }

        public Authored(NavigationBlockState block, String repairItemKind) {
            this(block, repairItemKind, Optional.empty());
        }
        @Override public boolean emptySpace() { return block.kind().equals("minecraft:air") && block.properties().isEmpty() && data.isEmpty(); }
        @Override
        public String wireIdentity() {
            return (data.isPresent() ? "3|" : "2|")
                    + block.kind()
                    + '|'
                    + repairItemKind
                    + '|'
                    + new TreeMap<>(block.properties())
                            .entrySet().stream()
                                    .map(e -> e.getKey() + '=' + e.getValue())
                                    .collect(java.util.stream.Collectors.joining(","))
                    + data.map(value -> "|" + value.kind().wireTag() + "|" + value.dataVersion() + "|" + value.encoded()).orElse("");
        }
    }

    static StructureMaterial require(String identity) {
        if (identity == null || identity.length() > 32768)
            throw new IllegalArgumentException("unbounded structural material");
        var fields = identity.split("\\|", -1);
        if (fields.length == 2 && fields[0].equals("1")) return GrayboxMaterial.valueOf(fields[1]);
        if (!(fields.length == 4 && fields[0].equals("2") || fields.length == 7 && fields[0].equals("3")))
            throw new IllegalArgumentException("unknown structural material wire declaration");
        var properties = new TreeMap<String, String>();
        if (!fields[3].isEmpty())
            for (var property : fields[3].split(",", -1)) {
                var pair = property.split("=", -1);
                if (pair.length != 2 || properties.putIfAbsent(pair[0], pair[1]) != null)
                    throw new IllegalArgumentException("duplicate or incomplete block property");
            }
        var result = new Authored(new NavigationBlockState(fields[1], properties), fields[2],
                fields.length == 7 ? Optional.of(AuthoredBlockData.decode(Integer.parseInt(fields[4]), Integer.parseInt(fields[5]), fields[6])) : Optional.empty());
        if (!result.wireIdentity().equals(identity))
            throw new IllegalArgumentException("noncanonical structural material declaration");
        return result;
    }
}
