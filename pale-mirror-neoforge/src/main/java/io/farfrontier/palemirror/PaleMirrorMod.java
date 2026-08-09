package io.farfrontier.palemirror;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(PaleMirrorMod.MOD_ID)
public final class PaleMirrorMod {
    public static final String MOD_ID = "pale_mirror";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PaleMirrorMod(IEventBus ignored) {
        LOGGER.info("Pale Mirror bootstrapped; core-only TestThreat profile is available.");
    }
}
