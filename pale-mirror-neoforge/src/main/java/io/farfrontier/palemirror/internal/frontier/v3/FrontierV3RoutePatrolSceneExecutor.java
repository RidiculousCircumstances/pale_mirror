package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
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

/**
 * Naturally loaded class-D patrol executor.
 *
 * <p>It does not discover a route or issue generic guard motion. Every local move is the next
 * body in {@link RoutePatrol}; the canonical formation advances only after Minecraft observes
 * that exact body cell.  A missing/blocked body becomes the patrol's own visible failure path.</p>
 */
final class FrontierV3RoutePatrolSceneExecutor {
    private FrontierV3RoutePatrolSceneExecutor() { }

    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        Optional<SceneLease> active = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isRoutePatrol)
                .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED && lease.status() != SceneLeaseStatus.CONFLICT)
                .min(Comparator.comparing(SceneLease::id));
        if (active.isPresent()) { execute(level, runtime, state, active.orElseThrow()); return true; }
        Optional<FrontierRoutePatrolSceneSupport.Candidate> candidate = FrontierRoutePatrolSceneSupport.nextCandidate(state)
                .filter(value -> FrontierV3SceneExecutor.demandExists(level, value.handoffPosition()));
        if (candidate.isEmpty()) return false;
        FrontierRoutePatrolSceneSupport.Candidate patrol = candidate.orElseThrow();
        SceneLease lease = lease(runtime, patrol);
        if (FrontierSceneAdmission.available(state, patrol.memberBodies().keySet())) prepare(level, runtime, lease);
        else handoff(level, runtime, state, lease);
        return true;
    }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierRoutePatrolSceneSupport.Candidate candidate) {
        var checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        String suffix = candidate.taskId().value().replace(':', '-');
        SceneLeaseId id = new SceneLeaseId("lease:route-patrol-" + suffix + "-r" + checkpoint.revision().value());
        List<SceneMember> members = candidate.memberBodies().keySet().stream().sorted().map(actor -> new SceneMember(actor,
                SceneLease.deterministicEntityId(checkpoint.worldId(), id, actor))).toList();
        return SceneLease.forCause(id, checkpoint.worldId(), new RoutePatrolSceneCause(candidate.taskId()), candidate.handoffPosition(),
                checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED, members, candidate.memberBodies(), Set.of(), Optional.empty());
    }

    private static void prepare(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "route_patrol_prepared", lease,
                submit(runtime, "route-patrol-prepare", lease.id().value(), new RoutePatrolSceneLeasePrepared(lease)));
    }

    /**
     * Transfers every already-visible exact member without despawning it.  A member that has
     * not yet materialized has no physical identity to preserve, so the prepared scene creates
     * that same canonical actor at its retained formation cell.  A half capture is therefore
     * safe; a half replacement of an existing ambient body is not.
     */
    private static void handoff(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        List<SceneMemberPosition> captures = new ArrayList<>();
        for (SceneMember member : lease.members()) {
            AmbientActorLease ambient = state.ambientLeases().get(member.actorId()); Entity entity = level.getEntity(member.entityId());
            if (ambient == null || ambient.status() == AmbientLeaseStatus.CLOSED) continue;
            if (ambient.status() != AmbientLeaseStatus.HOT || !(entity instanceof Mob body) || !body.isAlive()
                    || !FrontierV3AmbientActorExecutor.owned(body, member.actorId(), false)
                    || !at(body, lease.memberPosition(member.actorId()).supportingSurface())) return;
            captures.add(new SceneMemberPosition(member.actorId(), FrontierV3SurfaceObservation.observedAt(body,
                    lease.memberPosition(member.actorId()).supportingSurface()), fixed(body.getHealth())));
        }
        Map<SubjectId, BodyPosition> positions = new java.util.LinkedHashMap<>(lease.memberPositions());
        captures.forEach(capture -> positions.put(capture.actorId(), capture.body()));
        if (captures.isEmpty()) return;
        SceneLease captured = lease.withMemberPositions(positions).withAmbientHandoff(captures.stream().map(SceneMemberPosition::actorId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "route_patrol_handoff", captured,
                submit(runtime, "route-patrol-handoff", lease.id().value(), new RoutePatrolSceneLeaseHandoff(captured, captures)));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        switch (lease.status()) {
            case PREPARED -> materialize(level, runtime, state, lease);
            case HOT -> patrol(level, runtime, state, lease);
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
        FrontierV3SceneExecutor.rememberObserved(level, runtime, state, lease);
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "route_patrol_hot", lease,
                submit(runtime, "route-patrol-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
    }

    private static void patrol(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                               FrontierWorldState state, SceneLease lease) {
        RoutePatrol retained = FrontierRoutePatrolSceneSupport.require(state, FrontierSceneBehaviors.routePatrol(lease));
        if (!retained.active()) { drain(runtime, lease); return; }
        BlockPosition demand = lease.memberPosition(retained.guardId()).supportingSurface().support();
        boolean demanded = FrontierV3SceneExecutor.demandExists(level, demand);
        if (FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demanded,
                FrontierV3SceneExecutor.playerWithinSafeRadius(level, lease))) { drain(runtime, lease); return; }
        if (!demanded) return;
        Optional<BlockPosition> obstruction = FrontierRouteNetwork.firstObstructionOnCarriagewaySegment(retained.route(), retained.routeIndex(), state.physicalDeltas());
        if (obstruction.isPresent()) {
            submit(runtime, "route-patrol-obstruction", lease.id().value(), new RoutePatrolObstructionConfirmed(retained.taskId(), obstruction.orElseThrow()));
            return;
        }
        List<SubjectId> safe = retained.safeAdvances();
        if (safe.isEmpty()) { block(level, runtime, lease, retained); return; }
        SubjectId actorId = safe.getFirst();
        SceneMember member = lease.members().stream().filter(value -> value.actorId().equals(actorId)).findFirst().orElseThrow();
        Entity entity = level.getEntity(member.entityId());
        if (!(entity instanceof Mob body) || !body.isAlive() || !FrontierV3SceneExecutor.recognizes(runtime, body)) { conflict(level, runtime, lease, "member-unavailable"); return; }
        BodyPosition current = lease.memberPosition(actorId);
        RoutePatrol next = retained.advance(actorId); BodyPosition target = FrontierRoutePatrolSceneSupport.bodies(next).get(actorId);
        if (!at(body, current.supportingSurface())) {
            if (at(body, target.supportingSurface())) observe(level, runtime, lease, retained, actorId, target);
            else if (!reacquire(level, body, current.supportingSurface())) conflict(level, runtime, lease, "cursor-body-mismatch");
            return;
        }
        if (at(body, target.supportingSurface())) { observe(level, runtime, lease, retained, actorId, target); return; }
        if (!clearNextBody(level, body, target.supportingSurface())) { block(level, runtime, lease, retained); return; }
        FrontierV3ControlledMobMotion.moveToward(level, body, point(target.supportingSurface()));
    }

    private static void observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, RoutePatrol patrol,
                                SubjectId actorId, BodyPosition observed) {
        CommandResult result = submit(runtime, "route-patrol-traversal", lease.id().value(),
                new RoutePatrolTraversalObserved(patrol.taskId(), lease.id(), actorId, observed));
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "route_patrol_traversal", lease, result);
    }
    private static void block(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, RoutePatrol patrol) {
        CommandResult result = submit(runtime, "route-patrol-blocked", lease.id().value(), new RoutePatrolBlocked(patrol.taskId()));
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "route_patrol_blocked", lease, result);
    }
    private static boolean at(Mob body, SurfaceAnchor surface) { return FrontierV3SurfaceObservation.at(body, surface); }
    private static Vec3 point(SurfaceAnchor surface) { return FrontierV3SurfaceObservation.point(surface); }
    private static boolean clearNextBody(ServerLevel level, Mob body, SurfaceAnchor surface) {
        return level.noCollision(body, body.getBoundingBox().move(point(surface).subtract(body.position())));
    }
    private static boolean reacquire(ServerLevel level, Mob body, SurfaceAnchor surface) {
        if (!FrontierV3SurfaceObservation.mayReacquire(body, surface) || !clearNextBody(level, body, surface)) return false;
        FrontierV3ControlledMobMotion.moveToward(level, body, point(surface)); return true;
    }
    private static void drain(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        submit(runtime, "route-patrol-draining", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
    }
    private static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, String reason) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "route_patrol_conflict:" + reason, lease,
                submit(runtime, "route-patrol-conflict", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT)));
    }
    private static io.farfrontier.palemirror.frontier.v3.api.FixedScalar fixed(float health) {
        return new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(Math.round(health * io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE));
    }
    private static CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase, String id,
                                        io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return FrontierV3CommandSubmission.submit(runtime, phase, id, payload);
    }
}
