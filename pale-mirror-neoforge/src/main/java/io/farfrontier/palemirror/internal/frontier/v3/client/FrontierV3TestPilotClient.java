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
import net.minecraft.world.entity.Display;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
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
    private static final String CAPTURE_CONTROL_PROPERTY = "pale_mirror.frontier_v3.test_pilot.capture_control_directory";
    private static final long CAPTURE_SETTLE_TICKS = 10L;
    private static JsonArray actions;
    private static JsonArray setup;
    private static JsonArray frames;
    private static boolean runningSetup;
    private static int index;
    private static long actionStartedTick = -1L;
    private static boolean breaking;
    private static boolean visitSent;
    private static long visitChunkReadyTick = -1L;
    private static Boolean originalHideGui;
    private static CaptureBarrier captureBarrier;
    private static final Map<DiagnosticIdentity, ObservedDiagnostic> diagnostics = new HashMap<>();

    private FrontierV3TestPilotClient() { }

    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        String configured = System.getProperty(SCENARIO_PROPERTY, "");
        if (configured.isBlank()) return;
        try {
            FrontierV3TestPilotScenario.Parsed scenario = FrontierV3TestPilotScenario.parse(Files.readString(Path.of(configured)));
            setup = scenario.setup(); actions = scenario.actions(); frames = scenario.frames();
            runningSetup = !setup.isEmpty(); index = 0; actionStartedTick = -1L;
            breaking = false; visitSent = false; visitChunkReadyTick = -1L;
            captureBarrier = null; diagnostics.clear();
            PaleMirrorMod.LOGGER.info("PMV3_PILOT loaded scenario={} setup={} actions={} frames={}", configured, setup.size(), actions.size(), frames.size());
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
        if (actions == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) return;
        if (captureBarrier != null) {
            advanceCaptureBarrier(minecraft);
            return;
        }
        if ((runningSetup && index >= setup.size()) || (!runningSetup && index >= actions.size())) return;
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
                case "assert_fixture" -> assertFixture(minecraft, action);
                case "visit" -> visit(minecraft, action);
                case "assert_visible_block" -> assertVisibleBlock(minecraft, action);
                case "assert_visible_board" -> assertVisibleBoard(minecraft, action);
                case "fast_forward" -> { minecraft.player.connection.sendCommand("pale_mirror v3 advance " + action.get("ticks").getAsInt()); advance(type); }
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

    /**
     * A visit is ordinary operator travel by the one network client, followed by
     * client-observed natural chunk readiness. It never asks the server to load
     * a chunk; after travel, ordinary player demand performs that work.
     */
    private static void visit(Minecraft minecraft, JsonObject action) {
        BlockPos target = position(action, "position"); String dimension = action.get("dimension").getAsString(); long tick = minecraft.level.getGameTime();
        if (!visitSent) {
            String username = minecraft.player.getGameProfile().getName();
            minecraft.player.connection.sendCommand("execute in " + dimension + " run tp " + username + " " + target.getX() + " " + target.getY() + " " + target.getZ());
            visitSent = true;
            return;
        }
        boolean ready = minecraft.level.dimension().location().toString().equals(dimension) && minecraft.level.hasChunkAt(target);
        if (ready) {
            if (visitChunkReadyTick < 0L) visitChunkReadyTick = tick;
            if ((tick - visitChunkReadyTick) * 50L >= action.get("settleMs").getAsLong()) { advance("visit"); return; }
        } else visitChunkReadyTick = -1L;
        if ((tick - actionStartedTick) * 50L >= 120_000L + action.get("settleMs").getAsLong()) {
            throw new IllegalStateException("timed out visiting naturally loaded " + dimension + " at " + target);
        }
    }

    /** Proves the player camera itself is aimed at one loaded, non-air exact block. */
    private static void assertVisibleBlock(Minecraft minecraft, JsonObject action) {
        BlockPos expected = position(action, "position");
        Vec3 delta = Vec3.atCenterOf(expected).subtract(minecraft.player.getEyePosition());
        double distance = delta.length();
        boolean aimed = distance > 0.0D && distance <= 128.0D
                && minecraft.player.getViewVector(1.0F).normalize().dot(delta.scale(1.0D / distance)) >= Math.cos(Math.toRadians(5.0D));
        if (!minecraft.level.getBlockState(expected).isAir() && aimed) {
            advance("assert_visible_block"); return;
        }
        if ((minecraft.level.getGameTime() - actionStartedTick) * 50L >= action.get("timeoutMs").getAsLong()) {
            throw new IllegalStateException("camera never targeted visible block " + expected);
        }
    }

    /**
     * Proves a named owned board is in the local rendered entity set, near its
     * expected semantic anchor and inside the player-facing camera cone. It is
     * deliberately a presentation assertion only: no board/level mutation is
     * possible through this pilot.
     */
    private static void assertVisibleBoard(Minecraft minecraft, JsonObject action) {
        BlockPos anchor = position(action, "position"); String expectedText = action.get("text").getAsString();
        double radius = action.has("radius") ? action.get("radius").getAsDouble() : 3.0D;
        double maxDistance = action.has("maxDistance") ? action.get("maxDistance").getAsDouble() : 64.0D;
        double maxAngle = Math.cos(Math.toRadians(action.has("maxAngleDeg") ? action.get("maxAngleDeg").getAsDouble() : 50.0D));
        Vec3 eye = minecraft.player.getEyePosition(); Vec3 view = minecraft.player.getViewVector(1.0F).normalize(); Vec3 expected = Vec3.atCenterOf(anchor);
        boolean visible = minecraft.level.getEntitiesOfClass(Display.TextDisplay.class, minecraft.player.getBoundingBox().inflate(maxDistance), display -> {
            if (display.getCustomName() == null || !display.getCustomName().getString().contains(expectedText)
                    || display.position().distanceToSqr(expected) > radius * radius) return false;
            Vec3 delta = display.position().subtract(eye); double distance = delta.length();
            return distance > 0.0D && distance <= maxDistance && view.dot(delta.scale(1.0D / distance)) >= maxAngle;
        }).stream().findFirst().isPresent();
        if (visible) { advance("assert_visible_board"); return; }
        if ((minecraft.level.getGameTime() - actionStartedTick) * 50L >= action.get("timeoutMs").getAsLong()) {
            throw new IllegalStateException("camera never saw board text=" + expectedText + " near " + anchor);
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
            if (action.has("settlementId")) minecraft.player.connection.sendCommand("pale_mirror v3 inspect settlement " + action.get("settlementId").getAsString());
        }
        long timeoutMs = action.get("timeoutMs").getAsLong();
        if ((tick - actionStartedTick) * 50L >= timeoutMs) {
            throw new IllegalStateException("timed out waiting for confirmed harvest site=" + siteId + " intent=" + intentId + " item=" + itemId);
        }
    }

    private static boolean fresh(ObservedDiagnostic observed) { return observed != null && observed.tick() >= actionStartedTick; }

    /** A fixture is only a bounded read-only precondition; it cannot arrange or mutate the world. */
    private static void assertFixture(Minecraft minecraft, JsonObject action) {
        long tick = minecraft.level.getGameTime(); JsonArray checks = action.getAsJsonArray("checks"); boolean allMatch = true;
        for (JsonElement element : checks) {
            JsonObject check = element.getAsJsonObject(); String view = check.get("view").getAsString(); String id = check.get("id").getAsString();
            ObservedDiagnostic observed = diagnostics.get(new DiagnosticIdentity(view, id));
            if (observed == null || observed.tick() < actionStartedTick || !matches(observed.value(), check.getAsJsonObject("expect"))) allMatch = false;
        }
        if (allMatch) { advance("assert_fixture"); return; }
        if ((tick - actionStartedTick) % 20L == 0L) {
            for (JsonElement element : checks) {
                JsonObject check = element.getAsJsonObject(); String view = check.get("view").getAsString(); String id = check.get("id").getAsString();
                minecraft.player.connection.sendCommand("pale_mirror v3 inspect " + view + (id.isBlank() ? "" : " " + id));
            }
        }
        if ((tick - actionStartedTick) * 50L >= action.get("timeoutMs").getAsLong()) throw new IllegalStateException("fixture preconditions did not converge: " + checks);
    }

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

    /**
     * Frames are captured only after a local, disposable client has hidden all
     * generic UI, closed any incidental screen and discarded its diagnostic
     * chat backlog.  This changes neither server state nor the player/world.
     */
    private static void prepareCleanCapture(Minecraft minecraft) {
        if (originalHideGui == null) originalHideGui = minecraft.options.hideGui;
        minecraft.options.hideGui = true;
        minecraft.setScreen(null);
        minecraft.gui.getChat().clearMessages(false);
    }

    private static BlockPos position(JsonObject action, String field) {
        JsonObject value = Objects.requireNonNull(action.getAsJsonObject(field), field + " position");
        return new BlockPos(value.get("x").getAsInt(), value.get("y").getAsInt(), value.get("z").getAsInt());
    }
    private static String withoutSlash(String value) { return value.startsWith("/") ? value.substring(1) : value; }
    private static void advance(String type) {
        String phase = runningSetup ? "setup" : "action";
        PaleMirrorMod.LOGGER.info("PMV3_PILOT complete {} step={} type={}", phase, index + 1, type);
        int completedAction = runningSetup ? 0 : index + 1;
        JsonObject reachedFrame = runningSetup ? null : frameAfter(completedAction);
        index++; actionStartedTick = -1L; breaking = false;
        visitSent = false; visitChunkReadyTick = -1L;
        if (runningSetup && index >= setup.size()) { runningSetup = false; index = 0; PaleMirrorMod.LOGGER.info("PMV3_PILOT setup complete; beginning evidence actions={}", actions.size()); }
        else if (reachedFrame != null) {
            JsonObject frame = reachedFrame;
            String presentation = frame.has("presentation") ? frame.get("presentation").getAsString() : "clean";
            if (presentation.equals("clean")) prepareCleanCapture(Minecraft.getInstance());
            captureBarrier = new CaptureBarrier(completedAction, frame.get("name").getAsString(), presentation,
                    Minecraft.getInstance().level.getGameTime() + CAPTURE_SETTLE_TICKS, false);
        }
        else if (!runningSetup && index >= actions.size()) {
            completeScenario();
        }
    }
    private static JsonObject frameAfter(int completedAction) {
        if (frames == null) return null;
        for (JsonElement element : frames) {
            JsonObject frame = element.getAsJsonObject();
            if (frame.get("after").getAsInt() == completedAction) return frame;
        }
        return null;
    }

    /** A filesystem handshake prevents the next chat/command action racing the X11 capture. */
    private static void advanceCaptureBarrier(Minecraft minecraft) {
        CaptureBarrier barrier = captureBarrier;
        if (minecraft.level.getGameTime() < barrier.readyAtTick()) return;
        Path control = captureControlDirectory();
        if (control == null) throw new IllegalStateException("visual frame declared without capture-control directory");
        Path ready = control.resolve("frame-" + barrier.after() + ".ready");
        Path captured = control.resolve("frame-" + barrier.after() + ".captured");
        try {
            if (!barrier.announced()) {
                Files.createDirectories(control);
                Files.writeString(ready, barrier.name() + "\n", StandardCharsets.UTF_8);
                captureBarrier = new CaptureBarrier(barrier.after(), barrier.name(), barrier.presentation(), barrier.readyAtTick(), true);
                PaleMirrorMod.LOGGER.info("PMV3_PILOT frame_ready after={} name={} presentation={}", barrier.after(), barrier.name(), barrier.presentation());
                return;
            }
            if (Files.isRegularFile(captured)) {
                Files.deleteIfExists(ready);
                captureBarrier = null;
                PaleMirrorMod.LOGGER.info("PMV3_PILOT frame_captured after={} name={}", barrier.after(), barrier.name());
                if (index >= actions.size()) completeScenario();
            }
        } catch (IOException failure) {
            throw new IllegalStateException("visual frame handshake failed for " + barrier.name(), failure);
        }
    }
    private static Path captureControlDirectory() {
        String configured = System.getProperty(CAPTURE_CONTROL_PROPERTY, "");
        return configured.isBlank() ? null : Path.of(configured);
    }
    private static void completeScenario() {
        // The outer runner must still receive the server response to the final
        // read-only assertion before it closes this ordinary client.
        PaleMirrorMod.LOGGER.info("PMV3_PILOT completed scenario actions={}", actions.size());
    }
    private static void reset() {
        Minecraft minecraft = Minecraft.getInstance(); minecraft.options.keyUp.setDown(false);
        if (originalHideGui != null) { minecraft.options.hideGui = originalHideGui; originalHideGui = null; }
        actions = null; setup = null; frames = null; captureBarrier = null; runningSetup = false; index = 0; actionStartedTick = -1L; breaking = false; visitSent = false; visitChunkReadyTick = -1L; diagnostics.clear();
    }
    private record DiagnosticIdentity(String view, String id) { }
    private record ObservedDiagnostic(long tick, JsonObject value) { }
    private record CaptureBarrier(int after, String name, String presentation, long readyAtTick, boolean announced) { }
}
