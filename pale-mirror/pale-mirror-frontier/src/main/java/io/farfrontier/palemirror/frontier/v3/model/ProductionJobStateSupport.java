package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Canonical production mutations, including fungible lot claims and their physical boundary. */
final class ProductionJobStateSupport {
    private ProductionJobStateSupport() { }

    static FrontierWorldState start(FrontierWorldState state, ProductionJob job, SubjectId inputItemId) {
        Objects.requireNonNull(job, "production job");
        Objects.requireNonNull(inputItemId, "production input item id");
        if (!job.consumedItemId().equals(inputItemId)) throw new IllegalArgumentException("production job input identity differs");
        ExactItemStack input = state.inventory().items().get(inputItemId);
        if (input == null || !(input.custody() instanceof InventoryCustody.ContainerSlot)) throw new IllegalArgumentException("production input is unavailable");
        if (!(job.inputHold() instanceof ProductionInputHold.Cold held) || !held.item().equals(input)) {
            throw new IllegalArgumentException("cold production job must retain its exact removed input");
        }
        if (state.productionJobs().containsKey(job.id())) throw new IllegalArgumentException("production job identity already exists: " + job.id().value());
        if (state.productionJobs().values().stream().anyMatch(existing -> existing.facilityId().equals(job.facilityId()))) {
            throw new IllegalArgumentException("facility already has an active production job: " + job.facilityId().value());
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(state.productionJobs()); next.put(job.id(), job);
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory().withoutItem(inputItemId), next,
                state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState complete(FrontierWorldState state, SubjectId jobId, ExactItemStack output) {
        ProductionJob job = state.productionJobs().get(Objects.requireNonNull(jobId, "production job id"));
        if (job == null) throw new IllegalArgumentException("unknown production job: " + jobId.value());
        if (!job.outputItemId().equals(output.id()) || !job.outputItemKind().equals(output.itemKind()) || job.outputCount() != output.count()) {
            throw new IllegalArgumentException("production output does not match durable job result");
        }
        if (!(job.inputHold() instanceof ProductionInputHold.Cold) || state.inventory().items().containsKey(job.consumedItemId())) {
            throw new IllegalArgumentException("materialized production must confirm its physical transformation rather than emit a direct completion");
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(state.productionJobs()); next.remove(jobId);
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory().store(output), next, state.contracts(),
                state.operations(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(), state.structureDamage(),
                state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState completeFungible(FrontierWorldState state, SubjectId jobId, ResourceLot output) {
        ProductionJob job = state.productionJobs().get(Objects.requireNonNull(jobId, "fungible production job id"));
        if (job == null || !(job.inputHold() instanceof ProductionInputHold.FungibleCold cold)
                || !job.outputItemId().equals(output.id()) || !job.outputItemKind().equals(output.itemKind())
                || job.outputCount() != output.quantity() || !job.settlementId().equals(output.economicOwnerId())) {
            throw new IllegalArgumentException("fungible production output does not match durable job result");
        }
        FungibleResourceLedger resources = state.inventory().fungibleResources().transformCold(cold.accountId(),
                Map.of(cold.itemId(), job.outputCount()), Map.of(cold.claimId(), job.outputCount()), output);
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(state.productionJobs()); next.remove(jobId);
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory().withFungibleResources(resources), next,
                state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState startFungible(FrontierWorldState state, ProductionJob job) {
        Objects.requireNonNull(job, "fungible production job");
        ProductionInputHold hold = job.inputHold();
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        ClaimAllocation claim;
        FungibleResourceLedger reserved;
        if (hold instanceof ProductionInputHold.FungibleCold cold) {
            claim = new ClaimAllocation(cold.claimId(), job.id(), job.settlementId(), "minecraft:wheat", job.outputCount());
            reserved = resources.reserve(claim, cold.accountId());
        } else if (hold instanceof ProductionInputHold.FungibleBound bound) {
            claim = new ClaimAllocation(bound.claimId(), job.id(), job.settlementId(), "minecraft:wheat", job.outputCount());
            reserved = resources.reserveBound(claim, bound.accountId(), bound.authorityEpoch());
        } else {
            throw new IllegalArgumentException("fungible production job has no fungible input hold");
        }
        if (!resources.lots().containsKey(job.consumedItemId()) || state.productionJobs().containsKey(job.id())
                || state.productionJobs().values().stream().anyMatch(existing -> existing.facilityId().equals(job.facilityId()))) {
            throw new IllegalArgumentException("fungible production job is unavailable or duplicates its facility");
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(state.productionJobs()); next.put(job.id(), job);
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory().withFungibleResources(reserved), next,
                state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState cancel(FrontierWorldState state, SubjectId jobId) {
        ProductionJob job = state.productionJobs().get(Objects.requireNonNull(jobId, "production job id"));
        if (job == null) throw new IllegalArgumentException("unknown production job: " + jobId.value());
        ExactInventory nextInventory = switch (job.inputHold()) {
            case ProductionInputHold.Cold cold -> restoreExactColdInput(state.inventory(), cold);
            case ProductionInputHold.Materialized ignored -> state.inventory();
            case ProductionInputHold.FungibleCold cold -> state.inventory().withFungibleResources(state.inventory().fungibleResources()
                    .releaseClaim(cold.accountId(), cold.claimId()));
            case ProductionInputHold.FungibleBound ignored -> throw new IllegalArgumentException("bound fungible production must await physical recovery");
        };
        Map<PhysicalIntentId, PhysicalIntent> nextIntents = new LinkedHashMap<>(state.physicalIntents());
        for (PhysicalIntent intent : state.physicalIntents().values()) if (intent.causeSubjectId().equals(job.id())) {
            if (intent.kind() != PhysicalIntentKind.PRODUCTION_TRANSFORMATION || intent.status() != PhysicalIntentStatus.PREPARED) {
                throw new IllegalArgumentException("production job with a running or terminal physical transformation cannot be cancelled");
            }
            nextIntents.remove(intent.id());
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(state.productionJobs()); next.remove(job.id());
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), nextInventory, next, state.contracts(), state.operations(),
                nextIntents, state.physicalObservations(), state.sceneLeases(), state.hiveColony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    private static ExactInventory restoreExactColdInput(ExactInventory inventory, ProductionInputHold.Cold cold) {
        ExactItemStack held = cold.item();
        if (inventory.items().containsKey(held.id()) || !(held.custody() instanceof InventoryCustody.ContainerSlot source)
                || inventory.itemAt(source.containerId(), source.slot()).isPresent()) {
            throw new IllegalArgumentException("cold production hold cannot return to its original exact slot");
        }
        return inventory.store(held);
    }
}
