package io.farfrontier.palemirror.internal.client;

import java.util.List;

import io.farfrontier.palemirror.internal.network.PlayerContextCardPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Client-only, replace-not-stack rendering for one short contextual object card. */
public final class PaleMirrorContextCardClient {
    private static final int MIN_WIDTH = 168;
    private static final int MAX_WIDTH = 306;
    private static final int MARGIN = 12;
    private static Card card = Card.empty();
    private static long clientTick;

    private PaleMirrorContextCardClient() { }

    public static void receive(PlayerContextCardPayload payload) {
        card = new Card(payload.title(), payload.lines(), payload.accentRgb(), clientTick + payload.durationTicks());
    }

    public static void tick() { clientTick++; }

    public static void clear() { card = Card.empty(); clientTick = 0; }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || clientTick >= card.expiresAt()) return;
        Font font = minecraft.font;
        int textWidth = Math.max(font.width(card.title()), card.lines().stream().mapToInt(font::width).max().orElse(0));
        int available = Math.max(120, graphics.guiWidth() - MARGIN * 2);
        int width = Math.min(available, Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, textWidth + 20)));
        int height = 24 + card.lines().size() * 11;
        int x = graphics.guiWidth() - MARGIN - width;
        int y = MARGIN;
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xA5000000);
        graphics.fill(x, y, x + 3, y + height, 0xFF000000 | card.accentRgb());
        graphics.fill(x + 3, y, x + width, y + height, 0xD91A1A1A);
        graphics.drawString(font, fit(font, card.title(), width - 16), x + 10, y + 7, 0xFFFFFFFF, false);
        for (int index = 0; index < card.lines().size(); index++) {
            graphics.drawString(font, fit(font, card.lines().get(index), width - 16), x + 10, y + 19 + index * 11, 0xFFD6D6D6, false);
        }
    }

    private static String fit(Font font, String value, int width) {
        if (font.width(value) <= width) return value;
        return font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
    }

    static Card current() { return card; }

    record Card(String title, List<String> lines, int accentRgb, long expiresAt) {
        static Card empty() { return new Card("", List.of(), 0, Long.MIN_VALUE); }
    }
}
