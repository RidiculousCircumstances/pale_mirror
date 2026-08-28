package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorDied;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorObserved;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Materializes exact ordinary residents and hive bioforms near real players.
 *
 * <p>It owns no canonical mutation and never interprets an unloaded/missing body as death. A
 * deterministic UUID prevents a second body after ordinary unload/reload; an untagged body with
 * that UUID is a visible non-owning conflict and remains untouched.</p>
 */
final class FrontierV3AmbientActorExecutor {
    static final String ACTOR_KEY = "pale_mirror_frontier_v3_ambient_actor";
    static final String KIND_KEY = "pale_mirror_frontier_v3_ambient_kind";
    private static final int MAX_ACTORS_PER_TICK = 16;
    private static final int DEMAND_RADIUS_BLOCKS = 96;

    private FrontierV3AmbientActorExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.checkpointImage().map(image -> new FrontierWorldStateCodec().decode(image.canonicalState())).orElse(null);
        if (state == null) return;
        int admitted = 0;
        for (var entry : state.actorLocations().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            if (admitted >= MAX_ACTORS_PER_TICK) return;
            if (entry.getValue().condition().status() != ActorLifeStatus.ALIVE || !demand(level, entry.getValue().position())) continue;
            if (materialize(level, state, entry.getKey(), entry.getValue().position()) == Result.APPLIED) admitted++;
        }
    }

    static Result materialize(ServerLevel level, FrontierWorldState state, SubjectId actorId, BlockPosition canonicalPosition) {
        if (!state.actorLocations().containsKey(actorId)) return Result.CONFLICT;
        UUID entityId = entityId(actorId); Entity existing = level.getEntity(entityId); boolean bioform = bioform(state, actorId);
        if (existing != null) return owned(existing, actorId, bioform) ? Result.CURRENT : Result.CONFLICT;
        BlockPos anchor = new BlockPos(canonicalPosition.x(), canonicalPosition.y(), canonicalPosition.z());
        if (!level.hasChunkAt(anchor)) return Result.DEFERRED;
        BlockPos position = new BlockPos(anchor.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ()), anchor.getZ());
        if (!level.hasChunkAt(position) || !level.getBlockState(position).isAir() || !level.getBlockState(position.above()).isAir()) return Result.DEFERRED;
        Mob body = bioform ? EntityType.ZOMBIE.create(level) : EntityType.VILLAGER.create(level);
        if (body == null) throw new IllegalStateException("Minecraft could not create a Frontier v3 ambient actor");
        body.setUUID(entityId); body.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D); body.setPersistenceRequired();
        body.setCustomName(Component.literal((bioform ? "Hive " : "Frontier ") + actorId.value())); body.setCustomNameVisible(false);
        body.getPersistentData().putString(ACTOR_KEY, actorId.value()); body.getPersistentData().putString(KIND_KEY, bioform ? "BIOFORM" : "RESIDENT");
        return level.addFreshEntity(body) ? Result.APPLIED : Result.CONFLICT;
    }

    static UUID entityId(SubjectId actorId) { return UUID.nameUUIDFromBytes(("frontier-v3:ambient:" + actorId.value()).getBytes(StandardCharsets.UTF_8)); }
    /** Accepts only a real loaded-world death for the exact non-leased ambient body. */
    static boolean observeDeath(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity, Entity source) {
        FrontierWorldState state = runtime.checkpointImage().map(image -> new FrontierWorldStateCodec().decode(image.canonicalState())).orElse(null);
        if (state == null) return false;
        String rawActorId = entity.getPersistentData().getString(ACTOR_KEY);
        if (rawActorId.isBlank()) return false;
        SubjectId actorId;
        try { actorId = new SubjectId(rawActorId); } catch (IllegalArgumentException invalid) { return false; }
        if (!state.actorLocations().containsKey(actorId) || state.actorLocations().get(actorId).condition().status() != ActorLifeStatus.ALIVE
                || state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED
                && lease.members().stream().anyMatch(member -> member.actorId().equals(actorId)))
                || !entityId(actorId).equals(entity.getUUID()) || !owned(entity, actorId, bioform(state, actorId))) return false;
        String cause = source == null ? "environment" : "entity:" + source.getUUID();
        submit(runtime, "ambient-death", actorId.value(),
                new AmbientActorDied(actorId, new BlockPosition(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()), cause));
        return true;
    }
    /** Captures a living owned body before Minecraft releases it, never treating absence as death. */
    static boolean observeLeave(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        FrontierWorldState state = runtime.checkpointImage().map(image -> new FrontierWorldStateCodec().decode(image.canonicalState())).orElse(null);
        if (state == null || !(entity instanceof Mob body)) return false;
        String rawActorId = entity.getPersistentData().getString(ACTOR_KEY);
        if (rawActorId.isBlank()) return false;
        SubjectId actorId;
        try { actorId = new SubjectId(rawActorId); } catch (IllegalArgumentException invalid) { return false; }
        var current = state.actorLocations().get(actorId);
        if (current == null || current.condition().status() != ActorLifeStatus.ALIVE || !entityId(actorId).equals(entity.getUUID())
                || !owned(entity, actorId, bioform(state, actorId)) || body.getHealth() <= 0.0F) return false;
        BlockPosition position = new BlockPosition(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ());
        FixedScalar health = new FixedScalar(Math.round((double) body.getHealth() * FixedScalar.SCALE));
        if (current.position().equals(position) && current.condition().health().equals(health)) return false;
        submit(runtime, "ambient-observed", actorId.value(), new AmbientActorObserved(actorId, position, health));
        return true;
    }
    private static boolean demand(ServerLevel level, BlockPosition position) {
        BlockPos target = new BlockPos(position.x(), position.y(), position.z());
        return level.hasChunkAt(target) && level.players().stream().filter(player -> !player.isSpectator())
                .anyMatch(player -> player.blockPosition().closerThan(target, DEMAND_RADIUS_BLOCKS));
    }
    private static boolean bioform(FrontierWorldState state, SubjectId actorId) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .map(Bioform::id).anyMatch(actorId::equals);
    }
    static boolean owned(Entity entity, SubjectId actorId, boolean bioform) {
        return !entity.isRemoved() && actorId.value().equals(entity.getPersistentData().getString(ACTOR_KEY))
                && (bioform ? entity instanceof Zombie : entity instanceof Villager)
                && (bioform ? "BIOFORM" : "RESIDENT").equals(entity.getPersistentData().getString(KIND_KEY));
    }
    private static void submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase, String id, FrontierPayload payload) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId commandId = new CommandId("executor:" + phase + "-" + id.replace(':', '-') + "-r" + checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("ambient actor death was rejected: " + result);
    }
    enum Result { APPLIED, CURRENT, DEFERRED, CONFLICT }
}
