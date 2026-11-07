package com.vibecraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.minecraft.commands.CommandSourceStack;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


@Mod(value = VibeCraftClientMod.MOD_ID, dist = Dist.CLIENT)
public final class VibeCraftClientMod {
    public static final String MOD_ID = "vibecraft_client";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    // 30 seconds for large region scans (64x64x64 = 262k blocks)
    private static final Duration CLIENT_THREAD_TIMEOUT = Duration.ofSeconds(30);

    private static VibeCraftClientConfig config;
    private static VibeCraftWebSocketServer server;

    public VibeCraftClientMod() {
        config = VibeCraftClientConfig.load();

        // Register chat output capture for command responses
        ChatOutputCapture.register();

        if (config.enabled) {
            startServer();
        } else {
            LOGGER.info("VibeCraft client bridge disabled via config.");
        }
        registerCommands();
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> stopServer());
    }

    public static VibeCraftClientConfig getConfig() {
        return config;
    }

    public static boolean startServer() {
        if (server != null) {
            stopServer();
        }
        try {
            InetSocketAddress address = new InetSocketAddress(config.host, config.port);
            server = new VibeCraftWebSocketServer(address, config);
            server.start();
            LOGGER.info("VibeCraft client bridge listening on ws://{}:{}{}", config.host, config.port, config.path);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to start VibeCraft client bridge", e);
            server = null;
            return false;
        }
    }

    public static void stopServer() {
        if (server == null) {
            return;
        }
        try {
            server.stop(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.warn("Failed to stop VibeCraft client bridge cleanly", e);
        }
        server = null;
    }

    public static boolean restartServer() {
        stopServer();
        return startServer();
    }

    public static boolean isServerRunning() {
        return server != null;
    }

    public static BridgeResponse handleHello() {
        JsonObject result = new JsonObject();
        result.addProperty("client", "neoforge");
        result.addProperty("version", getModVersion());
        result.addProperty("minecraft", getMinecraftVersion());
        result.addProperty("enabled", config.enabled);
        result.addProperty("allow_ai_control", config.allowAiControl);
        result.add("capabilities", buildCapabilities());
        return BridgeResponse.ok(result);
    }

    public static BridgeResponse handleServerInfo() {
        try {
            JsonObject info = callOnClientThread(() -> {
                JsonObject payload = new JsonObject();
                ClientLevel world = Minecraft.getInstance().level;

                if (world != null) {
                    payload.addProperty("time", String.valueOf(world.getDayTime()));
                    payload.addProperty("difficulty", world.getDifficulty().getKey());
                } else {
                    payload.addProperty("time", "Unknown");
                    payload.addProperty("difficulty", "Unknown");
                }

                payload.addProperty("players", summarizePlayers());
                return payload;
            });
            return BridgeResponse.ok(info);
        } catch (BridgeException e) {
            return BridgeResponse.error(e.getMessage());
        }
    }

    public static BridgeResponse handleBlockRegistry() {
        try {
            return BridgeResponse.ok(callOnClientThread(BlockRegistry::describe));
        } catch (BridgeException e) {
            return BridgeResponse.error(e.getMessage());
        }
    }

    public static synchronized BridgeResponse handleCommandExecute(String command) {
        if (!config.allowAiControl) {
            return BridgeResponse.error("AI control is disabled. Run /vibecraft allow to enable.");
        }
        String trimmed = command == null ? "" : command.trim();
        if (trimmed.isEmpty()) {
            return BridgeResponse.error("Command cannot be empty.");
        }

        try {
            // Start capturing output before sending command
            ChatOutputCapture.startCapture();

            // Send command on client thread
            callOnClientThread(() -> {
                Minecraft client = Minecraft.getInstance();
                ClientPacketListener handler = client.getConnection();
                if (handler == null) {
                    throw new BridgeException("Not connected to a world.");
                }
                String toSend = trimmed;
                if (toSend.startsWith("/")) {
                    toSend = toSend.substring(1);
                }
                handler.sendCommand(toSend);
                if (config.logCommands) {
                    LOGGER.info("Command dispatched: {}", trimmed);
                }
                return null;
            });

            // Wait for output to be captured
            Thread.sleep(ChatOutputCapture.getCaptureWindowMs() + 50);

            // Get captured output
            String output = ChatOutputCapture.stopAndGetOutput();
            if (output.isEmpty()) {
                output = "Command sent; no server acknowledgement captured. Read the affected region to verify.";
            }

            return BridgeResponse.okMessage(output);
        } catch (BridgeException e) {
            ChatOutputCapture.stopAndGetOutput(); // Clean up
            return BridgeResponse.error(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ChatOutputCapture.stopAndGetOutput(); // Clean up
            return BridgeResponse.error("Command execution interrupted");
        }
    }

    // ========== Screenshot Handler ==========

    public static BridgeResponse handleScreenshot(int maxWidth, int maxHeight) {
        try {
            JsonObject result = callOnClientThread(() -> {
                int width = maxWidth > 0 ? maxWidth : 1920;
                int height = maxHeight > 0 ? maxHeight : 1080;
                return ScreenshotHandler.capture(width, height);
            });
            if (result.has("error")) {
                return BridgeResponse.error(result.get("error").getAsString());
            }
            return BridgeResponse.ok(result);
        } catch (BridgeException e) {
            return BridgeResponse.error(e.getMessage());
        }
    }

    // ========== Region Scanner Handlers ==========

    public static BridgeResponse handleRegionScan(int x1, int y1, int z1, int x2, int y2, int z2, boolean includeStates) {
        try {
            JsonObject result = callOnClientThread(() ->
                RegionScanner.scanRegion(x1, y1, z1, x2, y2, z2, includeStates)
            );
            if (result.has("error")) {
                return BridgeResponse.error(result.get("error").getAsString());
            }
            return BridgeResponse.ok(result);
        } catch (BridgeException e) {
            return BridgeResponse.error(e.getMessage());
        }
    }

    public static BridgeResponse handleHeightmap(int x1, int z1, int x2, int z2) {
        try {
            JsonObject result = callOnClientThread(() ->
                RegionScanner.getHeightmap(x1, z1, x2, z2)
            );
            if (result.has("error")) {
                return BridgeResponse.error(result.get("error").getAsString());
            }
            return BridgeResponse.ok(result);
        } catch (BridgeException e) {
            return BridgeResponse.error(e.getMessage());
        }
    }

    // ========== Player Context Handlers ==========

    public static BridgeResponse handlePlayerContext(double reach) {
        try {
            JsonObject result = callOnClientThread(() ->
                PlayerContext.getPlayerContext(reach)
            );
            if (result.has("error")) {
                return BridgeResponse.error(result.get("error").getAsString());
            }
            return BridgeResponse.ok(result);
        } catch (BridgeException e) {
            return BridgeResponse.error(e.getMessage());
        }
    }

    public static BridgeResponse handleNearbyEntities(double radius) {
        try {
            JsonObject result = callOnClientThread(() ->
                PlayerContext.getNearbyEntities(radius)
            );
            if (result.has("error")) {
                return BridgeResponse.error(result.get("error").getAsString());
            }
            return BridgeResponse.ok(result);
        } catch (BridgeException e) {
            return BridgeResponse.error(e.getMessage());
        }
    }

    // ========== Palette Analysis Handlers ==========

    public static BridgeResponse handlePaletteAnalyze(int x, int y, int z, int radius) {
        try {
            JsonObject result = callOnClientThread(() ->
                PaletteAnalyzer.analyzeRadius(x, y, z, radius)
            );
            if (result.has("error")) {
                return BridgeResponse.error(result.get("error").getAsString());
            }
            return BridgeResponse.ok(result);
        } catch (BridgeException e) {
            return BridgeResponse.error(e.getMessage());
        }
    }

    public static BridgeResponse handlePaletteRegion(int x1, int y1, int z1, int x2, int y2, int z2) {
        try {
            JsonObject result = callOnClientThread(() ->
                PaletteAnalyzer.analyzeRegion(x1, y1, z1, x2, y2, z2)
            );
            if (result.has("error")) {
                return BridgeResponse.error(result.get("error").getAsString());
            }
            return BridgeResponse.ok(result);
        } catch (BridgeException e) {
            return BridgeResponse.error(e.getMessage());
        }
    }

    // ========== Light Analysis Handler ==========

    public static BridgeResponse handleLightAnalysis(int x1, int y1, int z1, int x2, int y2, int z2, int resolution) {
        try {
            JsonObject result = callOnClientThread(() ->
                LightAnalyzer.analyzeRegion(x1, y1, z1, x2, y2, z2, resolution)
            );
            if (result.has("error")) {
                return BridgeResponse.error(result.get("error").getAsString());
            }
            return BridgeResponse.ok(result);
        } catch (BridgeException e) {
            return BridgeResponse.error(e.getMessage());
        }
    }

    // ========== Symmetry Checker Handler ==========

    public static BridgeResponse handleSymmetryCheck(
            int x1, int y1, int z1, int x2, int y2, int z2,
            String axis, int tolerance, int resolution
    ) {
        try {
            JsonObject result = callOnClientThread(() ->
                SymmetryChecker.checkSymmetry(x1, y1, z1, x2, y2, z2, axis, tolerance, resolution)
            );
            if (result.has("error")) {
                return BridgeResponse.error(result.get("error").getAsString());
            }
            return BridgeResponse.ok(result);
        } catch (BridgeException e) {
            return BridgeResponse.error(e.getMessage());
        }
    }

    public static JsonObject buildCapabilities() {
        JsonObject capabilities = new JsonObject();

        // WorldEdit detection
        JsonObject worldedit = new JsonObject();
        WorldEditStatus status = detectWorldEdit();
        worldedit.addProperty("available", status.available);
        worldedit.addProperty("reason", status.reason);
        capabilities.add("worldedit", worldedit);

        // Vision - screenshot capture
        capabilities.addProperty("vision", true);
        capabilities.addProperty("screenshot", true);

        // Region scanning - block data
        capabilities.addProperty("region_scan", true);
        capabilities.addProperty("heightmap", true);

        // Player context - raycast, position, rotation
        capabilities.addProperty("player_context", true);
        capabilities.addProperty("raycast", true);

        // Palette analysis - style detection
        capabilities.addProperty("palette_analysis", true);

        // Light analysis - from client's light cache
        capabilities.addProperty("light_analysis", true);

        // Symmetry checking - efficient client-side comparison
        capabilities.addProperty("symmetry_check", true);
        capabilities.addProperty("block_registry", true);

        // List all supported message types
        JsonObject messages = new JsonObject();
        messages.addProperty("hello", true);
        messages.addProperty("server.info", true);
        messages.addProperty("command.execute", true);
        messages.addProperty("screenshot.capture", true);
        messages.addProperty("region.scan", true);
        messages.addProperty("region.heightmap", true);
        messages.addProperty("player.context", true);
        messages.addProperty("player.entities", true);
        messages.addProperty("palette.analyze", true);
        messages.addProperty("palette.region", true);
        messages.addProperty("light.analyze", true);
        messages.addProperty("symmetry.check", true);
        messages.addProperty("blocks.registry", true);
        capabilities.add("message_types", messages);

        return capabilities;
    }

    private static WorldEditStatus detectWorldEdit() {
        try {
            return callOnClientThread(() -> {
                ClientPacketListener handler = Minecraft.getInstance().getConnection();
                if (handler == null) {
                    return new WorldEditStatus(false, "no_connection");
                }
                boolean available = WorldEditDetector.isWorldEditAvailable(handler);
                return new WorldEditStatus(available, "command_tree");
            });
        } catch (BridgeException e) {
            return new WorldEditStatus(false, "error");
        }
    }

    private static String summarizePlayers() {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener handler = client.getConnection();
        if (handler == null) {
            return "Not connected";
        }
        List<PlayerInfo> entries = new ArrayList<>(handler.getOnlinePlayers());
        if (entries.isEmpty()) {
            return "No players";
        }
        List<String> names = new ArrayList<>();
        for (PlayerInfo entry : entries) {
            names.add(VersionCompat.getProfileName(entry.getProfile()));
        }
        return "There are " + names.size() + " players online: " + String.join(", ", names);
    }

    private static <T> T callOnClientThread(BridgeCallable<T> action) throws BridgeException {
        Minecraft client = Minecraft.getInstance();
        if (client.isSameThread()) {
            try {
                return action.call();
            } catch (BridgeException e) {
                throw e;
            } catch (Exception e) {
                throw new BridgeException("Client action failed", e);
            }
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        client.execute(() -> {
            if (future.isCancelled()) return;
            try {
                future.complete(action.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return future.get(CLIENT_THREAD_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BridgeException("Client thread interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new BridgeException(cause == null ? "Client action failed" : cause.getMessage(), e);
        } catch (TimeoutException e) {
            future.cancel(false);
            throw new BridgeException("Timed out waiting for client thread", e);
        }
    }

    private static String getModVersion() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
    }

    private static String getMinecraftVersion() {
        return net.minecraft.SharedConstants.getCurrentVersion().getName();
    }

    private static void registerCommands() {
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                registerCommandTree(event.getDispatcher(), event.getBuildContext()));
    }

    private static void registerCommandTree(
            com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess
    ) {
        dispatcher.register(literal("vibecraft")
                .then(literal("status")
                        .executes(ctx -> {
                            sendStatus(ctx.getSource());
                            return 1;
                        }))
                .then(literal("allow")
                        .executes(ctx -> {
                            config.allowAiControl = true;
                            config.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("VibeCraft AI control enabled."), false);
                            return 1;
                        }))
                .then(literal("deny")
                        .executes(ctx -> {
                            config.allowAiControl = false;
                            config.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("VibeCraft AI control disabled."), false);
                            return 1;
                        }))
                .then(literal("toggle")
                        .executes(ctx -> {
                            config.allowAiControl = !config.allowAiControl;
                            config.save();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "VibeCraft AI control " + (config.allowAiControl ? "enabled" : "disabled") + "."), false);
                            return 1;
                        }))
                .then(literal("start")
                        .executes(ctx -> {
                            config.enabled = true;
                            config.save();
                            boolean ok = startServer();
                            ctx.getSource().sendSuccess(() -> Component.literal(ok
                                    ? "VibeCraft bridge started."
                                    : "VibeCraft bridge failed to start. Check logs."), false);
                            return ok ? 1 : 0;
                        }))
                .then(literal("stop")
                        .executes(ctx -> {
                            config.enabled = false;
                            config.save();
                            stopServer();
                            ctx.getSource().sendSuccess(() -> Component.literal("VibeCraft bridge stopped."), false);
                            return 1;
                        }))
                .then(literal("restart")
                        .executes(ctx -> {
                            boolean ok = restartServer();
                            ctx.getSource().sendSuccess(() -> Component.literal(ok
                                    ? "VibeCraft bridge restarted."
                                    : "VibeCraft bridge failed to restart."), false);
                            return ok ? 1 : 0;
                        }))
                .then(literal("token")
                        .then(argument("token", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String token = StringArgumentType.getString(ctx, "token");
                                    config.token = token.trim();
                                    config.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("VibeCraft token updated."), false);
                                    return 1;
                                })))
                .then(literal("port")
                        .then(argument("port", IntegerArgumentType.integer(1, 65535))
                                .executes(ctx -> {
                                    int port = IntegerArgumentType.getInteger(ctx, "port");
                                    config.port = port;
                                    config.save();
                                    boolean ok = restartServer();
                                    ctx.getSource().sendSuccess(() -> Component.literal(ok
                                            ? "VibeCraft bridge moved to port " + port + "."
                                            : "VibeCraft bridge failed to restart."), false);
                                    return ok ? 1 : 0;
                                })))
        );
    }

    private static void sendStatus(CommandSourceStack source) {
        String status = isServerRunning() ? "running" : "stopped";
        String allow = config.allowAiControl ? "enabled" : "disabled";
        source.sendSuccess(() -> Component.literal("VibeCraft bridge is " + status + " on ws://" + config.host
                + ":" + config.port + config.path), false);
        source.sendSuccess(() -> Component.literal("AI control is " + allow + "."), false);
        if (!config.token.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Token set: " + maskToken(config.token)), false);
        } else {
            source.sendSuccess(() -> Component.literal("Token not set. Use /vibecraft token <value>."), false);
        }
    }

    private static String maskToken(String token) {
        if (token.length() <= 4) {
            return "****";
        }
        return token.substring(0, 2) + "***" + token.substring(token.length() - 2);
    }

    static String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "/vibecraft";
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed;
    }

    @FunctionalInterface
    private interface BridgeCallable<T> {
        T call() throws Exception;
    }

    static final class WorldEditStatus {
        final boolean available;
        final String reason;

        WorldEditStatus(boolean available, String reason) {
            this.available = available;
            this.reason = reason == null ? "unknown" : reason.toLowerCase(Locale.ROOT);
        }
    }

    static final class BridgeException extends Exception {
        BridgeException(String message) {
            super(message);
        }

        BridgeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class BridgeResponse {
        final boolean ok;
        final com.google.gson.JsonElement result;
        final String error;

        private BridgeResponse(boolean ok, com.google.gson.JsonElement result, String error) {
            this.ok = ok;
            this.result = result;
            this.error = error;
        }

        static BridgeResponse okMessage(String message) {
            return new BridgeResponse(true, new com.google.gson.JsonPrimitive(message), null);
        }

        static BridgeResponse ok(JsonObject result) {
            return new BridgeResponse(true, result, null);
        }

        static BridgeResponse error(String error) {
            return new BridgeResponse(false, null, error);
        }
    }
}
