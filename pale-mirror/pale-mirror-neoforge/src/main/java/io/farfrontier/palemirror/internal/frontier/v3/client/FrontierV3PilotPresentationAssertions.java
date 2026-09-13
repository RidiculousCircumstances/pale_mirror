package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/** Player-local presentation checks; these inspect received render state and never mutate the world. */
final class FrontierV3PilotPresentationAssertions {
    private FrontierV3PilotPresentationAssertions() { }

    static void assertVisibleBoard(Minecraft minecraft, JsonObject action, long startedTick, BlockPos anchor,
                                   Consumer<BlockPos> look, Consumer<BlockPos> focus, Consumer<String> advance) {
        String expectedText = action.get("text").getAsString(); double radius = action.has("radius") ? action.get("radius").getAsDouble() : 3.0D;
        double maxDistance = action.has("maxDistance") ? action.get("maxDistance").getAsDouble() : 64.0D;
        double maxAngle = Math.cos(Math.toRadians(action.has("maxAngleDeg") ? action.get("maxAngleDeg").getAsDouble() : 50.0D));
        look.accept(anchor); focus.accept(anchor);
        Vec3 eye = minecraft.player.getEyePosition(); Vec3 view = minecraft.player.getViewVector(1.0F).normalize(); Vec3 expected = Vec3.atCenterOf(anchor);
        boolean visible = minecraft.level.getEntitiesOfClass(Display.TextDisplay.class, minecraft.player.getBoundingBox().inflate(maxDistance), display -> {
            if (!display.textRenderState().text().getString().contains(expectedText) || display.position().distanceToSqr(expected) > radius * radius) return false;
            Vec3 delta = display.position().subtract(eye); double distance = delta.length();
            return distance > 0.0D && distance <= maxDistance && view.dot(delta.scale(1.0D / distance)) >= maxAngle;
        }).stream().findFirst().isPresent();
        if (visible) { advance.accept("assert_visible_board"); return; }
        if ((minecraft.level.getGameTime() - startedTick) * 50L >= action.get("timeoutMs").getAsLong()) {
            String candidates = minecraft.level.getEntitiesOfClass(Display.TextDisplay.class, minecraft.player.getBoundingBox().inflate(maxDistance), display -> true).stream()
                    .limit(12).map(display -> display.blockPosition() + ":" + display.textRenderState().text().getString().replace('\n', '/')).collect(java.util.stream.Collectors.joining(","));
            throw new IllegalStateException("camera never saw board text=" + expectedText + " near " + anchor + "; candidates=" + candidates);
        }
    }
}
