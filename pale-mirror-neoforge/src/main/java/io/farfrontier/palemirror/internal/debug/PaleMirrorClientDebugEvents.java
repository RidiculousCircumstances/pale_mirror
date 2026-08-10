package io.farfrontier.palemirror.internal.debug;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.internal.adapter.SourceItemFirewall;
import io.farfrontier.palemirror.internal.economy.ResourceTransferRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Client-only, read-only item diagnostics exposed through Minecraft's advanced-tooltip switch (F3+H). */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID, value = Dist.CLIENT)
public final class PaleMirrorClientDebugEvents {
    private PaleMirrorClientDebugEvents() { }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!event.getFlags().isAdvanced() || event.getItemStack().isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (!"minecraft".equals(id.getNamespace())) {
            event.getToolTip().add(Component.literal("[PM debug] owner=" + id.getNamespace() + " id=" + id)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        SourceItemFirewall.classify(event.getItemStack()).ifPresent(blocked -> event.getToolTip().add(
                Component.literal("[PM policy] excluded source content: " + blocked.sourceId())
                        .withStyle(ChatFormatting.RED)));
        if (ResourceTransferRuntime.isReserved(event.getItemStack())) {
            event.getToolTip().add(Component.literal("[PM policy] reserved by an atomic resource transfer")
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
