package com.vibecraft.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import java.io.IOException;
import java.util.Base64;

/** Captures the actual framebuffer on the render thread without disk callbacks. */
public final class ScreenshotHandler {
    private ScreenshotHandler() {}
    public static JsonObject capture(int maxWidth, int maxHeight) throws IOException {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) throw new IllegalStateException("Screenshot requires render thread");
        if (maxWidth < 1 || maxWidth > 3840 || maxHeight < 1 || maxHeight > 2160) {
            throw new IllegalArgumentException("Screenshot bounds must be 1..3840 by 1..2160");
        }
        try (NativeImage original = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            double scale = Math.min(1.0, Math.min((double) maxWidth / original.getWidth(),
                    (double) maxHeight / original.getHeight()));
            int width = Math.max(1, (int) (original.getWidth() * scale));
            int height = Math.max(1, (int) (original.getHeight() * scale));
            byte[] png;
            if (scale < 1.0) {
                try (NativeImage resized = new NativeImage(width, height, false)) {
                    original.resizeSubRectTo(0, 0, original.getWidth(), original.getHeight(), resized);
                    png = resized.asByteArray();
                }
            } else { png = original.asByteArray(); }
            JsonObject result = new JsonObject();
            result.addProperty("image", "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
            result.addProperty("width", width);
            result.addProperty("height", height);
            result.addProperty("original_width", original.getWidth());
            result.addProperty("original_height", original.getHeight());
            if (client.player != null) {
                JsonObject position = new JsonObject();
                position.addProperty("x", client.player.getX());
                position.addProperty("y", client.player.getY());
                position.addProperty("z", client.player.getZ());
                result.add("player_position", position);
                JsonObject rotation = new JsonObject();
                rotation.addProperty("yaw", client.player.getYRot());
                rotation.addProperty("pitch", client.player.getXRot());
                result.add("player_rotation", rotation);
            }
            return result;
        }
    }
}
