package io.farfrontier.palemirror.internal.integration.distanthorizons;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/** Keeps server-side DH cache work subordinate to live travel and chunk generation. */
public final class DistantHorizonsRuntime implements AutoCloseable {
    private static final String BRIDGE_CLASS = "io.farfrontier.palemirror.internal.integration.distanthorizons.DistantHorizonsApiBridge";
    private static final int SAMPLE_INTERVAL_TICKS = 20;
    private static final int RETRY_INTERVAL_TICKS = 200;

    interface Bridge extends AutoCloseable {
        String version();
        double applyCacheOnly(double runtimeRatio);
        double setRuntimeRatio(double runtimeRatio);
        @Override void close();
    }

    record Policy(double enterSpeed, double exitSpeed, int recoveryTicks, double normalRatio, double fastRatio) { }
    private static final Policy SAFE_POLICY = new Policy(12.0D, 6.0D, 200, 0.35D, 0.05D);
    private record PlayerSample(ResourceKey<Level> dimension, double x, double z) { }

    private final Bridge bridge;
    private final String availability;
    private final boolean logTransitions;
    private final Policy policy;
    private final TravelLoadController controller;
    private final Map<UUID, PlayerSample> samples = new HashMap<>();
    private String initialFailure = "";
    private String transitionFailure = "";
    private boolean applied;
    private long lastSampleTick = Long.MIN_VALUE;
    private long nextRetryTick;
    private double maximumSpeed;
    private double appliedRatio;
    private TravelLoadController.Mode appliedMode = TravelLoadController.Mode.NORMAL;

    private DistantHorizonsRuntime(Bridge bridge, String availability, boolean logTransitions, Policy policy) {
        this.bridge = bridge;
        this.availability = availability;
        this.logTransitions = logTransitions;
        this.policy = policy;
        this.controller = new TravelLoadController(policy.enterSpeed(), policy.exitSpeed(), policy.recoveryTicks());
    }

    static DistantHorizonsRuntime forTest(Bridge bridge, Policy policy) {
        return new DistantHorizonsRuntime(bridge, "TEST", false, policy);
    }

    public static DistantHorizonsRuntime create() {
        Policy policy;
        try {
            policy = configuredPolicy();
        } catch (IllegalArgumentException exception) {
            PaleMirrorMod.LOGGER.error("Distant Horizons travel budget configuration is invalid", exception);
            return new DistantHorizonsRuntime(null, "INVALID_CONFIG " + exception.getMessage(), true, SAFE_POLICY);
        }
        if (!PaleMirrorServerConfig.DH_CACHE_ONLY_CONTROL.get()) {
            return new DistantHorizonsRuntime(null, "DISABLED_BY_CONFIG", true, policy);
        }
        if (!ModList.get().isLoaded("distanthorizons")) {
            return new DistantHorizonsRuntime(null, "ABSENT", true, policy);
        }
        try {
            Class<?> type = Class.forName(BRIDGE_CLASS, true, DistantHorizonsRuntime.class.getClassLoader());
            Bridge bridge = (Bridge) type.getConstructor().newInstance();
            return new DistantHorizonsRuntime(bridge, "AVAILABLE " + bridge.version(), true, policy);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException
                 | InvocationTargetException | LinkageError | ClassCastException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                    ? invocation.getCause() : exception;
            PaleMirrorMod.LOGGER.warn("Distant Horizons cache-only control is unavailable: {}", cause.toString());
            return new DistantHorizonsRuntime(null, "INCOMPATIBLE " + cause.getMessage(), true, policy);
        }
    }

    private static Policy configuredPolicy() {
        double enter = PaleMirrorServerConfig.DH_FAST_TRAVEL_ENTER_SPEED.get();
        double exit = PaleMirrorServerConfig.DH_FAST_TRAVEL_EXIT_SPEED.get();
        double normalRatio = PaleMirrorServerConfig.DH_NORMAL_RUNTIME_RATIO.get();
        double fastRatio = PaleMirrorServerConfig.DH_FAST_TRAVEL_RUNTIME_RATIO.get();
        if (enter <= exit) throw new IllegalArgumentException("enter speed must exceed exit speed");
        if (fastRatio > normalRatio) throw new IllegalArgumentException("fast-travel ratio must not exceed normal ratio");
        return new Policy(enter, exit, PaleMirrorServerConfig.DH_FAST_TRAVEL_RECOVERY_SECONDS.get() * 20,
                normalRatio, fastRatio);
    }

    public void tick(MinecraftServer server) {
        applyOnce();
        if (!applied) return;
        long gameTick = server.overworld().getGameTime();
        if (lastSampleTick != Long.MIN_VALUE && gameTick - lastSampleTick < SAMPLE_INTERVAL_TICKS) return;
        int elapsedTicks = lastSampleTick == Long.MIN_VALUE
                ? SAMPLE_INTERVAL_TICKS : (int) Math.min(Integer.MAX_VALUE, Math.max(1L, gameTick - lastSampleTick));
        lastSampleTick = gameTick;

        boolean discontinuity = false;
        maximumSpeed = 0.0D;
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            present.add(id);
            PlayerSample current = new PlayerSample(player.level().dimension(), player.getX(), player.getZ());
            PlayerSample previous = samples.put(id, current);
            if (previous == null) continue;
            if (!previous.dimension().equals(current.dimension())) {
                discontinuity = true;
                continue;
            }
            double distance = Math.hypot(current.x() - previous.x(), current.z() - previous.z());
            maximumSpeed = Math.max(maximumSpeed, distance * 20.0D / elapsedTicks);
            // A one-second displacement beyond plausible ordinary travel is a teleport even if dimension is unchanged.
            if (distance >= Math.max(128.0D, policy.enterSpeed() * elapsedTicks)) discontinuity = true;
        }
        samples.keySet().retainAll(present);
        TravelLoadController.Mode desired = controller.observe(maximumSpeed, discontinuity, elapsedTicks);
        applyMode(desired, gameTick);
    }

    void applyOnce() {
        if (bridge == null || applied || !initialFailure.isEmpty()) return;
        try {
            appliedRatio = bridge.applyCacheOnly(policy.normalRatio());
            applied = true;
            if (logTransitions) {
                PaleMirrorMod.LOGGER.info(
                        "Distant Horizons mode=CACHE_ONLY load=NORMAL runtimeRatio={} backgroundGenerator=DISABLED fallback=PRE_EXISTING_ONLY",
                        appliedRatio);
            }
        } catch (RuntimeException | LinkageError exception) {
            initialFailure = exception.toString();
            if (logTransitions) {
                PaleMirrorMod.LOGGER.error(
                        "Distant Horizons cache-only control failed closed; PM simulation continues", exception);
            }
            close();
        }
    }

    void observeForTest(double speed, boolean discontinuity, int elapsedTicks, long gameTick) {
        applyOnce();
        maximumSpeed = speed;
        applyMode(controller.observe(speed, discontinuity, elapsedTicks), gameTick);
    }

    private void applyMode(TravelLoadController.Mode desired, long gameTick) {
        if (!applied || desired == appliedMode || gameTick < nextRetryTick) return;
        double requested = desired == TravelLoadController.Mode.FAST_TRAVEL ? policy.fastRatio() : policy.normalRatio();
        try {
            appliedRatio = bridge.setRuntimeRatio(requested);
            appliedMode = desired;
            transitionFailure = "";
            if (logTransitions) {
                PaleMirrorMod.LOGGER.info("Distant Horizons load={} runtimeRatio={} maxPlayerSpeed={}",
                        desired, appliedRatio, String.format(java.util.Locale.ROOT, "%.1f", maximumSpeed));
            }
        } catch (RuntimeException | LinkageError exception) {
            transitionFailure = exception.toString();
            nextRetryTick = gameTick + RETRY_INTERVAL_TICKS;
            if (logTransitions) {
                PaleMirrorMod.LOGGER.warn(
                        "Could not change Distant Horizons runtime ratio; cache-only mode remains active", exception);
            }
        }
    }

    public String status() {
        String state = bridge == null ? availability : initialFailure.isEmpty() ? availability : "FAILED " + initialFailure;
        String degraded = transitionFailure.isEmpty() ? "" : ", transition=DEGRADED " + transitionFailure;
        return "dh=" + state
                + ", mode=CACHE_ONLY, load=" + appliedMode + ", desiredLoad=" + controller.mode() + ", maxSpeed="
                + String.format(java.util.Locale.ROOT, "%.1f", maximumSpeed)
                + ", runtimeRatio=" + String.format(java.util.Locale.ROOT, "%.2f", appliedRatio)
                + ", recoveryTicks=" + controller.recoveryTicksRemaining()
                + ", backgroundGenerator=DISABLED, fallback=PRE_EXISTING_ONLY, applied=" + applied + degraded;
    }

    @Override
    public void close() {
        if (bridge == null) return;
        try {
            bridge.close();
        } catch (RuntimeException | LinkageError exception) {
            PaleMirrorMod.LOGGER.warn("Could not clear Distant Horizons API overrides", exception);
        }
        applied = false;
        samples.clear();
    }
}
