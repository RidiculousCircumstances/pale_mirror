package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Canonical state mutations for a hive job; physical consumption itself remains an intent receipt. */
final class HiveGrowthStateSupport {
    private HiveGrowthStateSupport() { }

    static FrontierWorldState start(FrontierWorldState state, HiveGrowthJob job) {
        Objects.requireNonNull(job, "hive growth job"); ExactItemStack input = state.inventory().items().get(job.consumedItemId());
        if (input == null || !(input.custody() instanceof InventoryCustody.ContainerSlot slot) || !state.isHiveStore(slot.containerId())
                || !HiveStorageSupport.operationalNestForStore(state, slot.containerId()).id().equals(job.nestId())) {
            throw new IllegalArgumentException("hive growth needs one exact nest-local hive-store input");
        }
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().startGrowth(job), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState startFungible(FrontierWorldState state, HiveGrowthJob job) {
        if (!(job.inputHold() instanceof HiveGrowthInputHold.FungibleCold held)) {
            throw new IllegalArgumentException("fungible hive growth has no fungible biomass hold");
        }
        FungibleResourceLedger resources = state.inventory().fungibleResources().reserve(new ClaimAllocation(held.claimId(), job.id(), job.hiveId(),
                "minecraft:rotten_flesh", 64), held.accountId());
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory().withFungibleResources(resources), state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().startGrowth(job), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState complete(FrontierWorldState state, SubjectId jobId) {
        HiveGrowthJob job = state.hiveColony().growthJobs().get(Objects.requireNonNull(jobId, "hive growth job id"));
        if (job == null || state.actorLocations().containsKey(job.bioform().id())) throw new IllegalArgumentException("hive growth completion is invalid");
        var actors = new LinkedHashMap<>(state.actorLocations()); actors.put(job.bioform().id(), ActorLocation.standingOn(new SurfaceAnchor(job.bioform().position())));
        return state.next(actors, state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().completeGrowth(jobId), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState consume(FrontierWorldState state, SubjectId jobId, SubjectId itemId) {
        HiveGrowthJob job = state.hiveColony().growthJobs().get(Objects.requireNonNull(jobId, "hive growth job id"));
        if (job == null || !job.consumedItemId().equals(itemId)) throw new IllegalArgumentException("hive growth biomass does not match its active job");
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory().withoutItem(itemId), state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().consumeTransferredNutrient(jobId, itemId), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState consumeFungible(FrontierWorldState state, SubjectId jobId) {
        HiveGrowthJob job = state.hiveColony().growthJobs().get(Objects.requireNonNull(jobId, "fungible hive growth job id"));
        if (job == null || !(job.inputHold() instanceof HiveGrowthInputHold.FungibleCold held)) {
            throw new IllegalArgumentException("fungible hive growth biomass does not match its active job");
        }
        FungibleResourceLedger resources = state.inventory().fungibleResources().destroy(held.accountId(), Map.of(held.itemId(), 64), Map.of(held.claimId(), 64));
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory().withFungibleResources(resources), state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().consumeTransferredNutrient(jobId, held.itemId()), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState consumeObservedFungible(FrontierWorldState state, SubjectId jobId, FungibleResourceConsumedObservation observed) {
        HiveGrowthJob job = state.hiveColony().growthJobs().get(Objects.requireNonNull(jobId, "fungible observed growth job id"));
        if (job == null || !(job.inputHold() instanceof HiveGrowthInputHold.FungibleCold held)
                || !held.accountId().equals(observed.accountId()) || !held.itemId().equals(observed.lotId()) || !held.claimId().equals(observed.claimId())
                || observed.consumedCount() != 64) throw new IllegalArgumentException("fungible observed biomass does not match its active job");
        FungibleResourceLedger resources = state.inventory().fungibleResources().destroyObserved(held.accountId(), observed.authorityEpoch(),
                Map.of(held.itemId(), observed.consumedCount()), Map.of(held.claimId(), observed.consumedCount()), observed.remainingStacks());
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory().withFungibleResources(resources), state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().consumeTransferredNutrient(jobId, held.itemId()), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    static FrontierWorldState cancel(FrontierWorldState state, SubjectId jobId) {
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), state.operations(),
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony().cancelGrowth(jobId), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }
}
