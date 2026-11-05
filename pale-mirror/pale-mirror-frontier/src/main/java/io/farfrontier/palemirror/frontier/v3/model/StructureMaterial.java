package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState;

import java.util.*;

/**
 * Exact structural appearance and repair input. Encoding carries its own explicit stable type tag.
 */
public sealed interface StructureMaterial permits GrayboxMaterial, StructureMaterial.Authored {
    String repairItemKind();

    String wireIdentity();

    record Authored(NavigationBlockState block, String repairItemKind)
            implements StructureMaterial {
        public Authored {
            Objects.requireNonNull(block);
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

        @Override
        public String wireIdentity() {
            return "2|"
                    + block.kind()
                    + '|'
                    + repairItemKind
                    + '|'
                    + new TreeMap<>(block.properties())
                            .entrySet().stream()
                                    .map(e -> e.getKey() + '=' + e.getValue())
                                    .collect(java.util.stream.Collectors.joining(","));
        }
    }

    static StructureMaterial require(String identity) {
        if (identity == null || identity.length() > 8192)
            throw new IllegalArgumentException("unbounded structural material");
        var fields = identity.split("\\|", -1);
        if (fields.length == 2 && fields[0].equals("1")) return GrayboxMaterial.valueOf(fields[1]);
        if (fields.length != 4 || !fields[0].equals("2"))
            throw new IllegalArgumentException("unknown structural material wire declaration");
        var properties = new TreeMap<String, String>();
        if (!fields[3].isEmpty())
            for (var property : fields[3].split(",", -1)) {
                var pair = property.split("=", -1);
                if (pair.length != 2 || properties.putIfAbsent(pair[0], pair[1]) != null)
                    throw new IllegalArgumentException("duplicate or incomplete block property");
            }
        var result = new Authored(new NavigationBlockState(fields[1], properties), fields[2]);
        if (!result.wireIdentity().equals(identity))
            throw new IllegalArgumentException("noncanonical structural material declaration");
        return result;
    }
}
