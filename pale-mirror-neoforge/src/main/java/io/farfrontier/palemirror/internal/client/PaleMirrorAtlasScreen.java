package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.internal.network.AtlasActionPayload;
import io.farfrontier.palemirror.internal.network.PaleMirrorNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compact native UI: a read-only region explanation plus server-authorized action buttons. */
public final class PaleMirrorAtlasScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 500;
    private static final int PANEL_MARGIN = 16;
    private static final int HEADER_HEIGHT = 42;
    private static final int REGION_HEIGHT = 82;
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
        Layout layout = layout();
        for (int index = 0; index < snapshot.regions().size(); index++) {
            PaleMirrorAtlasClient.Region region = snapshot.regions().get(index);
            int row = layout.top() + HEADER_HEIGHT + 1 + index * REGION_HEIGHT;
            int actionLeft = layout.right() - 138;
            if (!region.scenarioId().isBlank() && "OFFERED".equals(region.scenarioStatus())) {
                addRenderableWidget(Button.builder(Component.translatable("screen.pale_mirror.atlas.accept"), ignored ->
                        PaleMirrorNetwork.sendAction(AtlasActionPayload.Action.ACCEPT_SCENARIO, region.scenarioId()))
                        .bounds(actionLeft, row + 4, 60, 18).build());
                addRenderableWidget(Button.builder(Component.translatable("screen.pale_mirror.atlas.decline"), ignored ->
                        PaleMirrorNetwork.sendAction(AtlasActionPayload.Action.DECLINE_SCENARIO, region.scenarioId()))
                        .bounds(actionLeft + 64, row + 4, 66, 18).build());
            } else if (region.canPrepareEvacuation()) {
                addRenderableWidget(Button.builder(Component.translatable("screen.pale_mirror.atlas.prepare_shelter"), ignored ->
                        PaleMirrorNetwork.sendAction(AtlasActionPayload.Action.PREPARE_EVACUATION, region.communityId()))
                        .bounds(actionLeft, row + 4, 130, 18).build());
            }
            if (region.canBeginEvacuation()) {
                addRenderableWidget(Button.builder(Component.translatable("screen.pale_mirror.atlas.begin_evacuation"), ignored ->
                        PaleMirrorNetwork.sendAction(AtlasActionPayload.Action.BEGIN_EVACUATION, region.communityId()))
                        .bounds(actionLeft, row + 27, 130, 18).build());
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(layout.right() - 60, layout.top() + 10, 52, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = layout();
        // Atlas is an in-world instrument, not a modal menu. Keep the world sharp
        // and darken only the compact panel instead of invoking Screen's fullscreen blur.
        graphics.fill(layout.left() + 3, layout.top() + 3, layout.right() + 3, layout.bottom() + 3, 0x66000000);
        graphics.fill(layout.left() - 1, layout.top() - 1, layout.right() + 1, layout.bottom() + 1, 0xFF5E7485);
        graphics.fill(layout.left(), layout.top(), layout.right(), layout.bottom(), 0xF2192633);
        graphics.drawString(font, title, layout.left() + 10, layout.top() + 12, 0xD7F5FF, false);
        graphics.drawString(font, Component.translatable("screen.pale_mirror.atlas.step", snapshot.step()),
                layout.left() + 10, layout.top() + 25, 0xA9BBC7, false);
        if (!snapshot.notice().isBlank()) graphics.drawString(font, snapshot.notice(), layout.left() + 10,
                layout.bottom() - 12,
                0xF7D27A, false);
        if (snapshot.regions().isEmpty()) graphics.drawString(font,
                Component.translatable("screen.pale_mirror.atlas.no_regions"), layout.left() + 10,
                layout.top() + 44, 0xD9D9D9, false);
        for (int index = 0; index < snapshot.regions().size(); index++) drawRegion(graphics, snapshot.regions().get(index),
                layout.left() + 10, layout.top() + HEADER_HEIGHT + index * REGION_HEIGHT, layout.width());
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Screen.render invokes this again before rendering widgets; Atlas already owns its compact panel. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Deliberately no fullscreen menu texture or post-process blur.
    }

    private void drawRegion(GuiGraphics graphics, PaleMirrorAtlasClient.Region region, int x, int y, int panelWidth) {
        int secondaryX = x + Math.max(230, panelWidth - 235);
        int crisisColor = "NONE".equals(region.crisis()) ? 0x8FE1A2 : 0xF6AA78;
        graphics.drawString(font, region.name(), x, y, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.pale_mirror.atlas.crisis."
                + region.crisis().toLowerCase(java.util.Locale.ROOT)), x + 150, y, crisisColor, false);
        if (!region.supplyKnown()) {
            graphics.drawString(font, Component.translatable("screen.pale_mirror.atlas.discover_depot"), x, y + 15,
                    0xF7D27A, false);
            graphics.drawString(font, Component.translatable("screen.pale_mirror.atlas.discovery_hint"), x, y + 30,
                    0xC4D5E4, false);
        } else {
            graphics.drawString(font, Component.translatable("screen.pale_mirror.atlas.cause",
                    diagnosis(region.primaryDiagnosis())), x, y + 15, 0xF7D27A, false);
            graphics.drawString(font, Component.translatable("screen.pale_mirror.atlas.iron",
                    region.iron(), region.ironCapacity(), signed(region.netFlow()),
                    region.reserve() < 0 ? Component.translatable("screen.pale_mirror.atlas.stable") : region.reserve()),
                    x, y + 30, 0xC4D5E4, false);
            graphics.drawString(font, Component.translatable("screen.pale_mirror.atlas.defence",
                    region.defence(), region.baseDefence()), x, y + 45, 0xC4D5E4, false);
            graphics.drawString(font, Component.translatable("screen.pale_mirror.atlas.routes",
                    diagnosis(region.primaryDiagnosis()), region.primaryCapacity(), region.primaryNominalCapacity(),
                    diagnosis(region.alternateDiagnosis()), region.alternateCapacity(), region.alternateNominalCapacity()),
                    x, y + 60, 0x92C6E8, false);
        }
        if (!region.scenarioTitle().isBlank()) graphics.drawString(font, Component.translatable(
                "scenario.pale_mirror." + region.scenarioTitle() + ".title"), secondaryX, y + 27, 0x92C6E8, false);
        if (region.primaryRepairCount() > 0 && region.primaryRepair().known()) {
            graphics.drawString(font, Component.translatable("screen.pale_mirror.atlas.route_repair",
                    region.primaryRepairCount(), region.primaryRepair().x(), region.primaryRepair().y(),
                    region.primaryRepair().z()), secondaryX, y + 60, 0xF6AA78, false);
        }
        if ("OPEN".equals(region.emergency())) graphics.drawString(font, Component.translatable(
                "screen.pale_mirror.atlas.intervention", region.remainingGrace(),
                Component.translatable("screen.pale_mirror.atlas.reachability."
                        + region.reachability().toLowerCase(java.util.Locale.ROOT))), secondaryX, y + 45, 0xF6AA78, false);
        else if ("PLANNED".equals(region.developmentState()) && region.developmentRequired() > 0) {
            graphics.drawString(font, Component.translatable("screen.pale_mirror.atlas.development",
                    region.developmentContributed(), region.developmentRequired(), region.developmentWait(),
                    region.developmentWaitRequired()), secondaryX, y + 45, 0x8FE1A2, false);
        } else if ("ACTIVE".equals(region.developmentState())) {
            graphics.drawString(font, Component.translatable("screen.pale_mirror.atlas.development_complete"),
                    secondaryX, y + 45, 0x8FE1A2, false);
        }
    }

    private Layout layout() {
        int availableWidth = Math.max(280, width - PANEL_MARGIN * 2);
        int panelWidth = Math.min(PANEL_MAX_WIDTH, availableWidth);
        int requestedHeight = 58 + snapshot.regions().size() * REGION_HEIGHT;
        int panelHeight = Math.min(Math.max(72, height - PANEL_MARGIN * 2), Math.min(330, requestedHeight));
        return new Layout(PANEL_MARGIN, PANEL_MARGIN, panelWidth, panelHeight);
    }

    private static Component diagnosis(String value) {
        String key = value == null || value.isBlank() ? "not_established"
                : value.toLowerCase(java.util.Locale.ROOT);
        return Component.translatable("screen.pale_mirror.atlas.diagnosis." + key);
    }

    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }

    @Override
    public boolean isPauseScreen() { return false; }

    private record Layout(int left, int top, int width, int height) {
        int right() { return left + width; }
        int bottom() { return top + height; }
    }
}
