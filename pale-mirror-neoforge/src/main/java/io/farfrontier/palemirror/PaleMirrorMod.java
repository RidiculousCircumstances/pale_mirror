package io.farfrontier.palemirror;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.slf4j.Logger;

@Mod(PaleMirrorMod.MOD_ID)
public final class PaleMirrorMod {
    public static final String MOD_ID = "pale_mirror";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PaleMirrorMod(IEventBus modBus) {
        modBus.addListener(PaleMirrorMod::registerBuiltInPacks);
        LOGGER.info("Pale Mirror bootstrapped; PM-owned vanilla anchors are available.");
    }

    private static void registerBuiltInPacks(AddPackFindersEvent event) {
        event.addPackFinders(ResourceLocation.fromNamespaceAndPath(MOD_ID, "crimson_sandbox"),
                PackType.SERVER_DATA, Component.literal("Pale Mirror Crimson Sandbox"), PackSource.BUILT_IN,
                true, Pack.Position.TOP);
    }
}
