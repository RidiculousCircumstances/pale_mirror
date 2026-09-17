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
import java.util.IdentityHashMap;
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
    /** Lets the ordinary observer read a terminal patrol result before mechanical scene release overwrites its trace. */
    private static final long TERMINAL_DRAIN_GRACE_TICKS = 40L;
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SceneLeaseId, Long>> TERMINAL_DRAIN_SINCE = new IdentityHashMap<>();

    private FrontierV3RoutePatrolSceneExecutor() { }

    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        Optional<SceneLease> active = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isRoutePatrol)
                .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED && lease.status() != SceneLeaseStatus.CONFLICT)
                .min(Comparator.comparing(SceneLease::id));
        if (active.isPresent()) { execute(level, runtime, state, active.orElseThrow()); return true; }
        Optional<FrontierRoutePatrolSceneSupport.Candidate> candidate = FrontierV3SceneDemand.firstDemandedCandidate(
                level, FrontierRoutePatrolSceneSupport.candidates(state), FrontierRoutePatrolSceneSupport.Candidate::handoffPosition);
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
        FrontierV3SceneExecutor.requireRegisteredSceneTurn(lease);
        switch (lease.status()) {
            case PREPARED -> materialize(level, runtime, state, lease);
            case HOT -> patrol(level, runtime, state, lease);
            case DRAINING -> FrontierV3SceneExecutor.release(level, runtime, lease);
            case UNKNOWN_AFTER_RESTART -> recover(level, runtime, state, lease);
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

    /**
     * A patrol's retained formation is a reversible COLD checkpoint.  A graceful restart starts
     * before the ordinary client can reconnect, so retain UNKNOWN for one bounded demand grace;
     * only then revoke the old pose.  Reclaim still accepts only the already loaded exact bodies.
     */
    private static void recover(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        if (!FrontierV3SceneExecutor.demandExists(level, lease.handoffPosition())) {
            if (!FrontierV3RestartDemandGrace.expired(runtime, lease.id(), level.getGameTime())) return;
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "route_patrol_recovery_revoked", lease,
                    submit(runtime, "route-patrol-recovery-revoked", lease.id().value(), new SceneLeaseRecoveryRevoked(lease.id())));
            return;
        }
        FrontierV3SceneExecutor.reclaim(level, runtime, state, lease);
    }

    private static void patrol(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                               FrontierWorldState state, SceneLease lease) {
        RoutePatrol retained = FrontierRoutePatrolSceneSupport.require(state, FrontierSceneBehaviors.routePatrol(lease));
        if (!retained.active()) {
            if (terminalDrainGraceExpired(runtime, lease.id(), level.getGameTime())) drain(runtime, lease);
            return;
        }
        BlockPosition demand = lease.memberPosition(retained.guardId()).supportingSurface().support();
        FrontierV3SceneDemand.Snapshot demanded = FrontierV3SceneExecutor.demandSnapshot(level, demand);
        if (FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demanded,
                FrontierV3SceneExecutor.playerWithinSafeRadius(level, lease))) { drain(runtime, lease); return; }
        if (!demanded.active()) return;
        Optional<BlockPosition> obstruction = FrontierRouteNetwork.firstObstructionOnCarriagewaySegment(retained.route(), retained.routeIndex(), state.physicalDeltas());
        if (obstruction.isPresent()) {
            submit(runtime, "route-patrol-obstruction", lease.id().value(), new RoutePatrolObstructionConfirmed(retained.taskId(), obstruction.orElseThrow()));
            return;
        }
        // Body custody is checked before proposing the next canonical formation.  A missing
        // HOT resident is its own fail-closed condition; it must not be disguised as a route
        // topology failure merely because the remaining retained column cannot advance alone.
        for (SceneMember candidate : lease.members()) {
            if (!(level.getEntity(candidate.entityId()) instanceof Mob)) {
                block(level, runtime, lease, retained, RoutePatrolBlockReason.MISSING_OWNED_BODY); return;
            }
        }
        RoutePatrol formation;
        try {
            formation = retained.advanceFormation();
        } catch (IllegalArgumentException invalidFormation) {
            block(level, runtime, lease, retained, RoutePatrolBlockReason.NO_OPEN_RETAINED_EDGE); return;
        }
        Map<SubjectId, BodyPosition> formationBodies = FrontierRoutePatrolSceneSupport.bodies(formation);
        boolean arrived = true;
        for (SceneMember candidate : lease.members()) {
            Entity candidateEntity = level.getEntity(candidate.entityId()); BodyPosition targetBody = formationBodies.get(candidate.actorId());
            if (!(candidateEntity instanceof Mob candidateBody) || targetBody == null) {
                block(level, runtime, lease, retained, RoutePatrolBlockReason.MISSING_OWNED_BODY); return;
            }
            if (!at(candidateBody, targetBody.supportingSurface())) { arrived = false; break; }
        }
        if (arrived) { observeFormation(level, runtime, lease, retained, formationBodies); return; }
        for (SceneMember candidate : lease.members()) {
            Entity candidateEntity = level.getEntity(candidate.entityId()); BodyPosition targetBody = formationBodies.get(candidate.actorId());
            if (!(candidateEntity instanceof Mob candidateBody) || targetBody == null) continue;
            if (!at(candidateBody, targetBody.supportingSurface())) {
                if (!clearNextBody(level, candidateBody, targetBody.supportingSurface(), lease)) {
                    block(level, runtime, lease, retained, RoutePatrolBlockReason.OCCUPIED_NEXT_BODY); return;
                }
                FrontierV3ControlledMobMotion.moveToward(level, candidateBody, point(targetBody.supportingSurface()));
            }
        }
    }

    private static void observeFormation(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease,
                                         RoutePatrol patrol, Map<SubjectId, BodyPosition> bodies) {
        CommandResult result = submit(runtime, "route-patrol-formation", lease.id().value(),
                new RoutePatrolFormationObserved(patrol.taskId(), lease.id(), bodies));
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "route_patrol_formation", lease, result);
    }
    private static void block(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, RoutePatrol patrol,
                              RoutePatrolBlockReason reason) {
        CommandResult result = submit(runtime, "route-patrol-blocked", lease.id().value(), new RoutePatrolBlocked(patrol.taskId(), reason));
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "route_patrol_blocked", lease, result);
    }
    private static boolean at(Mob body, SurfaceAnchor surface) { return FrontierV3SurfaceObservation.at(body, surface); }
    private static Vec3 point(SurfaceAnchor surface) { return FrontierV3SurfaceObservation.point(surface); }
    /**
     * A formation edge moves the complete retained roster together.  A scout may therefore
     * enter the leader's current cell while that exact leader is simultaneously leaving it.
     * Treating that owned, scheduled departure as a foreign obstacle deadlocks every close
     * column.  Blocks, players, ambient actors and any non-member body remain hard obstacles.
     */
    private static boolean clearNextBody(ServerLevel level, Mob body, SurfaceAnchor surface, SceneLease lease) {
        var target = body.getBoundingBox().move(point(surface).subtract(body.position()));
        if (level.getBlockCollisions(body, target).iterator().hasNext()) return false;
        Set<java.util.UUID> formationBodies = lease.members().stream().map(SceneMember::entityId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return level.getEntities(body, target, entity -> !formationBodies.contains(entity.getUUID())).isEmpty();
    }
    private static boolean terminalDrainGraceExpired(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId, long now) {
        Map<SceneLeaseId, Long> since = TERMINAL_DRAIN_SINCE.computeIfAbsent(runtime, ignored -> new java.util.LinkedHashMap<>());
        long started = since.computeIfAbsent(leaseId, ignored -> now);
        return now - started >= TERMINAL_DRAIN_GRACE_TICKS;
    }

    private static void drain(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        Map<SceneLeaseId, Long> since = TERMINAL_DRAIN_SINCE.get(runtime);
        if (since != null) {
            since.remove(lease.id());
            if (since.isEmpty()) TERMINAL_DRAIN_SINCE.remove(runtime);
        }
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
