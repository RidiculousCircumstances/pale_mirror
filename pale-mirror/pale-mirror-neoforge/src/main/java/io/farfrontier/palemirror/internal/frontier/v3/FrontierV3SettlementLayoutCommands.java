package io.farfrontier.palemirror.internal.frontier.v3;

import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3AuthoringDrafts.*;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.buildings.*;

import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.GameTestAddMarkerDebugPayload;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Settlement-plan drafts use the production compiler/validator and the shared authoring files. */
final class FrontierV3SettlementLayoutCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        var branch =
                Commands.literal("layout")
                        .then(
                                Commands.literal("export")
                                        .then(
                                                Commands.argument(
                                                                "settlement",
                                                                net.minecraft.commands.arguments
                                                                        .ResourceLocationArgument
                                                                        .id())
                                                        .then(
                                                                Commands.argument(
                                                                                "draft",
                                                                                StringArgumentType
                                                                                        .word())
                                                                        .executes(
                                                                                c ->
                                                                                        run(
                                                                                                c,
                                                                                                () ->
                                                                                                        exportLayout(
                                                                                                                c))))));
        branch.then(
                Commands.literal("preview")
                        .then(
                                Commands.argument("draft", StringArgumentType.word())
                                        .then(
                                                Commands.argument(
                                                                "structure",
                                                                net.minecraft.commands.arguments
                                                                        .ResourceLocationArgument
                                                                        .id())
                                                        .executes(
                                                                c ->
                                                                        run(
                                                                                c,
                                                                                () ->
                                                                                        previewLayoutBuilding(
                                                                                                c))))));
        for (var operation : List.of("validate", "overview", "overlay", "connect"))
            branch.then(
                    Commands.literal(operation)
                            .then(
                                    Commands.argument("draft", StringArgumentType.word())
                                            .executes(
                                                    c ->
                                                            run(
                                                                    c,
                                                                    () ->
                                                                            inspectLayout(
                                                                                    c,
                                                                                    operation)))));
        branch.then(
                Commands.literal("street")
                        .then(
                                Commands.argument("draft", StringArgumentType.word())
                                        .then(
                                                Commands.argument(
                                                                "from", BlockPosArgument.blockPos())
                                                        .then(
                                                                Commands.argument(
                                                                                "to",
                                                                                BlockPosArgument
                                                                                        .blockPos())
                                                                        .executes(
                                                                                c ->
                                                                                        run(
                                                                                                c,
                                                                                                () ->
                                                                                                        editLayoutStreet(
                                                                                                                c)))))));
        branch.then(
                Commands.literal("move")
                        .then(
                                Commands.argument("draft", StringArgumentType.word())
                                        .then(
                                                Commands.argument(
                                                                "structure",
                                                                net.minecraft.commands.arguments
                                                                        .ResourceLocationArgument
                                                                        .id())
                                                        .then(
                                                                Commands.argument(
                                                                                "position",
                                                                                BlockPosArgument
                                                                                        .blockPos())
                                                                        .then(
                                                                                Commands.argument(
                                                                                                "front",
                                                                                                StringArgumentType
                                                                                                        .word())
                                                                                        .executes(
                                                                                                c ->
                                                                                                        run(
                                                                                                                c,
                                                                                                                () ->
                                                                                                                        moveLayoutBuilding(
                                                                                                                                c))))))));
        return branch;
    }

    private static String previewLayoutBuilding(CommandContext<CommandSourceStack> c)
            throws Exception {
        var player = c.getSource().getPlayerOrException();
        var plan =
                SettlementLayoutFormat.decode(
                        read(directory(c).resolve("settlement.properties"), 2_097_152));
        var runtime = FrontierV3ServerLifecycle.runtimeFor(c.getSource().getServer());
        if (runtime == null) throw new IllegalArgumentException("no active v3 world");
        var bootstrap = runtime.decodedState().orElseThrow().bootstrap();
        SettlementLayoutValidation.validate(plan, bootstrap.terrain(), bootstrap.bounds());
        var id =
                new SubjectId(
                        net.minecraft.commands.arguments.ResourceLocationArgument.getId(
                                        c, "structure")
                                .toString());
        var building =
                plan.buildings().stream()
                        .filter(s -> s.id().equals(id))
                        .findFirst()
                        .orElseThrow(
                                () -> new IllegalArgumentException("building absent from draft"));
        var models =
                new LinkedHashMap<
                        net.minecraft.core.BlockPos,
                        net.minecraft.world.level.block.state.BlockState>();
        for (var cell : BuildingSpatialPlan.of(building).blocks().values()) {
            var p = cell.position();
            var position = new net.minecraft.core.BlockPos(p.x(), p.y(), p.z());
            if (player.distanceToSqr(p.x() + .5, p.y() + .5, p.z() + .5) > 128 * 128)
                throw new IllegalArgumentException(
                        "entire preview building must be within 128 blocks");
            models.put(position, FrontierV3GrayboxExecutor.material(cell.material()));
        }
        for (var mount : building.definition().workplaces().values()) {
            var p = building.placement().world(mount.socket().offset(0, 1, 0));
            models.put(
                    new net.minecraft.core.BlockPos(p.x(), p.y(), p.z()),
                    FrontierV3FixedContainerProviders.equipmentState(
                            mount.equipmentType(), building.placement().direction(mount.facing())));
        }
        var entries =
                models.entrySet().stream()
                        .map(
                                e ->
                                        new io.farfrontier.palemirror.internal.network
                                                .BuildingPreviewPayload.Model(
                                                e.getKey(), e.getValue()))
                        .toList();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player,
                new io.farfrontier.palemirror.internal.network.BuildingPreviewPayload(
                        player.level().dimension().location(), entries));
        return "Draft building model for 30 seconds, "
                + entries.size()
                + " registered blocks; no blocks, inventories or entities changed.";
    }

    private static String exportLayout(CommandContext<CommandSourceStack> c) throws Exception {
        var runtime = FrontierV3ServerLifecycle.runtimeFor(c.getSource().getServer());
        if (runtime == null) throw new IllegalArgumentException("no active v3 world");
        var id =
                new SubjectId(
                        net.minecraft.commands.arguments.ResourceLocationArgument.getId(
                                        c, "settlement")
                                .toString());
        var home =
                runtime.decodedState().orElseThrow().bootstrap().settlements().stream()
                        .filter(s -> s.id().equals(id))
                        .findFirst()
                        .orElseThrow();
        var target = directory(c);
        if (Files.exists(target))
            throw new IllegalArgumentException("draft exists; preserve it or choose another name");
        Files.createDirectories(target);
        var plan = new SettlementLayoutPlanner.Result(home.structures(), home.layout());
        write(target.resolve("settlement.properties"), SettlementLayoutFormat.encode(plan));
        write(
                target.resolve("overview.svg"),
                SettlementLayoutFormat.overview(plan).getBytes(StandardCharsets.UTF_8));
        return "Exported settlement plan and overview.svg to "
                + target
                + ". Live settlement unchanged.";
    }

    private static String editLayoutStreet(CommandContext<CommandSourceStack> c) throws Exception {
        var file = directory(c).resolve("settlement.properties");
        var plan = SettlementLayoutFormat.decode(read(file, 2_097_152));
        var a = BlockPosArgument.getBlockPos(c, "from");
        var b = BlockPosArgument.getBlockPos(c, "to");
        long span = Math.abs((long) b.getX() - a.getX()) + Math.abs((long) b.getZ() - a.getZ());
        if (span >= TraversalTopology.MAX_NODES)
            throw new IllegalArgumentException("draft street exceeds the bounded segment length");
        var path =
                FrontierRouteNetwork.expandWaypoints(
                        List.of(
                                new BlockPosition(a.getX(), a.getY(), a.getZ()),
                                new BlockPosition(b.getX(), b.getY(), b.getZ())));
        var cells = new LinkedHashSet<>(plan.layout().paving());
        int half = SettlementLayoutRules.standard().streetHalfWidth();
        for (var p : path)
            for (int dx = -half; dx <= half; dx++)
                for (int dz = -half; dz <= half; dz++) cells.add(p.offset(dx, 0, dz));
        var l = plan.layout();
        var next =
                new SettlementLayoutPlanner.Result(
                        plan.buildings(),
                        new SettlementLayout(
                                l.settlementId(),
                                l.revision(),
                                l.scheme(),
                                l.style(),
                                l.plots(),
                                cells,
                                l.extensionFronts()));
        write(file, SettlementLayoutFormat.encode(next));
        return "Draft street added; validate terrain, fabric and connectivity before promotion.";
    }

    private static String moveLayoutBuilding(CommandContext<CommandSourceStack> c)
            throws Exception {
        var values =
                new TreeMap<>(
                        BuildingDefinitionFormat.properties(
                                read(directory(c).resolve("settlement.properties"), 2_097_152),
                                2_097_152));
        var id =
                net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "structure")
                        .toString();
        var position = BlockPosArgument.getBlockPos(c, "position");
        var facing =
                FacilityFacing.valueOf(
                        StringArgumentType.getString(c, "front").toUpperCase(Locale.ROOT));
        int count = Integer.parseInt(values.get("count"));
        if (count < 1 || count > SettlementLayout.MAX_PLOTS)
            throw new IllegalArgumentException("unbounded draft building count");
        boolean found = false;
        for (int i = 0; i < count; i++)
            if (id.equals(values.get("building." + i + ".id"))) {
                values.put(
                        "building." + i + ".anchor",
                        position.getX() + "," + position.getY() + "," + position.getZ());
                values.put("building." + i + ".facing", facing.name());
                found = true;
                break;
            }
        if (!found) throw new IllegalArgumentException("building absent from draft");
        write(
                directory(c).resolve("settlement.properties"),
                BuildingDefinitionFormat.encodeProperties(values));
        return "Draft building moved; validate its plot and street frontage before promotion. Live"
                + " settlement unchanged.";
    }

    private static String inspectLayout(CommandContext<CommandSourceStack> c, String operation)
            throws Exception {
        var plan =
                SettlementLayoutFormat.decode(
                        read(directory(c).resolve("settlement.properties"), 2_097_152));
        if (operation.equals("connect")) {
            var runtime = FrontierV3ServerLifecycle.runtimeFor(c.getSource().getServer());
            if (runtime == null) throw new IllegalArgumentException("no active v3 world");
            plan =
                    SettlementLayoutPlanner.connect(
                            plan, runtime.decodedState().orElseThrow().bootstrap().terrain());
            write(
                    directory(c).resolve("settlement.properties"),
                    SettlementLayoutFormat.encode(plan));
        }
        var active = FrontierV3ServerLifecycle.runtimeFor(c.getSource().getServer());
        if (active == null) throw new IllegalArgumentException("no active v3 world");
        var bootstrap = active.decodedState().orElseThrow().bootstrap();
        SettlementLayoutValidation.validate(plan, bootstrap.terrain(), bootstrap.bounds());
        write(
                directory(c).resolve("overview.svg"),
                SettlementLayoutFormat.overview(plan).getBytes(StandardCharsets.UTF_8));
        if (operation.equals("overlay")) {
            var player = c.getSource().getPlayerOrException();
            int markers = 0;
            for (var building : plan.buildings()) {
                var box = building.placement().volume(building.definition().parcel());
                for (var point :
                        List.of(box.min(), box.max().offset(-1, 0, -1), building.anchor())) {
                    if (player.distanceToSqr(point.x() + .5, point.y() + .5, point.z() + .5)
                            > 128 * 128) continue;
                    player.connection.send(
                            new ClientboundCustomPayloadPacket(
                                    new GameTestAddMarkerDebugPayload(
                                            new net.minecraft.core.BlockPos(
                                                    point.x(), point.y(), point.z()),
                                            0xAA46B67A,
                                            building.id().value(),
                                            30_000)));
                    markers++;
                }
            }
            return "Validated layout; "
                    + markers
                    + " bounded requesting-client plot markers. No blocks changed.";
        }
        return "Layout valid: "
                + plan.layout().scheme()
                + ", "
                + plan.buildings().size()
                + " buildings, "
                + plan.layout().paving().size()
                + " paving cells; overview.svg refreshed.";
    }

    @FunctionalInterface
    private interface Action {
        String run() throws Exception;
    }

    private static int run(CommandContext<CommandSourceStack> c, Action action) {
        try {
            var result = action.run();
            c.getSource().sendSuccess(() -> Component.literal(result), false);
            return 1;
        } catch (Exception failure) {
            c.getSource()
                    .sendFailure(Component.literal("Settlement layout: " + failure.getMessage()));
            return 0;
        }
    }

    private FrontierV3SettlementLayoutCommands() {}
}
