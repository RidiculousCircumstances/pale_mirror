package io.farfrontier.palemirror.internal.integration.crimson;

import java.util.Comparator;

import io.farfrontier.palemirror.internal.world.EncounterActorRef;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded local behaviour for registered PM actors. It deliberately never
 * enumerates arbitrary Crimson/vanilla entities or delegates to Crimson tick.
 */
final class CrimsonActorRuntime {
    private static final int WORK_BUDGET_PER_TICK = 32;
    private static final long TARGET_REFRESH_TICKS = 20;
    private static final double TARGET_RANGE_SQUARED = 32.0D * 32.0D;

    void tick(MinecraftServer server, PaleMirrorSavedData data) {
        int remaining = WORK_BUDGET_PER_TICK;
        long gameTick = server.overworld().getGameTime();
        for (TestMineRecord mine : data.testMines().values().stream().sorted(Comparator.comparing(TestMineRecord::id)).toList()) {
            if (remaining == 0) return;
            ServerLevel level = levelFor(server, mine);
            if (level == null) continue;
            for (EncounterActorRef reference : mine.encounter().actors()) {
                if (remaining == 0) return;
                if (reference.status() != EncounterActorRef.Status.ACTIVE || reference.entityId() == null
                        || reference.nextRuntimeTick() > gameTick) continue;
                Entity entity = level.getEntity(reference.entityId());
                CrimsonActorProfile profile = CrimsonActorProfile.byId(reference.actorProfileId()).orElse(null);
                if (!(entity instanceof Mob actor) || profile == null
                        || !CrimsonSandboxAdapter.isOwnedActor(entity, mine, reference.slotId()) || !profile.matches(entity)) continue;
                keepInsideThreatSite(mine, actor);
                ServerPlayer target = refreshTarget(level, mine, actor);
                mine.encounter().scheduleRuntime(reference.slotId(), gameTick + profile.behavior().execute(actor, target));
                data.setDirty();
                remaining--;
            }
        }
    }

    private static ServerPlayer refreshTarget(ServerLevel level, TestMineRecord mine, Mob actor) {
        ServerPlayer target = level.players().stream()
                .filter(player -> !player.isSpectator() && mine.contains(player.blockPosition()))
                .filter(player -> actor.distanceToSqr(player) <= TARGET_RANGE_SQUARED)
                .min(Comparator.comparingDouble(actor::distanceToSqr))
                .orElse(null);
        if (target != null) actor.setTarget(target);
        else if (actor.getTarget() instanceof ServerPlayer) actor.setTarget(null);
        return target;
    }

    /** A PM actor is never allowed to carry combat or terrain interaction out of its registered site. */
    private static void keepInsideThreatSite(TestMineRecord mine, Mob actor) {
        if (mine.contains(actor.blockPosition())) return;
        int minX = mine.object().minBounds().getX();
        int maxX = mine.object().maxBounds().getX();
        int minY = mine.object().minBounds().getY();
        int maxY = mine.object().maxBounds().getY();
        int minZ = mine.object().minBounds().getZ();
        int maxZ = mine.object().maxBounds().getZ();
        actor.moveTo(Mth.clamp(actor.getX(), minX + 0.5D, maxX + 0.5D),
                Mth.clamp(actor.getY(), minY + 1.0D, maxY + 1.0D),
                Mth.clamp(actor.getZ(), minZ + 0.5D, maxZ + 0.5D), actor.getYRot(), actor.getXRot());
        actor.setDeltaMovement(Vec3.ZERO);
        actor.getNavigation().stop();
    }

    private static ServerLevel levelFor(MinecraftServer server, TestMineRecord mine) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(mine.dimensionId())) return level;
        }
        return null;
    }
}
