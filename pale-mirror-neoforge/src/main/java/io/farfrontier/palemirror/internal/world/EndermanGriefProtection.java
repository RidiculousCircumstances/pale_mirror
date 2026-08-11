package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.world.entity.monster.EnderMan;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;

/** Prevents Endermen from removing supports or placing carried blocks across a PM-managed world. */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID)
public final class EndermanGriefProtection {
    private EndermanGriefProtection() { }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (event.getEntity() instanceof EnderMan) event.setCanGrief(false);
    }
}
