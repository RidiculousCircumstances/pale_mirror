package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSiteHarvestSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldWorkAccessObserved;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestCropPrepared;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgressed;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSegmentRenewed;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestBlockedCellSkipped;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestImmatureCellSkipped;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestNavigationBlock;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestRouteBlocked;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestTargetRetargeted;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestRouteCleared;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestHotGoalArrived;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestHotTransitObserved;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestGoal;
import io.farfrontier.palemirror.frontier.v3.model.ServiceAccessCoordinator;
import io.farfrontier.palemirror.frontier.v3.model.ResidentActivityCoordinator;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneLeaseHandoff;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestHandProjected;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestRetargeting;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Naturally loaded field work by the exact farmer retained by the harvest job. */
final class FrontierV3ResourceSiteHarvestSceneExecutor {
    private FrontierV3ResourceSiteHarvestSceneExecutor() { }

    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        if (FrontierV3ResourceSiteHarvestReconciliation.reconcileOne(level, runtime, state)) return true;
        return FrontierV3SceneTurnScheduler.run(runtime, state, io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind.RESOURCE_SITE_HARVEST,
                lease -> execute(level, runtime, state, lease), () -> admit(level, runtime, state));
    }

    private static boolean admit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        // The canonical job may have crossed several COLD traversal edges before the first
        // natural arrival.  Its field is still non-interactable until the projection owner has
        // made that exact mature/partial crop state current in the loaded world.
        Optional<FrontierResourceSiteHarvestSceneSupport.Candidate> candidate = FrontierV3SceneDemand.nextDemandedCandidate(
                level, runtime, io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind.RESOURCE_SITE_HARVEST, FrontierResourceSiteHarvestSceneSupport.candidates(state).stream()
                        .filter(value -> !ResidentActivityCoordinator.shouldYieldAtOwnerCheckpoint(state, value.workerId(),
                                runtime.canonicalState().orElseThrow().instant().ticks()))
                        .filter(value -> fieldPresentationCurrent(level, state, value))
                        .filter(value -> !FrontierV3HarvestSceneStandingAdmission.obstructedBodyFreeColumn(level,
                                FrontierV3AmbientActorExecutor.entityId(state, value.workerId()),
                                BodyPosition.above(new io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor(
                                        value.memberPositions().get(value.workerId()))))).toList(),
                FrontierResourceSiteHarvestSceneSupport.Candidate::cropSlot, FrontierResourceSiteHarvestSceneSupport.Candidate::jobId);
        if (candidate.isEmpty()) return false;
        FrontierResourceSiteHarvestSceneSupport.Candidate work = candidate.orElseThrow();
        SceneLease lease = lease(runtime, work);
        if (FrontierSceneAdmission.available(state, work.memberPositions().keySet())) {
            submitBound(runtime, "resource-site-harvest-scene-prepare", lease.id().value(), new ResourceSiteHarvestSceneLeasePrepared(lease),
                    binding(runtime, state.resourceSites().site(work.siteId()).harvestJob(work.jobId()).orElseThrow()));
        } else {
            handoff(level, runtime, state, lease, binding(runtime, state.resourceSites().site(work.siteId()).harvestJob(work.jobId()).orElseThrow()));
        }
        return true;
    }

    private static boolean fieldPresentationCurrent(ServerLevel level, FrontierWorldState state,
                                                    FrontierResourceSiteHarvestSceneSupport.Candidate candidate) {
        var lifecycle = state.resourceSites().sites().get(candidate.siteId());
        var site = state.resourceSite(candidate.siteId());
        if (lifecycle == null || site == null || state.resourceSites().hasPendingWorldChange(candidate.siteId()))
            return false;
        var job = lifecycle.harvestJob(candidate.jobId()).orElse(null);
        if (job == null || !job.id().equals(candidate.jobId())) return false;
        // Once every crop result is canonical, this scene only follows the retained
        // worker route. It must not force the remote field to load for a depot-side
        // observer to see that exact worker at the current station.
        if (job.navigationBlock().isPresent() || job.progress().complete()
                || job.returningForBatch())
            return level.hasChunkAt(new BlockPos(candidate.cropSlot().x(),
                candidate.cropSlot().y(), candidate.cropSlot().z()));
        var cycle = state.resourceSites().cycle(site.id());
        var siteClaim = FrontierV3ResourceSiteLedger.get(level).siteClaim(site.id());
        if (siteClaim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim cellClaim) {
            if (!(cellClaim.claim() instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                    || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                    || !owner.witness().matchesCycle(cycle)) return false;
            var cellId = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
            var retained = owner.witness().cell(cellId);
            if (retained.pending().isPresent() || cycle.pendingPlayerBreaks().containsKey(cellId)) return false;
            var canonical = cycle.cell(cellId);
            if (canonical.soil() == ResourceFieldCycle.Soil.OBSTRUCTED
                    || canonical.crop() == ResourceFieldCycle.Crop.OBSTRUCTED) {
                var reading = FrontierV3ResourceFieldObservation.read(level, cycle.layout().requireCell(cellId),
                        "harvest-scene-blocked-cell");
                return retained.foreign().isPresent()
                        && reading instanceof FrontierV3ResourceFieldObservation.Foreign foreign
                        && retained.foreign().orElseThrow().observedSoil().equals(foreign.incident().observedSoil())
                        && retained.foreign().orElseThrow().observedCrop().equals(foreign.incident().observedCrop());
            }
            if (retained.foreign().isPresent()
                    || !retained.committed().equals(io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface.Condition.of(cycle.cell(cellId))))
                return false;
            return FrontierV3ResourceFieldObservation.observe(level, cycle, owner.witness(), cellId,
                    "harvest-scene-admission").disposition() == FrontierV3ResourceFieldObservation.Disposition.CURRENT;
        }
        if (!FrontierV3ResourceSiteExecutor.loaded(level, site)) return false;
        FrontierV3ResourceSiteLedger.Claim claim = siteClaim instanceof FrontierV3ResourceSiteLedger.LegacySiteClaim legacy
                ? legacy.claim() : null;
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || claim.stage() != io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle.MATURE_STAGE) return false;
        return claim.harvestedCropSlots() == job.progress().completedCropSlots()
                && FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, job.progress().completedCropSlots());
    }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierResourceSiteHarvestSceneSupport.Candidate candidate) {
        var checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        FrontierWorldState current = runtime.decodedState().orElseThrow(() -> new IllegalStateException("v3 runtime has no decoded field state"));
        SceneLeaseId id = new SceneLeaseId("lease:site-harvest-" + candidate.jobId().value().substring("job:site-harvest-".length())
                + "-crop-" + candidate.cropSlotIndex() + "-r" + checkpoint.revision().value());
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted()
                // A harvester's body survives the semantic receipt at the field station.  The
                // next growth epoch may later assign that same resident again, so field scenes
                // use the actor-stable physical identity rather than a new lease-derived UUID.
                // Lease ownership remains fully typed by the scene tags and revision.
                .map(actor -> new SceneMember(actor, FrontierV3AmbientActorExecutor.entityId(current, actor))).toList();
        return SceneLease.forCause(id, checkpoint.worldId(), new ResourceSiteHarvestSceneCause(candidate.siteId(), candidate.jobId()), candidate.cropSlot(),
                checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED, members, Set.of(), Optional.empty());
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.requireRegisteredSceneTurn(state, lease);
        if ((lease.status() == SceneLeaseStatus.PREPARED || lease.status() == SceneLeaseStatus.HOT)
                && state.resourceSites().hasPendingWorldChange(
                FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId())) return;
        switch (lease.status()) {
            case PREPARED -> materialize(level, runtime, state, lease);
            case HOT -> work(level, runtime, state, lease);
            case DRAINING -> {
                if (FrontierV3SceneReleaseReadiness.awaitingEntityStorage(level, state, lease)) return;
                // Completion/release has no residual tending duty.  Keep the exact body at
                // its observed station, but retire a prior crop gesture before that released
                // body becomes the visible predecessor of a later growth-epoch assignment.
                for (SceneMember member : lease.members()) {
                    Entity entity = level.getEntity(member.entityId());
                    if (entity instanceof Mob worker) FrontierV3ControlledMobMotion.clearStationWorkGesture(level, worker);
                }
                FrontierV3SceneExecutor.release(level, runtime, lease, releaseBinding(runtime, state, lease));
            }
            case UNKNOWN_AFTER_RESTART -> {
                // Restart changes the lease status, not the fact that body admission never
                // started. Inspect the naturally loaded exact column before generic reclaim
                // can mistake a solid feet cell for a missing farmer.
                if (!FrontierV3HarvestSceneStandingAdmission.abortObstructedBodyFreePreparation(level, runtime, state, lease))
                    FrontierV3SceneExecutor.reclaim(level, runtime, state, lease);
            }
            case CONFLICT, CLOSED -> { }
        }
    }

    private static void materialize(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.BodyMaterialization result = FrontierV3SceneExecutor.materializeBodies(level, state, lease, FrontierV3ActorCarrierComposition.InventoryEntry.RESOURCE_HARVEST);
        if (result == FrontierV3SceneExecutor.BodyMaterialization.CONFLICT) { conflict(level, runtime, lease, "prepared-body-conflict"); return; }
        if (result != FrontierV3SceneExecutor.BodyMaterialization.COMPLETE) {
            // A PREPARED scene has no physical authority yet. If its exact retained body cell
            // has become solid while no body exists, waiting here would also suspend the only
            // COLD continuation forever. Revoke just this body-free preparation; COLD can
            // advance from its retained position through the known route on the next turn.
            FrontierV3HarvestSceneStandingAdmission.abortObstructedBodyFreePreparation(level, runtime, state, lease);
            return;
        }
        // addFreshEntity publishes into Minecraft's UUID index after this executor turn.  A
        // same-tick getEntity check is therefore not an absence proof and used to convert a
        // normal admission race into a false field conflict.  PREPARED establishes the exact
        // owned body; HOT observes it on the following tick before issuing the first retained
        // work motion.

        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_hot", lease,
                submit(runtime, "resource-site-harvest-scene-hot", lease.id().value(), new io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
    }

    /**
     * Gives field work the already observed farmer body.  This is a durable ownership transfer,
     * not an ambient drain followed by a replacement body at a historical grid cell.
     */
    private static void handoff(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease, ScheduledAction binding) {
        List<SceneMemberPosition> captures = new ArrayList<>();
        for (SceneMember member : lease.members()) {
            AmbientActorLease ambient = state.ambientLeases().get(member.actorId());
            if (ambient == null || ambient.status() == io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED) continue;
            if (ambient.status() != io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.HOT) return;
            Entity entity = level.getEntity(member.entityId());
            if (!(entity instanceof Mob body) || !body.isAlive() || !FrontierV3AmbientActorExecutor.owned(body, member.actorId(), false)) return;
            var supported = FrontierV3SupportedBodyCapture.observe(level, body);
            if (supported.isEmpty()) return;
            captures.add(new SceneMemberPosition(member.actorId(), supported.orElseThrow(),
                    new FixedScalar(Math.round(body.getHealth() * FixedScalar.SCALE))));
        }
        if (!captures.isEmpty()) {
            SceneLease captured = lease.withAmbientHandoff(captures.stream()
                    .map(SceneMemberPosition::actorId).collect(java.util.stream.Collectors.toSet()));
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_handoff", captured,
                    submitBound(runtime, "resource-site-harvest-scene-handoff", lease.id().value(),
                            new ResourceSiteHarvestSceneLeaseHandoff(captured, captures), binding));
        }
    }

    private static void work(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                             FrontierWorldState state, SceneLease lease) {
        ResourceSiteHarvestSceneCause cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
        var job = FrontierResourceSiteHarvestSceneSupport.require(state, cause);
        var site = state.resourceSite(job.siteId());
        if (site == null) { conflict(level, runtime, lease, "field-work-site-unavailable"); return; }
        var physicalClaim = FrontierV3ResourceSiteLedger.get(level).siteClaim(site.id());
        if (physicalClaim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim) {
            var acknowledged = FrontierV3ResourceFieldWorkExecutor.acknowledgePrevious(level,
                    runtime.canonicalState().orElseThrow(), lease, job);
            if (acknowledged == FrontierV3ResourceFieldWorkExecutor.Disposition.PENDING) return;
            if (acknowledged == FrontierV3ResourceFieldWorkExecutor.Disposition.CONFLICT) {
                conflict(level, runtime, lease, "field-work-accepted-cell-postcondition"); return;
            }
        }
        if (projectColdCarriedHand(level, runtime, state, lease, job)) return;
        if (job.navigationBlock().isEmpty()
                && ResourceSiteHarvestGoal.actorAtDepot(state, job)) {
            // The delivery effect owns the returned hand, chest and atomic terminal event.
            // Draining here would strand canonical wheat on a released physical worker.
            return;
        }
        var canonicalWorker = state.actorLocations().get(job.workerId());
        if (canonicalWorker == null) { conflict(level, runtime, lease, "hot-worker-canonical-location-missing"); return; }
        var canonicalBody = canonicalWorker.body();
        io.farfrontier.palemirror.frontier.v3.model.BlockPosition demandAnchor = job.navigationBlock().isPresent()
                || job.progress().complete() || job.returningForBatch()
                ? new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(canonicalBody.x(), canonicalBody.y(), canonicalBody.z())
                : site.cropSlots().get(job.progress().nextCropSlotIndex());
        FrontierV3SceneDemand.Snapshot demand = FrontierV3SceneExecutor.demandSnapshot(level, demandAnchor);
        boolean playerWithinSafeRadius = FrontierV3SceneExecutor.playerWithinSafeRadius(level, lease);
        // Before an irreversible crop has been prepared, ordinary no-demand behavior remains
        // a release/idle boundary.  A pending crop below is deliberately the one exception.
        // This exact field worker must enter DRAINING while it is still naturally loaded: the
        // shared 200-tick scene grace outlives ordinary client chunk retention and used to make
        // the later carrier fence observe an absent body.  This is not force loading or a new
        // demand authority; it is the same present body's bounded hand-off on demand loss.
        if (!demand.active() && !job.progress().hasPendingCrop()
                && immediateColdRelease(demand.active(), playerWithinSafeRadius, job.progress().hasPendingCrop())) {
            if (FrontierV3ResourceFieldLabourExecutor.pause(level, runtime, state, lease, job)) return;
            beginImmediateColdRelease(level, runtime, state, lease);
            return;
        }
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        if (!(entity instanceof Mob worker) || !worker.isAlive() || !FrontierV3SceneExecutor.recognizes(runtime, worker)) {
            conflict(level, runtime, lease, "hot-worker-unavailable"); return;
        }
        var actuation = workActuation(state, runtime, job, worker);
        if (!actuation.current(worker)) return;
        var supportedExit = FrontierV3SupportedBodyCapture.observe(level, worker);
        if (supportedExit.isPresent() && ServiceAccessCoordinator.witnessedHarvestExit(
                state, job, lease.id(), supportedExit.orElseThrow())) {
            var binding = FrontierV3TraversalScheduleGate.binding(runtime.executionView().orElseThrow(), job);
            if (binding.isEmpty()) return;
            ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
            var exit = new ResourceSiteHarvestHotTransitObserved(job.id(), lease.id(), job.workerId(),
                    goal.layoutRevision(), goal.nextWorkSlot(), goal.kind(), supportedExit.orElseThrow());
            ResourceSiteHarvestProcess.reduceHotTransitObserved(state, job.siteId(), exit);
            submitBound(runtime, "resource-site-harvest-access-cleared", lease.id().value(), exit, binding.orElseThrow());
            return;
        }
        // The work owner fences unfinished physical effects, not personal cargo.
        // At a supported actual position the resident may yield without reaching the next goal.
        if (ResidentActivityCoordinator.shouldYieldAtOwnerCheckpoint(state, job.workerId(),
                        runtime.canonicalState().orElseThrow().instant().ticks())
                && FrontierV3SupportedBodyCapture.observe(level, worker).isPresent()) {
            FrontierV3GoalNavigation.stop(worker, actuation);
            if (FrontierV3ResourceFieldLabourExecutor.pause(level, runtime, state, lease, job)) return;
            beginImmediateColdRelease(level, runtime, state, lease);
            return;
        }
        ResourceSiteHarvestGoal serviceGoal = ResourceSiteHarvestGoal.current(state, job);
        if (serviceGoal.kind() == ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE
                && !ServiceAccessCoordinator.depotAvailableForHarvest(state, job)) {
            FrontierV3GoalNavigation.stop(worker, actuation);
            return;
        }
        if (!job.progress().complete() && !job.returningForBatch() && !job.progress().hasPendingCrop()
                && !state.resourceSites().hasPendingWorldChange(job.siteId())) {
            ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
            var cell = cycle.layout().cells().get(job.progress().nextCropSlotIndex());
            var head = cell.workstation().support().offset(0, 2, 0);
            var access = FrontierV3ResourceFieldWorkAccessExecutor.read(level, cell);
            if (access.isPresent() && !cycle.pendingPlayerBreaks().containsKey(cell.id())) {
                boolean blockedAccess = access.orElseThrow().blocked();
                if (blockedAccess != cycle.cell(cell.id()).workAccessBlocked()) {
                    var observed = new ResourceFieldWorkAccessObserved(job.siteId(), cycle.epoch(),
                            cycle.layout().revision(), cell.id(), head, blockedAccess,
                            access.orElseThrow().blockId(), Optional.of(lease.id()));
                    var accepted = submit(runtime, "resource-site-harvest-work-access", lease.id().value(), observed);
                    FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_work_access", lease, accepted);
                    return;
                }
            }
        }
        if (!job.progress().complete()
                && !job.returningForBatch() && !job.progress().hasPendingCrop()) {
            var cycle = state.resourceSites().cycle(job.siteId());
            var blockedCell = cycle.layout().cells().get(job.progress().nextCropSlotIndex());
            if (cycle.expectedWorkOutcome(blockedCell.id()) == ResourceFieldCycle.WorkOutcome.SKIPPED_IMMATURE) {
                if (!FrontierV3ResourceFieldExclusionObservation.immatureCellCurrent(level, state, site, blockedCell.id())) return;
                FrontierV3GoalNavigation.stop(worker, actuation);
                if (retainInterruptedTransit(level, runtime, state, lease, job, worker)) return;
                var binding = FrontierV3TraversalScheduleGate.binding(runtime.executionView().orElseThrow(), job);
                if (binding.isEmpty()) return;
                var action = binding.orElseThrow();
                var skipped = new ResourceSiteHarvestImmatureCellSkipped(job.siteId(), job.id(), job.workerId(),
                        cycle.layout().revision(), List.of(blockedCell.id()), action.id(),
                        action.dueAt().ticks(), Optional.of(lease.id()));
                ResourceSiteHarvestProcess.reduceCellSkipped(state, job.siteId(), skipped);
                var accepted = submitBound(runtime, "resource-site-harvest-immature-cell-skipped", lease.id().value(), skipped, action);
                FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_immature_cell_skipped", lease, accepted);
                return;
            }
            if (cycle.expectedWorkOutcome(blockedCell.id()) == ResourceFieldCycle.WorkOutcome.SKIPPED_BLOCKED) {
                if (state.resourceSites().hasPendingWorldChange(job.siteId())) return;
                // One witnessed unavailable target cannot hold the whole area task.
                var blockedIds = List.of(blockedCell.id());
                if (!blockedCellsPhysicallyCurrent(level, state, site, blockedIds)) return;
                if (retainInterruptedTransit(level, runtime, state, lease, job, worker)) return;
                var binding = FrontierV3TraversalScheduleGate.binding(runtime.executionView().orElseThrow(), job);
                if (binding.isEmpty()) return;
                var action = binding.orElseThrow();
                var skipped = new ResourceSiteHarvestBlockedCellSkipped(job.siteId(), job.id(), job.workerId(),
                        cycle.layout().revision(), blockedIds, action.id(),
                        action.dueAt().ticks(), Optional.of(lease.id()));
                ResourceSiteHarvestProcess.reduceBlockedCellSkipped(state, job.siteId(), skipped);
                var accepted = submitBound(runtime, "resource-site-harvest-blocked-cell-skipped", lease.id().value(), skipped, action);
                FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_blocked_cell_skipped", lease, accepted);
                return;
            }
        }
        if (job.navigationBlock().isPresent()) {
            var blocked = job.navigationBlock().orElseThrow();
            var goal = blocked.target();
            if (blocked.reason() == ResourceSiteHarvestNavigationBlock.Reason.CONTINUATION_UNAVAILABLE) {
                FrontierV3GoalNavigation.stop(worker, actuation);
                if (state.resourceSites().hasPendingWorldChange(job.siteId())) return;
                var cycle = state.resourceSites().cycle(job.siteId());
                var prefix = ResourceSiteHarvestProcess.blockedPrefix(cycle, job.progress().nextCropSlotIndex());
                if (ResourceSiteHarvestProcess.blockedPrefixMeetsPendingPlayerBreak(
                        state, job, prefix)) return;
                if (!prefix.isEmpty()
                        && goal.equals(ResourceSiteHarvestProcess.blockedPrefixContinuationGoal(state, job, prefix).representative())) {
                    try {
                        ResourceSiteHarvestProcess.blockedPrefixContinuation(state, job.siteId(), job, prefix);
                    } catch (ResourceSiteHarvestProcess.ContinuationUnavailable stillUnavailable) {
                        return;
                    }
                }
                var binding = FrontierV3TraversalScheduleGate.binding(runtime.executionView().orElseThrow(), job);
                if (binding.isEmpty()) return;
                var action = binding.orElseThrow();
                var cleared = new ResourceSiteHarvestRouteCleared(job.siteId(), job.id(), job.workerId(), blocked,
                        lease.id(), action.id(), action.dueAt().ticks());
                ResourceSiteHarvestProcess.reduceRouteCleared(state, job.siteId(), cleared);
                var accepted = submitBound(runtime, "resource-site-harvest-continuation-cleared", lease.id().value(), cleared, action);
                FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_goal_cleared", lease, accepted);
                return;
            }
            if (state.resourceSites().hasPendingWorldChange(job.siteId())) return;
            if ((blocked.reason() == ResourceSiteHarvestNavigationBlock.Reason.PATH_UNAVAILABLE
                    || blocked.reason() == ResourceSiteHarvestNavigationBlock.Reason.PATH_STALLED)
                    && tryRetargetWorkTarget(level, runtime, state, lease, job, worker)) return;
            ResourceSiteHarvestGoal currentGoal = ResourceSiteHarvestGoal.current(state, job);
            var outcome = FrontierV3GoalNavigation.pursue(level, worker,
                    hotGoal(state, job, currentGoal), actuation);
            if (outcome.status() == FrontierV3GoalNavigation.Status.ARRIVED)
                acceptObservedSemanticGoal(level, runtime, state, lease, job, worker);
            return;
        }
        if (acceptObservedSemanticGoal(level, runtime, state, lease, job, worker)) return;
        ResourceSiteHarvestGoal semanticGoal = ResourceSiteHarvestGoal.current(state, job);
        var atGoal = semanticGoal.legalStations().stream()
                .filter(station -> FrontierV3SemanticMovement.arrived(level, worker, station)).toList();
        if (atGoal.isEmpty()) {
            var motion = FrontierV3GoalNavigation.pursue(level, worker,
                    hotGoal(state, job, semanticGoal), actuation);
            if (motion.status() == FrontierV3GoalNavigation.Status.BLOCKED) {
                // A finite physical failure needs a job-local typed disposition before
                // COLD can resume.  The old route's waypoint must not be that target.
                if (retainInterruptedTransit(level, runtime, state, lease, job, worker)) return;
                holdSemanticGoal(level, runtime, state, lease, job, worker, motion.blockReason().orElseThrow());
            } else if (motion.status() == FrontierV3GoalNavigation.Status.AMBIGUOUS) {
                conflict(level, runtime, lease, "field-work-goal-ambiguous");
            }
            return;
        }
        if (semanticGoal.kind() == ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE) return;
        var station = semanticGoal.representative();
        var stationState = FrontierV3SemanticMovement.at(level, worker, station);
        if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, job)
                || stationState != io.farfrontier.palemirror.frontier.v3.model.SemanticTraversalArrival.Disposition.ARRIVED) {
            // A semantic arrival is the only work gate. An old corridor predecessor cannot
            // authorize a second movement policy or fabricate arrival at this crop.
            routeConflict(level, runtime, lease, site, station, stationState);
            return;
        }
        var intent = state.physicalIntents().get(job.intentId());
        if (intent == null || intent.status() == PhysicalIntentStatus.CONFIRMED
                || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            conflict(level, runtime, lease, "field-work-intent-unavailable"); return;
        }
        // The semantic crop transition may deliberately span a few durable turns (intent
        // prepare, observed crop postcondition, then receipt).  It is still one declared
        // crop-work phase at the exact station, never permission for a scheduler-paced frozen
        // body. Preserve the observed station and family-owned work gesture through those
        // turns without admitting another local locomotion target while the continuation waits.
        // Arrival is the existing observed checkpoint boundary. Publish its station phase
        // before the later due work turn, so the client cannot keep the preceding travel cue
        // across a stationary crop dwell.
        FrontierV3ControlledMobMotion.showHarvestStationDuty(level, worker);
        // Arrival already holds a supported position. Work may animate here, never
        // install a second unfenced local target follower while its continuation waits.
        if (!job.progress().hasPendingCrop()
                && FrontierV3ResourceFieldLabourExecutor.advance(level, runtime, state, lease, job)) return;
        var dueBinding = FrontierV3TraversalScheduleGate.dueBinding(runtime.executionView().orElseThrow(), job);
        if (dueBinding.isEmpty()) {
            if (job.progress().hasPendingCrop()) {
                conflict(level, runtime, lease, "field-work-pending-crop-continuation-not-due"); return;
            }
            return;
        }
        if (job.progress().hasPendingCrop()) {
            if (intent.status() != PhysicalIntentStatus.RUNNING) {
                conflict(level, runtime, lease, "field-work-pending-intent-state-" + intent.status().name().toLowerCase(java.util.Locale.ROOT)); return;
            }
            ResourceFieldCycle.WorkOutcome outcome;
            Optional<ResourceSiteHarvestProgressed.HandObservation> observedHand;
            if (physicalClaim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim) {
                var work = FrontierV3ResourceFieldWorkExecutor.advance(level, runtime, state, lease, job, worker);
                if (work.disposition() == FrontierV3ResourceFieldWorkExecutor.Disposition.PENDING) return;
                if (work.disposition() == FrontierV3ResourceFieldWorkExecutor.Disposition.CONFLICT) {
                    conflict(level, runtime, lease, "pending-cell-hand-postcondition:" + work.failure()); return;
                }
                outcome = work.outcome();
                observedHand = work.hand();
            } else {
                // The previous stage/prefix writer has no paired crop/offhand witness.
                // It cannot submit a trusted HOT work receipt under the new custody owner.
                conflict(level, runtime, lease, "legacy-harvest-owner-retired"); return;
            }
            var field = state.resourceSites().cycle(job.siteId());
            var cellId = field.layout().cells().get(job.progress().nextCropSlotIndex()).id();
            var admittedContinuation = dueBinding.orElseThrow();
            submitBound(runtime, "resource-site-harvest-progress", lease.id().value(),
                    new ResourceSiteHarvestProgressed(job.siteId(), field.epoch(), job.id(), job.progress().completedCropSlots() + 1,
                            field.layout().revision(), cellId, job.target().generation(), outcome,
                            admittedContinuation.id(), admittedContinuation.dueAt().ticks(), observedHand),
                    admittedContinuation);
            // Accepted final crop work still leaves the retained field-exit corridor.
            // The next scene turn follows it before DRAINING can release this body.
            return;
        }
        // CropPrepared is a durable non-replayable boundary.  The earlier no-demand branch
        // releases only an unprepared cursor; a pending crop reaches its observed receipt here
        // before any later COLD hand-off can be considered.
        if (!demand.active()) return;
        // The family scheduler already bounds and fairly rotates scene attempts. A global
        // tick-modulo filter aliases with that rotation and can permanently starve a worker.
        // Completed labour and the exact due continuation, not a tick phase, admit this effect.
        // RUNNING means a non-replayable crop effect may already have begun.  Reaching a
        // station is still reversible retained travel: leaving demand there must release a
        // PREPARED intent so COLD/restart can continue it normally.  Cross the durable effect
        // boundary only on the exact turn that will prepare this crop, then observe/commit it
        // on the next turn under RUNNING.
        if (intent.status() == PhysicalIntentStatus.PREPARED) {
            // Start the one bounded visible gesture at the durable work boundary, before the
            // later crop postcondition can expose the next retained edge.  A periodic cue from
            // the tending loop could begin just as the following traversal was admitted and
            // read as harvesting while walking.
            FrontierV3ControlledMobMotion.showStationWorkGesture(level, worker);
            submit(runtime, "resource-site-harvest-running", lease.id().value(),
                    new io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition(job.intentId(), PhysicalIntentStatus.RUNNING, Optional.empty()));
            return;
        }
        if (intent.status() != PhysicalIntentStatus.RUNNING) {
            conflict(level, runtime, lease, "field-work-intent-state-" + intent.status().name().toLowerCase(java.util.Locale.ROOT)); return;
        }
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        submitBound(runtime, "resource-site-harvest-crop-prepared", lease.id().value(),
                new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex(), job.target().generation()), dueBinding.orElseThrow());
    }

    /** A real goal-station observation takes precedence over stale intermediate route checkpoints. */
    private static boolean acceptObservedSemanticGoal(ServerLevel level,
                                                      FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                      FrontierWorldState state, SceneLease lease,
                                                      io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob job,
                                                      Mob worker) {
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        List<io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor> observed = goal.legalStations().stream()
                .filter(station -> FrontierV3SemanticMovement.arrived(level, worker, station)).toList();
        if (observed.isEmpty()) return false;
        var actuation = workActuation(state, runtime, job, worker);
        if (!FrontierV3GoalNavigation.stop(worker, actuation)) return false;
        if (observed.size() != 1) {
            conflict(level, runtime, lease, "field-work-goal-arrival-ambiguous");
            return true;
        }
        boolean gateAlreadyRetained = goal.kind() == ResourceSiteHarvestGoal.Kind.WORK_CELL
                ? ResourceSiteHarvestGoal.actorAtWorkCell(state, job) : ResourceSiteHarvestGoal.actorAtDepot(state, job);
        if (gateAlreadyRetained && job.navigationBlock().isEmpty() && observed.getFirst().standingBody().equals(
                state.actorLocations().get(job.workerId()).body())) return false;
        var binding = FrontierV3TraversalScheduleGate.binding(runtime.executionView().orElseThrow(), job);
        if (binding.isEmpty()) {
            // No route or synthetic crop pose: wait at the actual observed station.
            return true;
        }
        var arrived = new ResourceSiteHarvestHotGoalArrived(job.id(), lease.id(), job.workerId(),
                goal.layoutRevision(), goal.nextWorkSlot(), goal.kind(), observed.getFirst().standingBody());
        submitBound(runtime, "resource-site-harvest-goal-arrived", lease.id().value(), arrived, binding.orElseThrow());
        return true;
    }

    private static void holdSemanticGoal(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                         FrontierWorldState state, SceneLease lease, ResourceSiteHarvestJob job,
                                         Mob worker, FrontierV3GoalNavigation.BlockReason reason) {
        if ((reason == FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE
                || reason == FrontierV3GoalNavigation.BlockReason.PATH_STALLED)
                && tryRetargetWorkTarget(level, runtime, state, lease, job, worker)) return;
        var binding = FrontierV3TraversalScheduleGate.binding(runtime.executionView().orElseThrow(), job);
        if (binding.isEmpty()) return;
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        ResourceSiteHarvestNavigationBlock.Reason cause = switch (reason) {
            case PATH_UNAVAILABLE -> ResourceSiteHarvestNavigationBlock.Reason.PATH_UNAVAILABLE;
            case PATH_STALLED -> ResourceSiteHarvestNavigationBlock.Reason.PATH_STALLED;
            case TARGET_CHUNK_UNLOADED -> ResourceSiteHarvestNavigationBlock.Reason.TARGET_CHUNK_UNLOADED;
            case OFF_CONTRACT -> ResourceSiteHarvestNavigationBlock.Reason.OFF_CONTRACT;
            case UNSUPPORTED_CAPABILITY -> ResourceSiteHarvestNavigationBlock.Reason.UNSUPPORTED_CAPABILITY;
            case UNSUPPORTED_MEDIUM -> ResourceSiteHarvestNavigationBlock.Reason.TARGET_MEDIUM;
            case SEARCH_BUDGET_EXHAUSTED -> ResourceSiteHarvestNavigationBlock.Reason.SEARCH_BUDGET_EXHAUSTED;
        };
        var block = new ResourceSiteHarvestNavigationBlock(goal.representative(), goal.layoutRevision(), cause);
        var action = binding.orElseThrow();
        var blocked = new ResourceSiteHarvestRouteBlocked(job.siteId(), job.id(), job.workerId(), block,
                lease.id(), action.id(), action.dueAt().ticks());
        ResourceSiteHarvestProcess.reduceRouteBlocked(state, job.siteId(), blocked);
        submitBound(runtime, "resource-site-harvest-goal-blocked", lease.id().value(), blocked, action);
    }

    private static boolean tryRetargetWorkTarget(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                  FrontierWorldState state, SceneLease lease,
                                                  ResourceSiteHarvestJob job, Mob worker) {
        if (job.progress().complete() || job.returningForBatch() || job.progress().hasPendingCrop()
                || state.resourceSites().hasPendingWorldChange(job.siteId())) return false;
        ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
        var alternate = cycle.reachableWorkSlotAfter(job.progress().nextCropSlotIndex(), index -> {
                var cell = cycle.layout().cells().get(index);
                var access = FrontierV3ResourceFieldWorkAccessExecutor.read(level, cell);
                if (access.isEmpty() || access.orElseThrow().blocked()
                        || !state.resourceSites().site(job.siteId()).targetAvailable(index, job.id())) return false;
                ResourceSiteHarvestJob candidate = job.retargetTo(index).bindTarget(cycle);
                FrontierWorldState candidateState = state.withResourceSites(state.resourceSites().replace(
                        state.resourceSites().site(job.siteId()).retargetHarvestCell(job, index, cycle)));
                ResourceSiteHarvestGoal candidateGoal = ResourceSiteHarvestGoal.current(candidateState, candidate);
                return FrontierV3GoalNavigation.canReach(level, worker, hotGoal(candidateState, candidate, candidateGoal));
        });
        if (alternate.isEmpty()) return false;
        var binding = FrontierV3TraversalScheduleGate.binding(runtime.executionView().orElseThrow(), job);
        if (binding.isEmpty()) return false;
        var action = binding.orElseThrow();
        var retargeted = new ResourceSiteHarvestTargetRetargeted(job.siteId(), job.id(), job.workerId(),
                cycle.layout().revision(), job.progress().nextCropSlotIndex(), alternate.getAsInt(),
                action.id(), action.dueAt().ticks(), Optional.of(lease.id()));
        ResourceSiteHarvestRetargeting.reduceTargetRetargeted(state, job.siteId(), retargeted);
        var accepted = submitBound(runtime, "resource-site-harvest-target-retargeted", lease.id().value(),
                retargeted, action);
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_target_retargeted", lease, accepted);
        return true;
    }

    private static FrontierV3GoalNavigation.Goal hotGoal(
            FrontierWorldState state, ResourceSiteHarvestJob job, ResourceSiteHarvestGoal goal) {
        // Known geometry is a route hint. Only the shared physical provider may
        // authorize a different loaded-world path to this unchanged semantic goal.
        try {
            return FrontierV3GoalNavigation.Goal.routed(goal.movementOrder(),
                    io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestKnownNavigation.path(state, job),
                    state.bootstrap().bounds());
        } catch (io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestKnownNavigation.KnowledgeUnavailable gap) {
            return FrontierV3GoalNavigation.Goal.routed(goal.movementOrder(), java.util.List.of(), state.bootstrap().bounds());
        }
    }

    /** One owned physical checkpoint at an exceptional interruption, never per route support. */
    private static boolean retainInterruptedTransit(ServerLevel level,
                                                    FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                    FrontierWorldState state, SceneLease lease,
                                                    ResourceSiteHarvestJob job, Mob worker) {
        var supported = FrontierV3SupportedBodyCapture.observe(level, worker);
        if (supported.isEmpty()) return true;
        var body = supported.orElseThrow();
        if (body.equals(state.actorLocations().get(job.workerId()).body())) return false;
        var binding = FrontierV3TraversalScheduleGate.binding(runtime.executionView().orElseThrow(), job);
        if (binding.isEmpty()) return true;
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        var observed = new ResourceSiteHarvestHotTransitObserved(job.id(), lease.id(), job.workerId(),
                goal.layoutRevision(), goal.nextWorkSlot(), goal.kind(), body);
        ResourceSiteHarvestProcess.reduceHotTransitObserved(state, job.siteId(), observed);
        submitBound(runtime, "resource-site-harvest-transit-interrupted", lease.id().value(), observed,
                binding.orElseThrow());
        return true;
    }

    /** The same loaded farmer receives an already-issued COLD part before any next crop or depot effect. */
    private static boolean projectColdCarriedHand(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                   FrontierWorldState state, SceneLease lease,
                                                   io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob job) {
        var resources = state.inventory().fungibleResources();
        var account = resources.accounts().get(job.actorAccountId());
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        var pending = ledger.fieldHandProjection(job.siteId());
        // The durable physical transaction slot is shared; a sibling's exact pending
        // projection is temporary serialization, not a foreign hand or a worker conflict.
        if (pending != null && !pending.jobId().equals(job.id())) return true;
        if (account == null) {
            if (pending != null) { conflict(level, runtime, lease, "cold-carried-hand-lot-lost"); return true; }
            return false;
        }
        if (pending == null && resources.bindings().values().stream()
                .anyMatch(binding -> binding.accountId().equals(job.actorAccountId()))) return false;
        var cycle = state.resourceSites().cycle(job.siteId());
        var part = io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestCargo.part(state, job).orElse(null);
        if (part == null || job.progress().hasPendingCrop()
                || !account.custody().equals(new io.farfrontier.palemirror.frontier.v3.model.ResourceCustody.Actor(job.workerId()))
                || !account.lotQuantities().equals(java.util.Map.of(part.id(), part.quantity()))
                || !account.claimQuantities().isEmpty()) {
            conflict(level, runtime, lease, "cold-carried-hand-contradiction"); return true;
        }
        var expected = new FrontierV3ResourceSiteHandProjectionWitness(job.siteId(), job.id(), job.actorAccountId(),
                part.id(), job.workerId(), lease.members().getFirst().entityId(), lease.id(), lease.revision(),
                cycle.epoch(), job.progress().completedCropSlots(), part.quantity());
        if (pending != null && !pending.equals(expected)) {
            conflict(level, runtime, lease, "cold-carried-hand-witness-foreign"); return true;
        }
        var hand = FrontierV3ActorHandObservation.observe(level, state, lease, job);
        if (hand.disposition() == FrontierV3ActorHandObservation.Disposition.UNAVAILABLE) return true;
        var binding = resources.bindings().values().stream()
                .filter(value -> value.accountId().equals(job.actorAccountId())).toList();
        if (!binding.isEmpty()) {
            if (pending == null) return false;
            if (binding.size() != 1 || binding.getFirst().authorityEpoch() != lease.revision()
                    || hand.disposition() != FrontierV3ActorHandObservation.Disposition.WHEAT
                    || hand.stack().orElseThrow().quantity() != part.quantity()
                    || !binding.getFirst().address().equals(hand.stack().orElseThrow().address())) {
                conflict(level, runtime, lease, "cold-carried-hand-bound-witness-diverged"); return true;
            }
            ledger.retireFieldHandProjection(pending); ledger.persist(level);
            return true;
        }
        if (pending == null) {
            Entity existingBody = level.getEntity(lease.members().getFirst().entityId());
            boolean retainedCargo = existingBody instanceof Mob retained
                    && FrontierV3ActorCarryProjection.witnessed(state, job.workerId(), retained);
            if (hand.disposition() != FrontierV3ActorHandObservation.Disposition.EMPTY && !retainedCargo) {
                conflict(level, runtime, lease, "cold-carried-hand-unwitnessed-item"); return true;
            }
            // The already-accounted COLD wheat belongs to the actor account. A
            // first-visibility field writer is a separate physical owner and may
            // still be initializing while this worker appears by the depot.
            ledger.beginFieldHandProjection(expected); ledger.persist(level);
            if (retainedCargo) return true; // next turn binds the witnessed stack; never issues another one
            Entity entity = level.getEntity(lease.members().getFirst().entityId());
            if (!(entity instanceof Mob worker) || !FrontierV3SceneExecutor.owned(entity, state, lease, lease.members().getFirst())) {
                conflict(level, runtime, lease, "cold-carried-hand-body-foreign"); return true;
            }
            if (!FrontierV3VillagerHandMutation.setOffhand(worker,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT, part.quantity())))
                conflict(level, runtime, lease, "cold-carried-hand-write-rejected");
            return true;
        }
        if (hand.disposition() == FrontierV3ActorHandObservation.Disposition.EMPTY) {
            // The before-effect witness may survive a crash before the entity write, or the
            // hand may have been cleared after the write. Kind/count cannot distinguish the
            // two, so never issue a second stack from this pending witness.
            conflict(level, runtime, lease, "cold-carried-hand-after-restart-ambiguous");
            return true;
        }
        if (hand.disposition() != FrontierV3ActorHandObservation.Disposition.WHEAT
                || hand.stack().orElseThrow().quantity() != part.quantity()) {
            conflict(level, runtime, lease, "cold-carried-hand-physical-foreign"); return true;
        }
        var observed = new ResourceSiteHarvestHandProjected(job.siteId(), job.id(), job.actorAccountId(),
                lease.id(), lease.revision(), hand.stack().orElseThrow());
        submit(runtime, "resource-site-harvest-cold-hand-projected", lease.id().value(), observed);
        // The canonical WAL now owns this observed hand. A crash before this SavedData
        // retirement is harmless: the exact pending witness is checked on re-entry.
        ledger.retireFieldHandProjection(pending); ledger.persist(level);
        return true;
    }


    private static boolean blockedCellsPhysicallyCurrent(ServerLevel level, FrontierWorldState state,
                                                        io.farfrontier.palemirror.frontier.v3.model.ResourceSite site,
                                                        List<io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout.CellId> ids) {
        if (state.resourceSites().hasPendingWorldChange(site.id())) return false;
        var cycle = state.resourceSites().cycle(site.id());
        var siteClaim = FrontierV3ResourceSiteLedger.get(level).siteClaim(site.id());
        if (!(siteClaim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim claimed)
                || !(claimed.claim() instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || !owner.witness().matchesCycle(cycle)) return false;
        for (var id : ids) {
            var cell = cycle.layout().requireCell(id);
            if (cycle.pendingPlayerBreaks().containsKey(id)) return false;
            if (!level.hasChunkAt(new BlockPos(cell.crop().x(), cell.crop().y(), cell.crop().z()))) return false;
            var retained = owner.witness().cell(id);
            if (retained.pending().isPresent()) return false;
            if (cycle.cell(id).workAccessBlocked()) {
                var head = cell.workstation().support().offset(0, 2, 0);
                BlockPos physicalHead = new BlockPos(head.x(), head.y(), head.z());
                if (!level.hasChunkAt(physicalHead)
                        || level.getBlockState(physicalHead).getCollisionShape(level, physicalHead).isEmpty()) return false;
                if (retained.foreign().isEmpty()) {
                    var reading = FrontierV3ResourceFieldObservation.read(level, cell, "harvest-scene-work-access");
                    if (!(reading instanceof FrontierV3ResourceFieldObservation.Owned owned)
                            || !owned.condition().equals(retained.committed())) return false;
                }
                continue;
            }
            if (retained.foreign().isEmpty()) return false;
            var reading = FrontierV3ResourceFieldObservation.read(level, cell, "harvest-scene-blocked-cell");
            if (!(reading instanceof FrontierV3ResourceFieldObservation.Foreign foreign)
                    || !retained.foreign().orElseThrow().observedSoil().equals(foreign.incident().observedSoil())
                    || !retained.foreign().orElseThrow().observedCrop().equals(foreign.incident().observedCrop())) return false;
        }
        return !ids.isEmpty();
    }

    private static void routeConflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                      SceneLease lease, io.farfrontier.palemirror.frontier.v3.model.ResourceSite site,
                                      io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor target,
                                      io.farfrontier.palemirror.frontier.v3.model.SemanticTraversalArrival.Disposition disposition) {
        io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer producer = switch (disposition) {
            case BLOCKED_SUPPORT -> io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.FIELD_ROUTE_BLOCKED_SUPPORT;
            case BLOCKED_CLEARANCE -> io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.FIELD_ROUTE_BLOCKED_CLEARANCE;
            case BLOCKED_MEDIUM -> io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.FIELD_ROUTE_BLOCKED_MEDIUM;
            case OFF_CONTRACT -> io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.FIELD_ROUTE_OFF_CONTRACT;
            case ARRIVED, IN_PROGRESS -> throw new IllegalArgumentException("route conflict needs a blocked semantic disposition");
        };
        var checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        boolean accepted = FrontierV3ResourceSiteConflictExecutor.recordConflict(level, runtime, FrontierV3ResourceSiteLedger.get(level),
                site, target.support(), producer, new io.farfrontier.palemirror.frontier.v3.api.CommandId(
                        "executor:resource-site-field-route-" + producer.wireTag() + "-r" + checkpoint.revision().value()
                                + "-p" + new net.minecraft.core.BlockPos(target.x(), target.y(), target.z()).asLong()));
        if (!accepted) conflict(level, runtime, lease, "field-work-route-" + FrontierV3SemanticMovement.detail(disposition));
    }

    private static FrontierV3ActorActuation workActuation(FrontierWorldState state,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ResourceSiteHarvestJob job, Mob worker) {
        var execution = state.actorExecutions().current(
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST).get(job.workerId());
        if (execution == null || !execution.activityOwnerId().equals(job.id()))
            throw new IllegalArgumentException("field command lost its exact admitted execution");
        return FrontierV3ActorActuation.capture(state, worker, execution, runtime::decodedState);
    }


    static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, String reason) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_conflict:" + reason, lease,
                submit(runtime, "resource-site-harvest-scene-conflict", lease.id().value(),
                        new io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT)));
    }

    /**
     * A missing or foreign body at the one lawful release edge is neither a player break nor a
     * terminal adapter failure.  First persist the resource-site incident; only an accepted
     * canonical disposition may then stop the paired scene.
     */
    static void releaseCustodyConflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                       FrontierWorldState state, SceneLease lease, String reason) {
        io.farfrontier.palemirror.PaleMirrorMod.LOGGER.warn(
                "Frontier v3 harvest release custody rejected lease={} revision={} reason={}",
                lease.id().value(), lease.revision(), reason);
        carrierFenceConflict(level, runtime, state, lease,
                FrontierV3AmbientActorExecutor.SceneCarrierFenceResult.CARRIER_CONFLICT);
    }

    private static void carrierFenceConflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                             FrontierWorldState state, SceneLease lease,
                                             FrontierV3AmbientActorExecutor.SceneCarrierFenceResult fence) {
        ResourceSiteHarvestSceneCause cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
        var site = FrontierResourceSiteHarvestSceneSupport.site(state, cause);
        var witness = FrontierResourceSiteHarvestSceneSupport.isTerminalReceiptRelease(state, cause)
                ? site.cropSlots().getLast()
                : carrierFenceWitness(site, FrontierResourceSiteHarvestSceneSupport.require(state, cause).progress());
        boolean accepted = FrontierV3ResourceSiteConflictExecutor.recordConflict(level, runtime, FrontierV3ResourceSiteLedger.get(level), site,
                witness,
                io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.SCENE_CARRIER_FENCE,
                carrierFenceCommandId(lease.id(), lease.revision(), fence));
        if (accepted) conflict(level, runtime, lease, "cold-carrier-" + fence.name().toLowerCase(java.util.Locale.ROOT));
    }

    static io.farfrontier.palemirror.frontier.v3.api.CommandId carrierFenceCommandId(
            io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId sceneId, long sceneRevision,
            FrontierV3AmbientActorExecutor.SceneCarrierFenceResult fence) {
        return new io.farfrontier.palemirror.frontier.v3.api.CommandId("executor:resource-site-carrier-fence-"
                + fence.name().toLowerCase(java.util.Locale.ROOT) + "-"
                + java.util.UUID.nameUUIDFromBytes(sceneId.value().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "-r" + sceneRevision);
    }

    /** Terminal failure belongs to the last completed station, never a nonexistent next crop. */
    static io.farfrontier.palemirror.frontier.v3.model.BlockPosition carrierFenceWitness(
            io.farfrontier.palemirror.frontier.v3.model.ResourceSite site,
            io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgress progress) {
        return site.cropSlots().get(progress.complete()
                ? progress.lastCompletedCropSlotIndex() : progress.nextCropSlotIndex());
    }

    /** Transitions and fences in the same server turn, before normal chunk expiry can hide the exact body. */
    private static void beginImmediateColdRelease(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                  FrontierWorldState state, SceneLease lease) {
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "resource-site-harvest-scene-draining",
                lease.id().value(), new io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
        if (!(result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) return;
        SceneLease draining = runtime.decodedState().map(current -> current.sceneLeases().get(lease.id()))
                .filter(current -> current.status() == SceneLeaseStatus.DRAINING).orElse(null);
        if (draining != null) execute(level, runtime, state, draining);
    }

    /** Package-visible pure release policy regression seam. */
    static boolean immediateColdRelease(boolean demandActive, boolean playerWithinSafeRadius, boolean pendingCrop) {
        return !demandActive && !playerWithinSafeRadius && !pendingCrop;
    }

    /**
     * Registered behavior policy for the whole retained field-work topology.
     *
     * <p>The approach corridor still consists mostly of ordinary floor columns.  At its field
     * stations alone, a mature crop may occupy the exact feet cell.  Treating this as a
     * crop-only provider would strand a newly prepared farmer before the first field station;
     * treating it as a generic provider would silently admit crop feet.  The registered harvest
     * policy therefore composes the two explicit physical predicates without asking generic
     * materialization code to know the process cause.</p>
     */
    static BlockPos harvestStandingPosition(ServerLevel level, BlockPos floor) {
        BlockPos ordinary = FrontierV3StandingPosition.aboveExactFloor(level, new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(
                floor.getX(), floor.getY(), floor.getZ()));
        return ordinary != null ? ordinary : FrontierV3StandingPosition.aboveExactHarvestFieldFloor(level, floor);
    }

    private static io.farfrontier.palemirror.frontier.v3.api.CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                                    String phase, String id, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return FrontierV3CommandSubmission.submit(runtime, phase, id, payload);
    }

    private static io.farfrontier.palemirror.frontier.v3.api.CommandResult submitBound(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                                         String phase, String id,
                                                                                         io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload,
                                                                                         ScheduledAction binding) {
        return FrontierV3CommandSubmission.submitBound(runtime, phase, id, payload, binding);
    }

    private static ScheduledAction binding(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                           io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob job) {
        return FrontierV3TraversalScheduleGate.binding(runtime.executionView().orElseThrow(), job).orElseThrow(
                () -> new IllegalArgumentException("field scene has no exact retained job continuation"));
    }

    /**
     * A normal HOT release must carry the sole retained COLD continuation.  An accepted player
     * conflict is different: that same transaction has already cancelled its continuation and
     * moved the field into CONFLICT, so release is only the durable body-exit receipt.  Asking
     * for a now-retired binding would quarantine the server after the authoritative break.
     */
    static Optional<ScheduledAction> releaseBinding(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                    FrontierWorldState state, SceneLease lease) {
        return releaseBinding(runtime.executionView().orElseThrow(), state, lease);
    }

    static Optional<ScheduledAction> releaseBinding(io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView checkpoint,
                                                    FrontierWorldState state, SceneLease lease) {
        ResourceSiteHarvestSceneCause cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
        if (FrontierResourceSiteHarvestSceneSupport.isTerminalReceiptRelease(state, cause)) {
            // The terminal output receipt already consumed its sole continuation and created
            // the next growth action.  Rebinding this body-exit receipt to the retired job
            // would strand the exact worker in DRAINING at the retained return station.
            return Optional.empty();
        }
        var job = FrontierResourceSiteHarvestSceneSupport.require(state, cause);
        return state.resourceSites().site(job.siteId()).phase() == io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase.HARVESTING
                ? Optional.of(FrontierV3TraversalScheduleGate.binding(checkpoint, job).orElseThrow(
                        () -> new IllegalArgumentException("field release has no exact retained job continuation")))
                : Optional.empty();
    }

    static Optional<ScheduledAction> releaseBinding(io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView checkpoint,
                                                    io.farfrontier.palemirror.frontier.v3.api.SubjectId siteId,
                                                    io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase phase) {
        return FrontierV3ResourceSiteHarvestReleaseBinding.forPhase(checkpoint, siteId, phase);
    }

}
