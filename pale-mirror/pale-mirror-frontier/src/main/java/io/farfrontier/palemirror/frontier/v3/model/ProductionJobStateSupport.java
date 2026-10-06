package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Canonical production mutations, including fungible lot claims and their physical boundary. */
final class ProductionJobStateSupport {
    private ProductionJobStateSupport() { }

    static FrontierWorldState startMaterialized(FrontierWorldState state, ProductionJob job) {
        Objects.requireNonNull(job, "production job");
        if (!(job.inputHold() instanceof ProductionInputHold.Materialized))
            throw new IllegalArgumentException("only a materialized production input may remain in exact inventory");
        if (state.productionJobs().containsKey(job.id())) throw new IllegalArgumentException("production job identity already exists: " + job.id().value());
        if (!ProductionFacilityReservations.available(state.productionJobs(), job.facilityId()))
            throw new IllegalArgumentException("facility already has an active production job: " + job.facilityId().value());
        var jobs = new LinkedHashMap<>(state.productionJobs()); jobs.put(job.id(), job);
        return admit(state, job, state.inventory(), jobs);
    }

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
        if (!ProductionFacilityReservations.available(state.productionJobs(), job.facilityId())) {
            throw new IllegalArgumentException("facility already has an active production job: " + job.facilityId().value());
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(state.productionJobs()); next.put(job.id(), job);
        return admit(state, job, state.inventory().withoutItem(inputItemId), next);
    }

    static FrontierWorldState complete(FrontierWorldState state, SubjectId jobId, ExactItemStack output) {
        ProductionJob job = state.productionJobs().get(Objects.requireNonNull(jobId, "production job id"));
        if (job == null) throw new IllegalArgumentException("unknown production job: " + jobId.value());
        job.requireColdCompletion(state);
        if (!job.outputItemId().equals(output.id()) || !job.outputItemKind().equals(output.itemKind()) || job.outputCount() != output.count()) {
            throw new IllegalArgumentException("production output does not match durable job result");
        }
        ExactInventory inventory = state.inventory();
        if (job.inputHold() instanceof ProductionInputHold.Cold) {
            if (inventory.items().containsKey(job.consumedItemId())) {
                throw new IllegalArgumentException("cold production input is duplicated in exact inventory");
            }
        } else if (job.inputHold() instanceof ProductionInputHold.Materialized) {
            SubjectId depot = FrontierWorldState.depotId(job.settlementId());
            ExactItemStack input = inventory.items().get(job.consumedItemId());
            if (ReferenceContainerCustody.hasLiveCustody(state, depot) || input == null || !(input.custody() instanceof InventoryCustody.ContainerSlot slot)
                    || !slot.containerId().equals(depot) || input.count() != job.outputCount()) {
                throw new IllegalArgumentException("released materialized production has no exact COLD input");
            }
            inventory = inventory.withoutItem(input.id());
        } else {
            throw new IllegalArgumentException("production completion has no exact COLD input hold");
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(state.productionJobs()); next.remove(jobId);
        Map<PhysicalIntentId, PhysicalIntent> intents = new LinkedHashMap<>(state.physicalIntents());
        for (PhysicalIntent intent : state.physicalIntents().values()) if (intent.causeSubjectId().equals(jobId)) {
            if (intent.kind() != PhysicalIntentKind.PRODUCTION_TRANSFORMATION || intent.status() != PhysicalIntentStatus.PREPARED) {
                throw new IllegalArgumentException("production completion cannot supersede a started physical transformation");
            }
            intents.remove(intent.id());
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory.store(output)).productionJobs(next)
                .physicalIntents(intents).actorExecutions(ActorExecutionComposition.LIFECYCLE.retire(
                        state, job.workerId(), ActorActivityKind.PRODUCTION, job.id())));
    }

    static FrontierWorldState completeFungible(FrontierWorldState state, SubjectId jobId, ResourceLot output) {
        ProductionJob job = state.productionJobs().get(Objects.requireNonNull(jobId, "fungible production job id"));
        if (job == null || !(job.inputHold() instanceof ProductionInputHold.FungibleCold cold)
                || !job.outputItemId().equals(output.id()) || !job.outputItemKind().equals(output.itemKind())
                || job.outputCount() != output.quantity() || !job.rights().resourceOwner().id().equals(output.economicOwnerId())) {
            throw new IllegalArgumentException("fungible production output does not match durable job result");
        }
        job.requireColdCompletion(state);
        FungibleResourceLedger resources = state.inventory().fungibleResources().transformCold(cold.accountId(),
                cold.inputLots(), Map.of(cold.claimId(), job.outputCount()), output);
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(state.productionJobs()); next.remove(jobId);
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(resources))
                .productionJobs(next).actorExecutions(ActorExecutionComposition.LIFECYCLE.retire(
                        state, job.workerId(), ActorActivityKind.PRODUCTION, job.id())));
    }

    static FrontierWorldState startFungible(FrontierWorldState state, ProductionJob job) {
        Objects.requireNonNull(job, "fungible production job");
        ProductionInputHold hold = job.inputHold();
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        ClaimAllocation claim;
        FungibleResourceLedger reserved;
        if (hold instanceof ProductionInputHold.FungibleCold cold) {
            ProductionResourceCustody.requireNewHold(state, job, cold.accountId(), cold.claimId());
            claim = new ClaimAllocation(cold.claimId(), job.id(), job.rights().resourceOwner().id(), "minecraft:wheat", job.outputCount(), cold.inputLots(), ClaimPurpose.PRODUCTION_WORK);
            reserved = resources.reserve(claim, cold.accountId());
        } else if (hold instanceof ProductionInputHold.FungibleBound bound) {
            ProductionResourceCustody.requireNewHold(state, job, bound.accountId(), bound.claimId());
            claim = new ClaimAllocation(bound.claimId(), job.id(), job.rights().resourceOwner().id(), "minecraft:wheat", job.outputCount(), bound.inputLots(), ClaimPurpose.PRODUCTION_WORK);
            reserved = resources.reserveBound(claim, bound.accountId(), bound.authorityEpoch());
        } else {
            throw new IllegalArgumentException("fungible production job has no fungible input hold");
        }
        if (!resources.lots().containsKey(job.consumedItemId()) || state.productionJobs().containsKey(job.id())
                || !ProductionFacilityReservations.available(state.productionJobs(), job.facilityId())) {
            throw new IllegalArgumentException("fungible production job is unavailable or duplicates its facility");
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(state.productionJobs()); next.put(job.id(), job);
        return admit(state, job, state.inventory().withFungibleResources(reserved), next);
    }

    static FrontierWorldState cancel(FrontierWorldState state, SubjectId jobId) {
        ProductionJob job = state.productionJobs().get(Objects.requireNonNull(jobId, "production job id"));
        if (job == null) throw new IllegalArgumentException("unknown production job: " + jobId.value());
        // Once a bakery batch has left the depot, cancelling the job would orphan its
        // actor/station custody (and a completed batch would lose its payment receipt).
        // Keep the job as the durable owner until delivery or an explicit compensation
        // transition moves the batch back to a safe owner.
        if (job.bakeryWork().isPresent()
                && job.bakeryWork().orElseThrow().phase() != BakeryWorkState.Phase.DEPOT_PICKUP)
            throw new IllegalArgumentException("bakery job with in-flight cargo cannot be cancelled");
        ExactInventory nextInventory = switch (job.inputHold()) {
            case ProductionInputHold.Cold cold -> restoreExactColdInput(state.inventory(), cold);
            case ProductionInputHold.Materialized ignored -> state.inventory();
            case ProductionInputHold.FungibleCold cold -> state.inventory().withFungibleResources(state.inventory().fungibleResources()
                    .releaseClaim(cold.accountId(), cold.claimId()));
            case ProductionInputHold.FungibleBound bound -> state.inventory().withFungibleResources(
                    ProductionResourceCustody.cancelBound(state, job, bound));
        };
        Map<PhysicalIntentId, PhysicalIntent> nextIntents = new LinkedHashMap<>(state.physicalIntents());
        for (PhysicalIntent intent : state.physicalIntents().values()) if (intent.causeSubjectId().equals(job.id())) {
            if (intent.kind() != PhysicalIntentKind.PRODUCTION_TRANSFORMATION || intent.status() != PhysicalIntentStatus.PREPARED) {
                throw new IllegalArgumentException("production job with a running or terminal physical transformation cannot be cancelled");
            }
            nextIntents.remove(intent.id());
        }
        Map<SubjectId, ProductionJob> next = new LinkedHashMap<>(state.productionJobs()); next.remove(job.id());
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(nextInventory).productionJobs(next)
                .physicalIntents(nextIntents).actorExecutions(ActorExecutionComposition.LIFECYCLE.retire(
                        state, job.workerId(), ActorActivityKind.PRODUCTION, job.id())));
    }

    private static FrontierWorldState admit(FrontierWorldState state, ProductionJob job, ExactInventory inventory,
                                             Map<SubjectId, ProductionJob> jobs) {
        var execution = state.actorExecutions().next(job.workerId(), ActorActivityKind.PRODUCTION, job.id());
        return ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution).commit(state,
                FrontierWorldStateUpdate.begin().inventory(inventory).productionJobs(jobs));
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
