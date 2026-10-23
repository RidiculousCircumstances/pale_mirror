package io.farfrontier.palemirror.internal.frontier.v3;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Administrative storage declarations; gameplay stock is never created or silently adopted. */
public final class FrontierV3StorageCommands {
    private FrontierV3StorageCommands() { }
    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("storage").requires(source -> source.hasPermission(4))
                .then(Commands.literal("create")
                    .then(Commands.argument("home", ResourceLocationArgument.id())
                    .then(Commands.argument("container", ResourceLocationArgument.id())
                    .then(Commands.argument("chest", BlockPosArgument.blockPos())
                    .then(Commands.argument("station_support", BlockPosArgument.blockPos())
                    .then(Commands.argument("items", StringArgumentType.string())
                    .then(Commands.argument("priority", IntegerArgumentType.integer(-1000, 1000))
                        .executes(FrontierV3StorageCommands::create))))))))
                .then(Commands.literal("configure")
                    .then(Commands.argument("container", ResourceLocationArgument.id())
                    .then(Commands.argument("items", StringArgumentType.string())
                    .then(Commands.argument("priority", IntegerArgumentType.integer(-1000, 1000))
                        .executes(FrontierV3StorageCommands::configure)))));
    }
    private static ResourceStorageFilter filter(String text) {
        if (text.equals("all")) return ResourceStorageFilter.all();
        if (text.equals("none")) return new ResourceStorageFilter(false, Set.of(), Map.of());
        return new ResourceStorageFilter(false, new HashSet<>(Arrays.asList(text.split(",", -1))), Map.of());
    }
    private static BlockPosition position(BlockPos value) { return new BlockPosition(value.getX(), value.getY(), value.getZ()); }
    private static int create(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source = context.getSource(); var level = source.getLevel();
        var chest = BlockPosArgument.getLoadedBlockPos(context, "chest");
        var support = BlockPosArgument.getLoadedBlockPos(context, "station_support");
        try {
            var runtime = FrontierV3ServerLifecycle.runtimeForNativeMutation(source.getServer());
            if (runtime == null || !FrontierV3PhysicalWorld.isPhysical(level)) throw new IllegalArgumentException("storage requires the active physical world");
            var state = runtime.decodedState().orElseThrow();
            if (!FrontierV3LoadedWorld.ready(level, chest.below()) || !level.getBlockState(chest).isAir()
                    || !level.getBlockState(chest.below()).isFaceSturdy(level, chest.below(), Direction.UP))
                throw new IllegalArgumentException("new warehouse needs empty loaded space on sturdy ground; existing stock is not adopted");
            var station = new SurfaceAnchor(position(support));
            if (FrontierV3StandingPosition.aboveExactFloor(level, support) == null
                    || !station.equals(KnownPedestrianGround.forFrontier(state).at(station.x(), station.z()))
                    || !io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN.contains(station, position(chest)))
                throw new IllegalArgumentException("storage station lacks known safe supporting geometry or interaction reach");
            var home = new SubjectId(ResourceLocationArgument.getId(context, "home").toString());
            var id = new SubjectId(ResourceLocationArgument.getId(context, "container").toString());
            var endpoint = new ShipmentEndpoint.Storage(home, id, station, Optional.empty(), station);
            var policy = new ResourceStoragePolicy(endpoint, filter(StringArgumentType.getString(context, "items")),
                    IntegerArgumentType.getInteger(context, "priority"), ResourceStorageRole.WAREHOUSE);
            var event = new ResourceStorageDeclared(new ContainerRecord(id, home, 27, ContainerPurpose.RESOURCE_STORAGE,
                    Optional.empty(), Optional.of(policy)), position(chest));
            event.apply(state, home); // Exact read-only admission before the ordinary registered command.
            FrontierV3CommandSubmission.submit(runtime, "storage-create", id.value(), event);
            source.sendSuccess(() -> Component.literal("Storage declared: " + id.value() + "; empty chest projection pending; no stock created."), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            source.sendFailure(Component.literal("Storage refused: " + rejected.getMessage())); return 0;
        }
    }
    private static int configure(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        try {
            var runtime = FrontierV3ServerLifecycle.runtimeForNativeMutation(source.getServer());
            if (runtime == null) throw new IllegalArgumentException("storage requires the active runtime");
            var state = runtime.decodedState().orElseThrow(); var id = new SubjectId(ResourceLocationArgument.getId(context, "container").toString());
            var record = state.inventory().containers().get(id);
            if (record == null) throw new IllegalArgumentException("unknown declared container");
            var old = record.storage().orElseThrow(() -> new IllegalArgumentException("container has no storage capability"));
            var policy = new ResourceStoragePolicy(old.endpoint(), filter(StringArgumentType.getString(context, "items")),
                    IntegerArgumentType.getInteger(context, "priority"), old.role());
            var event = new ResourceStoragePolicyChanged(id, old, policy); event.apply(state, record.ownerId());
            FrontierV3CommandSubmission.submit(runtime, "storage-configure", id.value(), event);
            source.sendSuccess(() -> Component.literal("Storage policy changed: " + id.value() + "; existing stock preserved."), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            source.sendFailure(Component.literal("Storage refused: " + rejected.getMessage())); return 0;
        }
    }
}
