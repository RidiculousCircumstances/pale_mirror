package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.internal.materialization.ParcelLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;

/** Ephemeral Minecraft policy for the horizontal footprint of canonical PM settlements. */
public final class SettlementTerritoryPolicy {
    private SettlementTerritoryPolicy() { }

    public static boolean evaluate(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return false;
        boolean insideSettlement = insideSettlement(level, event.getPos());
        if (!deniesHostileSpawn(insideSettlement, event.getEntityType().getCategory(), event.getSpawnType())) {
            return false;
        }
        event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        return true;
    }

    public static void evaluate(BlockGrowFeatureEvent event) {
        if (event.getLevel() instanceof ServerLevel level && insideSettlement(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    static boolean deniesHostileSpawn(boolean insideSettlement, MobCategory category, MobSpawnType spawnType) {
        return insideSettlement && category == MobCategory.MONSTER && AmbientSpawnThrottle.managedSpawnType(spawnType);
    }

    static boolean insideSettlement(ParcelLedger parcels, String dimensionId, BlockPos position) {
        return parcels.influenceAtColumn(dimensionId, position).isPresent();
    }

    private static boolean insideSettlement(ServerLevel level, BlockPos position) {
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        return insideSettlement(data.parcels(), level.dimension().location().toString(), position);
    }
}
