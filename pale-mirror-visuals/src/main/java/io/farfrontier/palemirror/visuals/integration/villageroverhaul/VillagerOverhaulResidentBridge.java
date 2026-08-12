package io.farfrontier.palemirror.visuals.integration.villageroverhaul;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.ResidentSeed;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.z2six.villageroverhaul.network.patrol.PacketPatrolSetRouteType;
import org.z2six.villageroverhaul.server.ai.VillagerBrain;

/** All Villager Overhaul knowledge is confined to this exact-version bridge. */
public final class VillagerOverhaulResidentBridge {
    public void configure(Villager villager, ResidentSeed resident, AuthoredRegionSeed region) {
        VillagerBrain.ensureAttached(villager);
        VillagerBrain.setMode(villager, VillagerBrain.Mode.NEUTRAL);
        if (resident.cohort().equals("GUARDS")) configureGuard(villager, region);
        else if (resident.role().equals("worker")) VillagerBrain.setManualFarmingActive(villager, true);
    }

    private static void configureGuard(Villager guard, AuthoredRegionSeed region) {
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        guard.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        VillagerBrain.setCombatMode(guard, VillagerBrain.CombatMode.DEFEND);
        VillagerBrain.clearAllPatrolData(guard);
        int y = region.anchor().y() + 1;
        List<Vec3> points = List.of(
                new Vec3(region.anchor().x() + 68, y, region.anchor().z()),
                new Vec3(region.anchor().x(), y, region.anchor().z() + 68),
                new Vec3(region.anchor().x() - 68, y, region.anchor().z()),
                new Vec3(region.anchor().x(), y, region.anchor().z() - 68));
        points.forEach(point -> VillagerBrain.addPatrolWaypointAtPos(guard, point));
        VillagerBrain.markPatrolFinalized(guard);
        VillagerBrain.setPatrolRouteTypeAndStart(guard, PacketPatrolSetRouteType.RouteType.CIRCULAR);
    }
}
