package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
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
        var hall = state.bootstrap().settlements().stream().flatMap(home -> home.structures().stream())
                .filter(structure -> structure.id().equals(board.ownerId())
                        && structure.kind() == io.farfrontier.palemirror.frontier.v3.model.StructureKind.HALL).findFirst();
        var card = hall.isPresent() ? FrontierV3TownHallCard.from(state, hall.orElseThrow())
                : FrontierV3ObjectBoardCard.fromBoard(board);
        PaleMirrorPlayerPresentation.inspect(player, "frontier-v3:board:" + owner, card);
        return true;
    }
    static boolean presentTownHall(ServerLevel level, ServerPlayer player, BlockPos position) {
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null
                || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE
                || player.level() != level || !player.getMainHandItem().isEmpty()
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(position)) > 64.0
                || !level.hasChunkAt(position)) return false;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        var claim = FrontierV3GrayboxLedger.get(level).claim(position);
        if (claim == null || claim.deferred() || claim.targetTag()
                != io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE.wireTag()) return false;
        var hall = state.bootstrap().settlements().stream().flatMap(home -> home.structures().stream())
                .filter(structure -> structure.id().value().equals(claim.owner())
                        && structure.kind() == io.farfrontier.palemirror.frontier.v3.model.StructureKind.HALL).findFirst();
        if (hall.isEmpty() || !level.getBlockState(position).equals(FrontierV3GrayboxExecutor.material(
                io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial.valueOf(claim.material())))) return false;
        PaleMirrorPlayerPresentation.inspect(player, "frontier-v3:board:" + claim.owner(),
                FrontierV3TownHallCard.from(state, hall.orElseThrow()));
        return true;
    }
    static boolean presentResident(ServerLevel level, ServerPlayer player, Entity entity) {
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null
                || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE
                || entity.level() != level || player.level() != level || !player.getMainHandItem().isEmpty()
                || entity.distanceToSqr(player) > 64.0) return false;
        var state = runtime.decodedState().orElse(null);
        if (state == null || !FrontierV3ActorBodyController.recognizesRecordedBody(level, state, entity)) return false;
        String declared = entity.getPersistentData().getString(FrontierV3ActorCarrierComposition.ACTOR_KEY);
        var actor = new io.farfrontier.palemirror.frontier.v3.api.SubjectId(declared);
        if (state.humanPopulation().resident(actor) == null) return false;
        PaleMirrorPlayerPresentation.inspect(player, "frontier-v3:resident:" + actor.value(),
                FrontierV3ResidentCard.from(state, actor, runtime.executionView().orElseThrow().instant().ticks()));
        return true;
    }
    public static ExactCustodyObservation observeExactItemPickup(ServerLevel level, ServerPlayer player, ItemEntity itemEntity) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(itemEntity, "item entity");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return ExactCustodyObservation.NOT_MANAGED;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return ExactCustodyObservation.REJECTED;
        var carrierId = FrontierV3ExactItemPresentation.worldCarrierId(itemEntity.getItem());
        if (carrierId.isEmpty()) return ExactCustodyObservation.NOT_MANAGED;
        var source = new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.WorldCarrier(carrierId.orElseThrow());
        var item = state.inventory().items().values().stream().filter(value -> value.custody().equals(source))
                .filter(value -> FrontierV3ExactItemPresentation.exactMatch(itemEntity.getItem(), value)).findFirst();
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
                .filter(value -> FrontierV3ExactItemPresentation.exactMatch(itemEntity.getItem(), value)).findFirst();
        if (item.isEmpty()) {
            var carrierId = FrontierV3ExactItemPresentation.worldCarrierId(itemEntity.getItem());
            if (carrierId.isEmpty()) return ExactCustodyObservation.NOT_MANAGED;
            var source = new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.WorldCarrier(carrierId.orElseThrow());
            item = state.inventory().items().values().stream().filter(value -> value.custody().equals(source))
                    .filter(value -> FrontierV3ExactItemPresentation.exactMatch(itemEntity.getItem(), value)).findFirst();
            if (item.isEmpty()) return ExactCustodyObservation.NOT_MANAGED;
            FrontierV3ExactItemPresentation.bindWorldCarrier(itemEntity.getItem(), itemEntity.getUUID()); itemEntity.setItem(itemEntity.getItem());
            return submitExactCustody(runtime, "world-retoss", item.orElseThrow().id().value(),
                    new io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged(item.orElseThrow().id(), source,
                            new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.WorldCarrier(itemEntity.getUUID())));
        }
        FrontierV3ExactItemPresentation.bindWorldCarrier(itemEntity.getItem(), itemEntity.getUUID()); itemEntity.setItem(itemEntity.getItem());
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
        boolean ordinary = managed.map(intent -> FrontierV3ExplosionExecutor.observeDetonation(level, runtime, intent, directSource, affected, entities))
                .orElseGet(() -> FrontierV3PhysicalObservationExecutor.captureExternalExplosion(level, runtime, affected));
        return ordinary;
    }
}
