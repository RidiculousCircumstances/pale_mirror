package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.CargoCarrierReleased;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRecoveryConfiguration;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierReadabilityPlan;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.internal.presentation.PaleMirrorPlayerPresentation;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle.*;

/** Minecraft-facing player, cargo and world-effect interactions for the active runtime. */
final class FrontierV3ServerPhysicalInteractions {
    private FrontierV3ServerPhysicalInteractions() { }

    public static CargoCarrierInteraction releaseCargoCarrier(ServerLevel level, ServerPlayer player, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level)) return CargoCarrierInteraction.NOT_MANAGED;
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) {
            return FrontierV3CargoCarrierExecutor.hasDeclaration(entity) ? CargoCarrierInteraction.REJECTED : CargoCarrierInteraction.NOT_MANAGED;
        }
        return releaseCargoCarrier(level, runtime, entity, java.util.Optional.of(player.getUUID()));
    }
    public static boolean presentObjectBoard(ServerLevel level, ServerPlayer player, Entity entity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(entity, "entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        String owner = entity.getPersistentData().getString("pale_mirror.frontier_v3.board_owner");
        if (owner.isBlank()) return false;
        var board = FrontierReadabilityPlan.compile(state).boards().get(new io.farfrontier.palemirror.frontier.v3.api.SubjectId(owner));
        if (board == null || !FrontierV3ObjectBoardExecutor.isCurrentOwnedBoard(level, entity, board)) return false;
        PaleMirrorPlayerPresentation.inspect(player, "frontier-v3:board:" + owner, FrontierV3ObjectBoardCard.fromBoard(board));
        return true;
    }
    static CargoCarrierInteraction releaseCargoCarrier(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                Entity entity, java.util.Optional<java.util.UUID> observerPlayerId) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity"); Objects.requireNonNull(observerPlayerId, "observer player id");
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) {
            return FrontierV3CargoCarrierExecutor.hasDeclaration(entity) ? CargoCarrierInteraction.REJECTED : CargoCarrierInteraction.NOT_MANAGED;
        }
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return CargoCarrierInteraction.REJECTED;
        var lease = FrontierV3CargoCarrierExecutor.activeLease(state, entity);
        if (lease.isEmpty()) {
            // An accepted handoff can survive a torn save of the old cart declaration.
            // Canonical world custody allows ordinary interaction, not a second release.
            if (entity instanceof MinecartChest && FrontierV3CargoCarrierExecutor.hasWorldCustody(state, entity.getUUID())) {
                if (FrontierV3CargoCarrierExecutor.hasDeclaration(entity)) {
                    if (!FrontierV3CargoCarrierExecutor.hasCurrentDeclaration(state, entity)) return CargoCarrierInteraction.REJECTED;
                    if (!FrontierV3CargoCarrierProvenance.restore(state.inventory().items(), entity.getUUID(), (MinecartChest) entity)) {
                        return CargoCarrierInteraction.REJECTED;
                    }
                    FrontierV3CargoCarrierExecutor.relinquishDeclaration(entity);
                }
                return CargoCarrierInteraction.NOT_MANAGED;
            }
            return FrontierV3CargoCarrierExecutor.hasDeclaration(entity)
                    ? CargoCarrierInteraction.REJECTED : CargoCarrierInteraction.NOT_MANAGED;
        }
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElse(null);
        if (checkpoint == null) return CargoCarrierInteraction.REJECTED;
        CommandId commandId = FrontierV3CommandIds.physical("cargo-carrier-release", checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId),
                new CargoCarrierReleased(lease.orElseThrow().id(), io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.logistics(lease.orElseThrow()).cargoId(), entity.getUUID(), observerPlayerId)))
                .orElse(null);
        if (result instanceof CommandResult.Accepted) {
            // activeLease validated this exact physical carrier without mutation. The
            // server-thread handoff must commit before captions or item provenance change.
            // Keep the pre-command state solely to validate the already admitted contents.
            if (!FrontierV3CargoCarrierExecutor.markReleasedCarrier(state, lease.orElseThrow(), entity)) {
                throw new IllegalStateException("accepted cargo handoff lost its validated physical carrier");
            }
            FrontierV3CargoCarrierExecutor.relinquishDeclaration(entity);
            return CargoCarrierInteraction.RELEASED;
        }
        return CargoCarrierInteraction.REJECTED;
    }
    public static void observeTerminalVehicleDamage(ServerLevel level, Entity entity, DamageSource source) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity"); Objects.requireNonNull(source, "damage source");
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return;
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        observeTerminalVehicleDamage(level, runtime, entity, source);
    }
    public static boolean isLeasedRoadCargoCarrier(Entity entity) {
        return entity instanceof MinecartChest
                && entity.getPersistentData().contains(FrontierV3CargoCarrierExecutor.LEASE_KEY)
                && entity.getPersistentData().contains(FrontierV3CargoCarrierExecutor.CARGO_KEY);
    }
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
    public static ExactCustodyObservation observeExactItemPickup(ServerLevel level, ServerPlayer player, ItemEntity itemEntity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(itemEntity, "item entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return ExactCustodyObservation.NOT_MANAGED;
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
    public static ExactCustodyObservation observeExactItemToss(ServerLevel level, ServerPlayer player, ItemEntity itemEntity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(itemEntity, "item entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return ExactCustodyObservation.NOT_MANAGED;
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
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElse(null);
        if (checkpoint == null) return ExactCustodyObservation.REJECTED;
        CommandId commandId = FrontierV3CommandIds.physical(phase, checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload)).orElse(null);
        return result instanceof CommandResult.Accepted ? ExactCustodyObservation.ACCEPTED : ExactCustodyObservation.REJECTED;
    }
    public static PlayerBreakDisposition observePlayerBreakPacket(ServerLevel level, BlockPos position, ServerPlayer player) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(position, "position"); Objects.requireNonNull(player, "player");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return PlayerBreakDisposition.UNMANAGED;
        String cause = "player:" + player.getUUID();
        FrontierV3InfectionOverlayExecutor.BlockBreakObservation infection = FrontierV3InfectionOverlayExecutor.observeBlockBreak(runtime, level, position, cause);
        if (infection == FrontierV3InfectionOverlayExecutor.BlockBreakObservation.REJECTED) return PlayerBreakDisposition.REJECTED;
        if (infection == FrontierV3InfectionOverlayExecutor.BlockBreakObservation.ACCEPTED) {
            FrontierV3PlayerBreakDisposition.accept(level.getServer(), player.getUUID(), position); return PlayerBreakDisposition.ACCEPTED;
        }
        FrontierV3ResourceSiteExecutor.BlockBreakObservation resource = FrontierV3ResourceSiteExecutor.observePlayerBlockBreak(runtime, level, position, player);
        if (resource == FrontierV3ResourceSiteExecutor.BlockBreakObservation.REJECTED) return PlayerBreakDisposition.REJECTED;
        if (resource == FrontierV3ResourceSiteExecutor.BlockBreakObservation.ACCEPTED) {
            FrontierV3PlayerBreakDisposition.accept(level.getServer(), player.getUUID(), position); return PlayerBreakDisposition.ACCEPTED;
        }
        FrontierV3GrayboxExecutor.BlockBreakObservation graybox = FrontierV3GrayboxExecutor.observeBlockBreak(runtime, level, position, cause);
        if (graybox == FrontierV3GrayboxExecutor.BlockBreakObservation.REJECTED) return PlayerBreakDisposition.REJECTED;
        if (graybox == FrontierV3GrayboxExecutor.BlockBreakObservation.ACCEPTED) {
            FrontierV3PlayerBreakDisposition.accept(level.getServer(), player.getUUID(), position); return PlayerBreakDisposition.ACCEPTED;
        }
        return PlayerBreakDisposition.UNMANAGED;
    }
    public static boolean rejectBlockBreak(ServerLevel level, BlockPos position, ServerPlayer player) {
        return observePlayerBreakPacket(level, position, player) == PlayerBreakDisposition.REJECTED;
    }
    public static boolean acceptedPlayerBreak(ServerLevel level, BlockPos position, ServerPlayer player) {
        return FrontierV3PlayerBreakDisposition.accepted(level.getServer(), player.getUUID(), position);
    }
    public static boolean consumeAcceptedPlayerBreak(ServerLevel level, BlockPos position, ServerPlayer player) {
        return FrontierV3PlayerBreakDisposition.consume(level.getServer(), player.getUUID(), position);
    }
    /** Post-Vanilla crop result, including a cancelled/no-op removal. */
    public static void observePlayerBreakResult(ServerLevel level, BlockPos position, ServerPlayer player) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                && FrontierV3PhysicalWorld.isPhysical(level))
            FrontierV3ResourceFieldPlayerBreakExecutor.observePlayerAction(level, runtime, position, player);
    }
    public static void clearPlayerBreakDispositions(net.minecraft.server.MinecraftServer server) {
        FrontierV3PlayerBreakDisposition.clear(server);
    }
    public static boolean observeExplosion(ServerLevel level, net.minecraft.world.level.Explosion explosion,
                                           java.util.List<BlockPos> affected, java.util.List<Entity> entities) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(affected, "affected blocks");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        return observeExplosion(level, runtime, explosion, affected, entities);
    }
    static boolean observeExplosion(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    net.minecraft.world.level.Explosion explosion, java.util.List<BlockPos> affected, java.util.List<Entity> entities) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(affected, "affected blocks");
        Entity directSource = explosion == null ? null : explosion.getDirectSourceEntity();
        java.util.Optional<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId> managed = FrontierV3ExplosionExecutionScope.currentIntent()
                .or(() -> FrontierV3BomberBomb.intentFor(explosion));
        for (Entity entity : entities) {
            CargoCarrierInteraction released = releaseCargoCarrier(level, runtime, entity, java.util.Optional.empty());
            if (released == CargoCarrierInteraction.REJECTED) {
                runtime.quarantine(new IllegalStateException("explosion cannot durably release one HOT cargo carrier"));
                return false;
            }
            if (managed.isEmpty()) captureCargoCarrierImpact(level, runtime, entity, "external explosion");
            if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.QUARANTINED) return false;
        }
        boolean resourceSite = managed.map(intent -> FrontierV3ResourceSiteExplosionExecutor.captureManaged(level, runtime, intent, affected))
                .orElseGet(() -> FrontierV3ResourceSiteExplosionExecutor.captureExternal(level, runtime, affected));
        boolean ordinary = managed.map(intent -> FrontierV3ExplosionExecutor.observeDetonation(level, runtime, intent, directSource, affected, entities))
                .orElseGet(() -> FrontierV3PhysicalObservationExecutor.captureExternalExplosion(level, runtime, affected));
        return resourceSite || ordinary;
    }
    private static void captureCargoCarrierImpact(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                  Entity entity, String cause) {
        if (!(entity instanceof MinecartChest)) return;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) {
            runtime.quarantine(new IllegalStateException(cause + " has no canonical state"));
            return;
        }
        if (!FrontierV3CargoCarrierExecutor.hasWorldCustody(state, entity.getUUID())) return;
        try {
            FrontierV3CargoCarrierImpactLedger.get(level).capture(level.getGameTime(), entity, state);
        } catch (RuntimeException error) {
            runtime.quarantine(error);
        }
    }
}
