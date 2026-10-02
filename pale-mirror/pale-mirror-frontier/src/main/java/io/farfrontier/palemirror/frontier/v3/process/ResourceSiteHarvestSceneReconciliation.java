package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.LinkedHashMap;

/** Field-owned recovery of an inspected safe batch/checkpoint, never permission to replay a crop or mint stock. */
public final class ResourceSiteHarvestSceneReconciliation {
    private ResourceSiteHarvestSceneReconciliation() { }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject,
                                            ResourceSiteHarvestSceneReconciled receipt) {
        var lifecycle = state.resourceSites().site(receipt.siteId());
        var job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow(
                        () -> new IllegalArgumentException("harvest reconciliation has no active job"));
        var lease = state.sceneLeases().get(receipt.leaseId());
        var cycle = state.resourceSites().cycle(receipt.siteId());
        if (!subject.equals(receipt.siteId()) || lifecycle.phase() != ResourceSitePhase.HARVESTING
                || !job.id().equals(receipt.jobId()) || job.progress().hasPendingCrop()
                || job.progress().completedCropSlots() != cycle.accountedCount()
                || job.navigationBlock().isPresent() || job.progress().complete() && !cycle.cycleAccounted()
                || state.physicalIntents().get(job.intentId()) == null
                || state.physicalIntents().get(job.intentId()).status()
                    != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED
                    && state.physicalIntents().get(job.intentId()).status()
                    != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING
                || state.resourceSites().hasPendingWorldChange(subject) || !cycle.pendingPlayerBreaks().isEmpty()
                || lease == null || lease.status() != SceneLeaseStatus.CONFLICT
                || lease.revision() != receipt.leaseRevision() || lease.members().size() != 1
                || !FrontierSceneBehaviors.isResourceSiteHarvest(lease)
                || !FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(subject)
                || !FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id())
                || !lease.members().getFirst().actorId().equals(job.workerId()))
            throw new IllegalArgumentException("harvest reconciliation lacks its exact isolated safe job checkpoint");
        var actor = state.actorLocations().get(job.workerId());
        var hand = (PhysicalStackAddress.ActorHand) receipt.observedHand().address();
        var account = state.inventory().fungibleResources().accounts().get(job.actorAccountId());
        var bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(job.actorAccountId())).toList();
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || !hand.actorId().equals(job.workerId()) || !hand.entityId().equals(lease.members().getFirst().entityId())
                || !receipt.observedHand().itemKind().equals("minecraft:wheat")
                || receipt.observedHand().quantity() != job.carriedYieldQuantity(cycle.harvestedCount())
                || account == null || !account.custody().equals(new ResourceCustody.Actor(job.workerId()))
                || !account.claimQuantities().isEmpty() || bindings.size() != 1
                || !bindings.getFirst().address().equals(hand) || bindings.getFirst().authorityEpoch() != lease.revision()
                || !bindings.getFirst().lotQuantities().equals(account.lotQuantities())
                || bindings.getFirst().quantity() != receipt.observedHand().quantity())
            throw new IllegalArgumentException("harvest reconciliation cannot replace its exact bound worker batch");
        SubjectId recoveryId = FrontierSceneLeaseStateSupport.bodyRecoveryBindingId(job.workerId());
        var recovery = state.fencedRecovery().current().get(recoveryId);
        if (recovery == null || recovery.asset() != FencedRecoveryAsset.BODY
                || !recovery.ownerId().equals(FrontierSceneLeaseStateSupport.recoveryOwner(lease))
                || recovery.ownerRevision() != lease.revision() || recovery.authorityEpoch() != receipt.bodyEpoch()
                || recovery.phase() != FencedRecoveryPhase.AMBIGUOUS)
            throw new IllegalArgumentException("harvest reconciliation has a stale or foreign recovery epoch");
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), receipt.observedBody().supportingSurface().support());
        var actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(job.workerId(), actor.withBody(receipt.observedBody()));
        var positions = new LinkedHashMap<>(lease.memberPositions());
        positions.put(job.workerId(), receipt.observedBody());
        var leases = new LinkedHashMap<>(state.sceneLeases());
        leases.put(lease.id(), lease.withMemberPositions(positions).withStatus(SceneLeaseStatus.HOT));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).sceneLeases(leases)
                .fencedRecovery(state.fencedRecovery().inspectedRunning(recoveryId, receipt.bodyEpoch())));
    }
}
