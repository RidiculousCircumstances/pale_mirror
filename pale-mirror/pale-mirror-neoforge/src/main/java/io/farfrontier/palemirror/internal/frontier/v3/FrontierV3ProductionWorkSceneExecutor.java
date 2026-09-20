package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Naturally loaded, exact-worker workshop cycle; Minecraft observes but never owns job progress. */
final class FrontierV3ProductionWorkSceneExecutor {
    private static final double READY_DISTANCE_SQUARED = 2.25D;
    private FrontierV3ProductionWorkSceneExecutor() { }

    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return false;
        Optional<SceneLease> active = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isProductionWork)
                .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED && lease.status() != SceneLeaseStatus.CONFLICT).min(Comparator.comparing(SceneLease::id));
        if (active.isPresent()) { execute(level, runtime, state, active.orElseThrow()); return true; }
        Optional<FrontierProductionWorkSceneSupport.Candidate> candidate = FrontierProductionWorkSceneSupport.candidates(state).stream()
                // A current exact depot custody epoch is physical eligibility, not presentation
                // demand.  It may therefore admit the same retained worker/workshop cycle while
                // the naturally loaded depot is ticking without a nearby player.  Both anchors
                // must still be naturally loaded; this selector never creates a ticket.
                .filter(value -> FrontierV3SceneExecutor.demandSnapshot(level, value.demandPosition()).active()
                        || ReferenceContainerCustody.hasOperationalCustody(state, FrontierWorldState.depotId(value.settlementId())))
                .filter(value -> level.hasChunkAt(new net.minecraft.core.BlockPos(value.demandPosition().x(), value.demandPosition().y(), value.demandPosition().z()))
                        && level.hasChunkAt(new net.minecraft.core.BlockPos(value.handoffPosition().x(), value.handoffPosition().y(), value.handoffPosition().z())))
                .findFirst();
        if (candidate.isEmpty()) return false;
        FrontierProductionWorkSceneSupport.Candidate work = candidate.orElseThrow(); SceneLease lease = lease(runtime, work);
        if (FrontierSceneAdmission.available(state, work.memberPositions().keySet())) prepare(level, runtime, lease); else handoff(level, runtime, state, lease);
        return true;
    }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierProductionWorkSceneSupport.Candidate candidate) {
        var checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        SceneLeaseId id = new SceneLeaseId("lease:production-work-" + candidate.jobId().value().substring("job:production-".length()) + "-r" + checkpoint.revision().value());
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted().map(actor -> new SceneMember(actor,
                SceneLease.deterministicEntityId(checkpoint.worldId(), id, actor))).toList();
        return SceneLease.forCause(id, checkpoint.worldId(), new ProductionWorkSceneCause(candidate.jobId()), candidate.handoffPosition(), checkpoint.instant(),
                checkpoint.revision().value(), SceneLeaseStatus.PREPARED, members, SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), Set.of(), Optional.empty());
    }
    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.requireRegisteredSceneTurn(lease);
        switch (lease.status()) {
            case PREPARED -> materialize(level, runtime, state, lease);
            case HOT -> work(level, runtime, state, lease);
            case DRAINING -> release(level, runtime, state, lease);
            case UNKNOWN_AFTER_RESTART -> FrontierV3SceneExecutor.reclaim(level, runtime, state, lease);
            case CONFLICT, CLOSED -> { }
        }
    }
    private static void prepare(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "production_work_prepared", lease,
                submit(runtime, "production-work-prepare", lease.id().value(), new ProductionWorkSceneLeasePrepared(lease)));
    }
    private static void materialize(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.BodyMaterialization result = FrontierV3ActorCarrierFactory.materializeSceneBodies(FrontierV3ActorCarrierComposition.InventoryEntry.PRODUCTION_WORK, level, state, lease);
        if (result == FrontierV3SceneExecutor.BodyMaterialization.CONFLICT) { conflict(level, runtime, lease, "prepared-body-conflict"); return; }
        if (result != FrontierV3SceneExecutor.BodyMaterialization.COMPLETE) return;
        FrontierV3SceneExecutor.rememberObserved(level, runtime, state, lease);
        ProductionJob job = FrontierProductionWorkSceneSupport.require(state, FrontierSceneBehaviors.productionWork(lease));
        FrontierV3DiagnosticTrace.recordProductionScene(level.getServer(), "production_work_hot", lease, job,
                submit(runtime, "production-work-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
    }
    private static void handoff(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        SceneMember member = lease.members().getFirst(); AmbientActorLease ambient = state.ambientLeases().get(member.actorId());
        Entity entity = level.getEntity(member.entityId());
        if (ambient == null || ambient.status() != AmbientLeaseStatus.HOT || !(entity instanceof Mob body) || !body.isAlive()
                || !FrontierV3AmbientActorExecutor.owned(body, member.actorId(), false)) return;
        // The durable production reducer accepts this exact observed body only while its workshop
        // traversal has not started, and recompiles that traversal from this position. Requiring
        // the ambient body to return to the job's earlier canonical surface would deadlock a
        // legitimately retained WORK body before that reducer can preserve the hand-off.
        SceneMemberPosition capture = new SceneMemberPosition(member.actorId(), new BodyPosition(body.getBlockX(), body.getBlockY(), body.getBlockZ()),
                new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(Math.round(body.getHealth() * io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE)));
        // Integer Minecraft cells are not themselves an observation of standing: a body in the
        // upper portion of that cell may still be rising, falling, or supported by a different
        // physical datum.  Rebasing a canonical production cursor from it would turn an
        // in-flight ambient pose into a false exact surface.  Keep ambient custody until this
        // same body is actually at, and collision-clear on, the one inferred support—no reset,
        // replacement, alternate floor, or desired-state repair is permitted here.
        if (!observedHandoffSurfaceIsCurrent(level, body, capture.body())) return;
        // `handoffPosition` is the durable first route surface used by prepared-scene
        // validation.  The reducer rebases the unstarted traversal from this same observation,
        // so retain the support beneath the captured body as well as the captured body itself.
        SceneLease captured = lease.withHandoffPosition(capture.body().supportingSurface().support())
                .withMemberPositions(Map.of(member.actorId(), capture.body())).withAmbientHandoff(Set.of(member.actorId()));
        CommandResult handoff = submit(runtime, "production-work-handoff", lease.id().value(),
                new ProductionWorkSceneLeaseHandoff(captured, List.of(capture)));
        ProductionJob job = FrontierProductionWorkSceneSupport.require(state, FrontierSceneBehaviors.productionWork(lease));
        FrontierV3DiagnosticTrace.recordProductionScene(level.getServer(), "production_work_handoff", captured, job, handoff);
        // The accepted hand-off owns the same grounded body, not a new physical actor.  Its
        // support was just observed through the exact retained production surface, so a
        // historical vanilla fall counter is no longer an in-flight physical fact.  Clear only
        // at this accepted observation boundary; an unsupported or rejected body retains its
        // ordinary fall outcome.
        if (handoff instanceof CommandResult.Accepted) clearHistoricalFallDistanceAtAcceptedHandoff(level, body, capture.body());
    }
    private static void work(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        ProductionJob job = FrontierProductionWorkSceneSupport.require(state, FrontierSceneBehaviors.productionWork(lease));
        if (ReferenceContainerCustody.blocksCanonicalUse(state, FrontierWorldState.depotId(job.settlementId()))) {
            drain(level, runtime, lease, DrainReason.DEPOT_CONFLICT); return;
        }
        if (job.workProgress().terminalEffectEligible()) { drain(level, runtime, lease, DrainReason.TERMINAL_EFFECT_READY); return; }
        SettlementStructure workshop = state.bootstrap().settlements().stream().filter(s -> s.id().equals(job.settlementId())).findFirst().orElseThrow().structures().stream()
                .filter(s -> s.id().equals(job.facilityId())).findFirst().orElseThrow();
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT || !FrontierProductionWorkSceneSupport.hasExactMaterializedInput(state, job)) {
            drain(level, runtime, lease, DrainReason.FACILITY_OR_INPUT_UNAVAILABLE); return;
        }
        FrontierV3SceneDemand.Snapshot demand = FrontierV3SceneExecutor.demandSnapshot(level, workshop.anchor());
        boolean operationalCustody = ReferenceContainerCustody.hasOperationalCustody(state, FrontierWorldState.depotId(job.settlementId()));
        if (!operationalCustody && FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demand,
                FrontierV3SceneExecutor.playerWithinSafeRadius(level, lease))) {
            drain(level, runtime, lease, DrainReason.DEMAND_HYSTERESIS_WITHOUT_CUSTODY); return;
        }
        if (!demand.active() && !operationalCustody) return;
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        if (!(entity instanceof Mob worker) || !worker.isAlive() || !FrontierV3SceneExecutor.recognizes(runtime, worker)) { conflict(level, runtime, lease, "worker-unavailable"); return; }
        List<SurfaceAnchor> route = job.workTraversal().linearCorridorSurfaces(); SurfaceAnchor current = route.get(job.traversalCursor());
        int inputCursor = route.size() - 2;
        // Entity motion runs at the normal entity boundary, while a canonical command is
        // committed afterwards.  The exact body may therefore already be on the one retained
        // next surface when this tick reads the still-old cursor.  That is observed arrival,
        // not a conflict and not permission to skip an edge.  Any other displacement remains
        // a visible mismatch.
        if (!at(worker, current)) {
            if (job.traversalCursor() < route.size() - 1 && at(worker, route.get(job.traversalCursor() + 1))) {
                submit(runtime, "production-work-traversal", lease.id().value(),
                        new ProductionWorkTraversalAdvanced(job.id(), lease.id(), FrontierV3SurfaceObservation.observedAt(worker, route.get(job.traversalCursor() + 1)), job.traversalCursor() + 1));
            } else if (job.traversalCursor() < route.size() - 1
                    && settleRetainedDescendingArrival(level, worker, current, route.get(job.traversalCursor() + 1))) {
                // The exact body has already walked horizontally over the one retained lower
                // support.  It must settle that same edge before a current-surface recovery is
                // considered; otherwise the current/next tolerances make it oscillate across
                // the lip forever without changing the canonical cursor.
            } else if (job.traversalCursor() < route.size() - 1
                    && continueRetainedAscendingLip(level, worker, current, route.get(job.traversalCursor() + 1))) {
                // The body is still in the current column but already collision-lifted by the
                // one declared higher support.  Returning it to the lower cursor would try to
                // move through that support and loop forever; continue only this retained edge.
            } else if (!reacquireRetainedSurface(level, worker, current)) conflict(level, runtime, lease,
                    cursorBodyMismatch(level, job.traversalCursor(), worker, current));
            return;
        }
        if (job.workProgress().stage() == ProductionWorkProgress.Stage.APPROACH && job.traversalCursor() == inputCursor) {
            worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            submit(runtime, "production-work-input-ready", lease.id().value(), new ProductionWorkProgressed(job.id(), lease.id(), FrontierV3SurfaceObservation.observedAt(worker, current), ProductionWorkProgress.inputReady()));
            return;
        }
        if (job.traversalCursor() < route.size() - 1) {
            SurfaceAnchor next = route.get(job.traversalCursor() + 1);
            if (at(worker, next)) submit(runtime, "production-work-traversal", lease.id().value(), new ProductionWorkTraversalAdvanced(job.id(), lease.id(), FrontierV3SurfaceObservation.observedAt(worker, next), job.traversalCursor() + 1));
            else if (!clearNextBody(level, worker, next)) blocked(level, runtime, lease, job, worker, current, next);
            else pursueRetainedTraversalEdge(level, worker, current, next);
            return;
        }
        ProductionWorkProgress next = switch (job.workProgress().stage()) {
            case APPROACH -> ProductionWorkProgress.inputReady();
            case INPUT_READY -> ProductionWorkProgress.processing(0);
            case PROCESSING -> job.workProgress().completedTicks() + 1 == ProductionWorkProgress.REQUIRED_PROCESSING_TICKS
                    ? ProductionWorkProgress.outputReady() : ProductionWorkProgress.processing(job.workProgress().completedTicks() + 1);
            case OUTPUT_READY -> throw new IllegalStateException("terminal work may not continue");
        };
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        submit(runtime, "production-work-progress", lease.id().value(), new ProductionWorkProgressed(job.id(), lease.id(), FrontierV3SurfaceObservation.observedAt(worker, current), next));
    }
    private static boolean at(Mob worker, SurfaceAnchor surface) { return FrontierV3SurfaceObservation.at(worker, surface); }
    private static Vec3 point(SurfaceAnchor surface) { return FrontierV3SurfaceObservation.point(surface); }

    /**
     * Keeps one admitted production body inside the fixed neighborhood of its current and next
     * canonical supports when a live body temporarily occupies the direct physical column.
     * This is actuator-only collision latitude: the target remains {@code next}, and only an
     * observation at that target can advance the durable traversal cursor.
     */
    static void pursueRetainedTraversalEdge(ServerLevel level, Mob worker, SurfaceAnchor current, SurfaceAnchor next) {
        FrontierV3ControlledMobMotion.moveWithinEnvelope(level, worker, point(next),
                LocalNavigationEnvelope.around(current.standingBody(), next.standingBody()));
    }

    /**
     * Finishes only the vertical half of a directly observed one-grade descending retained
     * edge.  The body remains at the retained next X/Z checkpoint, and the actuator remains
     * bounded by the same current/next envelope; it cannot select a new support or cursor.
     */
    static boolean settleRetainedDescendingArrival(ServerLevel level, Mob worker, SurfaceAnchor current, SurfaceAnchor next) {
        if (next.y() >= current.y() || !FrontierV3SurfaceObservation.horizontallyAt(worker, next)
                || !FrontierV3SurfaceObservation.withinDescendingEdgeHeights(worker, current, next)
                || !clearNextBody(level, worker, next)) return false;
        pursueRetainedTraversalEdge(level, worker, current, next);
        return true;
    }

    /**
     * Continues precisely one declared ascending edge after collision has lifted the body over
     * its next support lip.  It has no cursor authority: only a later exact observation at
     * {@code next} can commit the durable traversal advance.
     */
    static boolean continueRetainedAscendingLip(ServerLevel level, Mob worker, SurfaceAnchor current, SurfaceAnchor next) {
        if (!FrontierV3SurfaceObservation.withinAscendingEdgeLip(worker, current, next)
                || !clearNextBody(level, worker, next)) return false;
        pursueRetainedTraversalEdge(level, worker, current, next);
        return true;
    }
    /** Tests the exact retained target support and body against loaded physical collision without choosing an alternate edge. */
    static boolean clearNextBody(ServerLevel level, Mob worker, SurfaceAnchor surface) {
        net.minecraft.core.BlockPos support = new net.minecraft.core.BlockPos(surface.x(), surface.y(), surface.z());
        return !level.getBlockState(support).isAir()
                && level.getBlockState(support).isFaceSturdy(level, support, net.minecraft.core.Direction.UP)
                && level.noCollision(worker, worker.getBoundingBox().move(point(surface).subtract(worker.position())));
    }

    /**
     * Minecraft may push a body one local cell between observations.  This does not advance or
     * rewrite the immutable canonical cursor: it merely lets the same exact body walk back to
     * that retained surface.  A farther displacement or an occupied target remains a visible
     * scene conflict, never a hidden route repair.
     */
    static boolean reacquireRetainedSurface(ServerLevel level, Mob worker, SurfaceAnchor surface) {
        if (!FrontierV3SurfaceObservation.mayReacquire(worker, surface) || !clearNextBody(level, worker, surface)) return false;
        FrontierV3ControlledMobMotion.moveToward(level, worker, point(surface));
        return true;
    }

    /**
     * The ambient-to-production boundary accepts one physical observation only after its
     * inferred support is both geometrically current and collision-clear for that same body.
     * This is a read-only admission fence, not a pose correction.
     */
    static boolean observedHandoffSurfaceIsCurrent(ServerLevel level, Mob worker, BodyPosition observed) {
        SurfaceAnchor inferredSupport = observed.supportingSurface();
        return at(worker, inferredSupport) && clearNextBody(level, worker, inferredSupport);
    }

    /** Applies the accepted grounded hand-off fact without changing the retained body or route. */
    static boolean clearHistoricalFallDistanceAtAcceptedHandoff(ServerLevel level, Mob worker, BodyPosition observed) {
        if (!observedHandoffSurfaceIsCurrent(level, worker, observed)) return false;
        worker.fallDistance = 0.0F;
        return true;
    }
    /**
     * A scene conflict is deliberately retained rather than repaired.  Keep its exact observed
     * and expected cells in the durable trace so an operator can distinguish an ownership
     * hand-off defect from a genuine loaded-world displacement without treating a generic
     * CONFLICT phase as a cause.
     */
    private static String cursorBodyMismatch(ServerLevel level, int cursor, Mob worker, SurfaceAnchor expected) {
        BlockPos physicalCell = worker.blockPosition();
        BlockPos expectedSupport = new BlockPos(expected.x(), expected.y(), expected.z());
        String physicalCellBlock = BuiltInRegistries.BLOCK.getKey(level.getBlockState(physicalCell).getBlock()).toString();
        String expectedSupportBlock = BuiltInRegistries.BLOCK.getKey(level.getBlockState(expectedSupport).getBlock()).toString();
        Vec3 exact = worker.position();
        Vec3 velocity = worker.getDeltaMovement();
        return "cursor-body-mismatch:cursor=" + cursor
                + ":actual=" + worker.getBlockX() + "," + worker.getBlockY() + "," + worker.getBlockZ()
                + ":exact=" + String.format(Locale.ROOT, "%.3f,%.3f,%.3f", exact.x, exact.y, exact.z)
                + ":velocity=" + String.format(Locale.ROOT, "%.3f,%.3f,%.3f", velocity.x, velocity.y, velocity.z)
                + ":on-ground=" + worker.onGround()
                + ":body-cell=" + physicalCellBlock
                + ":expected-support=" + expectedSupportBlock
                + ":expected=" + expected.x() + "," + expected.y() + "," + expected.z();
    }
    /** A production drain is a bounded exact-job disposition; retain its source in the same trace as the release receipt. */
    private static void drain(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, DrainReason reason) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "production_work_draining:" + reason.traceName(), lease,
                submit(runtime, "production-work-draining-" + reason.traceName(), lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING)));
    }

    /**
     * The retained next topology edge is authoritative.  A loaded collision may block it, but
     * cannot choose a detour or silently turn an admitted job into a generic scene release.
     * Keep the one bounded expected support in the causal trace alongside the ordinary command
     * that asks the pure owner to block and drain this exact job.
     */
    private static void blocked(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease,
                                ProductionJob job, Mob worker, SurfaceAnchor current, SurfaceAnchor next) {
        String expected = "next-support-unavailable:" + next.x() + "," + next.y() + "," + next.z();
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "production_work_blocked:" + expected, lease,
                submit(runtime, "production-work-route-blocked", lease.id().value(),
                        new ProductionWorkTraversalBlocked(job.id(), lease.id(), FrontierV3SurfaceObservation.observedAt(worker, current),
                                job.traversalCursor() + 1)));
    }

    private enum DrainReason {
        DEPOT_CONFLICT("depot-conflict"), TERMINAL_EFFECT_READY("terminal-effect-ready"),
        FACILITY_OR_INPUT_UNAVAILABLE("facility-or-input-unavailable"),
        DEMAND_HYSTERESIS_WITHOUT_CUSTODY("demand-hysteresis-without-custody");

        private final String traceName;
        DrainReason(String traceName) { this.traceName = traceName; }
        String traceName() { return traceName; }
    }

    /**
     * A production release has exactly the same physical-custody boundary as a harvested field:
     * the retained worker body is about to leave its closed scene, so its same actor UUID must
     * first become one durable inactive carrier.  Generic closed-scene cleanup deliberately
     * discards the old projection; doing that without this fence is indistinguishable from a
     * player/world deletion on the next ambient pre-lease and must remain fail-closed.
     */
    private static void release(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        SceneMember member = lease.members().getFirst();
        Entity retained = level.getEntity(member.entityId());
        FrontierV3AmbientActorExecutor.SceneCarrierFenceResult fence =
                FrontierV3AmbientActorExecutor.fenceDrainingSceneBody(level, state, lease, member, retained);
        if (fence != FrontierV3AmbientActorExecutor.SceneCarrierFenceResult.FENCED) {
            // Preserve the live exact worker and its durable job rather than allowing closed
            // cleanup to erase provenance.  This is an owned local failure, never a substitute
            // worker or a permissive re-materialization path.
            conflict(level, runtime, lease, "release-carrier-" + fence.name().toLowerCase(java.util.Locale.ROOT));
            return;
        }
        FrontierV3SceneExecutor.release(level, runtime, lease);
        if (runtime.decodedState().map(current -> current.sceneLeases().get(lease.id()))
                .filter(current -> current.status() == SceneLeaseStatus.CLOSED).isPresent()
                && retained instanceof Mob body
                && FrontierV3AmbientActorExecutor.hasInactiveCarrier(level, state, member.actorId())) {
            // The release receipt is durable and the inactive carrier now owns reconstruction
            // proof only.  Remove the former physical custodian before ambient return can adopt
            // the same UUID under its newer lease revision.
            body.discard();
        }
    }
    private static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, String reason) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "production_work_conflict:" + reason, lease,
                submit(runtime, "production-work-conflict", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT)));
    }
    private static CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase, String id, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return FrontierV3CommandSubmission.submit(runtime, phase, id, payload);
    }
}
