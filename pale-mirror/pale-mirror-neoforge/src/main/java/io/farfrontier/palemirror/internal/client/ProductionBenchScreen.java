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
        private Button startButton;
    public ProductionBenchScreen(ProductionBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageHeight = 201; inventoryLabelY = 107;
    }
    @Override protected void init() {
        super.init();
        startButton = addRenderableWidget(Button.builder(Component.translatable("container.pale_mirror.production_bench.start"),
                button -> { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0); })
                .bounds(leftPos + 90, topPos + 78, 76, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1);
        }).bounds(leftPos + 148, topPos + 35, 16, 18).build());
    }
    @Override protected void containerTick() {
        super.containerTick();
        startButton.active = menu.status() == 1 || menu.status() == 2 || menu.status() == 7;
    }
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
        event.register(PaleMirrorProductionStations.BENCH_MENU.get(), ProductionBenchScreen::new);
        event.register(PaleMirrorProductionStations.FORGE_MENU.get(), ProductionBenchScreen::new);
        event.register(PaleMirrorProductionStations.WOODWORKING_MENU.get(), ProductionBenchScreen::new);
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xffc6c6c6);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, 0xffffffff);
        graphics.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, 0xff555555);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xff373737);
            graphics.fill(x, y, x + 17, y + 17, 0xffffffff);
            graphics.fill(x, y, x + 16, y + 16, 0xff8b8b8b);
        }
        int progress = Math.clamp(menu.progressPermille(), 0, 1_000);
        graphics.fill(leftPos + 88, topPos + 42, leftPos + 88 + progress * 24 / 1_000, topPos + 46, 0xffb08020);
        graphics.drawString(font, Component.literal("Burn: " + menu.remainingBurnTicks()), leftPos + 90, topPos + 18, 0xff404040, false);
        graphics.drawString(font, Component.translatable("container.pale_mirror.production_bench.status." + menu.status()),
                leftPos + 8, topPos + 99, 0xff404040, false);
        var product = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(menu.productRegistryId()));
        if (!product.isEmpty()) {
            graphics.renderFakeItem(product, leftPos + 120, topPos + 58);
            if (mouseX >= leftPos + 120 && mouseX < leftPos + 137 && mouseY >= topPos + 58 && mouseY < topPos + 75)
                graphics.renderTooltip(font, Component.translatable(menu.repairRecipe()
                        ? "container.pale_mirror.production_bench.repair" : "container.pale_mirror.production_bench.make", product.getHoverName()), mouseX, mouseY);
        }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
