package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Explicit development bridge; V3 has no production activation before the cutover gate. */
public final class FrontierV3ServerLifecycle {
    private static final String ENABLED_PROPERTY = "pale_mirror.frontier_v3.enabled";
    private static final Map<MinecraftServer, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection>> RUNTIMES = new IdentityHashMap<>();
    private static final WorkBudget TICK_BUDGET = new WorkBudget(128, 512);

    private FrontierV3ServerLifecycle() { }

    public static void start(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!enabled() || RUNTIMES.containsKey(server)) return;
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:overworld"), server.overworld().getSeed()),
                new FrontierFileStore(server.getWorldPath(LevelResource.ROOT), FrontierWorldRuntimeDefinition.payloadCodecs()), 200);
        RUNTIMES.put(server, runtime);
        if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
            try {
                int uninspectable = FrontierV3PhysicalIntentRestartSafety.quarantineUninspectableRunningIntents(runtime);
                if (uninspectable > 0) {
                    PaleMirrorMod.LOGGER.error("Frontier v3 quarantined {} uninspectable running physical intent(s) after restart", uninspectable);
                }
            } catch (RuntimeException error) {
                runtime.quarantine(error);
            }
        }
        if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
            PaleMirrorMod.LOGGER.info("Frontier v3 development runtime started for {}", server.getWorldPath(LevelResource.ROOT));
        } else {
            PaleMirrorMod.LOGGER.error("Frontier v3 development runtime quarantined at startup: {}", runtime.status().detail().orElse("unknown"));
        }
    }

    public static void tick(MinecraftServer server) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(server);
        if (runtime == null) return;
        try {
            if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
                FrontierV3PhysicalObservationExecutor.tick(server.overworld(), runtime);
                FrontierV3GrayboxExecutor.tick(server.overworld(), runtime);
                FrontierV3InfectionOverlayExecutor.tick(server.overworld(), runtime);
                FrontierV3AmbientActorExecutor.tick(server.overworld(), runtime);
                // Observe player custody before passive surface drift inspection can classify it.
                FrontierV3InventoryObservationExecutor.tick(server.overworld(), runtime);
                FrontierV3ContainerSurfaceExecutor.tick(server.overworld(), runtime);
                FrontierV3CargoHandoffExecutor.tick(server.overworld(), runtime);
                FrontierV3SceneExecutor.tick(server.overworld(), runtime);
                runtime.tick(TICK_BUDGET);
            }
        } catch (RuntimeException error) {
            runtime.quarantine(error);
        }
        if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.QUARANTINED) {
            PaleMirrorMod.LOGGER.error("Frontier v3 development runtime quarantined: {}", runtime.status().detail().orElse("unknown"));
            RUNTIMES.remove(server);
        }
    }

    public static void stop(MinecraftServer server) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.remove(server);
        if (runtime != null) {
            FrontierV3GrayboxExecutor.forget(runtime);
            FrontierV3InfectionOverlayExecutor.forget(runtime);
            FrontierV3AmbientActorExecutor.forget(runtime);
            runtime.shutdown();
        }
    }

    /** Retains an exact restored ambient body until ServerLevel publishes its UUID index. */
    public static boolean observeEntityJoin(ServerLevel level, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && FrontierV3AmbientActorExecutor.observeJoin(runtime, entity);
    }

    /** Returns true only when this v3 runtime durably accepted the managed HOT death. */
    public static boolean observeLivingDeath(ServerLevel level, Entity entity, Entity source) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && (FrontierV3SceneExecutor.observeDeath(runtime, entity, source)
                || FrontierV3AmbientActorExecutor.observeDeath(runtime, entity, source));
    }

    /** Captures an ambient body on normal world departure; false means it is not v3-owned. */
    public static boolean observeEntityLeave(ServerLevel level, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && FrontierV3AmbientActorExecutor.observeLeave(runtime, entity);
    }

    /** True means the v3-owned break was not durably accepted and Minecraft must not apply it. */
    public static boolean rejectBlockBreak(ServerLevel level, BlockPos position, ServerPlayer player) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(position, "position"); Objects.requireNonNull(player, "player");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        String cause = "player:" + player.getUUID();
        if (FrontierV3InfectionOverlayExecutor.observeBlockBreak(runtime, level, position, cause)
                == FrontierV3InfectionOverlayExecutor.BlockBreakObservation.REJECTED) return true;
        return FrontierV3GrayboxExecutor.observeBlockBreak(runtime, level, position, cause)
                == FrontierV3GrayboxExecutor.BlockBreakObservation.REJECTED;
    }

    /** Captures real explosion candidates for next-tick postcondition inspection without altering the blast. */
    public static boolean observeExternalExplosion(ServerLevel level, java.util.List<BlockPos> affected) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(affected, "affected blocks");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && FrontierV3PhysicalObservationExecutor.captureExternalExplosion(level, runtime, affected);
    }

    static boolean enabled() { return Boolean.getBoolean(ENABLED_PROPERTY); }
}
