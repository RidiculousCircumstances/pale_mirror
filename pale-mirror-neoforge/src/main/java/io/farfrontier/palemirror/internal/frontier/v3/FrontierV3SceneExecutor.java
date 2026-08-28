package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorDied;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Loaded-chunk HOT executor for exact route participants.
 *
 * <p>A persisted lease is the authority boundary. PREPARED may safely finish materialization of
 * its deterministic bodies after a restart; HOT never respawns a missing body and instead enters
 * the observable unknown state. This executor neither loads chunks nor writes world blocks.</p>
 */
final class FrontierV3SceneExecutor {
    static final String LEASE_KEY = "pale_mirror_frontier_v3_scene_lease";
    static final String ACTOR_KEY = "pale_mirror_frontier_v3_scene_actor";
    static final String REVISION_KEY = "pale_mirror_frontier_v3_scene_revision";
    private static final int DEMAND_RADIUS_BLOCKS = 96;

    enum BodyMaterialization { COMPLETE, DEFERRED, CONFLICT }

    private FrontierV3SceneExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = state(runtime);
        if (state == null) return;
        Optional<RouteOperation> demand = state.operations().values().stream().sorted(Comparator.comparing(RouteOperation::id))
                .filter(operation -> operation.stage() == io.farfrontier.palemirror.frontier.v3.model.OperationStage.EN_ROUTE)
                .filter(operation -> state.sceneLeases().values().stream().noneMatch(lease -> lease.operationId().equals(operation.id())
                        && lease.status() != SceneLeaseStatus.CLOSED))
                .filter(operation -> demandExists(level, operation.route().get(operation.routeIndex()))).findFirst();
        if (demand.isPresent()) {
            prepare(runtime, state, demand.orElseThrow());
            return;
        }
        state.sceneLeases().values().stream().sorted(Comparator.comparing(SceneLease::id)).filter(lease -> lease.status() != SceneLeaseStatus.CLOSED)
                .findFirst().ifPresent(lease -> execute(level, runtime, lease));
        cleanReleasedBodies(level, state);
    }

    private static void prepare(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, RouteOperation operation) {
        CheckpointImage checkpoint = checkpoint(runtime);
        SceneLeaseId id = new SceneLeaseId("lease:" + operation.id().value().substring("operation:".length()) + "-r" + checkpoint.revision().value());
        List<SceneMember> members = operation.participantIds().stream().sorted().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(id, actor))).toList();
        SceneLease lease = new SceneLease(id, operation.id(), operation.cargoId(), operation.route().get(operation.routeIndex()), checkpoint.instant(), checkpoint.revision().value(),
                SceneLeaseStatus.PREPARED, members);
        submit(runtime, "scene-prepare", id.value(), new SceneLeasePrepared(lease));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        switch (lease.status()) {
            case PREPARED -> materializePrepared(level, runtime, lease);
            case HOT -> {
                if (!demandExists(level, lease.handoffPosition())) submit(runtime, "scene-draining", lease.id().value(),
                        new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            }
            case DRAINING -> release(level, runtime, lease);
            case UNKNOWN_AFTER_RESTART, CLOSED -> { }
        }
    }

    private static void materializePrepared(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        BodyMaterialization result = materializeBodies(level, lease);
        if (result == BodyMaterialization.COMPLETE) {
            submit(runtime, "scene-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        } else if (result == BodyMaterialization.CONFLICT) {
            unknown(runtime, lease);
        }
    }

    static BodyMaterialization materializeBodies(ServerLevel level, SceneLease lease) {
        for (int index = 0; index < lease.members().size(); index++) {
            SceneMember member = lease.members().get(index);
            Entity existing = level.getEntity(member.entityId());
            if (existing != null) {
                if (!owned(existing, lease, member)) return BodyMaterialization.CONFLICT;
                continue;
            }
            BlockPos candidate = spawnCandidate(lease.handoffPosition(), index);
            if (!level.hasChunkAt(candidate)) return BodyMaterialization.DEFERRED;
            BlockPos position = spawnPosition(level, candidate);
            if (position == null) return BodyMaterialization.CONFLICT;
            Villager villager = EntityType.VILLAGER.create(level);
            if (villager == null) throw new IllegalStateException("Minecraft could not create a Frontier v3 Villager");
            villager.setUUID(member.entityId());
            villager.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
            villager.setPersistenceRequired();
            villager.setCustomName(Component.literal("Frontier " + member.actorId().value()));
            villager.setCustomNameVisible(true);
            mark(villager, lease, member);
            if (!level.addFreshEntity(villager)) return BodyMaterialization.CONFLICT;
        }
        return BodyMaterialization.COMPLETE;
    }

    /** Accepts only an actual loaded-world death of a body owned by the active HOT lease. */
    static boolean observeDeath(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity, Entity source) {
        FrontierWorldState state = state(runtime);
        if (state == null) return false;
        Optional<SceneLease> matchingLease = state.sceneLeases().values().stream()
                .filter(lease -> lease.status() == SceneLeaseStatus.HOT)
                .filter(lease -> lease.members().stream().anyMatch(member -> member.entityId().equals(entity.getUUID()) && owned(entity, lease, member)))
                .findFirst();
        if (matchingLease.isEmpty()) return false;
        SceneLease lease = matchingLease.orElseThrow();
        SceneMember member = lease.members().stream().filter(candidate -> candidate.entityId().equals(entity.getUUID())).findFirst().orElseThrow();
        String cause = source == null ? "environment" : "entity:" + source.getUUID();
        submit(runtime, "scene-death", lease.id().value() + "-" + member.actorId().value(),
                new ActorDied(lease.id(), member.actorId(), new BlockPosition(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()), cause));
        return true;
    }

    private static void release(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        List<SceneMemberPosition> positions = new ArrayList<>();
        for (SceneMember member : lease.members()) {
            Entity entity = level.getEntity(member.entityId());
            if (!owned(entity, lease, member)) { unknown(runtime, lease); return; }
            if (!(entity instanceof Villager villager) || villager.getHealth() <= 0.0F) { unknown(runtime, lease); return; }
            long health = Math.round((double) villager.getHealth() * FixedScalar.SCALE);
            positions.add(new SceneMemberPosition(member.actorId(), new BlockPosition(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()), new FixedScalar(health)));
        }
        submit(runtime, "scene-release", lease.id().value(), new SceneLeaseReleased(lease.id(), positions));
    }

    private static void cleanReleasedBodies(ServerLevel level, FrontierWorldState state) {
        state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.CLOSED).forEach(lease -> lease.members().forEach(member -> {
            Entity entity = level.getEntity(member.entityId());
            if (owned(entity, lease, member)) entity.discard();
        }));
    }

    private static boolean demandExists(ServerLevel level, BlockPosition anchor) {
        BlockPos position = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        return level.hasChunkAt(position) && level.players().stream().filter(player -> !player.isSpectator())
                .anyMatch(player -> player.blockPosition().closerThan(position, DEMAND_RADIUS_BLOCKS));
    }

    private static BlockPos spawnCandidate(BlockPosition anchor, int ordinal) {
        return new BlockPos(anchor.x() + (ordinal % 2) * 2, anchor.y(), anchor.z() + (ordinal / 2) * 2);
    }
    private static BlockPos spawnPosition(ServerLevel level, BlockPos position) {
        if (!level.getBlockState(position).isAir() || !level.getBlockState(position.above()).isAir()
                || !level.getBlockState(position.below()).isFaceSturdy(level, position.below(), Direction.UP)) return null;
        return position;
    }

    private static boolean owned(Entity entity, SceneLease lease, SceneMember member) {
        return entity instanceof Villager && !entity.isRemoved() && member.entityId().equals(entity.getUUID())
                && lease.id().value().equals(entity.getPersistentData().getString(LEASE_KEY))
                && member.actorId().value().equals(entity.getPersistentData().getString(ACTOR_KEY))
                && lease.revision() == entity.getPersistentData().getLong(REVISION_KEY);
    }
    private static void mark(Entity entity, SceneLease lease, SceneMember member) {
        entity.getPersistentData().putString(LEASE_KEY, lease.id().value());
        entity.getPersistentData().putString(ACTOR_KEY, member.actorId().value());
        entity.getPersistentData().putLong(REVISION_KEY, lease.revision());
    }
    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        submit(runtime, "scene-unknown", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
    }
    private static void submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase, String id, FrontierPayload payload) {
        CheckpointImage checkpoint = checkpoint(runtime);
        CommandId commandId = new CommandId("executor:" + phase + "-" + id.replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("scene executor transition was rejected: " + result);
    }
    private static CheckpointImage checkpoint(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.checkpointImage().map(image -> new FrontierWorldStateCodec().decode(image.canonicalState())).orElse(null);
    }
}
