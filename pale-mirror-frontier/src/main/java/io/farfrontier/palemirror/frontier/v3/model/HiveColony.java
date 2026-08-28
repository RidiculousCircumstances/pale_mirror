package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded mutable expansion beyond the immutable two-nest bootstrap hive. */
public record HiveColony(Map<SubjectId, HiveOrgan> addedOrgans, Map<SubjectId, Bioform> spawnedBioforms,
                         Map<SubjectId, HiveGrowthJob> growthJobs) {
    public static final int MAX_ADDED_ORGANS = 128;
    public static final int MAX_SPAWNED_BIOFORMS = 2_048;
    public static final int MAX_GROWTH_JOBS = 128;

    public HiveColony {
        addedOrgans = immutable(addedOrgans, "added hive organs");
        spawnedBioforms = immutable(spawnedBioforms, "spawned bioforms");
        growthJobs = immutable(growthJobs, "hive growth jobs");
        if (addedOrgans.size() > MAX_ADDED_ORGANS) throw new IllegalArgumentException("hive organ retention limit exceeded");
        if (spawnedBioforms.size() > MAX_SPAWNED_BIOFORMS) throw new IllegalArgumentException("hive bioform retention limit exceeded");
        if (growthJobs.size() > MAX_GROWTH_JOBS) throw new IllegalArgumentException("hive growth job retention limit exceeded");
        addedOrgans.forEach((id, organ) -> { if (!id.equals(organ.id())) throw new IllegalArgumentException("added organ key differs from identity"); });
        spawnedBioforms.forEach((id, bioform) -> { if (!id.equals(bioform.id())) throw new IllegalArgumentException("spawned bioform key differs from identity"); });
        growthJobs.forEach((id, job) -> { if (!id.equals(job.id())) throw new IllegalArgumentException("hive growth job key differs from identity"); });
    }

    public static HiveColony empty() { return new HiveColony(Map.of(), Map.of(), Map.of()); }

    public HiveColony addOrgan(HiveOrgan organ) {
        Objects.requireNonNull(organ, "hive organ");
        if (addedOrgans.containsKey(organ.id())) throw new IllegalArgumentException("added organ identity already exists: " + organ.id().value());
        Map<SubjectId, HiveOrgan> next = new LinkedHashMap<>(addedOrgans); next.put(organ.id(), organ);
        return new HiveColony(next, spawnedBioforms, growthJobs);
    }

    public HiveColony spawn(Bioform bioform) {
        Objects.requireNonNull(bioform, "bioform");
        if (spawnedBioforms.containsKey(bioform.id())) throw new IllegalArgumentException("spawned bioform identity already exists: " + bioform.id().value());
        Map<SubjectId, Bioform> next = new LinkedHashMap<>(spawnedBioforms); next.put(bioform.id(), bioform);
        return new HiveColony(addedOrgans, next, growthJobs);
    }

    public HiveColony startGrowth(HiveGrowthJob job) {
        Objects.requireNonNull(job, "hive growth job");
        if (growthJobs.containsKey(job.id())) throw new IllegalArgumentException("hive growth job identity already exists: " + job.id().value());
        Map<SubjectId, HiveGrowthJob> next = new LinkedHashMap<>(growthJobs); next.put(job.id(), job);
        return new HiveColony(addedOrgans, spawnedBioforms, next);
    }

    public HiveColony completeGrowth(SubjectId jobId) {
        HiveGrowthJob job = growthJobs.get(Objects.requireNonNull(jobId, "hive growth job id"));
        if (job == null) throw new IllegalArgumentException("unknown hive growth job: " + jobId.value());
        if (addedOrgans.containsKey(job.organ().id()) || spawnedBioforms.containsKey(job.bioform().id())) {
            throw new IllegalArgumentException("hive growth output identity already exists");
        }
        Map<SubjectId, HiveGrowthJob> next = new LinkedHashMap<>(growthJobs); next.remove(jobId);
        Map<SubjectId, HiveOrgan> organs = new LinkedHashMap<>(addedOrgans); organs.put(job.organ().id(), job.organ());
        Map<SubjectId, Bioform> bioforms = new LinkedHashMap<>(spawnedBioforms); bioforms.put(job.bioform().id(), job.bioform());
        return new HiveColony(organs, bioforms, next);
    }

    public HiveColony cancelGrowth(SubjectId jobId) {
        if (!growthJobs.containsKey(Objects.requireNonNull(jobId, "hive growth job id"))) throw new IllegalArgumentException("unknown hive growth job: " + jobId.value());
        Map<SubjectId, HiveGrowthJob> next = new LinkedHashMap<>(growthJobs); next.remove(jobId);
        return new HiveColony(addedOrgans, spawnedBioforms, next);
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
            organIds.add(organ.id());
        }
        for (Bioform bioform : spawnedBioforms.values()) {
            if (bioformIds.contains(bioform.id()) || !hive.id().equals(bioform.hiveId()) || !nestIds.contains(bioform.nestId()) || !bootstrap.bounds().contains(bioform.position())) {
                throw new IllegalArgumentException("spawned bioform does not belong to this colony");
            }
            bioformIds.add(bioform.id());
        }
        java.util.Set<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId> consumptionIntents = new java.util.HashSet<>();
        for (HiveGrowthJob job : growthJobs.values()) {
            HiveOrgan organ = job.organ(); Bioform bioform = job.bioform();
            if (!hive.id().equals(job.hiveId()) || !nestIds.contains(job.nestId()) || organIds.contains(organ.id()) || bioformIds.contains(bioform.id())
                    || !bootstrap.bounds().contains(organ.anchor()) || !bootstrap.bounds().contains(bioform.position()) || !consumptionIntents.add(job.consumptionIntentId())) {
                throw new IllegalArgumentException("hive growth job does not belong to this colony");
            }
            organIds.add(organ.id()); bioformIds.add(bioform.id());
        }
    }

    private static <T> Map<SubjectId, T> immutable(Map<SubjectId, T> source, String label) {
        Objects.requireNonNull(source, label);
        LinkedHashMap<SubjectId, T> values = new LinkedHashMap<>();
        source.forEach((id, value) -> values.put(Objects.requireNonNull(id, label + " id"), Objects.requireNonNull(value, label + " value")));
        return Map.copyOf(values);
    }
}
