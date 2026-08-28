package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.model.CargoCarrierReleased;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.damagesource.DamageSource;
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
                int uninspectable = FrontierV3PhysicalIntentRestartSafety.quarantineUninspectableRunningIntents(runtime, server.overworld());
                int ambientUnknown = FrontierV3AmbientLeaseRestartSafety.quarantineActiveLeases(runtime);
                int sceneUnknown = FrontierV3SceneLeaseRestartSafety.quarantineActiveLeases(runtime);
                if (uninspectable > 0) {
                    PaleMirrorMod.LOGGER.error("Frontier v3 quarantined {} uninspectable running physical intent(s) after restart", uninspectable);
                }
                if (ambientUnknown > 0) {
                    PaleMirrorMod.LOGGER.warn("Frontier v3 retained {} ambient HOT lease(s) as UNKNOWN pending loaded-world recovery", ambientUnknown);
                }
                if (sceneUnknown > 0) {
                    PaleMirrorMod.LOGGER.warn("Frontier v3 retained {} scene lease(s) as UNKNOWN pending loaded-world recovery", sceneUnknown);
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
                FrontierV3ExplosionExecutor.tick(server.overworld(), runtime);
                FrontierV3CargoCarrierImpactExecutor.tick(server.overworld(), runtime);
                FrontierV3GrayboxExecutor.tick(server.overworld(), runtime);
                FrontierV3DecontaminationExecutor.tick(server.overworld(), runtime);
                FrontierV3InfectionOverlayExecutor.tick(server.overworld(), runtime);
                FrontierV3ObjectBoardExecutor.tick(server.overworld(), runtime);
                FrontierV3AmbientActorExecutor.tick(server.overworld(), runtime);
                // Observe player custody before passive surface drift inspection can classify it.
                FrontierV3InventoryObservationExecutor.tick(server.overworld(), runtime);
                FrontierV3CargoCarrierObservationExecutor.tick(server.overworld(), runtime);
                FrontierV3ContainerSurfaceExecutor.tick(server.overworld(), runtime);
                FrontierV3CargoHandoffExecutor.tick(server.overworld(), runtime);
                FrontierV3StructuralRepairExecutor.tick(server.overworld(), runtime);
                FrontierV3RouteConstructionExecutor.tick(server.overworld(), runtime);
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
            FrontierV3ObjectBoardExecutor.forget(runtime);
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

    /**
     * Durably releases an exact HOT shipment before vanilla opens its chest-minecart UI. The cart
     * remains in the world and each later player/drop/hopper move is observed from its stable
     * world-carrier identity; a rejected release must keep the interaction closed.
     */
    public static CargoCarrierInteraction releaseCargoCarrier(ServerLevel level, ServerPlayer player, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return CargoCarrierInteraction.NOT_MANAGED;
        return releaseCargoCarrier(level, runtime, entity, java.util.Optional.of(player.getUUID()));
    }

    /** Makes a carrier physically accountable before a real world effect may destroy it. */
    private static CargoCarrierInteraction releaseCargoCarrier(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                Entity entity, java.util.Optional<java.util.UUID> observerPlayerId) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity"); Objects.requireNonNull(observerPlayerId, "observer player id");
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return CargoCarrierInteraction.NOT_MANAGED;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return CargoCarrierInteraction.REJECTED;
        var lease = FrontierV3CargoCarrierExecutor.activeLease(state, entity);
        if (lease.isEmpty()) return CargoCarrierInteraction.NOT_MANAGED;
        if (!FrontierV3CargoCarrierExecutor.markReleasedCarrier(state, lease.orElseThrow(), entity)) return CargoCarrierInteraction.REJECTED;
        CheckpointImage checkpoint = runtime.checkpointImage().orElse(null);
        if (checkpoint == null) return CargoCarrierInteraction.REJECTED;
        String observer = observerPlayerId.map(java.util.UUID::toString).orElse("physical-effect");
        CommandId commandId = new CommandId("executor:cargo-carrier-release-" + lease.orElseThrow().id().value().replace(':', '-')
                + "-observer-" + observer + "-r" + checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId),
                new CargoCarrierReleased(lease.orElseThrow().id(), lease.orElseThrow().cargoId(), entity.getUUID(), observerPlayerId)))
                .orElse(null);
        return result instanceof CommandResult.Accepted ? CargoCarrierInteraction.RELEASED : CargoCarrierInteraction.REJECTED;
    }

    public enum CargoCarrierInteraction { NOT_MANAGED, RELEASED, REJECTED }

    /**
     * Captures a fatal non-explosion vehicle hit before vanilla discards its chest inventory.
     * Explosion detonation has a separate pre-effect bridge and must not create duplicate impact
     * evidence through this general vehicle hook.
     */
    public static void observeTerminalVehicleDamage(ServerLevel level, Entity entity, DamageSource source) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity"); Objects.requireNonNull(source, "damage source");
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return;
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        observeTerminalVehicleDamage(level, runtime, entity, source);
    }

    /** Package-visible so the materialized consequence test uses the same pre-destruction path. */
    static void observeTerminalVehicleDamage(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                             Entity entity, DamageSource source) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(entity, "entity"); Objects.requireNonNull(source, "damage source");
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return;
        CargoCarrierInteraction released = releaseCargoCarrier(level, runtime, entity, java.util.Optional.empty());
        if (released == CargoCarrierInteraction.REJECTED) {
            runtime.quarantine(new IllegalStateException("terminal vehicle damage cannot durably release one HOT cargo carrier"));
            return;
        }
        captureCargoCarrierImpact(level, runtime, entity, "terminal vehicle damage");
    }

    /** Admits one real world-drop pickup only after its exact custody receipt is durable. */
    public static ExactCustodyObservation observeExactItemPickup(ServerLevel level, ServerPlayer player, ItemEntity itemEntity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(itemEntity, "item entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return ExactCustodyObservation.NOT_MANAGED;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return ExactCustodyObservation.REJECTED;
        var carrierId = FrontierV3CargoHandoffExecutor.worldCarrierId(itemEntity.getItem());
        if (carrierId.isEmpty()) return ExactCustodyObservation.NOT_MANAGED;
        var source = new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.WorldCarrier(carrierId.orElseThrow());
        var item = state.inventory().items().values().stream().filter(value -> value.custody().equals(source))
                .filter(value -> FrontierV3CargoHandoffExecutor.exactMatch(itemEntity.getItem(), value)).findFirst();
        if (item.isEmpty()) return ExactCustodyObservation.NOT_MANAGED;
        return submitExactCustody(runtime, "world-pickup", item.orElseThrow().id().value(),
                new io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged(item.orElseThrow().id(), source,
                        new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.Player(player.getUUID())));
    }

    /** Captures a player toss as a new exact physical carrier before Minecraft releases the item entity. */
    public static ExactCustodyObservation observeExactItemToss(ServerLevel level, ServerPlayer player, ItemEntity itemEntity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(itemEntity, "item entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return ExactCustodyObservation.NOT_MANAGED;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return ExactCustodyObservation.REJECTED;
        var item = state.inventory().items().values().stream().filter(value -> value.custody() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.Player owner
                        && owner.playerId().equals(player.getUUID()))
                .filter(value -> FrontierV3CargoHandoffExecutor.exactMatch(itemEntity.getItem(), value)).findFirst();
        if (item.isEmpty()) {
            var carrierId = FrontierV3CargoHandoffExecutor.worldCarrierId(itemEntity.getItem());
            if (carrierId.isEmpty()) return ExactCustodyObservation.NOT_MANAGED;
            var source = new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.WorldCarrier(carrierId.orElseThrow());
            item = state.inventory().items().values().stream().filter(value -> value.custody().equals(source))
                    .filter(value -> FrontierV3CargoHandoffExecutor.exactMatch(itemEntity.getItem(), value)).findFirst();
            if (item.isEmpty()) return ExactCustodyObservation.NOT_MANAGED;
            FrontierV3CargoHandoffExecutor.bindWorldCarrier(itemEntity.getItem(), itemEntity.getUUID()); itemEntity.setItem(itemEntity.getItem());
            return submitExactCustody(runtime, "world-retoss", item.orElseThrow().id().value(),
                    new io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged(item.orElseThrow().id(), source,
                            new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.WorldCarrier(itemEntity.getUUID())));
        }
        FrontierV3CargoHandoffExecutor.bindWorldCarrier(itemEntity.getItem(), itemEntity.getUUID()); itemEntity.setItem(itemEntity.getItem());
        return submitExactCustody(runtime, "player-toss", item.orElseThrow().id().value(),
                new io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged(item.orElseThrow().id(), item.orElseThrow().custody(),
                        new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.WorldCarrier(itemEntity.getUUID())));
    }

    private static ExactCustodyObservation submitExactCustody(FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime,
                                                               String phase, String id, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElse(null);
        if (checkpoint == null) return ExactCustodyObservation.REJECTED;
        CommandId commandId = new CommandId("executor:" + phase + "-" + id.replace(':', '-') + "-r" + checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload)).orElse(null);
        return result instanceof CommandResult.Accepted ? ExactCustodyObservation.ACCEPTED : ExactCustodyObservation.REJECTED;
    }

    public enum ExactCustodyObservation { NOT_MANAGED, ACCEPTED, REJECTED }

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

    /** Routes a real blast either to its active v3 intent or to the ordinary external-effect observer. */
    public static boolean observeExplosion(ServerLevel level, java.util.List<BlockPos> affected, java.util.List<Entity> entities) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(affected, "affected blocks");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        return observeExplosion(level, runtime, affected, entities);
    }

    /** One shared server-thread bridge for the production host and real-world integration proofs. */
    static boolean observeExplosion(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    java.util.List<BlockPos> affected, java.util.List<Entity> entities) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(affected, "affected blocks");
        java.util.Optional<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId> managed = FrontierV3ExplosionExecutionScope.currentIntent();
        for (Entity entity : entities) {
            CargoCarrierInteraction released = releaseCargoCarrier(level, runtime, entity, java.util.Optional.empty());
            if (released == CargoCarrierInteraction.REJECTED) {
                runtime.quarantine(new IllegalStateException("explosion cannot durably release one HOT cargo carrier"));
                return false;
            }
            if (managed.isEmpty()) captureCargoCarrierImpact(level, runtime, entity, "external explosion");
            if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.QUARANTINED) return false;
        }
        return managed.isPresent() ? FrontierV3ExplosionExecutor.observeDetonation(level, runtime, managed.orElseThrow(), affected, entities)
                : FrontierV3PhysicalObservationExecutor.captureExternalExplosion(level, runtime, affected);
    }

    /** Retains a released chest cart even if its HOT lease was released by an earlier player interaction. */
    private static void captureCargoCarrierImpact(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                  Entity entity, String cause) {
        if (!(entity instanceof MinecartChest)) return;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) {
            runtime.quarantine(new IllegalStateException(cause + " has no canonical state"));
            return;
        }
        if (!state.inventory().worldCarrierItems().containsKey(entity.getUUID())) return;
        try {
            FrontierV3CargoCarrierImpactLedger.get(level).capture(level.getGameTime(), entity, state);
        } catch (RuntimeException error) {
            runtime.quarantine(error);
        }
    }

    static boolean enabled() { return Boolean.getBoolean(ENABLED_PROPERTY); }
}
