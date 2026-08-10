package io.farfrontier.palemirror;

import com.mojang.logging.LogUtils;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;

@Mod(PaleMirrorMod.MOD_ID)
public final class PaleMirrorMod {
    public static final String MOD_ID = "pale_mirror";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PaleMirrorMod(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        modBus.addListener(PaleMirrorMod::registerBuiltInPacks);
        AdapterRegistry.registerConfigs(container);
        LOGGER.info("Pale Mirror bootstrapped; PM-owned vanilla anchors are available.");
    }

    private static void registerBuiltInPacks(AddPackFindersEvent event) {
        AdapterRegistry.registerBuiltInPacks(event);
    }
}
