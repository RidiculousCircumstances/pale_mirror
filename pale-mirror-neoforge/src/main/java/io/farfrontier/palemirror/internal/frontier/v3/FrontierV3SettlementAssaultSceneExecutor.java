package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseRecoveryUnresolved;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCandidate;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneLeaseHandoff;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneLeasePrepared;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loaded-chunk executor for the cargo-free, typed settlement-assault scene.
 *
 * <p>It deliberately has no route-operation or carrier path. The only state it may create is a
 * typed lease whose exact floor map was compiled by the canonical assault process. Minecraft
 * then supplies movement, melee and death; the existing durable strike/death boundaries retain
 * the resulting canonical consequences.</p>
 */
final class FrontierV3SettlementAssaultSceneExecutor {
    private static final int DEMAND_RADIUS_BLOCKS = 96;
    private static final int DRAIN_SAFE_RADIUS_BLOCKS = 64;
    private static final int MAX_OBSERVED_SCENES = 4_096;
    private static final long RECOVERY_INSPECTION_WINDOW_TICKS = 400L;
    /** Same-runtime hand-off evidence only; restart deliberately clears it before inspection. */
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SceneLeaseId, List<SceneMemberPosition>>> LAST_OBSERVED = new IdentityHashMap<>();
    /** Volatile inspection start only; canonical evidence is emitted after the bounded window. */
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SceneLeaseId, Long>> RECOVERY_INSPECTION_STARTED = new IdentityHashMap<>();

    private FrontierV3SettlementAssaultSceneExecutor() { }

    /** @return true when a typed assault scene owns this materialization turn. */
    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = state(runtime);
        if (state == null) return false;
        forgetInactive(runtime, state);
        Optional<SceneLease> active = state.sceneLeases().values().stream().filter(FrontierV3SettlementAssaultSceneExecutor::isAssault)
                .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED && lease.status() != SceneLeaseStatus.CONFLICT)
                .sorted(Comparator.comparing(SceneLease::id)).findFirst();
        if (active.isPresent()) {
            execute(level, runtime, state, active.orElseThrow());
            return true;
        }
        Optional<SettlementAssaultSceneCandidate> candidate = state.coldSettlementAssaultSceneCandidates().stream()
                .filter(value -> demandExists(level, value.handoffPosition())).findFirst();
        if (candidate.isEmpty()) return false;
        SettlementAssaultSceneCandidate battle = candidate.orElseThrow();
        SceneLease lease = lease(runtime, battle);
        if (FrontierSceneAdmission.available(state, battle.memberPositions().keySet())) prepare(level, runtime, lease);
        else handoff(level, runtime, state, lease);
        return true;
    }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SettlementAssaultSceneCandidate candidate) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        String suffix = candidate.assaultId().value().substring("assault:".length());
        SceneLeaseId id = new SceneLeaseId("lease:assault-" + suffix + "-r" + checkpoint.revision().value());
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted()
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(checkpoint.worldId(), actor))).toList();
        return SceneLease.forCause(id, checkpoint.worldId(), new SettlementAssaultSceneCause(candidate.assaultId(), candidate.settlementId()),
                candidate.handoffPosition(), checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED, members,
                candidate.memberPositions(), Set.of(), Optional.empty());
    }

    private static void prepare(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_assault_prepared", lease,
                submit(runtime, "settlement-assault-prepare", new SettlementAssaultSceneLeasePrepared(lease)));
    }

    /** Claims only an existing exact ambient body; a partial hand-off waits rather than cloning it. */
    private static void handoff(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        List<SceneMemberPosition> captures = new ArrayList<>();
        for (SceneMember member : lease.members()) {
            var ambient = state.ambientLeases().get(member.actorId());
            if (ambient == null || ambient.status() == AmbientLeaseStatus.CLOSED) continue;
            if (ambient.status() != AmbientLeaseStatus.HOT) return;
            Entity body = level.getEntity(member.entityId());
            if (!(body instanceof Mob mob) || !mob.isAlive()
                    || !FrontierV3AmbientActorExecutor.owned(body, member.actorId(), bioform(state, member.actorId()))) return;
            captures.add(new SceneMemberPosition(member.actorId(), at(body), fixed(mob.getHealth())));
        }
        if (captures.isEmpty()) return;
        Map<SubjectId, BlockPosition> positions = new LinkedHashMap<>(lease.memberPositions());
        captures.forEach(capture -> positions.put(capture.actorId(), capture.position()));
        SceneLease handed = lease.withMemberPositions(positions).withAmbientHandoff(captures.stream()
                .map(SceneMemberPosition::actorId).collect(java.util.stream.Collectors.toSet()));
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_assault_handoff", handed,
                submit(runtime, "settlement-assault-handoff", new SettlementAssaultSceneLeaseHandoff(handed, captures)));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        switch (lease.status()) {
            case PREPARED -> materializePrepared(level, runtime, state, lease);
            case HOT -> executeHot(level, runtime, state, lease);
            case DRAINING -> release(level, runtime, state, lease);
            case UNKNOWN_AFTER_RESTART -> reclaim(level, runtime, state, lease);
            case CONFLICT, CLOSED -> { }
        }
    }

    private static void materializePrepared(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                            FrontierWorldState state, SceneLease lease) {
        FrontierV3SceneExecutor.BodyMaterialization result = FrontierV3SceneExecutor.materializeBodies(level, state, lease);
        if (result == FrontierV3SceneExecutor.BodyMaterialization.COMPLETE) {
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_assault_hot", lease,
                    submit(runtime, "settlement-assault-hot", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
        } else if (result == FrontierV3SceneExecutor.BodyMaterialization.CONFLICT) conflict(level, runtime, lease, "prepared-body-conflict");
    }

    private static void executeHot(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                   FrontierWorldState state, SceneLease lease) {
        boolean demand = demandExists(level, lease.handoffPosition());
        if (FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demand, playerWithinSafeRadius(level, lease))) {
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_assault_draining", lease,
                    submit(runtime, "settlement-assault-draining", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING)));
            return;
        }
        if (!demand) return;
        List<Body> bodies = bodies(level, runtime, state, lease);
        if (bodies.size() != lease.members().size()) {
            conflict(level, runtime, lease, "hot-body-unavailable");
            return;
        }
        for (Body actor : bodies) FrontierV3ControlledMobMotion.moveToward(level, actor.mob(), target(state, actor, bodies));
        if (level.getGameTime() % 20L == 0L) {
            forgetObserved(runtime, lease.id());
            FrontierV3SceneExecutor.executeStrike(level, runtime, state, lease);
        }
        rememberObserved(level, runtime, state, lease);
    }

    private static void reclaim(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        if (!demandExists(level, lease.handoffPosition()) || lease.recoveryEvidence().isPresent()) return;
        if (completeOwnedBodySet(level, runtime, state, lease)) {
            forgetRecoveryInspection(runtime, lease.id());
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_assault_reclaimed", lease,
                    submit(runtime, "settlement-assault-reclaimed", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
            return;
        }
        long started = recoveryInspectionStarted(runtime, lease.id(), level.getGameTime());
        if (level.getGameTime() - started < RECOVERY_INSPECTION_WINDOW_TICKS) return;
        Set<SubjectId> missing = lease.members().stream().filter(member -> state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(member -> !owns(runtime, level.getEntity(member.entityId()), member)).map(SceneMember::actorId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!missing.isEmpty()) {
            submit(runtime, "settlement-assault-recovery-unresolved", new SceneLeaseRecoveryUnresolved(lease.id(), missing, false));
            return;
        }
    }

    private static boolean completeOwnedBodySet(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                FrontierWorldState state, SceneLease lease) {
        return lease.members().stream().filter(member -> state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.ALIVE)
                .allMatch(member -> owns(runtime, level.getEntity(member.entityId()), member));
    }

    private static void release(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, SceneLease lease) {
        if (!level.hasChunkAt(new BlockPos(lease.handoffPosition().x(), lease.handoffPosition().y(), lease.handoffPosition().z()))) {
            releaseWhenUnloaded(level, runtime, lease);
            return;
        }
        List<SceneMemberPosition> captured = new ArrayList<>();
        for (SceneMember member : lease.members()) {
            if (state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.DEAD) continue;
            Entity entity = level.getEntity(member.entityId());
            if (!(entity instanceof Mob mob) || !mob.isAlive() || !owns(runtime, entity, member)) {
                conflict(level, runtime, lease, "release-body-unavailable");
                return;
            }
            captured.add(new SceneMemberPosition(member.actorId(), at(mob), fixed(mob.getHealth())));
        }
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_assault_released", lease,
                submit(runtime, "settlement-assault-release", new SceneLeaseReleased(lease.id(), captured)));
        forgetObserved(runtime, lease.id());
    }

    private static List<Body> bodies(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                     FrontierWorldState state, SceneLease lease) {
        return lease.members().stream().map(member -> {
            Entity entity = level.getEntity(member.entityId());
            return entity instanceof Mob mob && mob.isAlive() && owns(runtime, mob, member)
                    ? new Body(member, mob, bioform(state, member.actorId())) : null;
        }).filter(java.util.Objects::nonNull).sorted(Comparator.comparing(value -> value.member().actorId())).toList();
    }

    private static Vec3 target(FrontierWorldState state, Body actor, List<Body> bodies) {
        Body opponent = bodies.stream().filter(value -> value.bioform() != actor.bioform()).min(Comparator
                .comparingDouble((Body value) -> actor.mob().distanceToSqr(value.mob())).thenComparing(value -> value.member().actorId())).orElse(null);
        if (opponent == null) return actor.mob().position();
        Vec3 delta = opponent.mob().position().subtract(actor.mob().position());
        if (actor.bioform() || residentGuard(state, actor.member().actorId())) return opponent.mob().position();
        return delta.horizontalDistanceSqr() > 0.0001D ? actor.mob().position().subtract(delta.normalize().scale(5.0D)) : actor.mob().position();
    }

    private static boolean residentGuard(FrontierWorldState state, SubjectId actorId) {
        return state.humanPopulation().resident(actorId) != null && state.humanPopulation().resident(actorId).role()
                == io.farfrontier.palemirror.frontier.v3.model.ResidentRole.GUARD;
    }

    private static boolean demandExists(ServerLevel level, BlockPosition anchor) {
        BlockPos position = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        return level.hasChunkAt(position) && level.players().stream().filter(player -> !player.isSpectator())
                .anyMatch(player -> player.blockPosition().closerThan(position, DEMAND_RADIUS_BLOCKS));
    }

    private static boolean playerWithinSafeRadius(ServerLevel level, SceneLease lease) {
        List<BlockPos> positions = new ArrayList<>(List.of(new BlockPos(lease.handoffPosition().x(), lease.handoffPosition().y(), lease.handoffPosition().z())));
        for (SceneMember member : lease.members()) {
            Entity entity = level.getEntity(member.entityId());
            if (entity != null) positions.add(entity.blockPosition());
        }
        return level.players().stream().filter(player -> !player.isSpectator())
                .anyMatch(player -> positions.stream().anyMatch(position -> player.blockPosition().closerThan(position, DRAIN_SAFE_RADIUS_BLOCKS)));
    }

    private static boolean owns(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity, SceneMember member) {
        return entity != null && member.entityId().equals(entity.getUUID()) && FrontierV3SceneExecutor.recognizes(runtime, entity);
    }

    private static boolean bioform(FrontierWorldState state, SubjectId actor) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .anyMatch(value -> value.id().equals(actor));
    }

    private static void rememberObserved(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                         FrontierWorldState state, SceneLease lease) {
        if (!level.hasChunkAt(new BlockPos(lease.handoffPosition().x(), lease.handoffPosition().y(), lease.handoffPosition().z()))) return;
        List<SceneMemberPosition> captured = new ArrayList<>();
        for (SceneMember member : lease.members()) {
            if (state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.DEAD) continue;
            Entity entity = level.getEntity(member.entityId());
            if (!(entity instanceof Mob mob) || !mob.isAlive() || !owns(runtime, mob, member)) {
                forgetObserved(runtime, lease.id());
                return;
            }
            captured.add(new SceneMemberPosition(member.actorId(), at(mob), fixed(mob.getHealth())));
        }
        Map<SceneLeaseId, List<SceneMemberPosition>> retained = LAST_OBSERVED.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        if (retained.size() < MAX_OBSERVED_SCENES || retained.containsKey(lease.id())) retained.put(lease.id(), List.copyOf(captured));
    }

    private static void releaseWhenUnloaded(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        List<SceneMemberPosition> captured = lastObserved(runtime, lease.id());
        if (captured == null) return;
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_assault_released_unloaded", lease,
                submit(runtime, "settlement-assault-release-unloaded", new SceneLeaseReleased(lease.id(), captured)));
        forgetObserved(runtime, lease.id());
    }

    private static List<SceneMemberPosition> lastObserved(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId) {
        Map<SceneLeaseId, List<SceneMemberPosition>> retained = LAST_OBSERVED.get(runtime);
        return retained == null ? null : retained.get(leaseId);
    }

    private static void forgetObserved(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId) {
        Map<SceneLeaseId, List<SceneMemberPosition>> retained = LAST_OBSERVED.get(runtime);
        if (retained == null) return;
        retained.remove(leaseId);
        if (retained.isEmpty()) LAST_OBSERVED.remove(runtime);
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        LAST_OBSERVED.remove(runtime); RECOVERY_INSPECTION_STARTED.remove(runtime);
    }

    private static void forgetInactive(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Map<SceneLeaseId, List<SceneMemberPosition>> retained = LAST_OBSERVED.get(runtime);
        if (retained != null) {
            retained.keySet().removeIf(id -> {
                SceneLease lease = state.sceneLeases().get(id);
                return lease == null || !isAssault(lease) || (lease.status() != SceneLeaseStatus.HOT && lease.status() != SceneLeaseStatus.DRAINING);
            });
            if (retained.isEmpty()) LAST_OBSERVED.remove(runtime);
        }
        Map<SceneLeaseId, Long> inspections = RECOVERY_INSPECTION_STARTED.get(runtime);
        if (inspections == null) return;
        inspections.keySet().removeIf(id -> {
            SceneLease lease = state.sceneLeases().get(id);
            return lease == null || !isAssault(lease) || lease.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART;
        });
        if (inspections.isEmpty()) RECOVERY_INSPECTION_STARTED.remove(runtime);
    }

    private static long recoveryInspectionStarted(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId, long gameTime) {
        Map<SceneLeaseId, Long> inspections = RECOVERY_INSPECTION_STARTED.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        Long started = inspections.get(leaseId);
        if (started != null) return started;
        if (inspections.size() >= MAX_OBSERVED_SCENES) return gameTime;
        inspections.put(leaseId, gameTime);
        return gameTime;
    }

    private static void forgetRecoveryInspection(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId) {
        Map<SceneLeaseId, Long> inspections = RECOVERY_INSPECTION_STARTED.get(runtime);
        if (inspections == null) return;
        inspections.remove(leaseId);
        if (inspections.isEmpty()) RECOVERY_INSPECTION_STARTED.remove(runtime);
    }

    private static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, String reason) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "settlement_assault_conflict:" + reason, lease,
                submit(runtime, "settlement-assault-conflict", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT)));
    }

    private static BlockPosition at(Entity entity) { return new BlockPosition(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()); }
    private static FixedScalar fixed(float health) { return new FixedScalar(Math.max(0L, Math.round(health * FixedScalar.SCALE))); }
    private static boolean isAssault(SceneLease lease) { return io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.isSettlementAssault(lease); }
    private static CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase,
                                        io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return FrontierV3CommandSubmission.submit(runtime, phase, "settlement-assault", payload);
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) { return runtime.decodedState().orElse(null); }
    private record Body(SceneMember member, Mob mob, boolean bioform) { }
}
