package io.farfrontier.palemirror.internal.calendar;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Host presentation lifecycle, independent of simulation processes and physical-effect executors. */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID)
public final class CalendarPresentationEvents {
    private CalendarPresentationEvents() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterCanonicalTurn(ServerTickEvent.Post event) {
        MinecraftCalendarPresentation.synchronize(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopped(ServerStoppedEvent event) {
        MinecraftCalendarPresentation.detach(event.getServer());
    }
}
