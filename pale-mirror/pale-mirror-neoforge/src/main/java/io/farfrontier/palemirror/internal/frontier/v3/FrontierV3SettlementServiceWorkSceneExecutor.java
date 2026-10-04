package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registered semantic service-scene consumer of the common physical body controller.
 *
 * <p>The executor admits only its retained service candidate and never borrows production or
 * medical scene behavior. Input issue and decontamination remain separate typed effect owners.</p>
 */
final class FrontierV3SettlementServiceWorkSceneExecutor {
    private FrontierV3SettlementServiceWorkSceneExecutor() { }

    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return false;
        return FrontierV3SceneTurnScheduler.run(runtime, state, io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind.SERVICE_WORK,
                lease -> execute(level, runtime, state, lease), () -> admit(level, runtime, state));
    }

    private static boolean admit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Optional<FrontierSettlementServiceWorkSceneSupport.Candidate> candidate = FrontierV3SceneDemand.nextDemandedCandidate(
                level, runtime, io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind.SERVICE_WORK, FrontierSettlementServiceWorkSceneSupport.candidates(state),
                        FrontierSettlementServiceWorkSceneSupport.Candidate::handoffPosition, FrontierSettlementServiceWorkSceneSupport.Candidate::workId);
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
                checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED, List.of(member), Set.of(), Optional.empty());
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.requireRegisteredSceneTurn(state, lease);
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
        FrontierV3SceneExecutor.BodyMaterialization result = FrontierV3SceneExecutor.materializeBodies(level, state, lease, FrontierV3ActorCarrierComposition.InventoryEntry.SETTLEMENT_SERVICE);
        if (result == FrontierV3SceneExecutor.BodyMaterialization.CONFLICT) { conflict(level, runtime, lease, "prepared-body-conflict"); return; }
        if (result != FrontierV3SceneExecutor.BodyMaterialization.COMPLETE) return;

        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_service_work_hot", lease,
                submit(runtime, "settlement-service-work-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
    }

    private static void handoff(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        SceneMember member = lease.members().getFirst(); AmbientActorLease ambient = state.ambientLeases().get(member.actorId()); Entity entity = level.getEntity(member.entityId());
        if (ambient == null || ambient.status() != AmbientLeaseStatus.HOT || !(entity instanceof Mob body) || !body.isAlive()
                || !FrontierV3AmbientActorExecutor.owned(body, member.actorId(), false)
                || !FrontierV3SemanticMovement.arrived(level, body, lease.memberBody(state.actorLocations(), member.actorId()).supportingSurface())) return;
        BodyPosition observed = FrontierV3SurfaceObservation.observedAt(body, lease.memberBody(state.actorLocations(), member.actorId()).supportingSurface());
        // Admission uses the owner-retained actual departure origin, not an old semantic cursor.
        // Independent common inspection establishes the physical pose before process handoff.
        if (!observed.equals(lease.memberBody(state.actorLocations(), member.actorId()))) return;
        if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, body)) return;
        SceneMemberPosition capture = new SceneMemberPosition(member.actorId(), observed,
                new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(Math.round(body.getHealth() * io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE)));
        SceneLease captured = lease.withAmbientHandoff(Set.of(member.actorId()));
        var work = FrontierSettlementServiceWorkSceneSupport.require(state, FrontierSceneBehaviors.serviceWork(lease));
        var actuation = FrontierV3ActorActuation.capture(state, body,
                io.farfrontier.palemirror.frontier.v3.model.SettlementServiceExecutionAuthority.current(state, work), runtime::decodedState);
        CommandResult result = submit(runtime, "settlement-service-work-handoff", lease.id().value(), new SettlementServiceWorkSceneLeaseHandoff(captured, List.of(capture)));
        // Capture before submission: a later STOP cannot acquire a successor's authority.
        if (result instanceof CommandResult.Accepted) FrontierV3GoalNavigation.stop(body, actuation);
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_service_work_handoff", captured, result);
    }

    private static void work(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        SettlementServiceWork work = FrontierSettlementServiceWorkSceneSupport.require(state, FrontierSceneBehaviors.serviceWork(lease));
        var execution = io.farfrontier.palemirror.frontier.v3.model.SettlementServiceExecutionAuthority.current(state, work);
        // An unconfirmed endpoint still needs its exact HOT participant, not premature scope drain.
        if (state.structureConditions().get(work.facilityId()) != StructureCondition.INTACT) { drain(runtime, lease); return; }
        FrontierV3SceneDemand.Snapshot demand = FrontierV3SceneExecutor.demandSnapshot(level, FrontierSettlementServiceWorkSceneSupport.currentSurface(work).support());
        if (FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demand,
                FrontierV3SceneExecutor.playerWithinSafeRadius(level, lease))) { drain(runtime, lease); return; }
        if (!demand.active()) return;
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        if (!(entity instanceof Mob worker) || !worker.isAlive() || !FrontierV3SceneExecutor.recognizes(runtime, worker)) {
            conflict(level, runtime, lease, "worker-unavailable"); return;
        }
        var actuation = FrontierV3ActorActuation.capture(state, worker, execution, () -> runtime.decodedState().filter(
                now -> work.equals(now.serviceWorks().get(work.id())) && lease.equals(now.sceneLeases().get(lease.id()))));
        var observation = new SettlementServiceWorkObservation(new io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation(
                actuation.id(), lease.revision()), work.phase(), work.inputTraversalCursor(), work.workTraversalCursor(), work.completedWorkTicks(), work.spatial().revision());
        if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, worker) || !actuation.current(worker)) return;
        SurfaceAnchor current = FrontierSettlementServiceWorkSceneSupport.semanticSurface(work);
        if (work.spatial().pending()) {
            SurfaceAnchor target = SettlementServiceJourneyKnowledge.target(work);
            if (!FrontierV3SemanticMovement.arrived(level, worker, target)) {
                var retained = work.spatial().knownApproach(origin -> SettlementServiceJourneyKnowledge.checkpoint(state, work, origin).approach())
                        .map(value -> value.path().subList(value.cursor(), value.path().size()));
                if (retained.isPresent()) {
                    var hint = retained.orElseThrow();
                    FrontierV3GoalNavigation.pursue(level, worker, new FrontierV3GoalNavigation.Goal(List.of(target),
                            TraversalCapability.PEDESTRIAN, new FrontierV3NavigationScope.RetainedApproach(hint),
                            Optional.empty(), hint), actuation);
                }
                return; // Unknown geometry retains the same exact input/target, without fabricated progress.
            }
            if (isTraversalPhase(work.phase())) {
                submit(runtime, "settlement-service-work-traversal", lease.id().value(),
                        new SettlementServiceWorkTraversalAdvanced(work.id(), lease.id(),
                                FrontierV3SurfaceObservation.observedAt(worker, target), traversalCursor(work) + 1, observation));
                return;
            }
            // At input/work station, the resource or labour owner clears the approach in its receipt.
        }
        if (!FrontierV3SemanticMovement.arrived(level, worker, current)) {
            // The server can stop after the normal entity pre-tick has physically completed one
            // retained edge but before the executor's next canonical turn writes its cursor.
            // On recovery that exact next surface is an observed one-edge arrival, not a route
            // repair.  It is intentionally as narrow as the production-work rule: no further
            // cursor, side cell or alternate station may be inferred from an observed body.
            if (isTraversalPhase(work.phase())) {
                List<SurfaceAnchor> corridor = traversal(work);
                int cursor = traversalCursor(work);
                if (cursor < corridor.size() - 1 && FrontierV3SemanticMovement.arrived(level, worker, corridor.get(cursor + 1))) {
                    submit(runtime, "settlement-service-work-traversal", lease.id().value(),
                            new SettlementServiceWorkTraversalAdvanced(work.id(), lease.id(),
                                    FrontierV3SurfaceObservation.observedAt(worker, corridor.get(cursor + 1)), cursor + 1, observation));
                } else if (cursor < corridor.size() - 1 && FrontierV3SemanticMovement.withinRetainedEdgeEnvelope(
                        level, worker, current, corridor.get(cursor + 1))) {
                    // Common pose may be between semantic checkpoints while vanilla crosses the retained edge.
                    // Continue that edge; only exact next-station observation may advance the family cursor.
                    FrontierV3GoalNavigation.pursueRetainedEdge(level, worker, current, corridor.get(cursor + 1), actuation);
                } else conflict(level, runtime, lease, "cursor-" + FrontierV3SemanticMovement.detail(FrontierV3SemanticMovement.at(level, worker, current)));
            } else conflict(level, runtime, lease, "cursor-" + FrontierV3SemanticMovement.detail(FrontierV3SemanticMovement.at(level, worker, current)));
            return;
        }
        if (work.phase() == SettlementServiceWorkPhase.INPUT_ISSUE_PENDING || work.phase() == SettlementServiceWorkPhase.EFFECT_READY) return;
        if (isTraversalPhase(work.phase())) {
            List<SurfaceAnchor> corridor = traversal(work);
            int cursor = traversalCursor(work);
            if (cursor >= corridor.size() - 1) { conflict(level, runtime, lease, "uncommitted-station-arrival"); return; }
            SurfaceAnchor next = corridor.get(cursor + 1);
            if (FrontierV3SemanticMovement.arrived(level, worker, next)) {
                submit(runtime, "settlement-service-work-traversal", lease.id().value(),
                        new SettlementServiceWorkTraversalAdvanced(work.id(), lease.id(), FrontierV3SurfaceObservation.observedAt(worker, next), cursor + 1, observation));
            } else if (!FrontierV3SemanticMovement.targetIsNavigable(level, worker, next)) {
                submit(runtime, "settlement-service-work-route-blocked", lease.id().value(),
                        new SettlementServiceWorkTraversalBlocked(work.id(), lease.id(), FrontierV3SurfaceObservation.observedAt(worker, current), cursor + 1, observation));
            } else FrontierV3GoalNavigation.pursueRetainedEdge(level, worker, current, next, actuation);
            return;
        }
        if (work.phase() != SettlementServiceWorkPhase.WORKING) { conflict(level, runtime, lease, "unsupported-work-phase"); return; }
        SettlementServiceWorkPhase next = work.completedWorkTicks() + 1 == SettlementServiceWork.REQUIRED_WORK_TICKS
                ? SettlementServiceWorkPhase.EFFECT_READY : SettlementServiceWorkPhase.WORKING;
        int ticks = next == SettlementServiceWorkPhase.EFFECT_READY ? 0 : work.completedWorkTicks() + 1;
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        submit(runtime, "settlement-service-work-progress", lease.id().value(),
                new SettlementServiceWorkProgressed(work.id(), lease.id(), FrontierV3SurfaceObservation.observedAt(worker, current), next, ticks, observation));
    }

    private static boolean isTraversalPhase(SettlementServiceWorkPhase phase) {
        return phase == SettlementServiceWorkPhase.PREPARED || phase == SettlementServiceWorkPhase.APPROACH_INPUT
                || phase == SettlementServiceWorkPhase.APPROACH_WORK;
    }
    private static List<SurfaceAnchor> traversal(SettlementServiceWork work) {
        return work.phase() == SettlementServiceWorkPhase.APPROACH_WORK ? work.workTraversal().linearCorridorSurfaces()
                : work.inputTraversal().linearCorridorSurfaces();
    }
    private static int traversalCursor(SettlementServiceWork work) {
        return work.phase() == SettlementServiceWorkPhase.APPROACH_WORK ? work.workTraversalCursor() : work.inputTraversalCursor();
    }
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
