package io.farfrontier.palemirror.internal.integration.crimson;

import java.util.Comparator;

import io.farfrontier.palemirror.internal.world.EncounterActorRef;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

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
                if (!(entity instanceof Mob actor) || !CrimsonSandboxAdapter.isOwnedActor(entity, mine, reference.slotId())) continue;
                refreshTarget(level, mine, actor);
                mine.encounter().scheduleRuntime(reference.slotId(), gameTick + TARGET_REFRESH_TICKS);
                data.setDirty();
                remaining--;
            }
        }
    }

    private static void refreshTarget(ServerLevel level, TestMineRecord mine, Mob actor) {
        ServerPlayer target = level.players().stream()
                .filter(player -> !player.isSpectator() && mine.contains(player.blockPosition()))
                .filter(player -> actor.distanceToSqr(player) <= TARGET_RANGE_SQUARED)
                .min(Comparator.comparingDouble(actor::distanceToSqr))
                .orElse(null);
        if (target != null) actor.setTarget(target);
        else if (actor.getTarget() instanceof ServerPlayer) actor.setTarget(null);
    }

    private static ServerLevel levelFor(MinecraftServer server, TestMineRecord mine) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(mine.dimensionId())) return level;
        }
        return null;
    }
}
