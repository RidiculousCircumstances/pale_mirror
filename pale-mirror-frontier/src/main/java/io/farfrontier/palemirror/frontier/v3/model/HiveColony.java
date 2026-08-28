package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded mutable expansion beyond the immutable two-nest bootstrap hive. */
public record HiveColony(Map<SubjectId, HiveOrgan> addedOrgans, Map<SubjectId, Bioform> spawnedBioforms) {
    public static final int MAX_ADDED_ORGANS = 128;
    public static final int MAX_SPAWNED_BIOFORMS = 2_048;

    public HiveColony {
        addedOrgans = immutable(addedOrgans, "added hive organs");
        spawnedBioforms = immutable(spawnedBioforms, "spawned bioforms");
        if (addedOrgans.size() > MAX_ADDED_ORGANS) throw new IllegalArgumentException("hive organ retention limit exceeded");
        if (spawnedBioforms.size() > MAX_SPAWNED_BIOFORMS) throw new IllegalArgumentException("hive bioform retention limit exceeded");
        addedOrgans.forEach((id, organ) -> { if (!id.equals(organ.id())) throw new IllegalArgumentException("added organ key differs from identity"); });
        spawnedBioforms.forEach((id, bioform) -> { if (!id.equals(bioform.id())) throw new IllegalArgumentException("spawned bioform key differs from identity"); });
    }

    public static HiveColony empty() { return new HiveColony(Map.of(), Map.of()); }

    public HiveColony addOrgan(HiveOrgan organ) {
        Objects.requireNonNull(organ, "hive organ");
        if (addedOrgans.containsKey(organ.id())) throw new IllegalArgumentException("added organ identity already exists: " + organ.id().value());
        Map<SubjectId, HiveOrgan> next = new LinkedHashMap<>(addedOrgans); next.put(organ.id(), organ);
        return new HiveColony(next, spawnedBioforms);
    }

    public HiveColony spawn(Bioform bioform) {
        Objects.requireNonNull(bioform, "bioform");
        if (spawnedBioforms.containsKey(bioform.id())) throw new IllegalArgumentException("spawned bioform identity already exists: " + bioform.id().value());
        Map<SubjectId, Bioform> next = new LinkedHashMap<>(spawnedBioforms); next.put(bioform.id(), bioform);
        return new HiveColony(addedOrgans, next);
    }

    void validateAgainst(FrontierBootstrap bootstrap) {
        var hive = bootstrap.hive();
        var organIds = hive.organs().stream().map(HiveOrgan::id).collect(java.util.stream.Collectors.toSet());
        var bioformIds = hive.bioforms().stream().map(Bioform::id).collect(java.util.stream.Collectors.toSet());
        var nestIds = hive.seedNests().stream().map(HiveNest::id).collect(java.util.stream.Collectors.toSet());
        for (HiveOrgan organ : addedOrgans.values()) {
            if (organIds.contains(organ.id()) || !hive.id().equals(organ.hiveId()) || !nestIds.contains(organ.nestId()) || !bootstrap.bounds().contains(organ.anchor())) {
                throw new IllegalArgumentException("added hive organ does not belong to this colony");
            }
        }
        for (Bioform bioform : spawnedBioforms.values()) {
            if (bioformIds.contains(bioform.id()) || !hive.id().equals(bioform.hiveId()) || !nestIds.contains(bioform.nestId()) || !bootstrap.bounds().contains(bioform.position())) {
                throw new IllegalArgumentException("spawned bioform does not belong to this colony");
            }
        }
    }

    private static <T> Map<SubjectId, T> immutable(Map<SubjectId, T> source, String label) {
        Objects.requireNonNull(source, label);
        LinkedHashMap<SubjectId, T> values = new LinkedHashMap<>();
        source.forEach((id, value) -> values.put(Objects.requireNonNull(id, label + " id"), Objects.requireNonNull(value, label + " value")));
        return Map.copyOf(values);
    }
}
