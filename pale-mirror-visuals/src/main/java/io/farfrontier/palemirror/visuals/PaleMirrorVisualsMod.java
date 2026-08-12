package io.farfrontier.palemirror.visuals;

import com.mojang.logging.LogUtils;
import io.farfrontier.palemirror.api.PaleMirrorVisuals;
import io.farfrontier.palemirror.visuals.runtime.AuthoredVisualProvider;
import io.farfrontier.palemirror.visuals.threat.VisualEntityTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(PaleMirrorVisualsMod.MOD_ID)
public final class PaleMirrorVisualsMod {
    public static final String MOD_ID = "pale_mirror_visuals";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PaleMirrorVisualsMod(IEventBus modBus) {
        VisualEntityTypes.register(modBus);
        PaleMirrorVisuals.register(AuthoredVisualProvider.INSTANCE);
        LOGGER.info("Pale Mirror Visuals registered the fresh-world authored-region provider");
    }
}
