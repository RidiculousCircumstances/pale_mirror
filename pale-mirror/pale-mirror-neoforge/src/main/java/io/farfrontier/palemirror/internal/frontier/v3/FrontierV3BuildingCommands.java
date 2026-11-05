package io.farfrontier.palemirror.internal.frontier.v3;

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

/**
 * Developer files and read-only bounded overlays. No paste, stock, execution or geometry writer.
 */
public final class FrontierV3BuildingCommands {
    private static final String ROOT = "/io/farfrontier/palemirror/frontier/v3/buildings/";
    private static final Set<String> EXTENSIONS = Set.of("properties", "blocks", "geometry", "nbt");

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("building")
                .requires(s -> s.hasPermission(4))
                .then(
                        Commands.literal("export")
                                .then(
                                        Commands.argument(
                                                        "structure",
                                                        net.minecraft.commands.arguments
                                                                .ResourceLocationArgument.id())
                                                .then(
                                                        Commands.argument(
                                                                        "draft",
                                                                        StringArgumentType.word())
                                                                .executes(
                                                                        c ->
                                                                                run(
                                                                                        c,
                                                                                        () ->
                                                                                                export(
                                                                                                        c))))))
                .then(
                        Commands.literal("set")
                                .then(
                                        Commands.argument("draft", StringArgumentType.word())
                                                .then(
                                                        Commands.argument(
                                                                        "key",
                                                                        StringArgumentType.word())
                                                                .then(
                                                                        Commands.argument(
                                                                                        "value",
                                                                                        StringArgumentType
                                                                                                .greedyString())
                                                                                .executes(
                                                                                        c ->
                                                                                                run(
                                                                                                        c,
                                                                                                        () ->
                                                                                                                set(
                                                                                                                        c)))))))
                .then(
                        Commands.literal("point")
                                .then(
                                        Commands.argument("draft", StringArgumentType.word())
                                                .then(
                                                        Commands.argument(
                                                                        "key",
                                                                        StringArgumentType.word())
                                                                .then(
                                                                        Commands.argument(
                                                                                        "position",
                                                                                        BlockPosArgument
                                                                                                .blockPos())
                                                                                .executes(
                                                                                        c ->
                                                                                                run(
                                                                                                        c,
                                                                                                        () ->
                                                                                                                point(
                                                                                                                        c)))))))
                .then(
                        Commands.literal("validate")
                                .then(
                                        Commands.argument("draft", StringArgumentType.word())
                                                .executes(c -> run(c, () -> validate(c)))))
                .then(
                        Commands.literal("overlay")
                                .then(
                                        Commands.argument("draft", StringArgumentType.word())
                                                .executes(c -> run(c, () -> overlay(c)))));
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
                    .sendFailure(Component.literal("Building authoring: " + failure.getMessage()));
            return 0;
        }
    }

    private static Path directory(CommandContext<CommandSourceStack> c) {
        var name = StringArgumentType.getString(c, "draft");
        if (!name.matches("[a-z0-9_-]{1,48}"))
            throw new IllegalArgumentException("draft needs a bounded simple filename");
        return c.getSource()
                .getServer()
                .getServerDirectory()
                .resolve("debug/pale-mirror/buildings")
                .resolve(name);
    }

    private static byte[] read(Path file, int maximum) throws java.io.IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file) || Files.size(file) > maximum)
            throw new IllegalArgumentException(
                    "missing, symbolic or oversized draft file: " + file.getFileName());
        return Files.readAllBytes(file);
    }

    private static void write(Path file, byte[] bytes) throws java.io.IOException {
        if (Files.isSymbolicLink(file) || Files.isSymbolicLink(file.getParent()))
            throw new IllegalArgumentException("symbolic draft path");
        var temporary = Files.createTempFile(file.getParent(), ".building-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String export(CommandContext<CommandSourceStack> c) throws Exception {
        var runtime = FrontierV3ServerLifecycle.runtimeFor(c.getSource().getServer());
        if (runtime == null) throw new IllegalArgumentException("no active v3 world");
        var state = runtime.decodedState().orElseThrow();
        var id =
                new SubjectId(
                        net.minecraft.commands.arguments.ResourceLocationArgument.getId(
                                        c, "structure")
                                .toString());
        var structure =
                state.bootstrap().settlements().stream()
                        .flatMap(s -> s.structures().stream())
                        .filter(s -> s.id().equals(id))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("unknown structure"));
        var target = directory(c);
        if (Files.exists(target))
            throw new IllegalArgumentException(
                    "draft already exists; preserve or choose another name");
        String asset = null;
        for (var path : BuildingCatalog.assetPaths())
            try (var input =
                    FrontierV3BuildingCommands.class.getResourceAsStream(
                            ROOT + path + ".properties")) {
                if (input != null
                        && BuildingDefinitionFormat.properties(input.readAllBytes())
                                .get("id")
                                .equals(structure.building().template().value())) asset = path;
            }
        if (asset == null)
            throw new IllegalArgumentException("selected structure has no authored source asset");
        Files.createDirectories(target);
        for (var extension : EXTENSIONS)
            try (var input =
                    FrontierV3BuildingCommands.class.getResourceAsStream(
                            ROOT + asset + '.' + extension)) {
                if (input == null)
                    throw new IllegalStateException("missing packaged asset " + extension);
                write(target.resolve("building." + extension), input.readAllBytes());
            }
        var manifest =
                Map.of(
                        "style",
                        structure.building().style().value(),
                        "template",
                        structure.building().template().value(),
                        "sha256",
                        structure.building().sha256(),
                        "anchor",
                        coordinates(structure.anchor()),
                        "facing",
                        structure.facing().name(),
                        "structure",
                        structure.id().value());
        write(
                target.resolve("placement.properties"),
                BuildingDefinitionFormat.encodeProperties(manifest));
        return "Exported "
                + structure.id().value()
                + " to "
                + target
                + ". Edit files or use set/point; validate does not activate a draft in the world.";
    }

    private static Map<String, String> markup(CommandContext<CommandSourceStack> c)
            throws Exception {
        return new TreeMap<>(
                BuildingDefinitionFormat.properties(
                        read(directory(c).resolve("building.properties"), 262144)));
    }

    private static BuildingPlacement placement(
            CommandContext<CommandSourceStack> c, Map<String, String> markup) throws Exception {
        var values =
                BuildingDefinitionFormat.properties(
                        read(directory(c).resolve("placement.properties"), 8192));
        var ref =
                new BuildingPlanRef(
                        new BuildingStyleId(values.get("style")),
                        new BuildingTemplateId(values.get("template")),
                        values.get("sha256"));
        BuildingCatalog.standard().require(ref.template());
        if (!BuildingCatalog.standard().require(ref.template()).sha256().equals(ref.sha256()))
            throw new IllegalArgumentException("draft source content changed; export again");
        return new BuildingPlacement(
                BuildingDefinitionFormat.worldPoint(values.get("anchor")),
                BuildingDefinitionFormat.point(markup.get("origin")),
                FacilityFacing.valueOf(markup.get("front")),
                FacilityFacing.valueOf(values.get("facing")));
    }

    private static String set(CommandContext<CommandSourceStack> c) throws Exception {
        var values = markup(c);
        var key = StringArgumentType.getString(c, "key");
        var value = StringArgumentType.getString(c, "value");
        if (!key.matches("[A-Za-z0-9_.-]{1,128}")
                || value.length() > 8192
                || value.indexOf('\n') >= 0)
            throw new IllegalArgumentException("unbounded property");
        values.put(key, value.equals("\"\"") ? "" : value);
        write(
                directory(c).resolve("building.properties"),
                BuildingDefinitionFormat.encodeProperties(values));
        return "Draft property updated; validate before exporting to the content catalogue.";
    }

    private static String point(CommandContext<CommandSourceStack> c) throws Exception {
        var values = markup(c);
        var key = StringArgumentType.getString(c, "key");
        if (!key.matches("[A-Za-z0-9_.-]{1,128}"))
            throw new IllegalArgumentException("invalid annotation key");
        var p = BlockPosArgument.getBlockPos(c, "position");
        var local = placement(c, values).local(new BlockPosition(p.getX(), p.getY(), p.getZ()));
        values.put(key, coordinates(local));
        write(
                directory(c).resolve("building.properties"),
                BuildingDefinitionFormat.encodeProperties(values));
        return key + '=' + coordinates(local) + " (local; zone maxima are exclusive)";
    }

    private static BuildingDefinition definition(CommandContext<CommandSourceStack> c)
            throws Exception {
        var dir = directory(c);
        var properties = read(dir.resolve("building.properties"), 262144);
        var blocks = read(dir.resolve("building.blocks"), 16777216);
        return BuildingDefinitionFormat.decode(
                BuildingDefinitionFormat.properties(properties),
                BuildingDefinitionFormat.blocks(blocks),
                BuildingCatalog.sha256(
                        properties,
                        blocks,
                        read(dir.resolve("building.nbt"), 16777216),
                        read(dir.resolve("building.geometry"), 4194304)));
    }

    private static String validate(CommandContext<CommandSourceStack> c) throws Exception {
        var definition = definition(c);
        FrontierV3BuildingAuthoring.requireNativeTemplate(
                read(directory(c).resolve("building.nbt"), 16777216), definition.blocks());
        var compiled = FrontierV3BuildingAuthoring.collisionFacts(definition.blocks());
        var shapes = BuildingCollisionCatalog.decode(compiled.getBytes(StandardCharsets.UTF_8));
        BuildingGeometryValidation.validate(
                definition,
                block ->
                        Optional.ofNullable(shapes.get(block))
                                .flatMap(
                                        io.farfrontier.palemirror.frontier.v3.model.geometry
                                                        .NavigationObservation
                                                ::cell));
        write(directory(c).resolve("building.geometry"), compiled.getBytes(StandardCharsets.UTF_8));
        return "Valid "
                + definition.id().value()
                + ": native registry, room connectivity, entrances, worker clearance and shared"
                + " navigation paths. No live changes applied.";
    }

    private static String overlay(CommandContext<CommandSourceStack> c) throws Exception {
        var player = c.getSource().getPlayerOrException();
        var definition = definition(c);
        var transform = placement(c, markup(c));
        var markers = new LinkedHashMap<BlockPosition, Marker>();
        definition
                .rooms()
                .forEach((id, r) -> corners(markers, r.volume(), "room:" + id, 0x994080FF));
        definition
                .zones()
                .forEach((id, z) -> corners(markers, z.volume(), z.use() + ":" + id, 0x99FFCC22));
        definition
                .passages()
                .forEach(
                        (id, p) ->
                                p.supports()
                                        .forEach(
                                                point ->
                                                        markers.put(
                                                                point.offset(0, 1, 0),
                                                                new Marker(
                                                                        "passage:" + id,
                                                                        p.publicAccess()
                                                                                ? 0x9900FF66
                                                                                : 0x99FF8800))));
        definition
                .workplaces()
                .forEach(
                        (id, w) -> {
                            markers.put(
                                    w.input().offset(0, 1, 0),
                                    new Marker("input:" + id, 0x9900FFFF));
                            markers.put(
                                    w.worker().offset(0, 1, 0),
                                    new Marker("worker:" + id, 0x99FF00FF));
                            corners(markers, w.equipment(), "equipment:" + id, 0x99FF3333);
                            corners(markers, w.maintenance(), "maintenance:" + id, 0x99FFAA33);
                        });
        if (markers.size() > 512)
            throw new IllegalArgumentException("overlay exceeds 512 markers; narrow the draft");
        if (!player.serverLevel().equals(c.getSource().getLevel()))
            throw new IllegalArgumentException("overlay requires the selected player world");
        for (var entry : markers.entrySet()) {
            var p = transform.world(entry.getKey());
            if (player.distanceToSqr(p.x() + .5, p.y() + .5, p.z() + .5) > 128 * 128)
                throw new IllegalArgumentException("overlay must be within 128 blocks");
        }
        markers.forEach(
                (local, marker) -> {
                    var p = transform.world(local);
                    player.connection.send(
                            new ClientboundCustomPayloadPacket(
                                    new GameTestAddMarkerDebugPayload(
                                            new net.minecraft.core.BlockPos(p.x(), p.y(), p.z()),
                                            marker.color(),
                                            marker.label(),
                                            30000)));
                });
        return "Building overlay: "
                + markers.size()
                + " markers for 30 seconds, visible only to you.";
    }

    private record Marker(String label, int color) {}

    private static void corners(
            Map<BlockPosition, Marker> out, BuildingVolume box, String label, int color) {
        for (int x : new int[] {box.min().x(), box.max().x() - 1})
            for (int y : new int[] {box.min().y(), box.max().y() - 1})
                for (int z : new int[] {box.min().z(), box.max().z() - 1})
                    out.put(new BlockPosition(x, y, z), new Marker(label, color));
    }

    private static String coordinates(BlockPosition p) {
        return p.x() + "," + p.y() + "," + p.z();
    }

    private FrontierV3BuildingCommands() {}
}
