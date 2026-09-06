package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSiteHarvestSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestCropPrepared;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgressed;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestHotTraversalAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneLeaseHandoff;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
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
        Optional<SceneLease> active = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED && lease.status() != SceneLeaseStatus.CONFLICT)
                .min(Comparator.comparing(SceneLease::id));
        if (active.isPresent()) { execute(level, runtime, state, active.orElseThrow()); return true; }
        Optional<FrontierResourceSiteHarvestSceneSupport.Candidate> candidate = FrontierV3SceneExecutor.firstDemandedCandidate(
                level, FrontierResourceSiteHarvestSceneSupport.candidates(state), FrontierResourceSiteHarvestSceneSupport.Candidate::cropSlot);
        if (candidate.isEmpty()) return false;
        FrontierResourceSiteHarvestSceneSupport.Candidate work = candidate.orElseThrow();
        SceneLease lease = lease(runtime, work);
        if (FrontierSceneAdmission.available(state, work.memberPositions().keySet())) {
            submit(runtime, "resource-site-harvest-scene-prepare", lease.id().value(), new ResourceSiteHarvestSceneLeasePrepared(lease));
        } else {
            handoff(level, runtime, state, lease);
        }
        return true;
    }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierResourceSiteHarvestSceneSupport.Candidate candidate) {
        var checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        SceneLeaseId id = new SceneLeaseId("lease:site-harvest-" + candidate.jobId().value().substring("job:site-harvest-".length())
                + "-crop-" + candidate.cropSlotIndex() + "-r" + checkpoint.revision().value());
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted()
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(checkpoint.worldId(), id, actor))).toList();
        return SceneLease.forCause(id, checkpoint.worldId(), new ResourceSiteHarvestSceneCause(candidate.jobId()), candidate.cropSlot(),
                checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED, members,
                SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), Set.of(), Optional.empty());
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.requireRegisteredSceneTurn(lease);
        switch (lease.status()) {
            case PREPARED -> materialize(level, runtime, state, lease);
            case HOT -> work(level, runtime, state, lease);
            case DRAINING -> FrontierV3SceneExecutor.release(level, runtime, lease);
            case UNKNOWN_AFTER_RESTART -> FrontierV3SceneExecutor.reclaim(level, runtime, state, lease);
            case CONFLICT, CLOSED -> { }
        }
    }

    private static void materialize(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.BodyMaterialization result = FrontierV3SceneExecutor.materializeBodies(level, state, lease);
        if (result == FrontierV3SceneExecutor.BodyMaterialization.CONFLICT) { conflict(level, runtime, lease, "prepared-body-conflict"); return; }
        if (result != FrontierV3SceneExecutor.BodyMaterialization.COMPLETE) return;
        // addFreshEntity publishes into Minecraft's UUID index after this executor turn.  A
        // same-tick getEntity check is therefore not an absence proof and used to convert a
        // normal admission race into a false field conflict.  PREPARED establishes the exact
        // owned body; HOT observes it on the following tick before issuing the first retained
        // work motion.
        FrontierV3SceneExecutor.rememberObserved(level, runtime, state, lease);
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_hot", lease,
                submit(runtime, "resource-site-harvest-scene-hot", lease.id().value(), new io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
    }

    /**
     * Gives field work the already observed farmer body.  This is a durable ownership transfer,
     * not an ambient drain followed by a replacement body at a historical grid cell.
     */
    private static void handoff(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        List<SceneMemberPosition> captures = new ArrayList<>();
        for (SceneMember member : lease.members()) {
            AmbientActorLease ambient = state.ambientLeases().get(member.actorId());
            if (ambient == null || ambient.status() == io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED) continue;
            if (ambient.status() != io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.HOT) return;
            Entity entity = level.getEntity(member.entityId());
            if (!(entity instanceof Mob body) || !body.isAlive() || !FrontierV3AmbientActorExecutor.owned(body, member.actorId(), false)) return;
            captures.add(new SceneMemberPosition(member.actorId(), new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(
                    body.getBlockX(), body.getBlockY(), body.getBlockZ()), new FixedScalar(Math.round(body.getHealth() * FixedScalar.SCALE))));
        }
        if (!captures.isEmpty()) {
            java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, io.farfrontier.palemirror.frontier.v3.model.BodyPosition> positions =
                    new java.util.LinkedHashMap<>(lease.memberPositions());
            captures.forEach(capture -> positions.put(capture.actorId(), capture.body()));
            SceneLease captured = lease.withMemberPositions(positions).withAmbientHandoff(captures.stream()
                    .map(SceneMemberPosition::actorId).collect(java.util.stream.Collectors.toSet()));
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_handoff", captured,
                    submit(runtime, "resource-site-harvest-scene-handoff", lease.id().value(),
                            new ResourceSiteHarvestSceneLeaseHandoff(captured, captures)));
        }
    }

    private static void work(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                             FrontierWorldState state, SceneLease lease) {
        ResourceSiteHarvestSceneCause cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
        var job = FrontierResourceSiteHarvestSceneSupport.require(state, cause);
        var site = FrontierResourceSitePlan.compile(state.bootstrap()).get(job.siteId());
        if (site == null || job.progress().complete()) {
            submit(runtime, "resource-site-harvest-scene-draining", lease.id().value(),
                    new io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        io.farfrontier.palemirror.frontier.v3.model.BlockPosition crop = site.cropSlots().get(job.progress().nextCropSlotIndex());
        FrontierV3SceneDemand.Snapshot demand = FrontierV3SceneExecutor.demandSnapshot(level, crop);
        if (FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demand,
                FrontierV3SceneExecutor.playerWithinSafeRadius(level, lease))) {
            submit(runtime, "resource-site-harvest-scene-draining", lease.id().value(), new io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        if (!demand.active()) return;
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        if (!(entity instanceof Mob worker) || !worker.isAlive() || !FrontierV3SceneExecutor.recognizes(runtime, worker)) {
            conflict(level, runtime, lease, "hot-worker-unavailable"); return;
        }
        if (job.hasNextTraversalStep()) {
            var target = job.nextTraversalSurface();
            if (!atTraversalSurface(worker, job.traversal().linearCorridorSurfaces().get(job.traversalCursor()))) {
                // The physical edge can arrive one entity tick before the canonical command
                // records its cursor.  Accept only that exact next node; a different body
                // position remains a player/world conflict and is never direct-line repaired.
                if (atTraversalSurface(worker, target)) {
                    submit(runtime, "resource-site-harvest-traversal-advanced", lease.id().value(),
                            checkpoint(job, lease, worker));
                } else conflict(level, runtime, lease, "field-work-cursor-body-mismatch");
                return;
            }
            if (atTraversalSurface(worker, target)) {
                submit(runtime, "resource-site-harvest-traversal-advanced", lease.id().value(),
                        checkpoint(job, lease, worker));
            } else {
                moveToTraversalSurface(level, worker, target);
            }
            return;
        }
        if (!job.atCurrentCropStation() || !atTraversalSurface(worker, job.traversal().linearCorridorSurfaces().get(job.traversalCursor()))) {
            conflict(level, runtime, lease, "field-work-station-mismatch"); return;
        }
        if (job.progress().hasPendingCrop()) {
            if (!observePreparedCrop(level, state, job, crop)) {
                conflict(level, runtime, lease, "pending-crop-postcondition"); return;
            }
            var result = submit(runtime, "resource-site-harvest-progress", lease.id().value(),
                    new ResourceSiteHarvestProgressed(job.id(), job.progress().completedCropSlots() + 1));
            if (result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted
                    && job.progress().completedCropSlots() + 1 == io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS) {
                submit(runtime, "resource-site-harvest-scene-draining", lease.id().value(),
                        new io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            }
            return;
        }
        if (level.getGameTime() % 10L != 0L) return;
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        submit(runtime, "resource-site-harvest-crop-prepared", lease.id().value(),
                new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex()));
    }

    private static boolean atTraversalSurface(Mob worker, io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor surface) {
        // HOT causality is a grid checkpoint, not a near-enough movement hint.  The local
        // navigator may approach continuously, but only the observed feet cell exactly above
        // the retained support can advance the canonical cursor.
        return worker.getBlockX() == surface.x() && worker.getBlockY() == surface.y() + 1 && worker.getBlockZ() == surface.z();
    }

    private static void moveToTraversalSurface(ServerLevel level, Mob worker, io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor surface) {
        FrontierV3ControlledMobMotion.moveToward(level, worker,
                new Vec3(surface.x() + 0.5D, surface.y() + 1.0D, surface.z() + 0.5D));
    }

    /** Emits the complete observed causal checkpoint; the reducer rejects any stale or foreign tuple. */
    private static ResourceSiteHarvestHotTraversalAdvanced checkpoint(io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob job,
                                                                       SceneLease lease, Mob worker) {
        return new ResourceSiteHarvestHotTraversalAdvanced(job.id(), lease.id(), job.workerId(),
                new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(worker.getBlockX(), worker.getBlockY(), worker.getBlockZ()),
                job.traversalCursor() + 1);
    }

    private static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, String reason) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_conflict:" + reason, lease,
                submit(runtime, "resource-site-harvest-scene-conflict", lease.id().value(),
                        new io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT)));
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

    /** Reconciles exactly the durable pending crop. A crash after AIR is recoverable by this postcondition. */
    private static boolean observePreparedCrop(ServerLevel level, FrontierWorldState state,
                                               io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob acceptedJob,
                                               io.farfrontier.palemirror.frontier.v3.model.BlockPosition cropSlot) {
        var lifecycle = state.resourceSites().sites().get(acceptedJob.siteId()); if (lifecycle == null) return false;
        var current = lifecycle.activeWork().filter(io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob.class::cast).filter(job -> job.id().equals(acceptedJob.id())).orElse(null);
        var site = FrontierResourceSitePlan.compile(state.bootstrap()).get(acceptedJob.siteId());
        if (current == null || site == null || !current.progress().equals(acceptedJob.progress()) || !current.progress().hasPendingCrop()
                || !site.cropSlots().get(current.progress().pendingCropSlotIndex()).equals(cropSlot)) return false;
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level); FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || claim.stage() != io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle.MATURE_STAGE
                || (claim.harvestedCropSlots() != current.progress().completedCropSlots()
                && claim.harvestedCropSlots() != current.progress().completedCropSlots() + 1)) return false;
        BlockPos position = new BlockPos(cropSlot.x(), cropSlot.y(), cropSlot.z());
        boolean observed = FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, current.progress().completedCropSlots() + 1);
        if (!observed && level.getBlockState(position).equals(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,
                io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle.MATURE_STAGE))) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
            observed = FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, current.progress().completedCropSlots() + 1);
        }
        if (!observed) return false;
        if (claim.harvestedCropSlots() == current.progress().completedCropSlots()) {
            ledger.harvestOne(site.id(), current.progress().completedCropSlots() + 1);
        }
        return true;
    }

    private static io.farfrontier.palemirror.frontier.v3.api.CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                                    String phase, String id, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return FrontierV3CommandSubmission.submit(runtime, phase, id, payload);
    }
}
