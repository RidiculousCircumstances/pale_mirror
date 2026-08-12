package io.farfrontier.palemirror.visuals;

import com.mojang.logging.LogUtils;
import io.farfrontier.palemirror.api.PaleMirrorVisuals;
import io.farfrontier.palemirror.visuals.runtime.AuthoredVisualProvider;
import io.farfrontier.palemirror.visuals.threat.VisualEntityTypes;
import io.farfrontier.palemirror.visuals.genesis.VisualGenesisAttachments;
import io.farfrontier.palemirror.visuals.genesis.VisualWorldgenFeatures;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(PaleMirrorVisualsMod.MOD_ID)
public final class PaleMirrorVisualsMod {
    public static final String MOD_ID = "pale_mirror_visuals";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PaleMirrorVisualsMod(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        VisualGenesisAttachments.register(modBus);
        VisualWorldgenFeatures.register(modBus);
        VisualEntityTypes.register(modBus);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                io.farfrontier.palemirror.visuals.runtime.VisualServerConfig.SPEC, "pale-mirror-visuals-server.toml");
        modBus.addListener(PaleMirrorVisualsMod::registerAttributes);
        PaleMirrorVisuals.register(AuthoredVisualProvider.INSTANCE);
        LOGGER.info("Pale Mirror Visuals registered the fresh-world authored-region provider");
    }

    private static void registerAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(VisualEntityTypes.THREAT_HEART.get(),
                io.farfrontier.palemirror.visuals.threat.ThreatHeartEntity.createAttributes().build());
    }
}
