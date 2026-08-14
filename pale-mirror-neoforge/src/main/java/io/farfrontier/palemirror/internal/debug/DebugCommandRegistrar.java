package io.farfrontier.palemirror.internal.debug;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Operator-only command tree; mutation endpoints delegate to the isolated runtime debug controller. */
public final class DebugCommandRegistrar {
    private DebugCommandRegistrar() { }

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug").requires(source -> source.hasPermission(4));
        LiteralArgumentBuilder<CommandSourceStack> discovery = Commands.literal("discovery");
        discovery.then(Commands.literal("status").executes(context -> success(context, runtime(context).debug().discoveryStatus())));
        discovery.then(Commands.literal("auto").executes(context -> success(context,
                runtime(context).debug().discoveryMode(RuntimeDebugService.DiscoveryMode.AUTO))));
        discovery.then(Commands.literal("manual").executes(context -> success(context,
                runtime(context).debug().discoveryMode(RuntimeDebugService.DiscoveryMode.MANUAL))));
        debug.then(discovery);

        LiteralArgumentBuilder<CommandSourceStack> settlements = Commands.literal("settlements");
        settlements.then(Commands.literal("list").executes(context -> withPlayer(context,
                player -> components(context, runtime(context).debug().settlementLocations(player)))));
        settlements.then(Commands.literal("nearest").executes(context -> withPlayer(context,
                player -> success(context, runtime(context).debug().nearestSettlement(player)))));
        settlements.then(Commands.literal("bind-nearest").executes(context -> withPlayer(context,
                player -> action(context, runtime(context).debug().bindNearest(player)))));
        settlements.then(Commands.literal("bind").then(Commands.argument("id", ResourceLocationArgument.id())
                .executes(context -> action(context, runtime(context).debug().bind(new WorldObjectId(
                        ResourceLocationArgument.getId(context, "id").toString()))))));
        debug.then(settlements);

        debug.then(Commands.literal("mines").then(Commands.literal("list").executes(context -> withPlayer(context,
                player -> components(context, runtime(context).debug().mineLocations(player))))));
        debug.then(Commands.literal("objects").then(Commands.literal("list").executes(context -> withPlayer(context,
                player -> components(context, runtime(context).debug().objectLocations(player))))));

        LiteralArgumentBuilder<CommandSourceStack> teleport = Commands.literal("tp");
        teleport.then(teleportTarget("settlement", (controller, player, id) -> controller.teleportSettlement(player, id)));
        teleport.then(teleportTarget("mine", (controller, player, id) -> controller.teleportMine(player, id)));
        teleport.then(teleportTarget("object", (controller, player, id) -> controller.teleportObject(player, id)));
        debug.then(teleport);

        LiteralArgumentBuilder<CommandSourceStack> visualAudit = Commands.literal("visual-audit");
        visualAudit.then(Commands.literal("list").then(Commands.argument("settlement", ResourceLocationArgument.id())
                .executes(context -> components(context, runtime(context).debug().visualAuditViews(new WorldObjectId(
                        ResourceLocationArgument.getId(context, "settlement").toString()))))));
        visualAudit.then(Commands.literal("tp").then(Commands.argument("settlement", ResourceLocationArgument.id())
                .then(Commands.argument("view", StringArgumentType.word()).executes(context -> withPlayer(context,
                        player -> action(context, runtime(context).debug().teleportVisualAudit(player,
                                new WorldObjectId(ResourceLocationArgument.getId(context, "settlement").toString()),
                                StringArgumentType.getString(context, "view"))))))));
        debug.then(visualAudit);

        debug.then(Commands.literal("region").then(Commands.literal("status").executes(context ->
                success(context, runtime(context).debug().regionStatus()))));
        debug.then(Commands.literal("verify").executes(context -> success(context, runtime(context).debug().verify())));
        debug.then(Commands.literal("trigger").then(Commands.literal("infection").executes(context ->
                action(context, runtime(context).debug().triggerPrimaryInfection()))));

        LiteralArgumentBuilder<CommandSourceStack> markers = Commands.literal("markers");
        markers.then(Commands.literal("status").executes(context -> markerCommand(context, null)));
        markers.then(Commands.literal("on").executes(context -> markerCommand(context, true)));
        markers.then(Commands.literal("off").executes(context -> markerCommand(context, false)));
        debug.then(markers);

        LiteralArgumentBuilder<CommandSourceStack> reset = Commands.literal("reset");
        reset.then(Commands.literal("preview").executes(context -> action(context,
                runtime(context).debug().resetPreview(operatorId(context.getSource())))));
        reset.then(Commands.literal("confirm").then(Commands.argument("token", StringArgumentType.word())
                .executes(context -> action(context, runtime(context).debug().resetConfirm(
                        operatorId(context.getSource()), StringArgumentType.getString(context, "token"))))));
        debug.then(reset);
        root.then(debug);
    }

    private static int markerCommand(CommandContext<CommandSourceStack> context, Boolean enabled) {
        return withPlayer(context, player -> success(context, enabled == null
                ? runtime(context).debug().zoneMarkerStatus(player)
                : runtime(context).debug().zoneMarkers(player, enabled)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> teleportTarget(String kind, TeleportOperation operation) {
        return Commands.literal(kind).then(Commands.argument("id", ResourceLocationArgument.id()).executes(context ->
                withPlayer(context, player -> action(context, operation.apply(runtime(context).debug(), player,
                        new WorldObjectId(ResourceLocationArgument.getId(context, "id").toString()))))));
    }

    private static int withPlayer(CommandContext<CommandSourceStack> context,
                                  java.util.function.ToIntFunction<ServerPlayer> operation) {
        try {
            return operation.applyAsInt(context.getSource().getPlayerOrException());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            context.getSource().sendFailure(Component.literal("This visual/location command requires an in-game operator."));
            return 0;
        }
    }

    private static PaleMirrorRuntime runtime(CommandContext<CommandSourceStack> context) {
        return PaleMirrorRuntime.forServer(context.getSource().getServer());
    }

    private static int success(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int components(CommandContext<CommandSourceStack> context, java.util.List<Component> messages) {
        messages.forEach(message -> context.getSource().sendSuccess(() -> message, false));
        return messages.size();
    }

    private static int action(CommandContext<CommandSourceStack> context, RuntimeDebugService.ActionResult result) {
        if (result.success()) context.getSource().sendSuccess(() -> Component.literal(result.message()), true);
        else context.getSource().sendFailure(Component.literal(result.message()));
        return result.success() ? 1 : 0;
    }

    private static UUID operatorId(CommandSourceStack source) {
        return source.getEntity() == null
                ? UUID.nameUUIDFromBytes(("command-source:" + source.getTextName()).getBytes(StandardCharsets.UTF_8))
                : source.getEntity().getUUID();
    }

    @FunctionalInterface
    private interface TeleportOperation {
        RuntimeDebugService.ActionResult apply(RuntimeDebugController controller, ServerPlayer player, WorldObjectId id);
    }
}
