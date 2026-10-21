package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Production owns retained batch relations, independently of the common body/drop protocol. */
public final class BakeryDeathResources {
    private BakeryDeathResources() { }
    public static Optional<ActorExecutionId> execution(FrontierWorldState state, SubjectId actor) {
        var authority = state.actorExecutions().actors().get(actor);
        if (authority == null) return Optional.empty();
        return authority.current().filter(id -> id.activityKind() == ActorActivityKind.PRODUCTION)
                .or(() -> authority.suspended().filter(id -> id.activityKind() == ActorActivityKind.PRODUCTION));
    }
    public static Optional<ProductionJob> work(FrontierWorldState state, SubjectId actor) {
        return execution(state, actor).map(id -> {
            var job = state.productionJobs().get(id.activityOwnerId());
            if (job == null || !job.workerId().equals(actor))
                throw new IllegalArgumentException("retained production execution lost its exact job");
            return job;
        }).filter(job -> job.bakeryWork().isPresent());
    }
    public static Optional<SubjectId> pendingOwner(FrontierWorldState state, SubjectId actor) {
        return work(state, actor).filter(job -> {
            var work = job.bakeryWork().orElseThrow();
            if (work.pendingPhysicalStep().isPresent()) return true;
            var location = state.actorLocations().get(actor);
            return location != null && location.condition().status() == ActorLifeStatus.DEAD
                    && (work.phase() == BakeryWorkState.Phase.STATION_LOAD || work.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY);
        }).map(ProductionJob::id);
    }
    public static boolean ready(FrontierWorldState state, ProductionJob job) {
        var work = job.bakeryWork().orElseThrow();
        if (work.pendingPhysicalStep().isPresent()) return false;
        if (work.phase() == BakeryWorkState.Phase.PROCESSING || work.phase() == BakeryWorkState.Phase.STATION_UNLOAD) {
            var machine = state.productionStations().require(work.stationId());
            return machine.pendingFuel().isEmpty() && machine.pendingRecipe().isEmpty();
        }
        return true;
    }
    public static FrontierWorldState settle(FrontierWorldState state, SubjectId subject, BakeryDeathSettled receipt) {
        var identity = receipt.identity(); ActorBodyAuthority.requireRetiredDeath(state, identity.body());
        state.actorExecutions().requireRetained(identity.execution());
        var job = work(state, identity.body().actorId()).orElseThrow(() -> new IllegalArgumentException("bakery death lost its owner job"));
        var work = job.bakeryWork().orElseThrow();
        if (!job.id().equals(identity.execution().activityOwnerId()) || !subject.equals(job.settlementId())
                || work.phase() != receipt.expectedPhase() || !ready(state, job)
                || state.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(job.id())))
            throw new IllegalArgumentException("bakery death cannot retire another phase or an unresolved physical operation");
        var inventory = state.inventory(); var machines = state.productionStations();
        if (work.phase() == BakeryWorkState.Phase.PROCESSING || work.phase() == BakeryWorkState.Phase.STATION_UNLOAD) {
            var abandoned = ProductionStationProcessing.abandon(state, BakeryStationOperation.require(state, job));
            inventory = abandoned.inventory(); machines = abandoned.machines();
        } else if (job.inputHold() instanceof ProductionInputHold.FungibleCold cold) {
            inventory = releaseInput(inventory, cold.claimId(), job);
        } else if (job.inputHold() instanceof ProductionInputHold.FungibleBound bound) {
            inventory = releaseInput(inventory, bound.claimId(), job);
        }
        if (receipt.ground().isPresent()) {
            if (job.inputHold() instanceof ProductionInputHold.Materialized) {
                if (receipt.sourceEpoch() != 0) throw new IllegalArgumentException("exact cargo cannot declare a fungible epoch");
                var item = BakeryCargo.output(job) ? job.outputItemId() : job.consumedItemId();
                inventory = inventory.moveObservedItem(item, new InventoryCustody.Actor(job.workerId()),
                        new InventoryCustody.WorldCarrier(receipt.ground().orElseThrow().entityId()));
            } else {
                var resources = inventory.fungibleResources();
                inventory = inventory.withFungibleResources(resources.releaseObservedActorAccountToGround(work.actorAccountId(),
                        job.workerId(), ActorBodyId.entityId(state.bootstrap().worldId(), job.workerId()), receipt.sourceEpoch(), receipt.ground().orElseThrow()));
            }
        }
        var companies = state.companies(); var invoice = companies.market().acceptedForJob(job.id());
        if (work.phase() == BakeryWorkState.Phase.DELIVERED) {
            var paid = ProductionCommercialStateSupport.settleCommittedPhysicalWork(state, job);
            inventory = inventory.withEconomics(paid.inventory().economics()); companies = paid.companies();
            if (invoice.isPresent()) companies = companies.withMarket(companies.market().complete(invoice.orElseThrow().id(), job));
        } else if (invoice.isPresent()) {
            inventory = inventory.withEconomics(inventory.economics().release(invoice.orElseThrow().reservationId()));
            companies = companies.withMarket(companies.market().cancel(invoice.orElseThrow().id(), MarketWorkOrderStatus.CANCELLED));
        }
        var jobs = new java.util.LinkedHashMap<>(state.productionJobs()); jobs.remove(job.id());
        var leases = new java.util.LinkedHashMap<>(state.sceneLeases());
        for (var lease : state.sceneLeases().values()) {
            if (lease.status() == SceneLeaseStatus.CLOSED || !FrontierSceneBehaviors.isProductionWork(lease)
                    || !FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id())) continue;
            if (lease.members().size() != 1 || !lease.members().getFirst().actorId().equals(job.workerId()))
                throw new IllegalArgumentException("bakery retirement has foreign physical participants");
            leases.put(lease.id(), lease.withStatus(SceneLeaseStatus.CLOSED));
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).productionStations(machines).productionJobs(jobs)
                .companies(companies).sceneLeases(leases).strategicPlans(state.strategicPlans().transitionTask(job.taskId(),
                        work.phase() == BakeryWorkState.Phase.DELIVERED ? StrategicTaskStatus.COMPLETED : StrategicTaskStatus.BLOCKED))
                .actorExecutions(ActorExecutionComposition.LIFECYCLE.retire(state.actorExecutions(), job.workerId(), ActorActivityKind.PRODUCTION, job.id())));
    }
    private static ExactInventory releaseInput(ExactInventory inventory, SubjectId claimId, ProductionJob job) {
        var resources = inventory.fungibleResources(); var claim = resources.claims().get(claimId);
        if (claim == null) return inventory; // Transformed or explicitly forfeited input has no remaining promise.
        if (claim.purpose() != ClaimPurpose.PRODUCTION_WORK || !claim.claimantId().equals(job.id()))
            throw new IllegalArgumentException("bakery retirement cannot release another owner's allocation");
        return inventory.withFungibleResources(resources.releaseClaims(java.util.Set.of(claimId)));
    }
}
