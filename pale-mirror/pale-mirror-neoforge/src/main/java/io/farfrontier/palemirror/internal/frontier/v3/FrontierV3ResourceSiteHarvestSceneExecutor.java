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
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
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
        // The canonical job may have crossed several COLD traversal edges before the first
        // natural arrival.  Its field is still non-interactable until the projection owner has
        // made that exact mature/partial crop state current in the loaded world.
        Optional<FrontierResourceSiteHarvestSceneSupport.Candidate> candidate = FrontierV3SceneDemand.firstDemandedCandidate(
                level, FrontierResourceSiteHarvestSceneSupport.candidates(state).stream()
                        .filter(value -> fieldPresentationCurrent(level, state, value)).toList(),
                FrontierResourceSiteHarvestSceneSupport.Candidate::cropSlot);
        if (candidate.isEmpty()) return false;
        FrontierResourceSiteHarvestSceneSupport.Candidate work = candidate.orElseThrow();
        SceneLease lease = lease(runtime, work);
        if (FrontierSceneAdmission.available(state, work.memberPositions().keySet())) {
            submitBound(runtime, "resource-site-harvest-scene-prepare", lease.id().value(), new ResourceSiteHarvestSceneLeasePrepared(lease),
                    binding(runtime, work.jobId()));
        } else {
            handoff(level, runtime, state, lease, binding(runtime, work.jobId()));
        }
        return true;
    }

    private static boolean fieldPresentationCurrent(ServerLevel level, FrontierWorldState state,
                                                    FrontierResourceSiteHarvestSceneSupport.Candidate candidate) {
        var lifecycle = state.resourceSites().sites().get(candidate.siteId());
        var site = FrontierResourceSitePlan.compile(state.bootstrap()).get(candidate.siteId());
        if (lifecycle == null || site == null || !FrontierV3ResourceSiteExecutor.loaded(level, site)) return false;
        FrontierV3ResourceSiteLedger.Claim claim = FrontierV3ResourceSiteLedger.get(level).claim(site.id());
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || claim.stage() != io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle.MATURE_STAGE) return false;
        var job = lifecycle.activeWork().filter(io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob.class::cast).orElse(null);
        return job != null && job.id().equals(candidate.jobId())
                && claim.harvestedCropSlots() == job.progress().completedCropSlots()
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
            case DRAINING -> {
                // Completion/release has no residual tending duty.  Keep the exact body at
                // its observed station, but retire a prior crop gesture before that released
                // body becomes the visible predecessor of a later growth-epoch assignment.
                for (SceneMember member : lease.members()) {
                    Entity entity = level.getEntity(member.entityId());
                    if (entity instanceof Mob worker) FrontierV3ControlledMobMotion.clearStationWorkGesture(level, worker);
                }
                boolean coldRelease = !FrontierV3SceneExecutor.demandExists(level, lease.handoffPosition())
                        && !FrontierV3SceneExecutor.playerWithinSafeRadius(level, lease);
                Entity retained = lease.members().size() == 1 ? level.getEntity(lease.members().getFirst().entityId()) : null;
                FrontierV3AmbientActorExecutor.SceneCarrierFenceResult fence = coldRelease
                        ? FrontierV3AmbientActorExecutor.fenceDrainingSceneBody(level, state, lease, lease.members().getFirst(), retained)
                        : FrontierV3AmbientActorExecutor.SceneCarrierFenceResult.FENCED;
                if (fence != FrontierV3AmbientActorExecutor.SceneCarrierFenceResult.FENCED) {
                    // No carrier means no lawful disappearance.  Preserve the body and mark
                    // only this owning field as ambiguous rather than letting COLD invent a
                    // farmer.  This first accepted incident also owns the scene disposition.
                    carrierFenceConflict(level, runtime, state, lease, fence);
                    return;
                }
                FrontierV3SceneExecutor.release(level, runtime, lease, releaseBinding(runtime, state, lease));
                if (coldRelease && runtime.decodedState().map(current -> current.sceneLeases().get(lease.id()))
                        .filter(current -> current.status() == SceneLeaseStatus.CLOSED).isPresent() && retained instanceof Mob body
                        && FrontierV3AmbientActorExecutor.hasInactiveCarrier(level, state, lease.members().getFirst().actorId())) {
                    // The canonical release accepted the pre-fenced checkpoint.  Removing the
                    // sole naturally loaded custodian now permits COLD without a serialized
                    // duplicate, while return remains bound to this same UUID carrier.
                    body.discard();
                }
            }
            case UNKNOWN_AFTER_RESTART -> FrontierV3SceneExecutor.reclaim(level, runtime, state, lease);
            case CONFLICT, CLOSED -> { }
        }
    }

    private static void materialize(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.BodyMaterialization result = FrontierV3ActorCarrierFactory.materializeSceneBodies(FrontierV3ActorCarrierComposition.InventoryEntry.RESOURCE_HARVEST, level, state, lease);
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
                                FrontierWorldState state, SceneLease lease, ScheduledAction binding) {
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
                    submitBound(runtime, "resource-site-harvest-scene-handoff", lease.id().value(),
                            new ResourceSiteHarvestSceneLeaseHandoff(captured, captures), binding));
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
        boolean playerWithinSafeRadius = FrontierV3SceneExecutor.playerWithinSafeRadius(level, lease);
        // Before an irreversible crop has been prepared, ordinary no-demand behavior remains
        // a release/idle boundary.  A pending crop below is deliberately the one exception.
        // This exact field worker must enter DRAINING while it is still naturally loaded: the
        // shared 200-tick scene grace outlives ordinary client chunk retention and used to make
        // the later carrier fence observe an absent body.  This is not force loading or a new
        // demand authority; it is the same present body's bounded hand-off on demand loss.
        if (!demand.active() && !job.progress().hasPendingCrop()
                && immediateColdRelease(demand.active(), playerWithinSafeRadius, job.progress().hasPendingCrop())) {
            beginImmediateColdRelease(level, runtime, state, lease);
            return;
        }
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        if (!(entity instanceof Mob worker) || !worker.isAlive() || !FrontierV3SceneExecutor.recognizes(runtime, worker)) {
            conflict(level, runtime, lease, "hot-worker-unavailable"); return;
        }
        if (job.hasNextTraversalStep()) {
            var target = job.nextTraversalSurface();
            var current = job.traversal().linearCorridorSurfaces().get(job.traversalCursor());
            // Arrival is a provider-neutral exact support/medium/clearance observation.  A
            // local pose between supports never advances the cursor, and a water/collision
            // defect is a named owner-local disposition rather than a coordinate exception.
            if (FrontierV3SemanticMovement.arrived(level, worker, target)) {
                var binding = FrontierV3TraversalScheduleGate.binding(runtime.checkpointImage().orElseThrow(), job.id());
                if (binding.isPresent()) {
                    submitBound(runtime, "resource-site-harvest-traversal-advanced", lease.id().value(),
                            checkpoint(job, lease, worker), binding.orElseThrow());
                } else keepTraversalPhysicallyActive(level, worker, current, target);
            } else if (FrontierV3SemanticMovement.arrived(level, worker, current)) {
                var targetState = FrontierV3SemanticMovement.target(level, worker, target);
                if (targetState == io.farfrontier.palemirror.frontier.v3.model.SemanticTraversalArrival.Disposition.IN_PROGRESS) {
                    keepTraversalPhysicallyActive(level, worker, current, target);
                } else {
                    routeConflict(level, runtime, lease, site, target, targetState);
                }
            } else if (FrontierV3SemanticMovement.withinRetainedEdgeEnvelope(level, worker, current, target)) {
                // An ordinary collision-safe body can occupy a named intermediate support while
                // traversing exactly one retained edge.  That local latitude is already
                // enumerated by the immutable current/next envelope; it is not a new route,
                // an arrival, or permission to advance the cursor.  Treating it as an
                // off-contract body stranded a mature field at 6/7 despite a valid worker,
                // lease and ready harvest intent.
                keepTraversalPhysicallyActive(level, worker, current, target);
            } else {
                conflict(level, runtime, lease, "field-work-cursor-" + FrontierV3SemanticMovement.detail(
                        FrontierV3SemanticMovement.at(level, worker, current)));
            }
            return;
        }
        var station = job.traversal().linearCorridorSurfaces().get(job.traversalCursor());
        var stationState = FrontierV3SemanticMovement.at(level, worker, station);
        if (!job.atCurrentCropStation() || stationState != io.farfrontier.palemirror.frontier.v3.model.SemanticTraversalArrival.Disposition.ARRIVED) {
            // A checkpoint command records the exact observed body as an integer cell, while
            // vanilla can retain that same body on the immediately preceding support for one
            // more physical turn.  The next scene turn must therefore retain the one already
            // committed edge long enough to bring that body to its current exact station.  It
            // may not select a new support, advance the cursor, or turn this into a radius/Y
            // exception: the only permitted latitude is the immutable predecessor/current
            // edge that just produced this cursor.
            if (job.atCurrentCropStation() && job.traversalCursor() > 0) {
                var previous = job.traversal().linearCorridorSurfaces().get(job.traversalCursor() - 1);
                if (FrontierV3SemanticMovement.withinRetainedEdgeEnvelope(level, worker, previous, station)) {
                    keepTraversalPhysicallyActive(level, worker, previous, station);
                    return;
                }
            }
            // A body outside that one retained edge, or a blocked current station, is a
            // resource-site-owned physical contradiction.  Record the exact local
            // disposition before the scene closes so the board can never keep presenting a
            // generic active harvest with a conflicted lease hidden underneath it.
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
        // body.  Keep the visible, station-bounded tending pose active through those turns as
        // well as while the next continuation is not due.
        // Arrival is the existing observed checkpoint boundary. Publish its station phase
        // before the later due work turn, so the client cannot keep the preceding travel cue
        // across a stationary crop dwell.
        FrontierV3ControlledMobMotion.showHarvestStationDuty(level, worker);
        tendCurrentCrop(level, worker, crop);
        var dueBinding = FrontierV3TraversalScheduleGate.dueBinding(runtime.checkpointImage().orElseThrow(), job.id());
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
            if (!observePreparedCrop(level, state, job, crop)) {
                conflict(level, runtime, lease, "pending-crop-postcondition"); return;
            }
            var result = submitBound(runtime, "resource-site-harvest-progress", lease.id().value(),
                    new ResourceSiteHarvestProgressed(job.id(), job.progress().completedCropSlots() + 1), dueBinding.orElseThrow());
            if (result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted
                    && job.progress().completedCropSlots() + 1 == io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS) {
                submit(runtime, "resource-site-harvest-scene-draining", lease.id().value(),
                        new io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            }
            return;
        }
        // CropPrepared is a durable non-replayable boundary.  The earlier no-demand branch
        // releases only an unprepared cursor; a pending crop reaches its observed receipt here
        // before any later COLD hand-off can be considered.
        if (!demand.active()) return;
        if (level.getGameTime() % 10L != 0L) return;
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
                new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex()), dueBinding.orElseThrow());
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

    private static void keepTraversalPhysicallyActive(ServerLevel level, Mob worker,
                                                      io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor current,
                                                      io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor surface) {
        // An undued shared traversal action may keep its exact observed body cell, but cannot
        // leave a player-facing frozen worker there. This is a cell-local physical hold, not a
        // new route target or a cursor advance; the existing due binding remains the sole
        // semantic authority for exposing the next retained surface.
        if (FrontierV3SemanticMovement.arrived(level, worker, surface)) {
            FrontierV3ControlledMobMotion.holdRetainedCheckpoint(level, worker, FrontierV3SemanticMovement.point(surface));
        } else {
            FrontierV3ControlledMobMotion.moveWithinSemanticEnvelope(level, worker, FrontierV3SemanticMovement.point(surface),
                    io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope.around(current.standingBody(), surface.standingBody()));
        }
    }

    /**
     * A visible local crop-tending pose while the one canonical continuation remains in the
     * future.  It stays strictly inside the current crop's body cell and is intentionally not a
     * route, semantic action, or time source; the due-gated branches above remain the only
     * authority that can prepare/progress a crop.
     */
    private static void tendCurrentCrop(ServerLevel level, Mob worker,
                                        io.farfrontier.palemirror.frontier.v3.model.BlockPosition crop) {
        // A crop slot itself is the non-solid feet cell above its farmland support. The
        // retained crop position is therefore already the worker's Y datum. The actuator
        // derives its next local pose every server turn, independent of semantic due cadence.
        FrontierV3ControlledMobMotion.tendCurrentCrop(level, worker, crop);
        // The visible work gesture is emitted at PREPARED -> RUNNING above, not by this
        // per-turn local hold.  This pose must never create a periodic animation cadence of
        // its own or overlap a subsequently admitted retained traversal.
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
     * A missing or foreign body at the one lawful release edge is neither a player break nor a
     * terminal adapter failure.  First persist the resource-site incident; only an accepted
     * canonical disposition may then stop the paired scene.
     */
    private static void carrierFenceConflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                             FrontierWorldState state, SceneLease lease,
                                             FrontierV3AmbientActorExecutor.SceneCarrierFenceResult fence) {
        ResourceSiteHarvestSceneCause cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
        var job = FrontierResourceSiteHarvestSceneSupport.require(state, cause);
        var site = FrontierResourceSitePlan.compile(state.bootstrap()).get(job.siteId());
        if (site == null) throw new IllegalStateException("resource-site harvest scene has no immutable field");
        boolean accepted = FrontierV3ResourceSiteConflictExecutor.recordConflict(level, runtime, FrontierV3ResourceSiteLedger.get(level), site,
                site.cropSlots().get(job.progress().nextCropSlotIndex()),
                io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.SCENE_CARRIER_FENCE,
                new io.farfrontier.palemirror.frontier.v3.api.CommandId("executor:resource-site-carrier-fence-"
                        + fence.name().toLowerCase(java.util.Locale.ROOT) + "-r" + lease.revision()));
        if (accepted) conflict(level, runtime, lease, "cold-carrier-" + fence.name().toLowerCase(java.util.Locale.ROOT));
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

    private static io.farfrontier.palemirror.frontier.v3.api.CommandResult submitBound(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                                         String phase, String id,
                                                                                         io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload,
                                                                                         ScheduledAction binding) {
        return FrontierV3CommandSubmission.submitBound(runtime, phase, id, payload, binding);
    }

    private static ScheduledAction binding(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                           io.farfrontier.palemirror.frontier.v3.api.SubjectId jobId) {
        return FrontierV3ContinuationBinding.require(runtime.checkpointImage().orElseThrow(), jobId,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND);
    }

    /**
     * A normal HOT release must carry the sole retained COLD continuation.  An accepted player
     * conflict is different: that same transaction has already cancelled its continuation and
     * moved the field into CONFLICT, so release is only the durable body-exit receipt.  Asking
     * for a now-retired binding would quarantine the server after the authoritative break.
     */
    static Optional<ScheduledAction> releaseBinding(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                    FrontierWorldState state, SceneLease lease) {
        ResourceSiteHarvestSceneCause cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
        if (FrontierResourceSiteHarvestSceneSupport.isTerminalReceiptRelease(state, cause)) {
            // The terminal output receipt already consumed its sole continuation and created
            // the next growth action.  Rebinding this body-exit receipt to the retired job
            // would strand the exact worker in DRAINING at the final crop cell.
            return Optional.empty();
        }
        var job = FrontierResourceSiteHarvestSceneSupport.require(state, cause);
        return releaseBinding(runtime.checkpointImage().orElseThrow(), job.id(),
                state.resourceSites().site(job.siteId()).phase());
    }

    static Optional<ScheduledAction> releaseBinding(io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint,
                                                    io.farfrontier.palemirror.frontier.v3.api.SubjectId jobId,
                                                    io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase phase) {
        return FrontierV3ResourceSiteHarvestReleaseBinding.forPhase(checkpoint, jobId, phase);
    }

    /** Resource-harvest-specific closed-body retention stays at the registered behavior edge. */
    static boolean retainsClosedBody(Entity entity, FrontierWorldState state, SceneLease lease, SceneMember member) {
        if (!FrontierSceneBehaviors.isResourceSiteHarvest(lease)
                || !member.entityId().equals(FrontierV3AmbientActorExecutor.entityId(state, member.actorId()))
                || !FrontierV3SceneExecutor.ownedByClosedLease(entity, state, lease, member)) return false;
        var actor = state.actorLocations().get(member.actorId());
        if (actor == null || actor.condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE) return false;
        // A CLOSED lease is historical ownership evidence.  Its member position is the
        // original scene admission anchor, while release records the last observed station in
        // actor custody.  Neither is a second live-body authority: a Minecraft body can cross
        // an exact standing-column boundary between the observation and the durable close.
        // The immutable closed-lease tag, deterministic UUID, expected body type and living
        // canonical actor are therefore the complete same-actor proof.  In particular, using
        // either position as an additional deletion predicate made the lawful last-cell body
        // disappear in that close window.  Foreign/stale/mismatched bodies still fail at
        // ownedByClosedLease before this point and never become a replacement candidate.
        return true;
    }

    static boolean adoptableClosedBody(Entity entity, FrontierWorldState state, SceneLease successor, SceneMember member) {
        return state.sceneLeases().values().stream().filter(previous -> previous.status() == SceneLeaseStatus.CLOSED)
                .filter(FrontierSceneBehaviors::isResourceSiteHarvest).filter(previous -> previous.members().contains(member))
                .anyMatch(previous -> retainsClosedBody(entity, state, previous, member));
    }
}
