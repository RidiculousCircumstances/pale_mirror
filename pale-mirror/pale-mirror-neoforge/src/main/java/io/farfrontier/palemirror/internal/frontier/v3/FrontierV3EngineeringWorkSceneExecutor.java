package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.RouteConstructionProcess;
import io.farfrontier.palemirror.frontier.v3.process.RouteMaintenanceProcess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Naturally loaded HOT projection of the exact COLD-ready engineering crew.
 *
 * <p>This executor cannot select people, choose cells, or place blocks directly. It has only two
 * authorities: maintain the exact scene bodies, then durably request the one physical intent for
 * its active cell. The physical executor remains the sole Minecraft block writer.</p>
 */
final class FrontierV3EngineeringWorkSceneExecutor {
    private FrontierV3EngineeringWorkSceneExecutor() { }

    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        return FrontierV3SceneTurnScheduler.run(runtime, state, io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind.ENGINEERING_WORKSITE,
                lease -> execute(level, runtime, state, lease), () -> admit(level, runtime, state));
    }

    private static boolean admit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Optional<EngineeringWorkSceneCandidate> candidate = FrontierV3SceneDemand.nextDemandedCandidate(
                level, runtime, io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind.ENGINEERING_WORKSITE, FrontierEngineeringWorkSceneSupport.candidates(state), EngineeringWorkSceneCandidate::workCell,
                        EngineeringWorkSceneCandidate::projectId);
        if (candidate.isEmpty()) return false;
        EngineeringWorkSceneCandidate work = candidate.orElseThrow();
        SceneLease lease = lease(runtime, work);
        if (FrontierSceneAdmission.available(state, work.memberPositions().keySet())) {
            submit(runtime, "engineering-scene-prepare", lease.id().value(), new EngineeringWorkSceneLeasePrepared(lease));
        }
        // A partial ambient hand-off waits deliberately: the next natural loaded tick will
        // observe the exact bodies again rather than manufacturing replacements.
        return true;
    }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, EngineeringWorkSceneCandidate candidate) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        String suffix = candidate.projectId().value().replace(':', '-');
        SceneLeaseId id = new SceneLeaseId("lease:engineering-" + suffix + "-cell-" + candidate.workCellIndex() + "-r" + checkpoint.revision().value());
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted()
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(checkpoint.worldId(), id, actor))).toList();
        return SceneLease.forCause(id, checkpoint.worldId(), new EngineeringWorkSceneCause(candidate.projectId(), candidate.workCellIndex()),
                candidate.workCell(), checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED,
                members, SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), Set.of(), Optional.empty());
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

    private static void materialize(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.BodyMaterialization result = FrontierV3ActorCarrierFactory.materializeSceneBodies(FrontierV3ActorCarrierComposition.InventoryEntry.ENGINEERING_WORK, level, state, lease);
        if (result == FrontierV3SceneExecutor.BodyMaterialization.COMPLETE) {
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "engineering_worksite_hot", lease,
                    submit(runtime, "engineering-scene-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
        } else if (result == FrontierV3SceneExecutor.BodyMaterialization.CONFLICT) {
            conflict(level, runtime, lease, "prepared-body-conflict");
        }
    }

    private static void work(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                             FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneDemand.Snapshot demand = FrontierV3SceneExecutor.demandSnapshot(level, lease.handoffPosition());
        if (FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demand,
                FrontierV3SceneExecutor.playerWithinSafeRadius(level, lease))) {
            submit(runtime, "engineering-scene-draining", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        if (!demand.active()) return;
        for (SceneMember member : lease.members()) {
            Entity entity = level.getEntity(member.entityId());
            if (!(entity instanceof Mob mob) || !FrontierV3SceneExecutor.recognizes(runtime, entity)) {
                conflict(level, runtime, lease, "hot-body-unavailable"); return;
            }
            BodyPosition slot = lease.memberPosition(member.actorId());
            FrontierV3GoalNavigation.pursue(level, mob, FrontierV3GoalNavigation.Goal.station(slot.supportingSurface(),
                    new FrontierV3NavigationScope.ObservedWorld(state.bootstrap().bounds())));
            if (level.getGameTime() % 12L == 0L) mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }

        if (state.physicalIntents().values().stream().anyMatch(intent -> (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_CONSTRUCTION
                && intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ROUTE_CONSTRUCTION_PROJECT).equals(FrontierSceneBehaviors.engineeringWorksite(lease).projectId())
                || intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE
                && intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ROUTE_MAINTENANCE).equals(FrontierSceneBehaviors.engineeringWorksite(lease).projectId()))
                && (intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED
                || intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING))) return;
        EngineeringWorkSceneCause cause = FrontierSceneBehaviors.engineeringWorksite(lease);
        EngineeringWorkOrder project;
        try { project = EngineeringWorkOrderSupport.require(state, cause.projectId()); }
        catch (IllegalArgumentException absent) { return; }
        if (project == null || project.cargoId().isEmpty() || project.confirmedCells() != cause.workCellIndex()) return;
        SubjectId cargo = project.cargoId().orElseThrow();
        var batch = state.inventory().cargo().get(cargo);
        if (batch == null || batch.itemIds().size() != 1) { conflict(level, runtime, lease, "work-cargo-unavailable"); return; }
        submit(runtime, "engineering-work-intent", lease.id().value(), new PhysicalIntentPrepared(workIntent(project, cargo, batch.itemIds().getFirst())));
    }

    private static io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent workIntent(EngineeringWorkOrder project, SubjectId cargo, SubjectId item) {
        return switch (project) {
            case RouteConstruction construction -> RouteConstructionProcess.workIntent(construction, cargo, item);
            case RouteMaintenance maintenance -> RouteMaintenanceProcess.workIntent(maintenance, cargo, item);
        };
    }

    private static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, String reason) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "engineering_worksite_conflict:" + reason, lease,
                submit(runtime, "engineering-scene-conflict", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT)));
    }

    private static io.farfrontier.palemirror.frontier.v3.api.CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                                    String phase, String id, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return FrontierV3CommandSubmission.submit(runtime, phase, id, payload);
    }
}
