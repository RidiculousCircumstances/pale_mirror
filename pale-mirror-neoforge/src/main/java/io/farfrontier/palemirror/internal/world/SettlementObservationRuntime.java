package io.farfrontier.palemirror.internal.world;

import java.util.Comparator;

import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;

/** Bounded physical observation runtime; it neither materializes nor changes canonical settlement state. */
public final class SettlementObservationRuntime {
    private SettlementObservationRuntime() { }

    public static boolean observeNearPlayers(MinecraftServer server, PaleMirrorSavedData data) {
        boolean changed = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers().stream()
                .sorted(Comparator.comparing(value -> value.getUUID().toString())).toList()) {
            changed |= AdapterRegistry.observeSettlements(player.serverLevel(), player.blockPosition()).stream()
                    .map(data::observeSettlement).reduce(false, Boolean::logicalOr);
        }
        return changed;
    }

    /** Deaths are retained as player-visible diagnostics; physical counts never overwrite the domain population. */
    public static boolean observeDeath(PaleMirrorSavedData data, Entity entity) {
        boolean resident = entity instanceof Villager;
        boolean guard = entity instanceof IronGolem;
        if (!resident && !guard) return false;
        String dimension = entity.level().dimension().location().toString();
        return data.settlementObservations().values().stream()
                .filter(record -> record.contains(dimension, entity.blockPosition()))
                .findFirst().map(record -> resident ? record.recordResidentDeath() : record.recordGuardDeath()).orElse(false);
    }

    public static void recordDeath(MinecraftServer server, Entity entity) {
        PaleMirrorSavedData data = PaleMirrorSavedData.get(server.overworld());
        if (observeDeath(data, entity)) data.setDirty();
    }
}
