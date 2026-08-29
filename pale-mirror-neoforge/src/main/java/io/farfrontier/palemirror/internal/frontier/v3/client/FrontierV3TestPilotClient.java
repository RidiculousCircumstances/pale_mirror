package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Development-only visible pilot. It drives the ordinary NeoForge client interaction layer;
 * it has no v3 API, server state or world-file access. The server still receives normal player packets.
 */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID, value = Dist.CLIENT)
public final class FrontierV3TestPilotClient {
    private static final String SCENARIO_PROPERTY = "pale_mirror.frontier_v3.test_pilot.scenario";
    private static JsonArray actions;
    private static JsonArray setup;
    private static boolean runningSetup;
    private static int index;
    private static long actionStartedTick = -1L;
    private static boolean breaking;

    private FrontierV3TestPilotClient() { }

    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        String configured = System.getProperty(SCENARIO_PROPERTY, "");
        if (configured.isBlank()) return;
        try {
            FrontierV3TestPilotScenario.Parsed scenario = FrontierV3TestPilotScenario.parse(Files.readString(Path.of(configured)));
            setup = scenario.setup(); actions = scenario.actions(); runningSetup = !setup.isEmpty(); index = 0; actionStartedTick = -1L; breaking = false;
            PaleMirrorMod.LOGGER.info("PMV3_PILOT loaded scenario={} setup={} actions={}", configured, setup.size(), actions.size());
        } catch (IOException | IllegalArgumentException failure) {
            actions = null;
            PaleMirrorMod.LOGGER.error("PMV3_PILOT rejected scenario {}", configured, failure);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (actions == null || (runningSetup && index >= setup.size()) || (!runningSetup && index >= actions.size())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) return;
        JsonObject action = (runningSetup ? setup : actions).get(index).getAsJsonObject();
        String type = action.get("type").getAsString();
        long tick = minecraft.level.getGameTime();
        if (actionStartedTick < 0L) {
            actionStartedTick = tick;
            PaleMirrorMod.LOGGER.info("PMV3_PILOT step={} type={}", index + 1, type);
        }
        try {
            switch (type) {
                case "wait" -> { if (tick - actionStartedTick >= action.get("ms").getAsLong() / 50L) advance(type); }
                case "wait_until_block" -> waitUntilBlock(minecraft, action);
                case "command" -> { minecraft.player.connection.sendCommand(withoutSlash(action.get("command").getAsString())); advance(type); }
                case "inspect" -> { minecraft.player.connection.sendCommand("pale_mirror v3 inspect " + action.get("view").getAsString()
                        + (action.get("id").getAsString().isBlank() ? "" : " " + action.get("id").getAsString())); advance(type); }
                case "look" -> { look(minecraft, position(action, "at")); advance(type); }
                case "walk" -> walk(minecraft, position(action, "position"), action.has("radius") ? action.get("radius").getAsDouble() : 1.0D);
                case "break" -> breakBlock(minecraft, position(action, "position"));
                default -> throw new IllegalArgumentException("unsupported visible pilot action: " + type);
            }
        } catch (RuntimeException failure) {
            PaleMirrorMod.LOGGER.error("PMV3_PILOT failed step={} type={}", index + 1, type, failure);
            reset();
        }
    }

    private static void walk(Minecraft minecraft, BlockPos target, double radius) {
        Vec3 current = minecraft.player.position();
        double dx = target.getX() + 0.5D - current.x, dz = target.getZ() + 0.5D - current.z;
        if (dx * dx + dz * dz <= radius * radius) { minecraft.options.keyUp.setDown(false); advance("walk"); return; }
        minecraft.player.setYRot((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG));
        minecraft.options.keyUp.setDown(true);
    }

    private static void breakBlock(Minecraft minecraft, BlockPos target) {
        if (minecraft.level.getBlockState(target).isAir()) { breaking = false; advance("break"); return; }
        if (!breaking) { minecraft.gameMode.startDestroyBlock(target, Direction.UP); breaking = true; }
        else minecraft.gameMode.continueDestroyBlock(target, Direction.UP);
    }

    private static void waitUntilBlock(Minecraft minecraft, JsonObject action) {
        BlockPos target = position(action, "position");
        String expected = action.has("block") ? action.get("block").getAsString() : "minecraft:air";
        String actual = BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(target).getBlock()).toString();
        if (actual.equals(expected)) { advance("wait_until_block"); return; }
        long timeoutMs = action.has("timeoutMs") ? action.get("timeoutMs").getAsLong() : 30_000L;
        if ((minecraft.level.getGameTime() - actionStartedTick) * 50L >= timeoutMs) {
            throw new IllegalStateException("timed out waiting for " + expected + " at " + target + "; client saw " + actual);
        }
    }

    private static void look(Minecraft minecraft, BlockPos target) {
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 delta = Vec3.atCenterOf(target).subtract(eye);
        minecraft.player.setYRot((float) (Mth.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG));
        minecraft.player.setXRot((float) -(Mth.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * Mth.RAD_TO_DEG));
    }

    private static BlockPos position(JsonObject action, String field) {
        JsonObject value = Objects.requireNonNull(action.getAsJsonObject(field), field + " position");
        return new BlockPos(value.get("x").getAsInt(), value.get("y").getAsInt(), value.get("z").getAsInt());
    }
    private static String withoutSlash(String value) { return value.startsWith("/") ? value.substring(1) : value; }
    private static void advance(String type) {
        String phase = runningSetup ? "setup" : "action";
        PaleMirrorMod.LOGGER.info("PMV3_PILOT complete {} step={} type={}", phase, index + 1, type);
        index++; actionStartedTick = -1L; breaking = false;
        if (runningSetup && index >= setup.size()) { runningSetup = false; index = 0; PaleMirrorMod.LOGGER.info("PMV3_PILOT setup complete; beginning evidence actions={}", actions.size()); }
        else if (!runningSetup && index >= actions.size()) PaleMirrorMod.LOGGER.info("PMV3_PILOT completed scenario actions={}", actions.size());
    }
    private static void reset() { Minecraft minecraft = Minecraft.getInstance(); minecraft.options.keyUp.setDown(false); actions = null; setup = null; runningSetup = false; index = 0; actionStartedTick = -1L; breaking = false; }
}
