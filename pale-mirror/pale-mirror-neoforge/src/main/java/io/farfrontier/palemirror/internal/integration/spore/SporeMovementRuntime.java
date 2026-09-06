package io.farfrontier.palemirror.internal.integration.spore;

import java.util.Comparator;
import java.util.List;

import io.farfrontier.palemirror.internal.world.EncounterActorRef;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * PM-owned hybrid movement. Native AI remains disabled: actors either follow
 * an authored patrol lattice or make one collision-safe step toward a player
 * inside the same registered site. No path can claim or modify a block.
 */
final class SporeMovementRuntime {
    private static final int WORK_BUDGET_PER_TICK = 4;
    private static final List<BlockPos> PATROL_OFFSETS = List.of(
            new BlockPos(-3, 1, -3), new BlockPos(3, 1, -3),
            new BlockPos(3, 1, 3), new BlockPos(-3, 1, 3));

    void tick(MinecraftServer server, PaleMirrorSavedData data, SporeSandboxAdapter adapter) {
        int remaining = WORK_BUDGET_PER_TICK;
        long gameTick = server.overworld().getGameTime();
        for (TestMineRecord site : data.testMines().values().stream().sorted(Comparator.comparing(TestMineRecord::id)).toList()) {
            if (remaining == 0) return;
            ServerLevel level = levelFor(server, site);
            if (level == null) continue;
            for (EncounterActorRef reference : site.encounter().actors()) {
                if (remaining == 0) return;
                if (reference.status() != EncounterActorRef.Status.ACTIVE || reference.entityId() == null
                        || reference.nextMovementTick() > gameTick) continue;
                Entity entity = level.getEntity(reference.entityId());
                SporeActorProfile profile = SporeActorProfile.byId(reference.actorProfileId()).orElse(null);
                if (!(entity instanceof Mob actor) || profile == null || profile.movementPolicy() == SporeActorProfile.MovementPolicy.STATIONARY
                        || !adapter.matchesOwnedActor(actor, site, reference.slotId())) continue;
                adapter.holdConstrained(actor);
                MoveResult result = switch (profile.movementPolicy()) {
                    case PATROL -> patrol(level, site, actor, reference.routeCursor());
                    case PURSUE -> pursue(level, site, actor);
                    case STATIONARY -> MoveResult.noMove(reference.routeCursor());
                };
                if (result.destination() != null) {
                    BlockPos destination = result.destination();
                    actor.moveTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D,
                            actor.getYRot(), actor.getXRot());
                    adapter.holdConstrained(actor);
                }
                site.encounter().scheduleMovement(reference.slotId(), gameTick + profile.movementCooldownTicks(), result.routeCursor());
                data.setDirty();
                remaining--;
            }
        }
    }

    private static MoveResult patrol(ServerLevel level, TestMineRecord site, Mob actor, int cursor) {
        int nextCursor = Math.floorMod(cursor, PATROL_OFFSETS.size());
        BlockPos target = site.anchor().offset(PATROL_OFFSETS.get(nextCursor));
        if (actor.blockPosition().distManhattan(target) <= 1) {
            nextCursor = (nextCursor + 1) % PATROL_OFFSETS.size();
            target = site.anchor().offset(PATROL_OFFSETS.get(nextCursor));
        }
        BlockPos step = safeStepToward(level, site, actor.blockPosition(), target);
        return new MoveResult(step, nextCursor);
    }

    private static MoveResult pursue(ServerLevel level, TestMineRecord site, Mob actor) {
        ServerPlayer target = level.players().stream()
                .filter(player -> !player.isSpectator() && site.contains(player.blockPosition()))
                .min(Comparator.comparingDouble(actor::distanceToSqr)).orElse(null);
        return target == null ? MoveResult.noMove(0)
                : new MoveResult(safeStepToward(level, site, actor.blockPosition(), target.blockPosition()), 0);
    }

    private static BlockPos safeStepToward(ServerLevel level, TestMineRecord site, BlockPos current, BlockPos target) {
        if (!site.contains(current) || !isSafeCell(level, site, current)) return firstSafeCell(level, site);
        int dx = Integer.compare(target.getX(), current.getX());
        int dz = Integer.compare(target.getZ(), current.getZ());
        BlockPos xStep = current.offset(dx, 0, 0);
        BlockPos zStep = current.offset(0, 0, dz);
        boolean xFirst = Math.abs(target.getX() - current.getX()) >= Math.abs(target.getZ() - current.getZ());
        if (dx != 0 && xFirst && isSafeCell(level, site, xStep)) return xStep;
        if (dz != 0 && isSafeCell(level, site, zStep)) return zStep;
        if (dx != 0 && isSafeCell(level, site, xStep)) return xStep;
        return null;
    }

    private static BlockPos firstSafeCell(ServerLevel level, TestMineRecord site) {
        for (BlockPos offset : PATROL_OFFSETS) {
            BlockPos candidate = site.anchor().offset(offset);
            if (isSafeCell(level, site, candidate)) return candidate;
        }
        return null;
    }

    private static boolean isSafeCell(ServerLevel level, TestMineRecord site, BlockPos position) {
        return site.contains(position) && level.hasChunkAt(position) && level.getBlockState(position).isAir()
                && level.getBlockState(position.above()).isAir() && !level.getBlockState(position.below()).isAir();
    }

    private static ServerLevel levelFor(MinecraftServer server, TestMineRecord site) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(site.dimensionId())) return level;
        }
        return null;
    }

    private record MoveResult(BlockPos destination, int routeCursor) {
        static MoveResult noMove(int routeCursor) { return new MoveResult(null, routeCursor); }
    }
}
