package io.farfrontier.palemirror.internal.world;

import java.util.Comparator;

import io.farfrontier.palemirror.domain.DamageAttribution;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.SettlementCohort;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.adapter.VanillaAnchorAdapter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.npc.Villager;

/** Bounded evidence publisher and reconciler; it never materializes or estimates canonical population changes. */
public final class SettlementObservationRuntime {
    private SettlementObservationRuntime() { }

    public static boolean observeNearPlayers(MinecraftServer server, PaleMirrorSavedData data,
                                             DomainCommandProcessor commands) {
        boolean changed = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers().stream()
                .sorted(Comparator.comparing(value -> value.getUUID().toString())).toList()) {
            for (var observation : AdapterRegistry.observeSettlements(player.serverLevel(), player.blockPosition())) {
                changed |= data.observeSettlement(observation);
                SettlementObservationRecord record = data.settlementObservations().get(observation.settlementId());
                if (record != null) {
                    changed |= record.captureStructure(player.serverLevel());
                    var occupancy = data.worldState().place(record.id())
                            .map(io.farfrontier.palemirror.domain.SettlementPlace::occupancy)
                            .orElse(io.farfrontier.palemirror.domain.OccupancyState.INHABITED);
                    changed |= record.evaluateStructure(player.serverLevel(), occupancy);
                }
                changed |= reconcile(data, commands, record, observation.observedAtGameTime());
            }
        }
        long gameTime = server.overworld().getGameTime();
        for (SettlementObservationRecord record : data.settlementObservations().values()) {
            changed |= reconcile(data, commands, record, gameTime);
        }
        return changed;
    }

    public static boolean observeDeath(PaleMirrorSavedData data, DomainCommandProcessor commands,
                                       Entity entity, DamageSource source) {
        SettlementCohort cohort = entity instanceof IronGolem ? SettlementCohort.GUARDS
                : entity instanceof Villager villager && villager.isBaby() ? SettlementCohort.CHILDREN
                : entity instanceof Villager ? SettlementCohort.CIVILIANS : null;
        if (cohort == null) return false;
        String dimension = entity.level().dimension().location().toString();
        SettlementObservationRecord record = data.settlementObservations().values().stream()
                .filter(candidate -> candidate.contains(dimension, entity.blockPosition())).findFirst().orElse(null);
        if (record == null) return false;
        long gameTime = entity.level().getGameTime();
        if (!record.recordDeath(entity.getUUID().toString(), cohort, attribution(source), gameTime)) return false;
        reconcile(data, commands, record, gameTime);
        return true;
    }

    public static boolean observePlayerBlockDamage(PaleMirrorSavedData data, DomainCommandProcessor commands,
                                                   net.minecraft.server.level.ServerLevel level,
                                                   net.minecraft.core.BlockPos position, java.util.UUID playerId) {
        SettlementObservationRecord record = data.settlementObservations().values().stream()
                .filter(value -> value.contains(level.dimension().location().toString(), position)).findFirst().orElse(null);
        if (record == null || !record.recordStructuralDamage(position, DamageAttribution.PLAYER, level.getGameTime())) return false;
        reconcile(data, commands, record, level.getGameTime());
        return true;
    }

    private static boolean reconcile(PaleMirrorSavedData data, DomainCommandProcessor commands,
                                     SettlementObservationRecord record, long gameTime) {
        if (record == null || data.worldState().place(record.id()).isEmpty()) return false;
        var freshness = record.freshness(gameTime);
        String projectionId = record.lastEvidenceId() + ":" + freshness + ":" + record.reliability()
                + ":guards=" + record.registeredGuards();
        return !commands.execute(data.worldState(), new DomainCommand.ObserveSettlementPlace(record.id(),
                freshness, record.reliability(), record.registeredGuards(), record.inferredIntegrity(), projectionId,
                "adapter:settlement:" + record.lastEvidenceType() + ":" + record.lastDamageAttribution())).isEmpty();
    }

    private static DamageAttribution attribution(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer) return DamageAttribution.PLAYER;
        if (attacker != null && !attacker.getPersistentData().getString(VanillaAnchorAdapter.OBJECT_ID_KEY).isBlank()) {
            return DamageAttribution.THREAT;
        }
        if (attacker instanceof Raider) return DamageAttribution.RAID;
        if (attacker == null) return DamageAttribution.ENVIRONMENTAL;
        return DamageAttribution.UNKNOWN;
    }
}
