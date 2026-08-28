package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
                FrontierV3CargoHandoffExecutor.tick(server.overworld(), runtime);
                FrontierV3InventoryObservationExecutor.tick(server.overworld(), runtime);
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
        if (runtime != null) runtime.shutdown();
    }

    /** Returns true only when this v3 runtime durably accepted the managed HOT death. */
    public static boolean observeLivingDeath(ServerLevel level, Entity entity, Entity source) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        return runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && FrontierV3SceneExecutor.observeDeath(runtime, entity, source);
    }

    static boolean enabled() { return Boolean.getBoolean(ENABLED_PROPERTY); }
}
