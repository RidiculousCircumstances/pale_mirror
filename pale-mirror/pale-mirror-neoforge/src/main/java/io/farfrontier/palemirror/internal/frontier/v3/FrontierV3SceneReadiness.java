package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSettlementServiceWorkSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Read-only scene diagnosis; it has no materialization, movement or canonical mutation authority. */
final class FrontierV3SceneReadiness {
    record Value(String bodies, String carrier, String serviceDemand, String serviceMotion, String serviceInput, List<String> members) {
        Value { members = List.copyOf(members); }
    }

    private FrontierV3SceneReadiness() { }

    static Optional<Value> forSubject(ServerLevel level, FrontierWorldState state, SubjectId sceneSubject) {
        return currentLease(state, sceneSubject).map(lease -> forLease(level, state, lease));
    }

    static Value forLease(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        String carrier = FrontierV3SceneBehaviorRegistry.hasCargoCarrier(lease)
                ? FrontierV3CargoCarrierExecutor.readiness(level, state, lease).name() : "NOT_APPLICABLE";
        return new Value(bodyReadiness(level, state, lease), carrier, serviceDemand(level, state, lease),
                serviceMotion(level, state, lease), serviceInput(level, state, lease), observedMembers(level, lease));
    }

    private static Optional<SceneLease> currentLease(FrontierWorldState state, SubjectId sceneSubject) {
        return state.sceneLeases().values().stream().filter(lease -> FrontierSceneBehaviors.owns(lease, sceneSubject))
                .max(Comparator.comparingInt((SceneLease lease) -> lease.status() == SceneLeaseStatus.CLOSED ? 0 : 1)
                        .thenComparing(SceneLease::handoffInstant).thenComparingLong(SceneLease::revision).thenComparing(SceneLease::id));
    }

    private static String bodyReadiness(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        if (lease.status() == SceneLeaseStatus.CLOSED) return "CLOSED";
        boolean allCurrent = true;
        for (SceneMember member : lease.members()) {
            Entity existing = level.getEntity(member.entityId());
            if (existing != null) {
                if (FrontierV3SceneExecutor.owned(existing, state, lease, member)) continue;
                return "UUID_CONFLICT";
            }
            allCurrent = false;
            if (lease.ambientHandoffActorIds().contains(member.actorId())) return "AWAITING_AMBIENT_HANDOFF";
            BodyPosition canonical = lease.memberPosition(member.actorId());
            BlockPos candidate = new BlockPos(canonical.x(), canonical.y() - 1, canonical.z());
            if (!level.hasChunkAt(candidate)) return "UNLOADED";
            if (FrontierV3StandingPosition.aboveExactFloor(level, candidate) == null) return "AWAITING_EXACT_FLOOR";
        }
        return allCurrent ? "CURRENT" : "READY";
    }

    private static List<String> observedMembers(ServerLevel level, SceneLease lease) {
        return lease.members().stream().map(member -> {
            Entity entity = level.getEntity(member.entityId());
            return entity == null ? member.actorId().value() + "@absent"
                    : member.actorId().value() + "@" + entity.getBlockX() + "," + entity.getBlockY() + "," + entity.getBlockZ();
        }).toList();
    }

    private static String serviceDemand(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        if (!FrontierSceneBehaviors.isServiceWork(lease)) return "NOT_APPLICABLE";
        var work = state.serviceWorks().get(FrontierSceneBehaviors.serviceWork(lease).workId());
        if (work == null) return "WORK_MISSING";
        var surface = FrontierSettlementServiceWorkSceneSupport.currentSurface(work).support();
        BlockPos position = new BlockPos(surface.x(), surface.y(), surface.z());
        if (!level.hasChunkAt(position)) return "UNLOADED";
        return level.players().stream().filter(player -> !player.isSpectator()).anyMatch(player -> player.blockPosition().closerThan(position, 96)) ? "CURRENT" : "NO_PLAYER";
    }

    private static String serviceMotion(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        if (!FrontierSceneBehaviors.isServiceWork(lease)) return "NOT_APPLICABLE";
        var work = state.serviceWorks().get(FrontierSceneBehaviors.serviceWork(lease).workId());
        if (work == null) return "WORK_MISSING";
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        if (!(entity instanceof Mob worker) || !worker.isAlive()) return "WORKER_UNAVAILABLE";
        if (!FrontierV3SurfaceObservation.at(worker, FrontierSettlementServiceWorkSceneSupport.currentSurface(work))) return "BODY_OFF_CURSOR";
        return FrontierV3ControlledMobMotion.readiness(worker);
    }

    private static String serviceInput(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        if (!FrontierSceneBehaviors.isServiceWork(lease)) return "NOT_APPLICABLE";
        var work = state.serviceWorks().get(FrontierSceneBehaviors.serviceWork(lease).workId());
        if (work == null) return "WORK_MISSING";
        var intent = state.physicalIntents().get(work.inputIssueIntentId());
        return intent == null ? "INTENT_MISSING" : FrontierV3SettlementServiceInputIssueExecutor.diagnosticReadiness(level, state, intent);
    }
}
