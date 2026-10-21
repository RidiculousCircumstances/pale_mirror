package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.List;
import java.util.Map;

/** Exact stock binding shared by live projection and retired observation. It cannot issue yield. */
public final class HarvestHandBinding {
    private HarvestHandBinding() { }
    public static FrontierWorldState bind(FrontierWorldState state, ResourceSiteHarvestJob job,
            ResourceSiteHarvestHandProjected projected) {
        if (!job.id().equals(projected.jobId()) || !job.siteId().equals(projected.siteId())
                || !job.actorAccountId().equals(projected.actorAccountId()) || job.progress().hasPendingPhysicalWork())
            throw new IllegalArgumentException("field hand binding lacks its exact settled work boundary");
        return bind(state, job, projected.actorEpoch(), projected.hand());
    }
    private static FrontierWorldState bind(FrontierWorldState state, ResourceSiteHarvestJob job,
            long epoch, FungiblePhysicalObservation.Stack hand) {
        var address = (PhysicalStackAddress.ActorHand) hand.address();
        if (!address.actorId().equals(job.workerId())) throw new IllegalArgumentException("field hand binding has a foreign worker");
        var part = ResourceSiteHarvestCargo.part(state, job).orElseThrow(
                () -> new IllegalArgumentException("field hand projection has no positive COLD-carried part"));
        if (part.quantity() != hand.quantity())
            throw new IllegalArgumentException("field hand projection changes the accounted crop quantity");
        var resources = state.inventory().fungibleResources(); var account = resources.accounts().get(job.actorAccountId());
        if (account == null || !account.custody().equals(new ResourceCustody.Actor(job.workerId()))
                || !account.lotQuantities().equals(Map.of(part.id(), part.quantity())) || !account.claimQuantities().isEmpty()
                || resources.bindings().values().stream().anyMatch(binding -> binding.accountId().equals(account.id())))
            throw new IllegalArgumentException("field hand projection lacks its unbound exact actor part");
        var bindings = FungiblePhysicalObservation.bind(resources, account.id(), epoch, List.of(hand));
        return state.withInventory(state.inventory().withFungibleResources(resources.rebind(account.id(), epoch, bindings)));
    }
    public static FrontierWorldState retired(FrontierWorldState state, SubjectId subject, HarvestRetiredHandObserved observed) {
        var identity = observed.identity();
        ActorBodyAuthority.requireRetiredDeath(state, identity.body()); state.actorExecutions().requireRetained(identity.execution());
        var job = state.resourceSites().site(observed.siteId()).harvestJob(identity.execution().activityOwnerId()).orElseThrow();
        var address = (PhysicalStackAddress.ActorHand) observed.hand().address();
        if (!subject.equals(job.siteId()) || !job.actorAccountId().equals(observed.accountId())
                || job.progress().hasPendingPhysicalWork() || job.progress().completedCropSlots() != observed.completedCells()
                || !address.entityId().equals(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(
                    state.bootstrap().worldId(), job.workerId())))
            throw new IllegalArgumentException("retired hand has a changed field or progress boundary");
        var intent = state.physicalIntents().get(job.intentId());
        if (intent == null || !intent.roles().equals(PhysicalIntentRoleBinding.siteHarvest(job.siteId(), job.id(),
                job.workerId(), job.actorAccountId(), job.depotAccountId())))
            throw new IllegalArgumentException("retired hand lost its exact admitted intent");
        // Retained inventory belongs to the body, including suspended work whose
        // former scene is closed. Binding it does not reacquire that scene.
        return bind(state, job, observed.resourceEpoch(), observed.hand());
    }
}
