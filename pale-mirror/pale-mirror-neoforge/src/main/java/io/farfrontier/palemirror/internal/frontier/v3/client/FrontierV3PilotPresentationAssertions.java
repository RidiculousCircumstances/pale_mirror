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

    static boolean blockReady(Minecraft minecraft, JsonObject action, BlockPos target, long startedTick) {
        String expected = action.has("block") ? action.get("block").getAsString() : "minecraft:air";
        var state = minecraft.level.getBlockState(target);
        String actual = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (actual.equals(expected) && matchesProperties(state, action)) return true;
        long limit = action.has("timeoutMs") ? action.get("timeoutMs").getAsLong() : 30_000L;
        if ((minecraft.level.getGameTime() - startedTick) * 50L >= limit)
            throw new IllegalStateException("timed out waiting for " + expected + " at " + target + "; client saw " + state);
        return false;
    }

    static boolean matchesProperties(net.minecraft.world.level.block.state.BlockState state, JsonObject action) {
        return !action.has("properties") || action.getAsJsonObject("properties").entrySet().stream().allMatch(entry ->
                state.getValues().entrySet().stream().anyMatch(property -> property.getKey().getName().equals(entry.getKey())
                        && property.getValue().toString().equals(entry.getValue().getAsString())));
    }

    static void assertVisibleBoard(Minecraft minecraft, JsonObject action, long startedTick, BlockPos anchor,
                                   Consumer<BlockPos> look, Consumer<BlockPos> focus, Consumer<String> advance) {
        String expectedText = action.get("text").getAsString(); double radius = action.has("radius") ? action.get("radius").getAsDouble() : 3.0D;
        double maxDistance = action.has("maxDistance") ? action.get("maxDistance").getAsDouble() : 64.0D;
        double maxAngle = Math.cos(Math.toRadians(action.has("maxAngleDeg") ? action.get("maxAngleDeg").getAsDouble() : 50.0D));
        look.accept(anchor); focus.accept(anchor);
        Vec3 eye = minecraft.player.getEyePosition(); Vec3 view = minecraft.player.getViewVector(1.0F).normalize(); Vec3 expected = Vec3.atCenterOf(anchor);
        boolean visible = minecraft.level.getEntitiesOfClass(Display.TextDisplay.class, minecraft.player.getBoundingBox().inflate(maxDistance), display -> {
            var rendered = display.textRenderState();
            if (rendered == null || !rendered.text().getString().contains(expectedText)
                    || display.position().distanceToSqr(expected) > radius * radius) return false;
            Vec3 delta = display.position().subtract(eye); double distance = delta.length();
            return distance > 0.0D && distance <= maxDistance && view.dot(delta.scale(1.0D / distance)) >= maxAngle;
        }).stream().findFirst().isPresent();
        if (visible) { advance.accept("assert_visible_board"); return; }
        if ((minecraft.level.getGameTime() - startedTick) * 50L >= action.get("timeoutMs").getAsLong()) {
            String candidates = minecraft.level.getEntitiesOfClass(Display.TextDisplay.class, minecraft.player.getBoundingBox().inflate(maxDistance), display -> true).stream()
                    .limit(12).map(display -> {
                        var rendered = display.textRenderState();
                        return display.blockPosition() + ":" + (rendered == null ? "TEXT_NOT_READY" : rendered.text().getString().replace('\n', '/'));
                    }).collect(java.util.stream.Collectors.joining(","));
            throw new IllegalStateException("camera never saw board text=" + expectedText + " near " + anchor + "; candidates=" + candidates);
        }
    }
}
