package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable, fresh-world-only v3 bootstrap manifest. */
public record FrontierBootstrap(WorldId worldId, long seed, WorldBounds bounds, List<Settlement> settlements, Hive hive) {
    public FrontierBootstrap {
        Objects.requireNonNull(worldId, "world id");
        Objects.requireNonNull(bounds, "bounds");
        settlements = List.copyOf(settlements);
        Objects.requireNonNull(hive, "hive");
        if (bounds.width() != 1024 || bounds.depth() != 1024) throw new IllegalArgumentException("Frontier v3 bootstrap is exactly 1024 by 1024 blocks");
        if (settlements.size() != 12) throw new IllegalArgumentException("Frontier v3 bootstrap requires exactly 12 settlements");
        Set<SubjectId> ids = new HashSet<>();
        for (Settlement settlement : settlements) {
            if (!bounds.contains(settlement.anchor())) throw new IllegalArgumentException("settlement anchor is outside frontier bounds");
            add(ids, settlement.id());
            settlement.residents().forEach(resident -> { add(ids, resident.id()); require(bounds, resident.home()); });
            settlement.structures().forEach(structure -> { add(ids, structure.id()); require(bounds, structure.anchor()); });
        }
        add(ids, hive.id());
        hive.seedNests().forEach(nest -> { add(ids, nest.id()); require(bounds, nest.anchor()); });
        hive.bioforms().forEach(bioform -> { add(ids, bioform.id()); require(bounds, bioform.position()); });
    }

    public int residentCount() { return settlements.stream().mapToInt(settlement -> settlement.residents().size()).sum(); }
    public int bioformCount() { return hive.bioforms().size(); }

    /** Stable digest for golden tests and bootstrap diagnostics, independent of collection iteration. */
    public String canonicalSha256() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalText().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private String canonicalText() {
        StringBuilder text = new StringBuilder(worldId.value()).append('|').append(seed).append('|')
                .append(bounds.minX()).append(',').append(bounds.minZ()).append(',').append(bounds.width()).append(',').append(bounds.depth());
        for (Settlement settlement : settlements) {
            append(text, settlement.id(), settlement.anchor());
            text.append('|').append(settlement.displayName());
            settlement.residents().forEach(resident -> { append(text, resident.id(), resident.home()); text.append(':').append(resident.role()); });
            settlement.structures().forEach(structure -> { append(text, structure.id(), structure.anchor()); text.append(':').append(structure.kind()); });
        }
        text.append('|').append(hive.id().value());
        hive.seedNests().forEach(nest -> append(text, nest.id(), nest.anchor()));
        hive.bioforms().forEach(bioform -> { append(text, bioform.id(), bioform.position()); text.append(':').append(bioform.nestId().value()).append(':').append(bioform.role()); });
        return text.toString();
    }

    private static void append(StringBuilder text, SubjectId id, BlockPosition position) {
        text.append('|').append(id.value()).append('@').append(position.x()).append(',').append(position.y()).append(',').append(position.z());
    }
    private static void add(Set<SubjectId> ids, SubjectId id) { if (!ids.add(id)) throw new IllegalArgumentException("duplicate bootstrap subject id: " + id.value()); }
    private static void require(WorldBounds bounds, BlockPosition position) { if (!bounds.contains(position)) throw new IllegalArgumentException("bootstrap position is outside frontier bounds"); }
}
