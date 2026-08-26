package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Captures PM-owned graybox entities at their authoritative level-admission boundary. */
@EventBusSubscriber(modid = PaleMirrorVisualsMod.MOD_ID)
public final class FrontierGrayboxEntityEvents {
    private FrontierGrayboxEntityEvents() { }

    @SubscribeEvent
    public static void entityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        AuthoredVisualProvider.INSTANCE.observeFrontierEntityJoin(level, event.getEntity());
    }
}
