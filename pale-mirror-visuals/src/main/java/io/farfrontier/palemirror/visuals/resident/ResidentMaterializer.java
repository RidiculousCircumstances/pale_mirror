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
            villager.setPos(seed.home().x() + 0.5, seed.home().y() + 2.0, seed.home().z() + 0.5);
            if (level.addFreshEntity(villager)) ledger.commissionResident(seed.residentId());
        }
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
