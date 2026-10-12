package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The field owner supplies safe checkpoints and route constraints to shared storage discovery. */
public final class HarvestStorageReplacement {
    private HarvestStorageReplacement() { }
    public static boolean retainedAvailable(FrontierWorldState state, ResourceSiteHarvestJob job) {
        if (!job.reservesOutputCapacity()) return true;
        var record = state.inventory().containers().get(job.outputSlot().containerId());
        return record != null && record.storage().map(policy -> policy.role() == ResourceStorageRole.WAREHOUSE
                && policy.filter().accepts("minecraft:wheat")).orElse(false)
                && !ReferenceContainerCustody.blocksCanonicalUse(state, record.id())
                && ResourceSiteHarvestCargo.deliveryCapacityAvailable(state, job);
    }
    private static boolean safe(FrontierWorldState state, ResourceSiteHarvestJob job) {
        var intent = state.physicalIntents().get(job.intentId());
        return !job.progress().hasPendingPhysicalWork() && job.batchSuccessorSlot().isEmpty()
                && !state.resourceSites().harvestMutationPending(job)
                && !FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, job)
                && ActorExecutionCoordinator.coldAvailable(state, job.workerId())
                && state.actorExecutions().owns(job.workerId(), io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST, job.id())
                && !state.actorMovements().containsKey(job.workerId())
                && intent != null && (intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING);
    }
    private static PhysicalIntentId replacementId(ResourceSiteHarvestJob job) {
        return new PhysicalIntentId("intent:site-harvest-storage-" + UUID.nameUUIDFromBytes(
                (job.id().value() + "|" + Math.addExact(job.storageRevision(), 1)).getBytes(StandardCharsets.UTF_8)));
    }
    public static Optional<ResourceSiteHarvestStorageRetargeted> select(FrontierWorldState state, ResourceSiteHarvestJob job) {
        var lifecycle = state.resourceSites().site(job.siteId());
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING || lifecycle.harvestJob(job.id()).filter(job::equals).isEmpty())
            throw new IllegalArgumentException("field storage selection lacks its current exact execution");
        if (retainedAvailable(state, job) || !safe(state, job)) return Optional.empty();
        var site = state.resourceSite(job.siteId()); var cycle = state.resourceSites().cycle(job.siteId());
        var origin = state.actorLocations().get(job.workerId()).supportingSurface();
        int quantity = job.undeliveredYieldQuantity() == 0 ? 64 : job.undeliveredYieldQuantity();
        var replacement = replacementId(job);
        for (var endpoint : ResourceStorageSelection.receivingCandidates(state, site.settlementId(), "minecraft:wheat", quantity, origin,
                Set.of(ResourceStorageRole.WAREHOUSE), Optional.empty())) {
                    if (endpoint.containerId().equals(job.outputSlot().containerId())) continue;
                    var slot = state.firstFreeContainerSlot(endpoint.containerId());
                    if (slot.isEmpty()) continue;
                    var candidate = job.retargetStorage(new InventoryCustody.ContainerSlot(endpoint.containerId(), slot.getAsInt()), replacement);
                    var goal = ResourceSiteHarvestGoal.forSlot(state, candidate, cycle.layout().cells().size());
                    try {
                        var route = KnownPedestrianRouteKnowledge.forField(state, site, cycle,
                                ResourceStorageAccess.at(state, endpoint.containerId()), origin).plannedPath(origin, goal.movementOrder());
                        return Optional.of(new ResourceSiteHarvestStorageRetargeted(job.siteId(), job.id(), job.workerId(), job.storageRevision(),
                                new InventoryCustody.ContainerSlot(endpoint.containerId(), slot.getAsInt()), replacement,
                                state.actorExecutions().current(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST)
                                        .get(job.workerId()), route.size() < 2 ? Optional.empty() : Optional.of(
                                                new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt(route))));
                    } catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
                        if (unavailable.status() != io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH)
                            return Optional.empty();
                    }
        }
        return Optional.empty();
    }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, ResourceSiteHarvestStorageRetargeted event) {
        var lifecycle = state.resourceSites().site(event.siteId()); var job = lifecycle.harvestJob(event.jobId()).orElseThrow();
        if (!subject.equals(event.siteId()) || !job.workerId().equals(event.workerId()) || job.storageRevision() != event.expectedRevision()
                || retainedAvailable(state, job) || !safe(state, job) || lifecycle.phase() != ResourceSitePhase.HARVESTING
                || !event.replacementIntent().equals(replacementId(job))
                || event.destination().containerId().equals(job.outputSlot().containerId())
                || !state.containerSlotAvailable(event.destination())
                || ResourceStorageSelection.receivingCandidates(state, state.resourceSite(job.siteId()).settlementId(), "minecraft:wheat",
                        job.undeliveredYieldQuantity() == 0 ? 64 : job.undeliveredYieldQuantity(),
                        state.actorLocations().get(job.workerId()).supportingSurface(), Set.of(ResourceStorageRole.WAREHOUSE), Optional.empty())
                        .stream().noneMatch(endpoint -> endpoint.containerId().equals(event.destination().containerId())))
            throw new IllegalArgumentException("field storage replacement lost its exact safe selection");
        state.actorExecutions().requireCurrent(event.executionId());
        var nextJob = job.retargetStorage(event.destination(), event.replacementIntent());
        ResourceSiteHarvestKnownNavigation.requireAcceptedStorageReplacement(state, job, nextJob, event.plannedRoute());
        var old = state.physicalIntents().get(job.intentId());
        var next = new PhysicalIntent(nextJob.intentId(), old.kind(), PhysicalIntentStatus.PREPARED, old.causeSubjectId(),
                PhysicalIntentRoleBinding.siteHarvest(job.siteId(), job.id(), job.workerId(), job.actorAccountId(), nextJob.depotAccountId()),
                old.origin(), old.radiusBlocks(), old.postcondition(), old.lifecycleOwner());
        return PhysicalIntentTransitionStorage.supersedeAtCheckpoint(state, old, next, FencedRecoveryAsset.EFFECT,
                "storage-endpoint-replaced", FrontierWorldStateUpdate.begin().resourceSites(
                        state.resourceSites().replace(lifecycle.retargetHarvestStorage(job, event.destination(), event.replacementIntent()))));
    }
}
