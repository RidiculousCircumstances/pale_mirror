package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.internal.network.AtlasActionPayload;
import io.farfrontier.palemirror.internal.network.PaleMirrorNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compact native UI: a read-only region explanation plus server-authorized action buttons. */
public final class PaleMirrorAtlasScreen extends Screen {
    private PaleMirrorAtlasClient.Snapshot snapshot;

    public PaleMirrorAtlasScreen(PaleMirrorAtlasClient.Snapshot snapshot) {
        super(Component.translatable("screen.pale_mirror.atlas"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    public void refresh(PaleMirrorAtlasClient.Snapshot updated) {
        snapshot = updated;
        if (minecraft != null) rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int left = (width - 388) / 2;
        int top = (height - Math.min(238, 60 + snapshot.regions().size() * 58)) / 2;
        for (int index = 0; index < snapshot.regions().size(); index++) {
            PaleMirrorAtlasClient.Region region = snapshot.regions().get(index);
            int row = top + 40 + index * 58;
            if (!region.scenarioId().isBlank() && "OFFERED".equals(region.scenarioStatus())) {
                addRenderableWidget(Button.builder(Component.literal("Accept"), ignored ->
                        PaleMirrorNetwork.sendAction(AtlasActionPayload.Action.ACCEPT_SCENARIO, region.scenarioId()))
                        .bounds(left + 262, row + 4, 54, 18).build());
                addRenderableWidget(Button.builder(Component.literal("Decline"), ignored ->
                        PaleMirrorNetwork.sendAction(AtlasActionPayload.Action.DECLINE_SCENARIO, region.scenarioId()))
                        .bounds(left + 320, row + 4, 60, 18).build());
            } else if (region.canPrepareEvacuation()) {
                addRenderableWidget(Button.builder(Component.literal("Refugee site"), ignored ->
                        PaleMirrorNetwork.sendAction(AtlasActionPayload.Action.PREPARE_EVACUATION, region.communityId()))
                        .bounds(left + 262, row + 4, 118, 18).build());
            }
            if (region.canBeginEvacuation()) {
                addRenderableWidget(Button.builder(Component.literal("Evacuate"), ignored ->
                        PaleMirrorNetwork.sendAction(AtlasActionPayload.Action.BEGIN_EVACUATION, region.communityId()))
                        .bounds(left + 262, row + 27, 118, 18).build());
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(left + 328, top + 12, 52, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelWidth = 388;
        int panelHeight = Math.min(238, 60 + snapshot.regions().size() * 58);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xE5192633);
        graphics.drawString(font, title, left + 12, top + 14, 0xD7F5FF, false);
        graphics.drawString(font, "Step " + snapshot.step() + " — server-authoritative regional record", left + 12, top + 27,
                0xA9BBC7, false);
        if (!snapshot.notice().isBlank()) graphics.drawString(font, snapshot.notice(), left + 12, top + panelHeight - 12,
                0xF7D27A, false);
        if (snapshot.regions().isEmpty()) graphics.drawString(font,
                "No recognized region belongs to this story audience yet.", left + 12, top + 48, 0xD9D9D9, false);
        for (int index = 0; index < snapshot.regions().size(); index++) drawRegion(graphics, snapshot.regions().get(index),
                left + 12, top + 42 + index * 58);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRegion(GuiGraphics graphics, PaleMirrorAtlasClient.Region region, int x, int y) {
        int crisisColor = "NONE".equals(region.crisis()) ? 0x8FE1A2 : 0xF6AA78;
        graphics.drawString(font, region.name(), x, y, 0xFFFFFF, false);
        graphics.drawString(font, region.crisis(), x + 132, y, crisisColor, false);
        graphics.drawString(font, "Iron " + region.iron() + "/" + region.ironCapacity() + "  flow " + signed(region.netFlow())
                + "  reserve " + (region.reserve() < 0 ? "stable" : region.reserve()), x, y + 13, 0xC4D5E4, false);
        graphics.drawString(font, "Defence " + region.defence() + "/" + region.baseDefence() + "  mine "
                + (region.primaryMineStatus().isBlank() ? "unrepresented" : region.primaryMineStatus()), x, y + 25, 0xC4D5E4, false);
        String story = region.scenarioTitle().isBlank() ? "Routes: " + region.primaryRoute()
                : region.scenarioTitle() + " — " + region.scenarioStatus();
        graphics.drawString(font, story, x, y + 37, 0x92C6E8, false);
    }

    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }

    @Override
    public boolean isPauseScreen() { return false; }
}
