package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registered physical owner for the service-work scene family.
 *
 * <p>MAT-003 now admits retained service work, but has not yet emitted a service lease. This
 * deliberately has no candidate or effect fallback: the subsequent vertical adds its
 * retained-worker HOT executor here, and must not borrow production or medical scene behavior.</p>
 */
final class FrontierV3SettlementServiceWorkSceneExecutor {
    private static final double READY_DISTANCE_SQUARED = 2.25D;
    private FrontierV3SettlementServiceWorkSceneExecutor() { }

    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return false;
        Optional<SceneLease> active = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isServiceWork)
                .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED && lease.status() != SceneLeaseStatus.CONFLICT)
                .min(Comparator.comparing(SceneLease::id));
        if (active.isPresent()) { execute(level, runtime, state, active.orElseThrow()); return true; }
        Optional<FrontierSettlementServiceWorkSceneSupport.Candidate> candidate = FrontierSettlementServiceWorkSceneSupport.nextCandidate(state)
                .filter(value -> FrontierV3SceneExecutor.demandExists(level, value.handoffPosition()));
        if (candidate.isEmpty()) return false;
        FrontierSettlementServiceWorkSceneSupport.Candidate work = candidate.orElseThrow(); SceneLease lease = lease(runtime, work);
        if (FrontierSceneAdmission.available(state, Set.of(work.workerId()))) prepare(level, runtime, lease); else handoff(level, runtime, state, lease);
        return true;
    }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierSettlementServiceWorkSceneSupport.Candidate candidate) {
        var checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        SceneLeaseId id = new SceneLeaseId("lease:service-work-" + candidate.workId().value().substring("service:".length())
                + "-r" + checkpoint.revision().value());
        SceneMember member = new SceneMember(candidate.workerId(), SceneLease.deterministicEntityId(checkpoint.worldId(), id, candidate.workerId()));
        return SceneLease.forCause(id, checkpoint.worldId(), new SettlementServiceWorkSceneCause(candidate.workId()), candidate.handoffPosition(),
                checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED, List.of(member),
                SceneLease.bodiesAboveSupportCells(Map.of(candidate.workerId(), candidate.handoffPosition())), Set.of(), Optional.empty());
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        switch (lease.status()) {
            case PREPARED -> materialize(level, runtime, state, lease);
            case HOT -> work(level, runtime, state, lease);
            case DRAINING -> FrontierV3SceneExecutor.release(level, runtime, lease);
            case UNKNOWN_AFTER_RESTART -> FrontierV3SceneExecutor.reclaim(level, runtime, state, lease);
            case CONFLICT, CLOSED -> { }
        }
    }

    private static void prepare(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_service_work_prepared", lease,
                submit(runtime, "settlement-service-work-prepare", lease.id().value(), new SettlementServiceWorkSceneLeasePrepared(lease)));
    }

    private static void materialize(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.BodyMaterialization result = FrontierV3SceneExecutor.materializeBodies(level, state, lease);
        if (result == FrontierV3SceneExecutor.BodyMaterialization.CONFLICT) { conflict(level, runtime, lease, "prepared-body-conflict"); return; }
        if (result != FrontierV3SceneExecutor.BodyMaterialization.COMPLETE) return;
        FrontierV3SceneExecutor.rememberObserved(level, runtime, state, lease);
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_service_work_hot", lease,
                submit(runtime, "settlement-service-work-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
    }

    private static void handoff(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        SceneMember member = lease.members().getFirst(); AmbientActorLease ambient = state.ambientLeases().get(member.actorId()); Entity entity = level.getEntity(member.entityId());
        if (ambient == null || ambient.status() != AmbientLeaseStatus.HOT || !(entity instanceof Mob body) || !body.isAlive()
                || !FrontierV3AmbientActorExecutor.owned(body, member.actorId(), false)
                || !at(body, lease.memberPosition(member.actorId()).supportingSurface())) return;
        SceneMemberPosition capture = new SceneMemberPosition(member.actorId(), observed(body),
                new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(Math.round(body.getHealth() * io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE)));
        SceneLease captured = lease.withMemberPositions(Map.of(member.actorId(), capture.body())).withAmbientHandoff(Set.of(member.actorId()));
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_service_work_handoff", captured,
                submit(runtime, "settlement-service-work-handoff", lease.id().value(), new SettlementServiceWorkSceneLeaseHandoff(captured, List.of(capture))));
    }

    private static void work(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        SettlementServiceWork work = FrontierSettlementServiceWorkSceneSupport.require(state, FrontierSceneBehaviors.serviceWork(lease));
        if (work.phase() == SettlementServiceWorkPhase.EFFECT_READY) { drain(runtime, lease); return; }
        if (state.structureConditions().get(work.facilityId()) != StructureCondition.INTACT) { drain(runtime, lease); return; }
        boolean demand = FrontierV3SceneExecutor.demandExists(level, FrontierSettlementServiceWorkSceneSupport.currentSurface(work).support());
        if (FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demand,
                FrontierV3SceneExecutor.playerWithinSafeRadius(level, lease))) { drain(runtime, lease); return; }
        if (!demand) return;
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        if (!(entity instanceof Mob worker) || !worker.isAlive() || !FrontierV3SceneExecutor.recognizes(runtime, worker)) {
            conflict(level, runtime, lease, "worker-unavailable"); return;
        }
        SurfaceAnchor current = FrontierSettlementServiceWorkSceneSupport.currentSurface(work);
        if (!at(worker, current)) { conflict(level, runtime, lease, "cursor-body-mismatch"); return; }
        if (work.phase() == SettlementServiceWorkPhase.INPUT_ISSUE_PENDING) return;
        if (work.phase() == SettlementServiceWorkPhase.PREPARED || work.phase() == SettlementServiceWorkPhase.APPROACH_INPUT
                || work.phase() == SettlementServiceWorkPhase.APPROACH_WORK) {
            List<SurfaceAnchor> corridor = work.phase() == SettlementServiceWorkPhase.APPROACH_WORK ? work.workTraversal().linearCorridorSurfaces()
                    : work.inputTraversal().linearCorridorSurfaces();
            int cursor = work.phase() == SettlementServiceWorkPhase.APPROACH_WORK ? work.workTraversalCursor() : work.inputTraversalCursor();
            if (cursor >= corridor.size() - 1) { conflict(level, runtime, lease, "uncommitted-station-arrival"); return; }
            SurfaceAnchor next = corridor.get(cursor + 1);
            if (at(worker, next)) {
                submit(runtime, "settlement-service-work-traversal", lease.id().value(),
                        new SettlementServiceWorkTraversalAdvanced(work.id(), lease.id(), observed(worker), cursor + 1));
            } else if (!FrontierV3ProductionWorkSceneExecutor.clearNextBody(level, worker, next)) {
                submit(runtime, "settlement-service-work-route-blocked", lease.id().value(),
                        new SettlementServiceWorkTraversalBlocked(work.id(), lease.id(), observed(worker), cursor + 1));
            } else FrontierV3ControlledMobMotion.moveToward(level, worker, point(next));
            return;
        }
        if (work.phase() != SettlementServiceWorkPhase.WORKING) { conflict(level, runtime, lease, "unsupported-work-phase"); return; }
        SettlementServiceWorkPhase next = work.completedWorkTicks() + 1 == SettlementServiceWork.REQUIRED_WORK_TICKS
                ? SettlementServiceWorkPhase.EFFECT_READY : SettlementServiceWorkPhase.WORKING;
        int ticks = next == SettlementServiceWorkPhase.EFFECT_READY ? 0 : work.completedWorkTicks() + 1;
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        submit(runtime, "settlement-service-work-progress", lease.id().value(),
                new SettlementServiceWorkProgressed(work.id(), lease.id(), observed(worker), next, ticks));
    }

    private static boolean at(Mob worker, SurfaceAnchor surface) {
        return worker.getBlockX() == surface.x() && worker.getBlockY() == surface.y() + 1 && worker.getBlockZ() == surface.z()
                && worker.distanceToSqr(point(surface)) <= READY_DISTANCE_SQUARED;
    }
    private static BodyPosition observed(Mob worker) { return new BodyPosition(worker.getBlockX(), worker.getBlockY(), worker.getBlockZ()); }
    private static Vec3 point(SurfaceAnchor surface) { return new Vec3(surface.x() + .5D, surface.y() + 1D, surface.z() + .5D); }
    private static void drain(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        submit(runtime, "settlement-service-work-draining", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
    }
    private static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, String reason) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_service_work_conflict:" + reason, lease,
                submit(runtime, "settlement-service-work-conflict", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT)));
    }
    private static CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase, String id,
                                        io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return FrontierV3CommandSubmission.submit(runtime, phase, id, payload);
    }
}
