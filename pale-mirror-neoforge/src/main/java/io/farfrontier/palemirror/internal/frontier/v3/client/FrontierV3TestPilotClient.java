package io.farfrontier.palemirror.internal.frontier.v3.client;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.internal.client.PaleMirrorContextCardClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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
    private static final String SERVER_PROPERTY = "pale_mirror.frontier_v3.test_pilot.server";
    private static final long CAPTURE_SETTLE_TICKS = 10L;
    private static JsonArray actions;
    private static JsonArray setup;
    private static JsonArray frames;
    private static boolean runningSetup;
    private static int index;
    private static long actionStartedTick = -1L;
    private static boolean breaking;
    private static boolean placementAttempted;
    private static boolean visitSent;
    private static long visitChunkReadyTick = -1L;
    private static boolean containerOpenAttempted;
    private static boolean quickMoveAttempted;
    private static boolean inspectSent;
    private static boolean fastForwardSent;
    private static boolean fastForwardObservedActive;
    private static boolean boardInteractionAttempted;
    private static boolean entityInteractionAttempted;
    private static int attackedEntityRuntimeId = -1;
    private static int entityAttackAttempts;
    private static long lastEntityAttackTick = Long.MIN_VALUE;
    private static Vec3 lastAttackedEntityPosition;
    private static CaptureBarrier captureBarrier;
    private static final Map<DiagnosticIdentity, ObservedDiagnostic> diagnostics = new HashMap<>();
    private FrontierV3TestPilotClient() { }
    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        String configured = System.getProperty(SCENARIO_PROPERTY, "");
        if (configured.isBlank()) return;
        try {
            FrontierV3PilotSessionControl.publishClientPrepared();
            FrontierV3PilotSessionControl.onLogin(Path.of(configured));
            FrontierV3PilotSessionControl.bindActiveConnection(event.getConnection());
            FrontierV3TestPilotScenario.Parsed scenario = FrontierV3TestPilotScenario.parse(Files.readString(Path.of(configured)));
            setup = scenario.setup(); actions = scenario.actions(); frames = scenario.frames();
            runningSetup = !setup.isEmpty(); index = 0; actionStartedTick = -1L;
            breaking = false; placementAttempted = false; visitSent = false; visitChunkReadyTick = -1L;
            containerOpenAttempted = false; quickMoveAttempted = false;
            inspectSent = false; fastForwardSent = false; fastForwardObservedActive = false;
            boardInteractionAttempted = false;
            entityInteractionAttempted = false;
            attackedEntityRuntimeId = -1; entityAttackAttempts = 0; lastEntityAttackTick = Long.MIN_VALUE; lastAttackedEntityPosition = null;
            captureBarrier = null; diagnostics.clear();
            FrontierV3TestPilotPresentation.clear(Minecraft.getInstance());
            if (FrontierV3PilotSessionControl.resumed()) {
                FrontierV3PilotSessionControl.publishLifecycleSignal("same_client_reconnected_state_cleared", FrontierV3PilotSessionControl.lifecycleSegment(), new JsonObject());
            } else if (!runningSetup) {
                FrontierV3PilotSessionControl.publishLifecycleSignal("client_connected_fixture_ready", FrontierV3PilotSessionControl.lifecycleSegment(), new JsonObject());
            }
            FrontierV3PilotSessionControl.completeReconnect();
            PaleMirrorMod.LOGGER.info("PMV3_PILOT loaded scenario={} setup={} actions={} frames={}", configured, setup.size(), actions.size(), frames.size());
        } catch (IOException | IllegalArgumentException failure) {
            actions = null;
            PaleMirrorMod.LOGGER.error("PMV3_PILOT rejected scenario {}", configured, failure);
            if (FrontierV3PilotSessionControl.reconnectInFlight()) {
                FrontierV3PilotSessionControl.failPersistentLifecycle("persistent_reconnect_failed", failure);
            }
        }
    }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        // The first logout is an expected, runner-owned server restart. Preserve only the
        // already parsed test scenario until the replacement server has become ready; the next
        // login atomically reloads the runner-written second segment. Any ordinary logout still
        // resets all pilot state as before.
        if (FrontierV3PilotSessionControl.enabled() && FrontierV3PilotSessionControl.expectedLossArmed()) {
            try {
                FrontierV3PilotSessionControl.claimExpectedLoss(event.getConnection());
                clearExpectedLossTransientState();
                return;
            } catch (IOException | RuntimeException failure) {
                if (FrontierV3PilotSessionControl.expectedLossClaimed()) clearExpectedLossTransientState();
                FrontierV3PilotSessionControl.failPersistentLifecycle("expected_loss", failure); return;
            }
        }
        if (FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(event.getConnection())) return;
        if (FrontierV3PilotSessionControl.reconnectFailureRequiresFatal()) {
            FrontierV3PilotSessionControl.failPersistentLifecycle("persistent_reconnect_lost",
                    new IllegalStateException("persistent pilot lost the replacement connection"));
            return;
        }
        if (FrontierV3PilotSessionControl.enabled()
                && (FrontierV3PilotSessionControl.awaitingResume() || FrontierV3PilotSessionControl.finalCloseRequested())) {
            if (FrontierV3PilotSessionControl.awaitingResume() || FrontierV3PilotSessionControl.finalCloseRequested()) {
                try {
                    FrontierV3PilotSessionControl.publishNormalDisconnectAcknowledgement();
                } catch (IOException | RuntimeException failure) {
                    FrontierV3PilotSessionControl.failPersistentLifecycle("normal_disconnect", failure);
                    return;
                }
            }
            Minecraft.getInstance().options.keyUp.setDown(false);
            captureBarrier = null;
            diagnostics.clear();
            if (FrontierV3PilotSessionControl.finalCloseRequested()) {
                // This was a protocol-directed ordinary disconnect, not a supervisor SIGINT.
                Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
            }
            return;
        }
        if (!FrontierV3PilotSessionControl.failUnexpectedActiveLoss()) reset();
    }
    /**
     * Retains pilot diagnostics but never renders server command feedback in
     * the visible test client.  The runner's JSONL and the client log remain
     * the diagnostic evidence; ordinary player chat is deliberately outside
     * this system-message-only development policy.
     */
    @SubscribeEvent
    public static void diagnostic(ClientChatReceivedEvent.System event) {
        if (actions == null) return;
        String message = event.getMessage().getString(); int marker = message.indexOf("PMV3_DIAG ");
        if (marker >= 0) {
            try {
                JsonObject value = JsonParser.parseString(message.substring(marker + "PMV3_DIAG ".length())).getAsJsonObject();
                if (value.has("kind") && value.has("id")) {
                    Minecraft minecraft = Minecraft.getInstance(); long tick = minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime();
                    // Client/Gradle stdout and stderr are separate pipes. Preserve the action
                    // identity in the local evidence itself rather than inferring it from
                    // incidental cross-pipe log ordering in the Node runner.
                    if (!runningSetup && actionStartedTick >= 0L) FrontierV3PilotSessionControl.stampDiagnostic(value, index + 1);
                    diagnostics.put(new DiagnosticIdentity(value.get("kind").getAsString(), value.get("id").getAsString()), new ObservedDiagnostic(tick, value));
                    PaleMirrorMod.LOGGER.info("PMV3_PILOT_DIAGNOSTIC {}", value);
                }
            } catch (RuntimeException ignored) {
                // A malformed diagnostic remains absent from the terminal predicate.
            }
        }
        event.setCanceled(true);
    }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            try {
                FrontierV3PersistentPilotTransport.reconnectIfRequested(minecraft, System.getProperty(SERVER_PROPERTY, ""));
            } catch (RuntimeException failure) {
                if (FrontierV3PilotSessionControl.reconnectRequestFailureRequiresFatal()) {
                    FrontierV3PilotSessionControl.failPersistentLifecycle("persistent_reconnect_failed", failure);
                }
            }
            return;
        }
        if (actions == null || !FrontierV3PilotSessionControl.mayContinueScenarioActions()) return;
        if (FrontierV3PilotSessionControl.awaitingFinalClose()) {
            FrontierV3PersistentPilotTransport.closeIfRequested(minecraft);
            return;
        }
        FrontierV3TestPilotPresentation.clear(minecraft);
        if (FrontierV3PilotSessionControl.expectedCrashSegment() && !FrontierV3PilotSessionControl.expectedLossArmed()) {
            try { if (FrontierV3PilotSessionControl.armExpectedLoss(runningSetup ? 0 : index)) return; }
            catch (IOException | RuntimeException failure) { FrontierV3PilotSessionControl.failPersistentLifecycle("expected_loss_arm", failure); return; }
        }
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
                case "wait_until_container_item" -> waitUntilContainerItem(minecraft, action);
                case "assert_fixture" -> assertFixture(minecraft, action);
                case "visit" -> visit(minecraft, action);
                case "visit_operation" -> visitOperation(minecraft, action);
                case "assert_visible_block" -> assertVisibleBlock(minecraft, action);
                case "assert_visible_board" -> assertVisibleBoard(minecraft, action);
                case "assert_visible_entity" -> assertVisibleEntity(minecraft, action);
                case "interact_board" -> interactBoard(minecraft, action);
                case "interact_nearest_entity" -> interactNearestEntity(minecraft, action);
                case "attack_nearest_entity" -> attackNearestEntity(minecraft, action);
                case "fast_forward" -> waitForFastForward(minecraft, action);
                case "command" -> { minecraft.player.connection.sendCommand(withoutSlash(action.get("command").getAsString())); advance(type); }
                case "inspect" -> inspect(minecraft, action);
                case "look" -> lookAtPosition(minecraft, action);
                case "look_nearest_entity" -> lookNearestEntity(minecraft, action);
                case "look_operation" -> lookOperation(minecraft, action);
                case "walk" -> walk(minecraft, position(action, "position"), action.has("radius") ? action.get("radius").getAsDouble() : 1.0D);
                case "break" -> {
                    BlockPos target = resolvedPosition(minecraft, action, "position");
                    if (target != null) breakBlock(minecraft, target);
                }
                case "place" -> placeBlock(minecraft, action);
                case "open_container" -> {
                    BlockPos target = resolvedPosition(minecraft, action, "position");
                    if (target != null) openContainer(minecraft, target, action.get("timeoutMs").getAsLong());
                }
                case "quick_move_from_inventory" -> quickMoveFromInventory(minecraft, action);
                case "quick_move_from_container" -> quickMoveFromContainer(minecraft, action);
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
    /** Places one declared ordinary block through the normal client use-item-on-block packet. */
    private static void placeBlock(Minecraft minecraft, JsonObject action) {
        BlockPos target = resolvedPosition(minecraft, action, "position");
        if (target == null) return;
        ResourceLocation itemId = ResourceLocation.parse(action.get("item").getAsString());
        var item = BuiltInRegistries.ITEM.getOptional(itemId).orElseThrow(() -> new IllegalArgumentException("unknown placement item " + itemId));
        var expected = BuiltInRegistries.BLOCK.getOptional(itemId).orElseThrow(() -> new IllegalArgumentException("placement item is not a block " + itemId));
        if (minecraft.level.getBlockState(target).is(expected)) { advance("place"); return; }
        BlockPos support = target.below();
        if (minecraft.level.getBlockState(support).isAir()) throw new IllegalStateException("ordinary placement has no support at " + support);
        if (!placementAttempted) {
            int slot = -1;
            for (int index = 0; index < minecraft.player.getInventory().items.size(); index++) {
                ItemStack stack = minecraft.player.getInventory().items.get(index);
                if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId)) { slot = index; break; }
            }
            if (slot < 0 || slot >= 9) throw new IllegalStateException("ordinary player hotbar lacks placement item " + itemId);
            minecraft.player.getInventory().selected = slot;
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(support).add(0.0D, 0.5D, 0.0D), Direction.UP, support, false));
            placementAttempted = true;
        }
        timeout(minecraft, action, "ordinary placement did not produce " + itemId + " at " + target);
    }
    /** Opens the real block menu through Minecraft's normal client interaction packet. */
    private static void openContainer(Minecraft minecraft, BlockPos target, long timeoutMs) {
        if (minecraft.player.containerMenu != minecraft.player.inventoryMenu) { advance("open_container"); return; }
        if (!containerOpenAttempted) {
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
            containerOpenAttempted = true;
        }
        if ((minecraft.level.getGameTime() - actionStartedTick) * 50L >= timeoutMs) {
            throw new IllegalStateException("timed out opening ordinary container at " + target);
        }
    }
    /** Shift-clicks one exact player stack through the ordinary open-menu protocol. */
    private static void quickMoveFromInventory(Minecraft minecraft, JsonObject action) {
        if (minecraft.player.containerMenu == minecraft.player.inventoryMenu) {
            timeout(minecraft, action, "container menu was not open for quick move"); return;
        }
        ResourceLocation item = ResourceLocation.parse(action.get("item").getAsString()); int count = action.get("count").getAsInt();
        if (quickMoveAttempted) {
            boolean moved = minecraft.player.containerMenu.slots.stream().filter(slot -> slot.container != minecraft.player.getInventory())
                    .anyMatch(slot -> sameStack(slot, item, count));
            if (moved) { advance("quick_move_from_inventory"); return; }
            timeout(minecraft, action, "quick move did not reach the container"); return;
        }
        Slot source = minecraft.player.containerMenu.slots.stream().filter(slot -> slot.container == minecraft.player.getInventory())
                .filter(slot -> sameStack(slot, item, count)).findFirst().orElse(null);
        if (source == null) { timeout(minecraft, action, "player lacks exact stack " + item + " x" + count); return; }
        int menuSlot = minecraft.player.containerMenu.slots.indexOf(source);
        if (menuSlot < 0) throw new IllegalStateException("player inventory slot is absent from the open container menu");
        minecraft.gameMode.handleInventoryMouseClick(minecraft.player.containerMenu.containerId, menuSlot, 0, ClickType.QUICK_MOVE, minecraft.player);
        quickMoveAttempted = true;
    }
    /** Shift-clicks one exact existing container stack through the ordinary open-menu protocol. */
    private static void quickMoveFromContainer(Minecraft minecraft, JsonObject action) {
        if (minecraft.player.containerMenu == minecraft.player.inventoryMenu) {
            timeout(minecraft, action, "container menu was not open for quick move"); return;
        }
        ResourceLocation item = ResourceLocation.parse(action.get("item").getAsString()); int count = action.get("count").getAsInt();
        if (quickMoveAttempted) {
            boolean moved = minecraft.player.containerMenu.slots.stream().filter(slot -> slot.container == minecraft.player.getInventory())
                    .anyMatch(slot -> sameStack(slot, item, count));
            if (moved) { advance("quick_move_from_container"); return; }
            timeout(minecraft, action, "quick move did not reach the player inventory"); return;
        }
        Slot source = minecraft.player.containerMenu.slots.stream().filter(slot -> slot.container != minecraft.player.getInventory())
                .filter(slot -> sameStack(slot, item, count)).findFirst().orElse(null);
        if (source == null) { timeout(minecraft, action, "container lacks exact stack " + item + " x" + count); return; }
        int menuSlot = minecraft.player.containerMenu.slots.indexOf(source);
        if (menuSlot < 0) throw new IllegalStateException("container slot is absent from the open container menu");
        minecraft.gameMode.handleInventoryMouseClick(minecraft.player.containerMenu.containerId, menuSlot, 0, ClickType.QUICK_MOVE, minecraft.player);
        quickMoveAttempted = true;
    }
    private static boolean sameStack(Slot slot, ResourceLocation item, int count) {
        return !slot.getItem().isEmpty() && slot.getItem().getCount() == count
                && BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).equals(item);
    }
    private static void waitUntilBlock(Minecraft minecraft, JsonObject action) {
        BlockPos target = resolvedPosition(minecraft, action, "position");
        if (target == null) return;
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
        BlockPos target = resolvedPosition(minecraft, action, "position");
        if (target == null) return;
        visit(minecraft, target, action.get("dimension").getAsString(), action.get("settleMs").getAsLong(), 120_000L, "visit");
    }
    /** Uses only a fresh read-only operation snapshot to choose a player-side observation point. */
    private static void visitOperation(Minecraft minecraft, JsonObject action) {
        BlockPos anchor = operationAnchor(minecraft, action, "travelCurrent");
        if (anchor == null) return;
        JsonObject offset = action.getAsJsonObject("offset");
        visit(minecraft, anchor.offset(offset.get("x").getAsInt(), offset.get("y").getAsInt(), offset.get("z").getAsInt()),
                action.get("dimension").getAsString(), action.get("settleMs").getAsLong(), action.get("timeoutMs").getAsLong(), "visit_operation");
    }
    private static void visit(Minecraft minecraft, BlockPos target, String dimension, long settleMs, long timeoutMs, String actionType) {
        long tick = minecraft.level.getGameTime();
        if (!visitSent) {
            String username = minecraft.player.getGameProfile().getName();
            minecraft.player.connection.sendCommand("execute in " + dimension + " run tp " + username + " " + target.getX() + " " + target.getY() + " " + target.getZ());
            visitSent = true;
            return;
        }
        boolean ready = minecraft.level.dimension().location().toString().equals(dimension) && minecraft.level.hasChunkAt(target);
        if (ready) {
            if (visitChunkReadyTick < 0L) visitChunkReadyTick = tick;
            if ((tick - visitChunkReadyTick) * 50L >= settleMs) { advance(actionType); return; }
        } else visitChunkReadyTick = -1L;
        if ((tick - actionStartedTick) * 50L >= timeoutMs) {
            throw new IllegalStateException("timed out visiting naturally loaded " + dimension + " at " + target);
        }
    }
    private static void lookOperation(Minecraft minecraft, JsonObject action) {
        BlockPos anchor = operationAnchor(minecraft, action, action.has("anchor") ? action.get("anchor").getAsString() : "travelCargo");
        if (anchor == null) return;
        look(minecraft, anchor); advance("look_operation");
    }
    /** Returns one current diagnostic coordinate, requesting it at the same bounded cadence as other waits. */
    private static BlockPos operationAnchor(Minecraft minecraft, JsonObject action, String anchor) {
        String operation = action.get("operationId").getAsString(); long tick = minecraft.level.getGameTime();
        ObservedDiagnostic diagnostic = diagnostics.get(new DiagnosticIdentity("operation", operation));
        if (fresh(diagnostic)) {
            JsonObject value = diagnostic.value().getAsJsonObject(anchor);
            if (value == null || !value.has("x") || !value.has("y") || !value.has("z")) {
                throw new IllegalStateException("operation diagnostic lacks " + anchor + " for " + operation);
            }
            return new BlockPos(value.get("x").getAsInt(), value.get("y").getAsInt(), value.get("z").getAsInt());
        }
        if ((tick - actionStartedTick) % 20L == 0L) minecraft.player.connection.sendCommand("pale_mirror v3 inspect operation " + operation);
        timeout(minecraft, action, "timed out reading current operation anchor " + operation);
        return null;
    }
    /** Proves the player camera itself is aimed at one loaded, non-air exact block. */
    private static void assertVisibleBlock(Minecraft minecraft, JsonObject action) {
        BlockPos expected = resolvedPosition(minecraft, action, "position");
        if (expected == null) return;
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
    /** Presentation-only proof: the player camera sees a locally rendered ordinary entity, not a server-selected UUID. */
    private static void assertVisibleEntity(Minecraft minecraft, JsonObject action) {
        ResourceLocation expectedType = ResourceLocation.parse(action.get("entityType").getAsString());
        String expectedName = action.get("nameContains").getAsString();
        double maxDistance = action.has("maxDistance") ? action.get("maxDistance").getAsDouble() : 64.0D;
        double maxAngle = Math.cos(Math.toRadians(action.has("maxAngleDeg") ? action.get("maxAngleDeg").getAsDouble() : 50.0D));
        Vec3 eye = minecraft.player.getEyePosition(); Vec3 view = minecraft.player.getViewVector(1.0F).normalize();
        boolean visible = minecraft.level.getEntitiesOfClass(Entity.class, minecraft.player.getBoundingBox().inflate(maxDistance), entity -> {
            if (entity.isRemoved() || !BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(expectedType)
                    || !(entity instanceof Display.TextDisplay || entity.isCustomNameVisible())
                    || entity.getCustomName() == null || !entity.getCustomName().getString().contains(expectedName)
                    || !minecraft.player.hasLineOfSight(entity)) return false;
            Vec3 delta = entity.position().subtract(eye); double distance = delta.length();
            return distance > 0.0D && distance <= maxDistance && view.dot(delta.scale(1.0D / distance)) >= maxAngle;
        }).stream().findFirst().isPresent();
        if (visible) { advance("assert_visible_entity"); return; }
        timeout(minecraft, action, "camera never saw local entity " + expectedType + " named " + expectedName
                + "; candidates=" + localEntityEvidence(minecraft, expectedType, expectedName, maxDistance)
                + "; view=" + Math.round(view.x * 100.0D) + "," + Math.round(view.y * 100.0D) + "," + Math.round(view.z * 100.0D));
    }
    /** Rotates only the local test camera towards one locally rendered named body. */
    private static void lookNearestEntity(Minecraft minecraft, JsonObject action) {
        ResourceLocation expectedType = ResourceLocation.parse(action.get("entityType").getAsString());
        String expectedName = action.get("nameContains").getAsString();
        double maximum = action.has("maxDistance") ? action.get("maxDistance").getAsDouble() : 64.0D;
        Entity target = minecraft.level.getEntitiesOfClass(Entity.class, minecraft.player.getBoundingBox().inflate(maximum), entity ->
                        !entity.isRemoved() && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(expectedType)
                                && (entity instanceof Display.TextDisplay || entity.isCustomNameVisible())
                                && entity.getCustomName() != null && entity.getCustomName().getString().contains(expectedName)
                                && minecraft.player.hasLineOfSight(entity))
                .stream().sorted(java.util.Comparator.comparingDouble((Entity entity) -> entity.distanceToSqr(minecraft.player))
                        .thenComparing(Entity::getUUID)).findFirst().orElse(null);
        if (target == null) {
            timeout(minecraft, action, "no line-of-sight local entity " + expectedType + " named " + expectedName
                    + "; candidates=" + localEntityEvidence(minecraft, expectedType, expectedName, maximum));
            return;
        }
        // Use the same direct local rotation primitive as the coordinate camera actions.  The
        // inherited Entity#lookAt path is authoritative for server entities but does not
        // consistently update the client render view between consecutive pilot actions.
        look(minecraft, target.getEyePosition());
        advance("look_nearest_entity");
    }
    /** Bounded failure evidence for a presentation assertion; it neither selects nor mutates a server entity. */
    private static String localEntityEvidence(Minecraft minecraft, ResourceLocation expectedType, String expectedName, double maximum) {
        Vec3 eye = minecraft.player.getEyePosition();
        return minecraft.level.getEntitiesOfClass(Entity.class, minecraft.player.getBoundingBox().inflate(maximum), entity ->
                        !entity.isRemoved() && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(expectedType)
                                && entity.getCustomName() != null && entity.getCustomName().getString().contains(expectedName))
                .stream().sorted(java.util.Comparator.comparingDouble((Entity entity) -> entity.distanceToSqr(minecraft.player))
                        .thenComparing(Entity::getUUID)).limit(8).map(entity -> entity.getUUID() + "@"
                        + entity.getBlockX() + "," + entity.getBlockY() + "," + entity.getBlockZ() + "/los="
                        + minecraft.player.hasLineOfSight(entity) + "/distance=" + Math.round(eye.distanceTo(entity.getEyePosition())))
                .collect(java.util.stream.Collectors.joining(";"));
    }
    /**
     * Uses Minecraft's normal entity interaction packet and waits for its local, optional
     * presentation receipt.  The card itself remains noncanonical; the semantic board is the
     * durable physical explanation and was already verified before this click.
     */
    private static void interactBoard(Minecraft minecraft, JsonObject action) {
        BlockPos anchor = position(action, "position"); String expectedText = action.get("text").getAsString();
        double radius = action.has("radius") ? action.get("radius").getAsDouble() : 3.0D;
        Display.TextDisplay board = minecraft.level.getEntitiesOfClass(Display.TextDisplay.class,
                minecraft.player.getBoundingBox().inflate(action.has("maxDistance") ? action.get("maxDistance").getAsDouble() : 64.0D), display ->
                        display.getCustomName() != null && display.getCustomName().getString().contains(expectedText)
                                && display.position().distanceToSqr(Vec3.atCenterOf(anchor)) <= radius * radius)
                .stream().findFirst().orElse(null);
        if (board == null) { timeout(minecraft, action, "no visible named board was available for ordinary interaction"); return; }
        if (!boardInteractionAttempted) {
            minecraft.gameMode.interact(minecraft.player, board, InteractionHand.MAIN_HAND);
            boardInteractionAttempted = true;
        }
        if (PaleMirrorContextCardClient.hasActiveTitle(action.get("title").getAsString())) {
            advance("interact_board"); return;
        }
        timeout(minecraft, action, "timed out waiting for the contextual board card");
    }
    /**
     * Sends one ordinary entity-interaction packet to the nearest locally
     * rendered entity of the declared type. This has no server-side entity
     * selection or UUID authority; the server still validates the action.
     */
    private static void interactNearestEntity(Minecraft minecraft, JsonObject action) {
        ResourceLocation expectedType = ResourceLocation.parse(action.get("entityType").getAsString());
        double maximum = action.has("maxDistance") ? action.get("maxDistance").getAsDouble() : 16.0D;
        Entity target = minecraft.level.getEntitiesOfClass(Entity.class, minecraft.player.getBoundingBox().inflate(maximum), entity ->
                        !entity.isRemoved() && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(expectedType))
                .stream().sorted(java.util.Comparator.comparingDouble((Entity entity) -> entity.distanceToSqr(minecraft.player))
                        .thenComparing(Entity::getUUID)).findFirst().orElse(null);
        if (target == null) { timeout(minecraft, action, "no nearby ordinary entity of type " + expectedType); return; }
        if (!entityInteractionAttempted) {
            minecraft.gameMode.interact(minecraft.player, target, InteractionHand.MAIN_HAND);
            entityInteractionAttempted = true;
        }
        if (minecraft.player.containerMenu != minecraft.player.inventoryMenu) { advance("interact_nearest_entity"); return; }
        timeout(minecraft, action, "timed out opening ordinary entity container " + expectedType);
    }
    /**
     * Repeats ordinary client attack packets against one initially nearest,
     * locally rendered entity. The scenario never supplies an entity UUID or
     * server-side target; after the first local choice, the client keeps that
     * same body so a death cannot spill into a second nearby resident. If that
     * body walks a few blocks away, the normal client holds forward towards it
     * rather than silently selecting another target.
     */
    private static void attackNearestEntity(Minecraft minecraft, JsonObject action) {
        ResourceLocation expectedType = ResourceLocation.parse(action.get("entityType").getAsString());
        String expectedName = action.has("nameContains") ? action.get("nameContains").getAsString() : null;
        double maximum = action.has("maxDistance") ? action.get("maxDistance").getAsDouble() : 8.0D;
        int maximumAttempts = action.get("maxAttacks").getAsInt();
        Entity target;
        if (attackedEntityRuntimeId < 0) {
            target = minecraft.level.getEntitiesOfClass(Entity.class, minecraft.player.getBoundingBox().inflate(maximum), entity ->
                            !entity.isRemoved() && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(expectedType)
                                    && (expectedName == null || entity.getCustomName() != null && entity.getCustomName().getString().contains(expectedName)))
                    .stream().sorted(java.util.Comparator.comparingDouble((Entity entity) -> entity.distanceToSqr(minecraft.player))
                            .thenComparing(Entity::getUUID)).findFirst().orElse(null);
            if (target == null) { timeout(minecraft, action, "no nearby ordinary entity of type " + expectedType); return; }
            attackedEntityRuntimeId = target.getId();
        } else {
            target = minecraft.level.getEntity(attackedEntityRuntimeId);
            // A normal death packet reaches the client before Minecraft necessarily removes
            // the corpse entity from its local index.  The pilot must treat that ordinary
            // dead body as completion, otherwise a test can spin forever waiting for removal.
            if (target == null || target.isRemoved() || target instanceof LivingEntity living && !living.isAlive()) {
                // A dead or removed target is the terminal physical result of this bounded
                // interaction.  Do not make completion depend on walking back to a corpse:
                // its local presentation/removal timing is not part of the domain contract.
                advance("attack_nearest_entity"); return;
            }
            if (!BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).equals(expectedType)
                    || expectedName != null && (target.getCustomName() == null || !target.getCustomName().getString().contains(expectedName))) {
                throw new IllegalStateException("selected ordinary entity changed its permitted type/name");
            }
        }
        lastAttackedEntityPosition = target.position();
        double distanceSquared = target.distanceToSqr(minecraft.player);
        if (distanceSquared > (maximum + 8.0D) * (maximum + 8.0D)) {
            throw new IllegalStateException("selected ordinary entity left the bounded local pursuit range");
        }
        if (distanceSquared > 3.5D * 3.5D) {
            Vec3 current = minecraft.player.position(); Vec3 targetPosition = target.position();
            minecraft.player.setYRot((float) (Mth.atan2(current.x - targetPosition.x, targetPosition.z - current.z) * Mth.RAD_TO_DEG));
            minecraft.options.keyUp.setDown(true);
            return;
        }
        minecraft.options.keyUp.setDown(false);
        long tick = minecraft.level.getGameTime();
        if (entityAttackAttempts < maximumAttempts && (lastEntityAttackTick == Long.MIN_VALUE || tick - lastEntityAttackTick >= 12L)) {
            minecraft.gameMode.attack(minecraft.player, target);
            entityAttackAttempts++;
            lastEntityAttackTick = tick;
        }
        if (entityAttackAttempts >= maximumAttempts) {
            // This action only sends bounded ordinary player attacks.  A following domain
            // diagnostic establishes death; local entity removal is presentation timing and
            // must not make an otherwise completed physical action spin indefinitely.
            advance("attack_nearest_entity");
            return;
        }
        timeout(minecraft, action, "timed out attacking ordinary entity " + expectedType);
    }
    /** Walks only to the final locally rendered position of the already selected ordinary target. */
    private static boolean approach(Minecraft minecraft, Vec3 target, double radius) {
        Vec3 current = minecraft.player.position(); double dx = target.x - current.x, dz = target.z - current.z;
        if (dx * dx + dz * dz <= radius * radius) { minecraft.options.keyUp.setDown(false); return false; }
        minecraft.player.setYRot((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG)); minecraft.options.keyUp.setDown(true);
        return true;
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
     * The server accepts an advance command before its bounded canonical slices have run.  Keep
     * the pilot on this action until the registered read-only performance view proves there is
     * no remaining request; otherwise a following visit can accidentally turn an in-flight COLD
     * test advance into HOT execution.
     */
    private static void waitForFastForward(Minecraft minecraft, JsonObject action) {
        if (!fastForwardSent) {
            minecraft.player.connection.sendCommand("pale_mirror v3 advance " + action.get("ticks").getAsInt());
            fastForwardSent = true;
        }
        ObservedDiagnostic observed = diagnostics.get(new DiagnosticIdentity("performance", ""));
        if (fresh(observed) && observed.value().has("fastForwardRemaining")) {
            int remaining = observed.value().get("fastForwardRemaining").getAsInt();
            if (remaining > 0) fastForwardObservedActive = true;
            // A delayed terminal diagnostic from the preceding advance has no authority to
            // complete this one.  This action must first observe its own active request.
            if (fastForwardObservedActive && remaining == 0) {
                advance("fast_forward");
                return;
            }
        }
        long tick = minecraft.level.getGameTime();
        if ((tick - actionStartedTick) % 20L == 0L) minecraft.player.connection.sendCommand("pale_mirror v3 inspect performance");
        if ((tick - actionStartedTick) * 50L >= action.get("timeoutMs").getAsLong()) {
            throw new IllegalStateException("timed out waiting for bounded canonical fast-forward completion");
        }
    }
    /**
     * An inspect completes only after its ordinary read-only command returned. Advancing on
     * packet submission would make that delayed reply look like evidence for the next action.
     */
    private static void inspect(Minecraft minecraft, JsonObject action) {
        String view = action.get("view").getAsString(); String id = action.get("id").getAsString();
        ObservedDiagnostic observed = diagnostics.get(new DiagnosticIdentity(view, id));
        if (observed != null && observed.tick() >= actionStartedTick) { advance("inspect"); return; }
        if (!inspectSent) {
            minecraft.player.connection.sendCommand("pale_mirror v3 inspect " + view + (id.isBlank() ? "" : " " + id));
            inspectSent = true;
        }
        if ((minecraft.level.getGameTime() - actionStartedTick) * 50L >= 30_000L) {
            throw new IllegalStateException("timed out awaiting read-only diagnostic " + view + " " + id);
        }
    }
    /**
     * Waits for one complete physical harvest result: the durable intent carries an exact
     * receipt for its named wheat identity and the site entered its next growth epoch. The
     * stack may already have become a real production input, so current item custody is not
     * evidence that the completed receipt did or did not happen.
     */
    private static void waitUntilHarvestResult(Minecraft minecraft, JsonObject action) {
        String siteId = action.get("siteId").getAsString(); String intentId = action.get("intentId").getAsString(); String itemId = action.get("itemId").getAsString();
        long tick = minecraft.level.getGameTime();
        ObservedDiagnostic site = diagnostics.get(new DiagnosticIdentity("site", siteId));
        ObservedDiagnostic intent = diagnostics.get(new DiagnosticIdentity("intent", intentId));
        if (fresh(site) && fresh(intent) && harvestComplete(site.value(), intent.value(), itemId)) {
            advance("wait_until_harvest_result"); return;
        }
        if ((tick - actionStartedTick) % 20L == 0L) {
            minecraft.player.connection.sendCommand("pale_mirror v3 inspect site " + siteId);
            minecraft.player.connection.sendCommand("pale_mirror v3 inspect intent " + intentId);
            if (action.has("settlementId")) minecraft.player.connection.sendCommand("pale_mirror v3 inspect settlement " + action.get("settlementId").getAsString());
            // The terminal outcome is owned by site/intent/item, but a duration-bearing scene
            // also needs its named worker's lease/body evidence when it is still pending.  This
            // is read-only scenario telemetry; it neither selects a replacement worker nor
            // changes admission.
            if (action.has("workerId")) minecraft.player.connection.sendCommand("pale_mirror v3 inspect actor " + action.get("workerId").getAsString());
        }
        long timeoutMs = action.get("timeoutMs").getAsLong();
        if ((tick - actionStartedTick) * 50L >= timeoutMs) {
            throw new IllegalStateException("timed out waiting for confirmed harvest site=" + siteId + " intent=" + intentId + " item=" + itemId);
        }
    }
    /** Waits for a precise canonical slot-owned stack without needing its generated item identity. */
    private static void waitUntilContainerItem(Minecraft minecraft, JsonObject action) {
        String containerId = action.get("containerId").getAsString(); long tick = minecraft.level.getGameTime();
        ObservedDiagnostic observed = diagnostics.get(new DiagnosticIdentity("container", containerId));
        if (fresh(observed) && containerContains(observed.value(), action)) { advance("wait_until_container_item"); return; }
        if ((tick - actionStartedTick) % 20L == 0L) minecraft.player.connection.sendCommand("pale_mirror v3 inspect container " + containerId);
        timeout(minecraft, action, "timed out waiting for exact container ingress " + containerId);
    }
    private static boolean containerContains(JsonObject container, JsonObject action) {
        if (!"ok".equals(string(container, "status")) || !container.has("occupied") || !container.get("occupied").isJsonArray()) return false;
        String item = action.get("item").getAsString(); int count = action.get("count").getAsInt();
        Integer slot = action.has("slot") ? action.get("slot").getAsInt() : null;
        for (JsonElement element : container.getAsJsonArray("occupied")) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            if (item.equals(string(value, "itemKind")) && value.has("count") && value.get("count").getAsInt() == count
                    && (slot == null || value.has("slot") && value.get("slot").getAsInt() == slot)) return true;
        }
        return false;
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
    private static boolean harvestComplete(JsonObject site, JsonObject intent, String itemId) {
        if (!"ok".equals(string(site, "status")) || !"GROWING".equals(string(site, "phase")) || site.get("growthEpoch").getAsLong() < 2L
                || !"ok".equals(string(intent, "status")) || !"CONFIRMED".equals(string(intent, "intentStatus"))
                || !"RESOURCE_SITE_HARVEST".equals(string(intent, "intentKind")) || string(intent, "receiptId").isBlank()
                || !intent.has("subjects") || !intent.get("subjects").isJsonArray()) return false;
        return java.util.stream.StreamSupport.stream(intent.getAsJsonArray("subjects").spliterator(), false)
                .anyMatch(value -> value.isJsonPrimitive() && itemId.equals(value.getAsString()));
    }
    private static String string(JsonObject object, String member) {
        JsonElement value = object.get(member); return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
    private static void timeout(Minecraft minecraft, JsonObject action, String detail) {
        if ((minecraft.level.getGameTime() - actionStartedTick) * 50L >= action.get("timeoutMs").getAsLong()) throw new IllegalStateException(detail);
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
        look(minecraft, Vec3.atCenterOf(target));
    }
    /** Updates the local render camera only; no target UUID or server selection is involved. */
    private static void look(Minecraft minecraft, Vec3 target) {
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 delta = target.subtract(eye);
        minecraft.player.setYRot((float) (Mth.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG));
        minecraft.player.setXRot((float) -(Mth.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * Mth.RAD_TO_DEG));
    }
    /**
     * Looks at either a literal test coordinate or the one explicitly declared
     * read-only field anchor. The latter prevents a materialization test from
     * quietly retaining a stale generated coordinate when the immutable field
     * compiler legitimately chooses another free side of the farm.
     */
    private static void lookAtPosition(Minecraft minecraft, JsonObject action) {
        BlockPos target = resolvedPosition(minecraft, action, action.has("at") ? "at" : "position");
        if (target == null) return;
        look(minecraft, target);
        advance("look");
    }
    /**
     * Resolves a deliberately narrow immutable plan anchor from a read-only
     * diagnostic. The container form is usable for an ordinary right-click;
     * it grants neither server-side selection nor any mutation authority.
     */
    private static BlockPos resolvedPosition(Minecraft minecraft, JsonObject action, String field) {
        JsonObject value = Objects.requireNonNull(action.getAsJsonObject(field), field + " position");
        if (value.has("x") && value.has("y") && value.has("z")) {
            return new BlockPos(value.get("x").getAsInt(), value.get("y").getAsInt(), value.get("z").getAsInt());
        }
        JsonObject reference = value.getAsJsonObject("diagnostic");
        if (reference == null) throw new IllegalArgumentException(field + " requires a literal coordinate or named diagnostic anchor");
        String view = reference.get("view").getAsString(); String id = reference.get("id").getAsString();
        String diagnosticField = reference.get("field").getAsString();
        ObservedDiagnostic observed = diagnostics.get(new DiagnosticIdentity(view, id));
        // The narrow whitelist is independently enforced by the parsed scenario
        // schema. A preceding wait may have proved the same immutable anchor, so
        // a following action uses its recorded diagnostic rather than inventing
        // a second server-side coordinate lookup.
        if (observed != null) {
            JsonObject anchor = diagnosticAnchor(observed.value(), diagnosticField);
            if (anchor == null || !anchor.has("x") || !anchor.has("y") || !anchor.has("z")) {
                throw new IllegalStateException(view + " diagnostic lacks " + diagnosticField + " for " + id);
            }
            return new BlockPos(anchor.get("x").getAsInt(), anchor.get("y").getAsInt(), anchor.get("z").getAsInt());
        }
        if ((minecraft.level.getGameTime() - actionStartedTick) % 20L == 0L) {
            minecraft.player.connection.sendCommand("pale_mirror v3 inspect " + view + " " + id);
        }
        if ((minecraft.level.getGameTime() - actionStartedTick) * 50L >= FrontierV3TestPilotTimeouts.resolutionTimeoutMillis(action)) {
            throw new IllegalStateException("timed out reading current " + view + " anchor " + id);
        }
        return null;
    }
    /**
     * The parsed scenario permits only a finite published anchor vocabulary.
     * Dotted fields are therefore structural read-only paths, not a general
     * JSON query language or a server-side target selector.
     */
    private static JsonObject diagnosticAnchor(JsonObject diagnostic, String field) {
        JsonObject current = diagnostic;
        String[] segments = field.split("\\.", -1);
        if (segments.length == 0 || segments.length > 2) return null;
        for (String segment : segments) {
            if (segment.isEmpty()) return null;
            current = current.getAsJsonObject(segment);
            if (current == null) return null;
        }
        return current;
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
        index++; actionStartedTick = -1L; breaking = false; placementAttempted = false;
        visitSent = false; visitChunkReadyTick = -1L; containerOpenAttempted = false; quickMoveAttempted = false; inspectSent = false;
        fastForwardSent = false; fastForwardObservedActive = false;
        boardInteractionAttempted = false; entityInteractionAttempted = false;
        attackedEntityRuntimeId = -1; entityAttackAttempts = 0; lastEntityAttackTick = Long.MIN_VALUE; lastAttackedEntityPosition = null;
        if (runningSetup && index >= setup.size()) {
            runningSetup = false; index = 0;
            try {
                if (!FrontierV3PilotSessionControl.resumed()) {
                    FrontierV3PilotSessionControl.publishLifecycleSignal("client_connected_fixture_ready", FrontierV3PilotSessionControl.lifecycleSegment(), new JsonObject());
                }
            } catch (IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("pilot could not acknowledge fixture readiness", failure);
            }
            PaleMirrorMod.LOGGER.info("PMV3_PILOT setup complete; beginning evidence actions={}", actions.size());
        }
        else if (reachedFrame != null) {
            JsonObject frame = reachedFrame;
            String presentation = frame.has("presentation") ? frame.get("presentation").getAsString() : "clean";
            if (presentation.equals("clean")) FrontierV3TestPilotPresentation.prepareCleanCapture(Minecraft.getInstance());
            else FrontierV3TestPilotPresentation.preparePlayerCapture(Minecraft.getInstance());
            captureBarrier = new CaptureBarrier(completedAction, frame.get("name").getAsString(), presentation,
                    Minecraft.getInstance().level.getGameTime() + CAPTURE_SETTLE_TICKS, false);
        }
        else if (!runningSetup && index >= actions.size()) {
            completeScenario();
        }
        if (completedAction > 0) {
            JsonObject detail = new JsonObject(); detail.addProperty("actionStep", completedAction); detail.addProperty("actionType", type);
            try {
                FrontierV3PilotSessionControl.publishLifecycleSignal("action_checkpoint", FrontierV3PilotSessionControl.lifecycleSegment() + "-" + String.format("%04d", completedAction), detail);
            } catch (IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("pilot could not acknowledge action checkpoint", failure);
            }
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
        String segment = FrontierV3PilotSessionControl.lifecycleSegment();
        if (FrontierV3PilotSessionControl.expectedCrashSegment()) {
            // A declared fault can park after the final ordinary player action.  Keep the same
            // connection idle only for the runner's bounded real-probe/arm wait; publishing a
            // terminal result or replaying actions would both falsify the crash boundary.
            if (!FrontierV3PilotSessionControl.expectedLossArmed()) FrontierV3PilotSessionControl.markAwaitingExpectedLossProbe();
            return;
        }
        if (FrontierV3PilotSessionControl.shouldAwaitNextSegment()) {
            try {
                String runId = FrontierV3PilotSessionControl.runId();
                FrontierV3PilotSessionControl.publishLifecycleSignal("scenario_segment_complete", segment, new JsonObject());
                FrontierV3PilotSessionControl.markAwaitingResume();
                PaleMirrorMod.LOGGER.info("PMV3_PILOT session_segment_complete runId={}", runId);
                // Queue the normal client disconnect after this tick.  Calling it re-entrantly
                // from the scenario action tick can leave the live network connection open
                // until server shutdown, which makes a persistent-client restart slower and
                // fails to prove an ordinary departure.
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.execute(() -> {
                    if (minecraft.getConnection() == null) {
                        throw new IllegalStateException("persistent pilot has no live connection to close");
                    }
                    minecraft.getConnection().getConnection().disconnect(Component.literal("Frontier v3 persistent-pilot restart"));
                    minecraft.disconnect();
                });
                return;
            } catch (IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("persistent pilot could not publish its exact restart boundary", failure);
            }
        }
        if (FrontierV3PilotSessionControl.shouldAwaitFinalClose()) {
            try {
                FrontierV3PilotSessionControl.publishLifecycleSignal("scenario_segment_complete", segment, new JsonObject());
                FrontierV3PilotSessionControl.markAwaitingFinalClose();
                PaleMirrorMod.LOGGER.info("PMV3_PILOT final_segment_awaiting_supervisor_close runId={}", FrontierV3PilotSessionControl.runId());
                return;
            } catch (IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("persistent pilot could not publish its final matrix boundary", failure);
            }
        }
        try {
            FrontierV3PilotSessionControl.publishLifecycleSignal("scenario_segment_complete", segment, new JsonObject());
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("pilot could not acknowledge terminal scenario segment", failure);
        }
        // The outer runner must still receive the server response to the final
        // read-only assertion before it closes this ordinary client.
        PaleMirrorMod.LOGGER.info("PMV3_PILOT completed scenario actions={}", actions.size());
    }
    private static void reset() {
        Minecraft minecraft = Minecraft.getInstance(); minecraft.options.keyUp.setDown(false);
        FrontierV3TestPilotPresentation.reset(minecraft);
        actions = null; setup = null; frames = null; captureBarrier = null;
        runningSetup = false; index = 0; actionStartedTick = -1L; breaking = false;
        visitSent = false; visitChunkReadyTick = -1L;
        containerOpenAttempted = false; quickMoveAttempted = false; inspectSent = false;
        boardInteractionAttempted = false; entityInteractionAttempted = false;
        attackedEntityRuntimeId = -1; entityAttackAttempts = 0; lastEntityAttackTick = Long.MIN_VALUE;
        diagnostics.clear(); FrontierV3PilotSessionControl.reset(); fastForwardSent = false;
    }
    private static void clearExpectedLossTransientState() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.keyUp.setDown(false);
        actions = null; setup = null; frames = null; captureBarrier = null;
        runningSetup = false; index = 0; actionStartedTick = -1L; breaking = false;
        placementAttempted = false; visitSent = false; visitChunkReadyTick = -1L;
        containerOpenAttempted = false; quickMoveAttempted = false; inspectSent = false;
        fastForwardSent = false; fastForwardObservedActive = false; boardInteractionAttempted = false;
        entityInteractionAttempted = false; attackedEntityRuntimeId = -1; entityAttackAttempts = 0;
        lastEntityAttackTick = Long.MIN_VALUE; lastAttackedEntityPosition = null; diagnostics.clear();
    }
    private record DiagnosticIdentity(String view, String id) { }
    private record ObservedDiagnostic(long tick, JsonObject value) { }
    private record CaptureBarrier(int after, String name, String presentation, long readyAtTick, boolean announced) { }
}
