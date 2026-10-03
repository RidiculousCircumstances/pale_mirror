package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.LinkedHashMap;

/** Field-owned recovery of an inspected safe batch/checkpoint, never permission to replay a crop or mint stock. */
public final class ResourceSiteHarvestSceneReconciliation {
    private ResourceSiteHarvestSceneReconciliation() { }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject,
                                            ResourceSiteHarvestSceneReconciled receipt) {
        var lifecycle = state.resourceSites().site(receipt.siteId());
        var job = lifecycle.harvestJob(receipt.jobId()).orElseThrow(
                        () -> new IllegalArgumentException("harvest reconciliation has no active job"));
        var lease = state.sceneLeases().get(receipt.leaseId());
        var cycle = state.resourceSites().cycle(receipt.siteId());
        if (!subject.equals(receipt.siteId()) || lifecycle.phase() != ResourceSitePhase.HARVESTING
                || !job.id().equals(receipt.jobId())
                 || job.navigationBlock().isPresent()
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
        if (job.progress().hasPendingCrop()) {
            var cell = cycle.layout().cells().get(job.progress().pendingCropSlotIndex()).id();
            if (cycle.expectedWorkOutcome(cell) != ResourceFieldCycle.WorkOutcome.HARVESTED
                    || job.progress().work().filter(WorkProgress::complete).isEmpty()
                    || !ResourceSiteHarvestGoal.current(state, job).arrivedAt(receipt.observedBody().supportingSurface()))
                throw new IllegalArgumentException("prepared crop recovery lacks its untouched mature work checkpoint");
        }
        var hand = (PhysicalStackAddress.ActorHand) receipt.observedHand().address();
        var account = state.inventory().fungibleResources().accounts().get(job.actorAccountId());
        var bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(job.actorAccountId())).toList();
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || !hand.actorId().equals(job.workerId()) || !hand.entityId().equals(lease.members().getFirst().entityId())
                || !receipt.observedHand().itemKind().equals("minecraft:wheat")
                || receipt.observedHand().quantity() != ResourceSiteHarvestCargo.quantity(state, job)
                || account == null || !account.custody().equals(new ResourceCustody.Actor(job.workerId()))
                || !account.claimQuantities().isEmpty() || bindings.size() > 1
                || bindings.isEmpty() && job.progress().hasPendingCrop()
                || !bindings.isEmpty() && (!bindings.getFirst().address().equals(hand)
                    || bindings.getFirst().authorityEpoch() != lease.revision()
                    || !bindings.getFirst().lotQuantities().equals(account.lotQuantities())
                    || bindings.getFirst().quantity() != receipt.observedHand().quantity()))
            throw new IllegalArgumentException("harvest reconciliation cannot replace its exact bound worker batch");
        SubjectId recoveryId = ActorBodyId.recoveryBindingId(job.workerId());
        var recovery = state.fencedRecovery().current().get(recoveryId);
        if (recovery == null || recovery.asset() != FencedRecoveryAsset.BODY
                || !recovery.ownerId().equals(job.workerId())
                || recovery.ownerRevision() != 0L || recovery.authorityEpoch() != receipt.recoveryEpoch())
            throw new IllegalArgumentException("harvest reconciliation has a stale or foreign recovery epoch");
        var inspected = ActorBodyAuthority.inspectedPresent(state, new ActorBodyId(job.workerId(), receipt.recoveryEpoch()));
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), receipt.observedBody().supportingSurface().support());
        var actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(job.workerId(), actor.withBody(receipt.observedBody()));
        var positions = new LinkedHashMap<>(lease.memberPositions());
        positions.put(job.workerId(), receipt.observedBody());
        var leases = new LinkedHashMap<>(state.sceneLeases());
        leases.put(lease.id(), lease.withMemberPositions(positions).withStatus(SceneLeaseStatus.HOT));
        var restored = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).sceneLeases(leases)
                .fencedRecovery(inspected));
        // Admission can fail before the already-witnessed carried part gets its hand binding.
        // Use the ordinary exact projection reducer in this SAME recovery event: no second
        // resource issuer, no partially resumed scene and no replacement of an existing binding.
        return bindings.isEmpty() ? ResourceSiteHarvestProcess.reduceHandProjected(restored, subject,
                new ResourceSiteHarvestHandProjected(subject, job.id(), job.actorAccountId(), lease.id(),
                        lease.revision(), receipt.observedHand())) : restored;
    }
}
