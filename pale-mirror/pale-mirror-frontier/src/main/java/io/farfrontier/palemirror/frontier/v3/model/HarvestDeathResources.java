package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Field-owned causal obligations; common inventory must not dispose of yield behind the job. */
public final class HarvestDeathResources {
    private HarvestDeathResources() { }
    public static Optional<ActorExecutionId> execution(FrontierWorldState state, SubjectId actor) {
        var authority = state.actorExecutions().actors().get(actor);
        if (authority == null) return Optional.empty();
        return authority.current().filter(id -> id.activityKind() == ActorActivityKind.FIELD_HARVEST)
                .or(() -> authority.suspended().filter(id -> id.activityKind() == ActorActivityKind.FIELD_HARVEST));
    }
    public static Optional<ResourceSiteHarvestJob> work(FrontierWorldState state, SubjectId actor) {
        return execution(state, actor).map(id -> state.resourceSites().sites().values().stream()
                .flatMap(site -> site.harvestJob(id.activityOwnerId()).stream())
                .filter(job -> job.workerId().equals(actor))
                .reduce((left, right) -> { throw new IllegalArgumentException("duplicate retained harvest owner"); })
                .orElseThrow(() -> new IllegalArgumentException("field execution lost its exact retained job")));
    }
    public static Optional<SubjectId> pendingOwner(FrontierWorldState state, SubjectId actor) {
        return work(state, actor).filter(job -> {
            var location = state.actorLocations().get(actor);
            return location != null && location.condition().status() == ActorLifeStatus.DEAD
                    && (job.progress().hasPendingPhysicalWork() || job.undeliveredYieldQuantity() > 0);
        }).map(ResourceSiteHarvestJob::id);
    }
    public static boolean ready(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return !job.progress().hasPendingPhysicalWork() && !state.resourceSites().harvestMutationPending(job)
                && ReferenceContainerCustody.pendingOwnerForActor(state, job.workerId()).isEmpty();
    }
    public static FrontierWorldState settle(FrontierWorldState state, SubjectId subject, HarvestDeathSettled receipt) {
        var identity = receipt.identity(); ActorBodyAuthority.requireRetiredDeath(state, identity.body());
        state.actorExecutions().requireRetained(identity.execution());
        var site = state.resourceSites().site(receipt.siteId());
        var job = site.harvestJob(identity.execution().activityOwnerId()).orElseThrow();
        if (!subject.equals(receipt.siteId()) || !job.workerId().equals(identity.body().actorId()) || !ready(state, job)
                || job.progress().completedCropSlots() != receipt.completedCells()
                || job.harvestedYieldQuantity() != receipt.harvestedQuantity()
                || job.deliveredYieldQuantity() != receipt.deliveredQuantity())
            throw new IllegalArgumentException("field death cannot retire changed or unresolved work");
        int carried = ResourceSiteHarvestCargo.quantity(state, job);
        if (receipt.ground().isPresent() != (carried > 0))
            throw new IllegalArgumentException("field death cannot discard or invent carried yield");
        var inventory = state.inventory();
        if (carried > 0) inventory = inventory.withFungibleResources(inventory.fungibleResources()
                .releaseObservedActorAccountToGround(job.actorAccountId(), job.workerId(),
                    ActorBodyId.entityId(state.bootstrap().worldId(), job.workerId()), receipt.sourceEpoch(), receipt.ground().orElseThrow()));
        var intent = state.physicalIntents().get(job.intentId());
        if (intent == null || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.siteHarvest(
                job.siteId(), job.id(), job.workerId(), job.actorAccountId(), job.depotAccountId())))
            throw new IllegalArgumentException("field death lost its exact retained intent");
        var intents = new java.util.LinkedHashMap<>(state.physicalIntents()); intents.remove(intent.id());
        var recoveryId = FencedRecoveryPhysicalIntentSupport.bindingId(intent);
        var binding = state.fencedRecovery().current().get(recoveryId);
        if (binding == null) throw new IllegalArgumentException("field death lost its exact recovery fence");
        var recovery = state.fencedRecovery().retireSettledOwnerEffect(recoveryId, binding.authorityEpoch(), job.siteId());
        var leases = new java.util.LinkedHashMap<>(state.sceneLeases());
        for (var lease : state.sceneLeases().values()) {
            if (lease.status() == SceneLeaseStatus.CLOSED || !FrontierSceneBehaviors.isResourceSiteHarvest(lease)
                    || !FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(job.siteId())
                    || !FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id())) continue;
            if (lease.members().size() != 1 || !lease.members().getFirst().actorId().equals(job.workerId()))
                throw new IllegalArgumentException("field death scene has foreign participants");
            leases.put(lease.id(), lease.withStatus(SceneLeaseStatus.CLOSED));
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).physicalIntents(intents).fencedRecovery(recovery)
                .sceneLeases(leases).resourceSites(state.resourceSites().replace(site.abandonHarvest(job)))
                .actorExecutions(ActorExecutionComposition.LIFECYCLE.retire(state.actorExecutions(), job.workerId(),
                        ActorActivityKind.FIELD_HARVEST, job.id())));
    }
}
