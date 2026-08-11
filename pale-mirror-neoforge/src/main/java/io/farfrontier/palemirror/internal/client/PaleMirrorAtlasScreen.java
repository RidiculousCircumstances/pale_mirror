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
        int left = (width - 520) / 2;
        int top = (height - Math.min(330, 68 + snapshot.regions().size() * 82)) / 2;
        for (int index = 0; index < snapshot.regions().size(); index++) {
            PaleMirrorAtlasClient.Region region = snapshot.regions().get(index);
            int row = top + 43 + index * 82;
            if (!region.scenarioId().isBlank() && "OFFERED".equals(region.scenarioStatus())) {
                addRenderableWidget(Button.builder(Component.translatable("screen.pale_mirror.atlas.accept"), ignored ->
                        PaleMirrorNetwork.sendAction(AtlasActionPayload.Action.ACCEPT_SCENARIO, region.scenarioId()))
                        .bounds(left + 382, row + 4, 60, 18).build());
                addRenderableWidget(Button.builder(Component.translatable("screen.pale_mirror.atlas.decline"), ignored ->
                        PaleMirrorNetwork.sendAction(AtlasActionPayload.Action.DECLINE_SCENARIO, region.scenarioId()))
                        .bounds(left + 446, row + 4, 66, 18).build());
            } else if (region.canPrepareEvacuation()) {
                addRenderableWidget(Button.builder(Component.translatable("screen.pale_mirror.atlas.prepare_shelter"), ignored ->
                        PaleMirrorNetwork.sendAction(AtlasActionPayload.Action.PREPARE_EVACUATION, region.communityId()))
                        .bounds(left + 382, row + 4, 130, 18).build());
            }
            if (region.canBeginEvacuation()) {
                addRenderableWidget(Button.builder(Component.translatable("screen.pale_mirror.atlas.begin_evacuation"), ignored ->
                        PaleMirrorNetwork.sendAction(AtlasActionPayload.Action.BEGIN_EVACUATION, region.communityId()))
                        .bounds(left + 382, row + 27, 130, 18).build());
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(left + 460, top + 12, 52, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelWidth = 520;
        int panelHeight = Math.min(330, 68 + snapshot.regions().size() * 82);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xE5192633);
        graphics.drawString(font, title, left + 12, top + 14, 0xD7F5FF, false);
        graphics.drawString(font, Component.translatable("screen.pale_mirror.atlas.step", snapshot.step()), left + 12,
                top + 27, 0xA9BBC7, false);
        if (!snapshot.notice().isBlank()) graphics.drawString(font, snapshot.notice(), left + 12, top + panelHeight - 12,
                0xF7D27A, false);
        if (snapshot.regions().isEmpty()) graphics.drawString(font,
                Component.translatable("screen.pale_mirror.atlas.no_regions"), left + 12, top + 48, 0xD9D9D9, false);
        for (int index = 0; index < snapshot.regions().size(); index++) drawRegion(graphics, snapshot.regions().get(index),
                left + 12, top + 42 + index * 82);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRegion(GuiGraphics graphics, PaleMirrorAtlasClient.Region region, int x, int y) {
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
                "scenario.pale_mirror." + region.scenarioTitle() + ".title"), x + 285, y + 27, 0x92C6E8, false);
        if ("OPEN".equals(region.emergency())) graphics.drawString(font, Component.translatable(
                "screen.pale_mirror.atlas.intervention", region.remainingGrace(),
                Component.translatable("screen.pale_mirror.atlas.reachability."
                        + region.reachability().toLowerCase(java.util.Locale.ROOT))), x + 285, y + 45, 0xF6AA78, false);
        else if ("PLANNED".equals(region.developmentState()) && region.developmentRequired() > 0) {
            graphics.drawString(font, Component.translatable("screen.pale_mirror.atlas.development",
                    region.developmentContributed(), region.developmentRequired(), region.developmentWait(),
                    region.developmentWaitRequired()), x + 285, y + 45, 0x8FE1A2, false);
        } else if ("ACTIVE".equals(region.developmentState())) {
            graphics.drawString(font, Component.translatable("screen.pale_mirror.atlas.development_complete"),
                    x + 285, y + 45, 0x8FE1A2, false);
        }
    }

    private static Component diagnosis(String value) {
        String key = value == null || value.isBlank() ? "not_established"
                : value.toLowerCase(java.util.Locale.ROOT);
        return Component.translatable("screen.pale_mirror.atlas.diagnosis." + key);
    }

    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }

    @Override
    public boolean isPauseScreen() { return false; }
}
