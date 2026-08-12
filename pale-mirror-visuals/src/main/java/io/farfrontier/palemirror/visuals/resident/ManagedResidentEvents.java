package io.farfrontier.palemirror.visuals.resident;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.visuals.runtime.AuthoredVisualProvider;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import io.farfrontier.palemirror.api.ResidentDeathObservation;

/** Prevents ambient breeding from inventing physical citizens outside canonical growth. */
@EventBusSubscriber(modid = PaleMirrorVisualsMod.MOD_ID)
public final class ManagedResidentEvents {
    private ManagedResidentEvents() { }

    @SubscribeEvent
    public static void entityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (ManagedResident.isManaged(villager)
                && AuthoredVisualProvider.INSTANCE.journeys().suppressJoin(villager)) {
            event.setCanceled(true);
            return;
        }
        if (!villager.isBaby() || ManagedResident.isManaged(villager)) return;
        String dimension = event.getLevel().dimension().location().toString();
        for (AuthoredRegionSeed region : AuthoredVisualProvider.INSTANCE.markers().discovered(dimension)) {
            var bounds = region.settlementBounds();
            var pos = villager.blockPosition();
            if (pos.getX() >= bounds.min().x() && pos.getX() <= bounds.max().x()
                    && pos.getY() >= bounds.min().y() && pos.getY() <= bounds.max().y()
                    && pos.getZ() >= bounds.min().z() && pos.getZ() <= bounds.max().z()
                    && !villager.getPersistentData().getBoolean(ManagedResident.GROWTH_PERMIT)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void livingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || !ManagedResident.isManaged(villager)
                || villager.level().isClientSide()) return;
        var data = villager.getPersistentData();
        String actor = event.getSource().getEntity() == null ? "" : event.getSource().getEntity().getUUID().toString();
        String observationId = "resident-death:" + data.getString(ManagedResident.ID) + ":" + villager.level().getGameTime();
        AuthoredVisualProvider.INSTANCE.observeDeath(new ResidentDeathObservation(observationId,
                villager.level().dimension().location().toString(), data.getString(ManagedResident.REGION),
                data.getString(ManagedResident.ID),
                data.getString(ManagedResident.COHORT), event.getSource().getMsgId(), actor));
    }
}
