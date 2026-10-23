package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.internal.production.PaleMirrorProductionStations;
import io.farfrontier.palemirror.internal.production.ProductionBenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Minimal familiar graybox UI: ingredient above fuel, product to the right. */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ProductionBenchScreen extends AbstractContainerScreen<ProductionBenchMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");
    private Button startButton;
    public ProductionBenchScreen(ProductionBenchMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    @Override protected void init() {
        super.init();
        startButton = addRenderableWidget(Button.builder(Component.translatable("container.pale_mirror.production_bench.start"),
                button -> { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0); })
                .bounds(leftPos + 84, topPos + 58, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1);
        }).bounds(leftPos + 148, topPos + 33, 16, 18).build());
    }
    @Override protected void containerTick() {
        super.containerTick();
        startButton.active = menu.status() == 1 || menu.status() == 2 || menu.status() == 7;
    }
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
        event.register(PaleMirrorProductionStations.BENCH_MENU.get(), ProductionBenchScreen::new);
        event.register(PaleMirrorProductionStations.FORGE_MENU.get(), ProductionBenchScreen::new);
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (menu.hasMaintenancePort()) graphics.blit(BACKGROUND, leftPos + 29, topPos + 52, 55, 52, 18, 18);
        int progress = Math.clamp(menu.progressPermille(), 0, 1_000);
        graphics.fill(leftPos + 79, topPos + 34, leftPos + 79 + progress * 24 / 1_000, topPos + 38, 0xffddb94c);
        graphics.drawString(font, Component.literal("Heat"), leftPos + 8, topPos + 17, 0xff404040, false);
        graphics.drawString(font, Component.literal(Long.toString(menu.storedHeat())), leftPos + 8, topPos + 29, 0xff404040, false);
        graphics.drawString(font, Component.translatable("container.pale_mirror.production_bench.status." + menu.status()),
                leftPos + 84, topPos + 17, 0xff404040, false);
        var product = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(menu.productRegistryId()));
        if (!product.isEmpty()) {
            graphics.renderFakeItem(product, leftPos + 130, topPos + 35);
            if (mouseX >= leftPos + 130 && mouseX < leftPos + 147 && mouseY >= topPos + 35 && mouseY < topPos + 52)
                graphics.renderTooltip(font, Component.translatable(menu.repairRecipe()
                        ? "container.pale_mirror.production_bench.repair" : "container.pale_mirror.production_bench.make", product.getHoverName()), mouseX, mouseY);
        }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
