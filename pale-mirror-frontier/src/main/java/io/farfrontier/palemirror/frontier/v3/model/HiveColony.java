package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded mutable expansion beyond the immutable two-nest bootstrap hive. */
public record HiveColony(Map<SubjectId, HiveOrgan> addedOrgans, Map<SubjectId, Bioform> spawnedBioforms,
                         Map<SubjectId, HiveGrowthJob> growthJobs,
                         Map<SubjectId, HiveNutrientTransfer> nutrientTransfers,
                         Map<SubjectId, HiveNutrientReceipt> nutrientReceipts,
                         Map<SubjectId, BioformLifecycle> bioformLifecycles,
                         Map<SubjectId, HiveMobilization> mobilizations) {
    public static final int MAX_ADDED_ORGANS = 128;
    public static final int MAX_SPAWNED_BIOFORMS = 2_048;
    public static final int MAX_GROWTH_JOBS = 128;
    public static final int MAX_NUTRIENT_TRANSFERS = 128;
    public static final int MAX_NUTRIENT_RECEIPTS = 512;
    public static final int MAX_MOBILIZATIONS = 128;

    public HiveColony {
        addedOrgans = immutable(addedOrgans, "added hive organs");
        spawnedBioforms = immutable(spawnedBioforms, "spawned bioforms");
        growthJobs = immutable(growthJobs, "hive growth jobs");
        nutrientTransfers = immutable(nutrientTransfers, "hive nutrient transfers");
        nutrientReceipts = immutable(nutrientReceipts, "hive nutrient receipts");
        bioformLifecycles = immutable(bioformLifecycles, "bioform lifecycles");
        mobilizations = immutable(mobilizations, "hive mobilizations");
        if (addedOrgans.size() > MAX_ADDED_ORGANS) throw new IllegalArgumentException("hive organ retention limit exceeded");
        if (spawnedBioforms.size() > MAX_SPAWNED_BIOFORMS) throw new IllegalArgumentException("hive bioform retention limit exceeded");
        if (growthJobs.size() > MAX_GROWTH_JOBS) throw new IllegalArgumentException("hive growth job retention limit exceeded");
        if (nutrientTransfers.size() > MAX_NUTRIENT_TRANSFERS) throw new IllegalArgumentException("hive nutrient transfer retention limit exceeded");
        if (nutrientReceipts.size() > MAX_NUTRIENT_RECEIPTS) throw new IllegalArgumentException("hive nutrient receipt retention limit exceeded");
        if (mobilizations.size() > MAX_MOBILIZATIONS) throw new IllegalArgumentException("hive mobilization retention limit exceeded");
        addedOrgans.forEach((id, organ) -> { if (!id.equals(organ.id())) throw new IllegalArgumentException("added organ key differs from identity"); });
        spawnedBioforms.forEach((id, bioform) -> { if (!id.equals(bioform.id())) throw new IllegalArgumentException("spawned bioform key differs from identity"); });
        growthJobs.forEach((id, job) -> { if (!id.equals(job.id())) throw new IllegalArgumentException("hive growth job key differs from identity"); });
        nutrientTransfers.forEach((id, transfer) -> { if (!id.equals(transfer.id())) throw new IllegalArgumentException("hive nutrient transfer key differs from identity"); });
        nutrientReceipts.forEach((id, receipt) -> { if (!id.equals(receipt.transferId())) throw new IllegalArgumentException("hive nutrient receipt key differs from transfer identity"); });
        mobilizations.forEach((id, mobilization) -> { if (!id.equals(mobilization.id())) throw new IllegalArgumentException("hive mobilization key differs from identity"); });
    }

    public HiveColony(Map<SubjectId, HiveOrgan> addedOrgans, Map<SubjectId, Bioform> spawnedBioforms,
                      Map<SubjectId, HiveGrowthJob> growthJobs) {
        this(addedOrgans, spawnedBioforms, growthJobs, Map.of(), Map.of(), Map.of(), Map.of());
    }

    /** Source convenience for pure fixtures; persisted worlds always use the full current form. */
    public HiveColony(Map<SubjectId, HiveOrgan> addedOrgans, Map<SubjectId, Bioform> spawnedBioforms,
                      Map<SubjectId, HiveGrowthJob> growthJobs, Map<SubjectId, HiveNutrientTransfer> nutrientTransfers,
                      Map<SubjectId, HiveNutrientReceipt> nutrientReceipts, Map<SubjectId, BioformLifecycle> bioformLifecycles) {
        this(addedOrgans, spawnedBioforms, growthJobs, nutrientTransfers, nutrientReceipts, bioformLifecycles, Map.of());
    }

    public static HiveColony empty() { return new HiveColony(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()); }

    public HiveColony withBioformLifecycles(Map<SubjectId, BioformLifecycle> lifecycles) {
        return new HiveColony(addedOrgans, spawnedBioforms, growthJobs, nutrientTransfers, nutrientReceipts, lifecycles, mobilizations);
    }

    public HiveColony addOrgan(HiveOrgan organ) {
        Objects.requireNonNull(organ, "hive organ");
        if (addedOrgans.containsKey(organ.id())) throw new IllegalArgumentException("added organ identity already exists: " + organ.id().value());
        Map<SubjectId, HiveOrgan> next = new LinkedHashMap<>(addedOrgans); next.put(organ.id(), organ);
        return new HiveColony(next, spawnedBioforms, growthJobs, nutrientTransfers, nutrientReceipts, bioformLifecycles, mobilizations);
    }

    public HiveColony spawn(Bioform bioform) {
        Objects.requireNonNull(bioform, "bioform");
        if (spawnedBioforms.containsKey(bioform.id())) throw new IllegalArgumentException("spawned bioform identity already exists: " + bioform.id().value());
        Map<SubjectId, Bioform> next = new LinkedHashMap<>(spawnedBioforms); next.put(bioform.id(), bioform);
        Map<SubjectId, BioformLifecycle> lifecycles = new LinkedHashMap<>(bioformLifecycles);
        lifecycles.put(bioform.id(), BioformLifecycle.activeWithoutHome());
        return new HiveColony(addedOrgans, next, growthJobs, nutrientTransfers, nutrientReceipts, lifecycles, mobilizations);
    }

    public HiveColony startGrowth(HiveGrowthJob job) {
        Objects.requireNonNull(job, "hive growth job");
        if (growthJobs.containsKey(job.id())) throw new IllegalArgumentException("hive growth job identity already exists: " + job.id().value());
        Map<SubjectId, HiveGrowthJob> next = new LinkedHashMap<>(growthJobs); next.put(job.id(), job);
        return new HiveColony(addedOrgans, spawnedBioforms, next, nutrientTransfers, nutrientReceipts, bioformLifecycles, mobilizations);
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
        Map<SubjectId, BioformLifecycle> lifecycles = new LinkedHashMap<>(bioformLifecycles);
        lifecycles.put(job.bioform().id(), BioformLifecycle.activeWithoutHome());
        return new HiveColony(organs, bioforms, next, nutrientTransfers, nutrientReceipts, lifecycles, mobilizations);
    }

    public HiveColony cancelGrowth(SubjectId jobId) {
        if (!growthJobs.containsKey(Objects.requireNonNull(jobId, "hive growth job id"))) throw new IllegalArgumentException("unknown hive growth job: " + jobId.value());
        Map<SubjectId, HiveGrowthJob> next = new LinkedHashMap<>(growthJobs); next.remove(jobId);
        return new HiveColony(addedOrgans, spawnedBioforms, next, nutrientTransfers, nutrientReceipts, bioformLifecycles, mobilizations);
    }

    public HiveColony startNutrientTransfer(HiveNutrientTransfer transfer) {
        Objects.requireNonNull(transfer, "hive nutrient transfer");
        if (nutrientTransfers.containsKey(transfer.id()) || nutrientReceipts.containsKey(transfer.id())) {
            throw new IllegalArgumentException("hive nutrient transfer identity already exists: " + transfer.id().value());
        }
        if (nutrientTransfers.values().stream().anyMatch(current -> current.requesterTaskId().equals(transfer.requesterTaskId()))) {
            throw new IllegalArgumentException("strategic task already owns a hive nutrient transfer");
        }
        Map<SubjectId, HiveNutrientTransfer> next = new LinkedHashMap<>(nutrientTransfers); next.put(transfer.id(), transfer);
        return new HiveColony(addedOrgans, spawnedBioforms, growthJobs, next, nutrientReceipts, bioformLifecycles, mobilizations);
    }

    public HiveColony advanceNutrientTransfer(SubjectId transferId, int cursor) {
        HiveNutrientTransfer current = nutrientTransfers.get(Objects.requireNonNull(transferId, "hive nutrient transfer id"));
        if (current == null) throw new IllegalArgumentException("unknown hive nutrient transfer: " + transferId.value());
        Map<SubjectId, HiveNutrientTransfer> next = new LinkedHashMap<>(nutrientTransfers); next.put(transferId, current.advanceTo(cursor));
        return new HiveColony(addedOrgans, spawnedBioforms, growthJobs, next, nutrientReceipts, bioformLifecycles, mobilizations);
    }

    public HiveColony advanceNutrientTransferState(SubjectId transferId, HiveNutrientTransfer replacement) {
        HiveNutrientTransfer current = nutrientTransfers.get(Objects.requireNonNull(transferId, "hive nutrient transfer id"));
        if (current == null || !current.id().equals(replacement.id())) throw new IllegalArgumentException("unknown hive nutrient transfer state");
        Map<SubjectId, HiveNutrientTransfer> next = new LinkedHashMap<>(nutrientTransfers); next.put(transferId, replacement);
        return new HiveColony(addedOrgans, spawnedBioforms, growthJobs, next, nutrientReceipts, bioformLifecycles, mobilizations);
    }

    public HiveColony blockNutrientTransfer(SubjectId transferId, HiveNutrientTransferBlockReason reason) {
        HiveNutrientTransfer current = nutrientTransfers.get(Objects.requireNonNull(transferId, "hive nutrient transfer id"));
        if (current == null) throw new IllegalArgumentException("unknown hive nutrient transfer: " + transferId.value());
        Map<SubjectId, HiveNutrientTransfer> next = new LinkedHashMap<>(nutrientTransfers); next.put(transferId, current.block(reason));
        return new HiveColony(addedOrgans, spawnedBioforms, growthJobs, next, nutrientReceipts, bioformLifecycles, mobilizations);
    }

    public HiveColony completeNutrientTransfer(HiveNutrientReceipt receipt) {
        Objects.requireNonNull(receipt, "hive nutrient receipt");
        HiveNutrientTransfer transfer = nutrientTransfers.get(receipt.transferId());
        if (transfer == null || !receipt.matches(transfer) || nutrientReceipts.containsKey(receipt.transferId())) {
            throw new IllegalArgumentException("hive nutrient receipt does not complete one active transfer");
        }
        Map<SubjectId, HiveNutrientTransfer> active = new LinkedHashMap<>(nutrientTransfers); active.remove(receipt.transferId());
        Map<SubjectId, HiveNutrientReceipt> completed = new LinkedHashMap<>(nutrientReceipts); completed.put(receipt.transferId(), receipt);
        return new HiveColony(addedOrgans, spawnedBioforms, growthJobs, active, completed, bioformLifecycles, mobilizations);
    }

    public HiveColony consumeTransferredNutrient(SubjectId jobId, SubjectId itemId) {
        List<HiveNutrientReceipt> matches = nutrientReceipts.values().stream().filter(receipt -> receipt.itemId().equals(itemId)
                && receipt.status() == HiveNutrientReceiptStatus.STORED).toList();
        if (matches.isEmpty()) return this;
        if (matches.size() != 1) throw new IllegalArgumentException("exact hive nutrient has ambiguous retained receipts");
        HiveNutrientReceipt receipt = matches.getFirst(); Map<SubjectId, HiveNutrientReceipt> next = new LinkedHashMap<>(nutrientReceipts);
        next.put(receipt.transferId(), receipt.consumeBy(jobId));
        return new HiveColony(addedOrgans, spawnedBioforms, growthJobs, nutrientTransfers, next, bioformLifecycles, mobilizations);
    }

    /** Starts one exact task-owned wake boundary; no body moves through this method. */
    public HiveColony startMobilization(HiveMobilization mobilization) {
        Objects.requireNonNull(mobilization, "hive mobilization");
        if (mobilizations.containsKey(mobilization.id())) {
            throw new IllegalArgumentException("hive mobilization identity already exists: " + mobilization.id().value());
        }
        if (mobilizations.values().stream().anyMatch(current -> !current.status().terminal()
                && (current.taskId().equals(mobilization.taskId()) || current.memberIds().stream().anyMatch(mobilization.memberIds()::contains)))) {
            throw new IllegalArgumentException("active hive mobilization already owns this task or bioform");
        }
        Map<SubjectId, HiveMobilization> next = new LinkedHashMap<>(mobilizations);
        next.put(mobilization.id(), mobilization);
        return new HiveColony(addedOrgans, spawnedBioforms, growthJobs, nutrientTransfers, nutrientReceipts, bioformLifecycles, next);
    }

    /** Begins one exact durable before-effect cocoon release. */
    public HiveColony startMobilizationRelease(SubjectId mobilizationId) {
        HiveMobilization current = mobilizations.get(Objects.requireNonNull(mobilizationId, "hive mobilization id"));
        if (current == null) throw new IllegalArgumentException("unknown hive mobilization: " + mobilizationId.value());
        Map<SubjectId, HiveMobilization> next = new LinkedHashMap<>(mobilizations);
        next.put(mobilizationId, current.startRelease());
        return new HiveColony(addedOrgans, spawnedBioforms, growthJobs, nutrientTransfers, nutrientReceipts, bioformLifecycles, next);
    }

    /** Confirms the named cocoon after its exact owned Minecraft block was physically removed. */
    public HiveColony confirmMobilizationRelease(SubjectId mobilizationId, SubjectId memberId) {
        HiveMobilization current = mobilizations.get(Objects.requireNonNull(mobilizationId, "hive mobilization id"));
        if (current == null) throw new IllegalArgumentException("unknown hive mobilization: " + mobilizationId.value());
        Map<SubjectId, HiveMobilization> next = new LinkedHashMap<>(mobilizations);
        next.put(mobilizationId, current.confirmRelease(memberId));
        return new HiveColony(addedOrgans, spawnedBioforms, growthJobs, nutrientTransfers, nutrientReceipts, bioformLifecycles, next);
    }

    public HiveColony conflictMobilization(SubjectId mobilizationId, HiveMobilizationConflictReason reason) {
        HiveMobilization current = mobilizations.get(Objects.requireNonNull(mobilizationId, "hive mobilization id"));
        if (current == null) throw new IllegalArgumentException("unknown hive mobilization: " + mobilizationId.value());
        Map<SubjectId, HiveMobilization> next = new LinkedHashMap<>(mobilizations);
        next.put(mobilizationId, current.conflict(reason));
        return new HiveColony(addedOrgans, spawnedBioforms, growthJobs, nutrientTransfers, nutrientReceipts, bioformLifecycles, next);
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
        Hive.requireNonOverlappingOrganFootprints(java.util.stream.Stream.concat(hive.organs().stream(), addedOrgans.values().stream()).toList());
        for (Bioform bioform : spawnedBioforms.values()) {
            if (bioformIds.contains(bioform.id()) || !hive.id().equals(bioform.hiveId()) || !nestIds.contains(bioform.nestId()) || !bootstrap.bounds().contains(bioform.position())) {
                throw new IllegalArgumentException("spawned bioform does not belong to this colony");
            }
            bioformIds.add(bioform.id());
        }
        if (!bioformLifecycles.keySet().equals(bioformIds)) {
            throw new IllegalArgumentException("bioform lifecycle index must own every and only mature hive bioform");
        }
        java.util.Set<SubjectId> activeMobilized = new java.util.HashSet<>();
        for (HiveMobilization mobilization : mobilizations.values()) {
            if (!hive.id().equals(mobilization.hiveId()) || !nestIds.contains(mobilization.nestId())) {
                throw new IllegalArgumentException("hive mobilization does not belong to this colony");
            }
            for (SubjectId member : mobilization.memberIds()) {
                Bioform bioform = java.util.stream.Stream.concat(hive.bioforms().stream(), spawnedBioforms.values().stream())
                        .filter(candidate -> candidate.id().equals(member)).findFirst().orElse(null);
                BioformLifecycle lifecycle = bioformLifecycles.get(member);
                if (bioform == null || lifecycle == null || !bioform.nestId().equals(mobilization.nestId())) {
                    throw new IllegalArgumentException("hive mobilization member does not belong to its named nest");
                }
                if (!mobilization.status().terminal() && !activeMobilized.add(member)) {
                    throw new IllegalArgumentException("bioform may not belong to multiple active hive mobilizations");
                }
                BioformLifecyclePhase expected = mobilization.releasedMemberIds().contains(member)
                        ? BioformLifecyclePhase.ASSEMBLING
                        : (mobilization.status().terminal() ? null : BioformLifecyclePhase.WAKING);
                if (expected != null && lifecycle.phase() != expected) {
                    throw new IllegalArgumentException("hive mobilization lifecycle does not match its physical release phase");
                }
            }
        }
        java.util.Set<HiveCocoonSlot> reservedSlots = new java.util.HashSet<>();
        for (BioformLifecycle lifecycle : bioformLifecycles.values()) {
            if (lifecycle.homeSlot().isEmpty()) continue;
            HiveCocoonSlot slot = lifecycle.homeSlot().orElseThrow();
            HiveOrgan organ = java.util.stream.Stream.concat(hive.organs().stream(), addedOrgans.values().stream())
                    .filter(candidate -> candidate.id().equals(slot.hibernaculumId())).findFirst().orElse(null);
            if (organ == null || organ.kind() != HiveOrganKind.HIBERNACULUM || !reservedSlots.add(slot)) {
                throw new IllegalArgumentException("cocoon home must be unique and belong to one HIBERNACULUM");
            }
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
