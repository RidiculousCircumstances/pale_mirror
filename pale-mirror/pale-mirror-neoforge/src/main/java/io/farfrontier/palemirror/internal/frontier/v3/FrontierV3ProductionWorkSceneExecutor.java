package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
            case DRAINING -> FrontierV3SceneExecutor.release(level, runtime, lease);
            case UNKNOWN_AFTER_RESTART -> FrontierV3SceneExecutor.reclaim(level, runtime, state, lease);
            case CONFLICT, CLOSED -> { }
        }
    }
    private static void prepare(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "production_work_prepared", lease,
                submit(runtime, "production-work-prepare", lease.id().value(), new ProductionWorkSceneLeasePrepared(lease)));
    }
    private static void materialize(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.BodyMaterialization result = FrontierV3SceneExecutor.materializeBodies(level, state, lease);
        if (result == FrontierV3SceneExecutor.BodyMaterialization.CONFLICT) { conflict(level, runtime, lease, "prepared-body-conflict"); return; }
        if (result != FrontierV3SceneExecutor.BodyMaterialization.COMPLETE) return;
        FrontierV3SceneExecutor.rememberObserved(level, runtime, state, lease);
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "production_work_hot", lease,
                submit(runtime, "production-work-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
    }
    private static void handoff(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        SceneMember member = lease.members().getFirst(); AmbientActorLease ambient = state.ambientLeases().get(member.actorId());
        Entity entity = level.getEntity(member.entityId());
        if (ambient == null || ambient.status() != AmbientLeaseStatus.HOT || !(entity instanceof Mob body) || !body.isAlive()
                || !FrontierV3AmbientActorExecutor.owned(body, member.actorId(), false)
                || !at(body, lease.memberPosition(member.actorId()).supportingSurface())) return;
        SceneMemberPosition capture = new SceneMemberPosition(member.actorId(), FrontierV3SurfaceObservation.observedAt(body, lease.memberPosition(member.actorId()).supportingSurface()),
                new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(Math.round(body.getHealth() * io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE)));
        SceneLease captured = lease.withMemberPositions(Map.of(member.actorId(), capture.body())).withAmbientHandoff(Set.of(member.actorId()));
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "production_work_handoff", captured,
                submit(runtime, "production-work-handoff", lease.id().value(), new ProductionWorkSceneLeaseHandoff(captured, List.of(capture))));
    }
    private static void work(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        ProductionJob job = FrontierProductionWorkSceneSupport.require(state, FrontierSceneBehaviors.productionWork(lease));
        if (ReferenceContainerCustody.blocksCanonicalUse(state, FrontierWorldState.depotId(job.settlementId()))) { drain(runtime, lease); return; }
        if (job.workProgress().terminalEffectEligible()) { drain(runtime, lease); return; }
        SettlementStructure workshop = state.bootstrap().settlements().stream().filter(s -> s.id().equals(job.settlementId())).findFirst().orElseThrow().structures().stream()
                .filter(s -> s.id().equals(job.facilityId())).findFirst().orElseThrow();
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT || !FrontierProductionWorkSceneSupport.hasExactMaterializedInput(state, job)) {
            drain(runtime, lease); return;
        }
        FrontierV3SceneDemand.Snapshot demand = FrontierV3SceneExecutor.demandSnapshot(level, workshop.anchor());
        boolean operationalCustody = ReferenceContainerCustody.hasOperationalCustody(state, FrontierWorldState.depotId(job.settlementId()));
        if (!operationalCustody && FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demand,
                FrontierV3SceneExecutor.playerWithinSafeRadius(level, lease))) { drain(runtime, lease); return; }
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
            } else if (!reacquireRetainedSurface(level, worker, current)) conflict(level, runtime, lease, "cursor-body-mismatch");
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
            else if (!clearNextBody(level, worker, next)) submit(runtime, "production-work-route-blocked", lease.id().value(),
                    new ProductionWorkTraversalBlocked(job.id(), lease.id(), FrontierV3SurfaceObservation.observedAt(worker, current), job.traversalCursor() + 1));
            else FrontierV3ControlledMobMotion.moveToward(level, worker, point(next));
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
    /** Tests the exact retained target body against loaded physical collision without choosing an alternate edge. */
    static boolean clearNextBody(ServerLevel level, Mob worker, SurfaceAnchor surface) {
        return level.noCollision(worker, worker.getBoundingBox().move(point(surface).subtract(worker.position())));
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
    private static void drain(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) { submit(runtime, "production-work-draining", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING)); }
    private static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, String reason) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "production_work_conflict:" + reason, lease,
                submit(runtime, "production-work-conflict", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT)));
    }
    private static CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase, String id, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return FrontierV3CommandSubmission.submit(runtime, phase, id, payload);
    }
}
