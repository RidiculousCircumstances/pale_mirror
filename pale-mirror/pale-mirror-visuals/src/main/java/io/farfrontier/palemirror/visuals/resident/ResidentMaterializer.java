package io.farfrontier.palemirror.visuals.resident;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.ResidentSeed;
import io.farfrontier.palemirror.visuals.integration.villageroverhaul.VillagerOverhaulResidentBridge;
import io.farfrontier.palemirror.visuals.runtime.VisualGenesisSavedData;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Stable UUID commissioning. Missing/unloaded entities are never interpreted as deaths. */
public final class ResidentMaterializer {
    private final VillagerOverhaulResidentBridge behavior = new VillagerOverhaulResidentBridge();

    public void materialize(ServerLevel level, ChunkPos chunk, AuthoredRegionSeed region, VisualGenesisSavedData ledger) {
        for (ResidentSeed seed : region.residents()) {
            ChunkPos homeChunk = new ChunkPos(seed.home().x() >> 4, seed.home().z() >> 4);
            if (!homeChunk.equals(chunk) || ledger.residentCommissioned(seed.residentId())) continue;
            UUID uuid = UUID.fromString(seed.residentId());
            if (level.getEntity(uuid) instanceof Villager) {
                ledger.commissionResident(seed.residentId());
                continue;
            }
            Villager villager = create(level, region, seed, uuid);
            Vec3 spawn = safeSpawn(level, new BlockPos(seed.home().x(), seed.home().y(), seed.home().z()));
            if (spawn == null) continue;
            villager.setPos(spawn.x, spawn.y, spawn.z);
            if (level.addFreshEntity(villager)) ledger.commissionResident(seed.residentId());
        }
    }

    /**
     * Finds a deterministic two-block clearance near the authored public
     * entrance. No safe cell means no entity: canonical population remains
     * abstract and commissioning retries after neighbouring geometry arrives.
     */
    public static Vec3 safeSpawn(ServerLevel level, BlockPos intended) {
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                int x = intended.getX() + dx;
                int z = intended.getZ() + dz;
                BlockPos column = new BlockPos(x, intended.getY(), z);
                if (!level.hasChunkAt(column)) continue;
                for (int dy : new int[]{0, 1, -1, 2, -2, 3}) {
                    BlockPos feet = column.offset(0, dy, 0);
                    if (safe(level, feet)) return Vec3.atBottomCenterOf(feet);
                }
            }
        }
        return null;
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) return false;
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return false;
        BlockPos floor = feet.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, net.minecraft.core.Direction.UP);
    }

    public Villager create(ServerLevel level, AuthoredRegionSeed region, ResidentSeed seed, UUID uuid) {
        Villager villager = new Villager(EntityType.VILLAGER, level);
        villager.setUUID(uuid);
        villager.setPersistenceRequired();
        villager.setCustomName(Component.literal(displayName(seed)));
        villager.setCustomNameVisible(false);
        villager.setVillagerData(villager.getVillagerData().setProfession(profession(seed)));
        if (seed.cohort().equals("CHILDREN")) villager.setAge(-24_000);
        ManagedResident.attach(villager, region.planId(), seed);
        behavior.configure(villager, seed, region);
        return villager;
    }

    private static VillagerProfession profession(ResidentSeed seed) {
        if (seed.cohort().equals("GUARDS")) return VillagerProfession.WEAPONSMITH;
        if (seed.cohort().equals("SPECIALISTS")) return VillagerProfession.TOOLSMITH;
        if (seed.cohort().equals("WORKERS")) return VillagerProfession.FARMER;
        return VillagerProfession.NONE;
    }

    private static String displayName(ResidentSeed seed) {
        String serial = seed.residentId().substring(0, 4).toUpperCase(java.util.Locale.ROOT);
        return switch (seed.cohort()) {
            case "GUARDS" -> "Frontier Guard " + serial;
            case "SPECIALISTS" -> "Frontier Specialist " + serial;
            case "WORKERS" -> "Frontier Worker " + serial;
            case "CHILDREN" -> "Frontier Child " + serial;
            default -> "Frontier Resident " + serial;
        };
    }
}
