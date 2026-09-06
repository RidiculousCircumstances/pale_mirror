package io.farfrontier.palemirror.internal.client;

import java.util.List;

import io.farfrontier.palemirror.internal.network.PlayerContextCardPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Client-only, replace-not-stack rendering for one short contextual object card. */
public final class PaleMirrorContextCardClient {
    private static final int MIN_WIDTH = 154;
    private static final int MAX_WIDTH = 260;
    private static final int MARGIN = 12;
    private static final int MAX_SCREEN_PERCENT = 34;
    private static Card card = Card.empty();
    private static long clientTick;

    private PaleMirrorContextCardClient() { }

    public static void receive(PlayerContextCardPayload payload) {
        card = new Card(payload.title(), payload.lines(), payload.accentRgb(), clientTick + payload.durationTicks());
    }

    public static void tick() { clientTick++; }

    public static void clear() { card = Card.empty(); clientTick = 0; }

    /** Test-pilot-only read of local presentation receipt; never a server or canonical query. */
    public static boolean hasActiveTitle(String title) {
        return title != null && clientTick < card.expiresAt() && title.equals(card.title());
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || clientTick >= card.expiresAt()) return;
        Font font = minecraft.font;
        int textWidth = Math.max(font.width(card.title()), card.lines().stream().mapToInt(font::width).max().orElse(0));
        int width = layout(graphics.guiWidth(), textWidth).width();
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

    /**
     * The inspection card is an intentional, temporary view, not a second
     * HUD.  Its maximum is proportional to the current GUI surface so a
     * large monitor or a low GUI scale cannot turn one object inspection into
     * a screen-wide banner.
     */
    static Layout layout(int guiWidth, int textWidth) {
        int available = Math.max(1, guiWidth - MARGIN * 2);
        int minimum = Math.min(MIN_WIDTH, available);
        int proportionalMaximum = Math.max(minimum, guiWidth * MAX_SCREEN_PERCENT / 100);
        int maximum = Math.min(available, Math.min(MAX_WIDTH, proportionalMaximum));
        int desired = Math.max(minimum, textWidth + 20);
        return new Layout(Math.min(maximum, desired));
    }

    static Card current() { return card; }

    record Layout(int width) {
        Layout {
            if (width < 1) throw new IllegalArgumentException("card width must be positive");
        }
    }

    record Card(String title, List<String> lines, int accentRgb, long expiresAt) {
        static Card empty() { return new Card("", List.of(), 0, Long.MIN_VALUE); }
    }
}
