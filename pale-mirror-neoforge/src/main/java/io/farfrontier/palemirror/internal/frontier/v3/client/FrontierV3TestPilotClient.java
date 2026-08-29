package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
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
    private static Boolean originalHideGui;
    private static final Map<DiagnosticIdentity, ObservedDiagnostic> diagnostics = new HashMap<>();

    private FrontierV3TestPilotClient() { }

    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        String configured = System.getProperty(SCENARIO_PROPERTY, "");
        if (configured.isBlank()) return;
        try {
            FrontierV3TestPilotScenario.Parsed scenario = FrontierV3TestPilotScenario.parse(Files.readString(Path.of(configured)));
            setup = scenario.setup(); actions = scenario.actions(); runningSetup = !setup.isEmpty(); index = 0; actionStartedTick = -1L; breaking = false; diagnostics.clear();
            PaleMirrorMod.LOGGER.info("PMV3_PILOT loaded scenario={} setup={} actions={}", configured, setup.size(), actions.size());
        } catch (IOException | IllegalArgumentException failure) {
            actions = null;
            PaleMirrorMod.LOGGER.error("PMV3_PILOT rejected scenario {}", configured, failure);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }

    /** Retains only the latest server-authored read-only diagnostic from ordinary client chat. */
    @SubscribeEvent
    public static void diagnostic(ClientChatReceivedEvent.System event) {
        String message = event.getMessage().getString(); int marker = message.indexOf("PMV3_DIAG ");
        if (marker < 0) return;
        try {
            JsonObject value = JsonParser.parseString(message.substring(marker + "PMV3_DIAG ".length())).getAsJsonObject();
            if (!value.has("kind") || !value.has("id")) return;
            Minecraft minecraft = Minecraft.getInstance(); long tick = minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime();
            diagnostics.put(new DiagnosticIdentity(value.get("kind").getAsString(), value.get("id").getAsString()), new ObservedDiagnostic(tick, value));
        } catch (RuntimeException ignored) {
            // Only the server command's prefixed JSON is useful to the pilot; other chat remains presentation.
        }
    }

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
            PaleMirrorMod.LOGGER.info("PMV3_PILOT step={} phase={} type={}", index + 1, runningSetup ? "setup" : "action", type);
        }
        try {
            switch (type) {
                case "wait" -> { if (tick - actionStartedTick >= action.get("ms").getAsLong() / 50L) advance(type); }
                case "wait_until_block" -> waitUntilBlock(minecraft, action);
                case "wait_until_diagnostic" -> waitUntilDiagnostic(minecraft, action);
                case "wait_until_harvest_result" -> waitUntilHarvestResult(minecraft, action);
                case "fast_forward" -> { minecraft.player.connection.sendCommand("pale_mirror v3 advance " + action.get("ticks").getAsInt()); advance(type); }
                case "command" -> { minecraft.player.connection.sendCommand(withoutSlash(action.get("command").getAsString())); advance(type); }
                case "inspect" -> { minecraft.player.connection.sendCommand("pale_mirror v3 inspect " + action.get("view").getAsString()
                        + (action.get("id").getAsString().isBlank() ? "" : " " + action.get("id").getAsString())); advance(type); }
                case "look" -> { look(minecraft, position(action, "at")); advance(type); }
                case "walk" -> walk(minecraft, position(action, "position"), action.has("radius") ? action.get("radius").getAsDouble() : 1.0D);
                case "break" -> breakBlock(minecraft, position(action, "position"));
                case "hud" -> { setHud(minecraft, action.get("visible").getAsBoolean()); advance(type); }
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

    /** Polls the existing read-only diagnostic command at most once per second until a fresh exact predicate arrives. */
    private static void waitUntilDiagnostic(Minecraft minecraft, JsonObject action) {
        String view = action.get("view").getAsString(); String id = action.get("id").getAsString(); long tick = minecraft.level.getGameTime();
        ObservedDiagnostic observed = diagnostics.get(new DiagnosticIdentity(view, id));
        if (observed != null && observed.tick() >= actionStartedTick && matches(observed.value(), action.getAsJsonObject("expect"))) {
            advance("wait_until_diagnostic"); return;
        }
        if ((tick - actionStartedTick) % 20L == 0L) minecraft.player.connection.sendCommand("pale_mirror v3 inspect " + view + (id.isBlank() ? "" : " " + id));
        long timeoutMs = action.get("timeoutMs").getAsLong();
        if ((tick - actionStartedTick) * 50L >= timeoutMs) {
            throw new IllegalStateException("timed out waiting for diagnostic " + view + " " + id + " predicate=" + action.get("expect"));
        }
    }

    /**
     * Waits for one complete physical harvest result: the intent is durably confirmed,
     * the exact wheat stack has canonical depot custody, and the site entered its next
     * growth epoch.  It intentionally never treats READY/HARVESTING as success.
     */
    private static void waitUntilHarvestResult(Minecraft minecraft, JsonObject action) {
        String siteId = action.get("siteId").getAsString(); String intentId = action.get("intentId").getAsString(); String itemId = action.get("itemId").getAsString();
        long tick = minecraft.level.getGameTime();
        ObservedDiagnostic site = diagnostics.get(new DiagnosticIdentity("site", siteId));
        ObservedDiagnostic intent = diagnostics.get(new DiagnosticIdentity("intent", intentId));
        ObservedDiagnostic item = diagnostics.get(new DiagnosticIdentity("item", itemId));
        if (fresh(site) && fresh(intent) && fresh(item) && harvestComplete(site.value(), intent.value(), item.value())) {
            advance("wait_until_harvest_result"); return;
        }
        if ((tick - actionStartedTick) % 20L == 0L) {
            minecraft.player.connection.sendCommand("pale_mirror v3 inspect site " + siteId);
            minecraft.player.connection.sendCommand("pale_mirror v3 inspect intent " + intentId);
            minecraft.player.connection.sendCommand("pale_mirror v3 inspect item " + itemId);
        }
        long timeoutMs = action.get("timeoutMs").getAsLong();
        if ((tick - actionStartedTick) * 50L >= timeoutMs) {
            throw new IllegalStateException("timed out waiting for confirmed harvest site=" + siteId + " intent=" + intentId + " item=" + itemId);
        }
    }

    private static boolean fresh(ObservedDiagnostic observed) { return observed != null && observed.tick() >= actionStartedTick; }

    private static boolean harvestComplete(JsonObject site, JsonObject intent, JsonObject item) {
        if (!"ok".equals(string(site, "status")) || !"GROWING".equals(string(site, "phase")) || site.get("growthEpoch").getAsLong() < 2L
                || !"ok".equals(string(intent, "status")) || !"CONFIRMED".equals(string(intent, "intentStatus"))
                || !"RESOURCE_SITE_HARVEST".equals(string(intent, "intentKind"))
                || !"ok".equals(string(item, "status")) || !"minecraft:wheat".equals(string(item, "itemKind")) || item.get("count").getAsInt() != 64) return false;
        JsonObject custody = item.getAsJsonObject("custody");
        return custody != null && "CONTAINER_SLOT".equals(string(custody, "kind"));
    }

    private static String string(JsonObject object, String member) {
        JsonElement value = object.get(member); return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static boolean matches(JsonObject actual, JsonObject expected) {
        for (Map.Entry<String, JsonElement> entry : expected.entrySet()) {
            JsonElement value = actual.get(entry.getKey());
            if (value == null) return false;
            if (entry.getValue().isJsonObject()) {
                if (!value.isJsonObject() || !matches(value.getAsJsonObject(), entry.getValue().getAsJsonObject())) return false;
            } else if (!value.equals(entry.getValue())) return false;
        }
        return true;
    }

    private static void look(Minecraft minecraft, BlockPos target) {
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 delta = Vec3.atCenterOf(target).subtract(eye);
        minecraft.player.setYRot((float) (Mth.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG));
        minecraft.player.setXRot((float) -(Mth.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * Mth.RAD_TO_DEG));
    }

    /** Local presentation only; restored when the disposable pilot disconnects. */
    private static void setHud(Minecraft minecraft, boolean visible) {
        if (originalHideGui == null) originalHideGui = minecraft.options.hideGui;
        minecraft.options.hideGui = !visible;
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
    private static void reset() {
        Minecraft minecraft = Minecraft.getInstance(); minecraft.options.keyUp.setDown(false);
        if (originalHideGui != null) { minecraft.options.hideGui = originalHideGui; originalHideGui = null; }
        actions = null; setup = null; runningSetup = false; index = 0; actionStartedTick = -1L; breaking = false; diagnostics.clear();
    }
    private record DiagnosticIdentity(String view, String id) { }
    private record ObservedDiagnostic(long tick, JsonObject value) { }
}
