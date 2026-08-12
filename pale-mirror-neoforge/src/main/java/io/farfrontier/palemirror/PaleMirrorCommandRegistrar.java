package io.farfrontier.palemirror;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.debug.DebugCommandRegistrar;
import io.farfrontier.palemirror.internal.presentation.ScenarioCommandPresentation;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Owns the Brigadier command tree; event subscriptions only delegate here. */
final class PaleMirrorCommandRegistrar {
    private PaleMirrorCommandRegistrar() { }

    static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pale_mirror")
                .then(Commands.literal("status").requires(source -> source.hasPermission(2)).executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(PaleMirrorRuntime.forServer(context.getSource().getServer()).status()), false);
                    return 1;
                }));
        root.then(Commands.literal("testmine").then(Commands.literal("create").requires(source -> source.hasPermission(4))
                .then(Commands.argument("source", StringArgumentType.word()).executes(context ->
                        createTestMine(context, StringArgumentType.getString(context, "source"))))));
        root.then(Commands.literal("threatsite").then(Commands.literal("register").requires(source -> source.hasPermission(4))
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .then(Commands.argument("source", StringArgumentType.word()).executes(context ->
                                registerThreatSite(context, StringArgumentType.getString(context, "source")))))));
        root.then(Commands.literal("simulate").then(Commands.literal("step").requires(source -> source.hasPermission(4))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 24)).executes(context -> {
                            int count = IntegerArgumentType.getInteger(context, "count");
                            int events = PaleMirrorRuntime.forServer(context.getSource().getServer()).advanceSimulation(count).size();
                            context.getSource().sendSuccess(() -> Component.literal("Advanced " + count + " simulation step(s); produced " + events + " event(s)."), true);
                            return events;
                }))));
        LiteralArgumentBuilder<CommandSourceStack> scenario = Commands.literal("scenario");
        scenario.then(Commands.literal("list").executes(context -> {
                            PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(context.getSource().getServer());
                            var offered = runtime.offered(audienceFor(context.getSource(), runtime));
                            context.getSource().sendSuccess(() -> ScenarioCommandPresentation.offered(offered), false);
                            return offered.size();
        }));
        scenario.then(Commands.literal("accept").then(Commands.argument("id", StringArgumentType.greedyString()).executes(context -> {
                            String id = StringArgumentType.getString(context, "id");
                            PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(context.getSource().getServer());
                            boolean accepted = runtime.accept(id, audienceFor(context.getSource(), runtime));
                            if (accepted) context.getSource().sendSuccess(() -> Component.translatable("pale_mirror.command.scenario.accepted", id), true);
                            else context.getSource().sendFailure(Component.translatable("pale_mirror.command.scenario.missing"));
                            return accepted ? 1 : 0;
        })));
        scenario.then(Commands.literal("decline").then(Commands.argument("id", StringArgumentType.greedyString()).executes(context -> {
                            String id = StringArgumentType.getString(context, "id");
                            PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(context.getSource().getServer());
                            boolean declined = runtime.decline(id, audienceFor(context.getSource(), runtime));
                            if (declined) context.getSource().sendSuccess(() -> Component.literal("Declined scenario " + id), false);
                            else context.getSource().sendFailure(Component.translatable("pale_mirror.command.scenario.missing"));
                            return declined ? 1 : 0;
        })));
        root.then(scenario);
        root.then(Commands.literal("adapter").requires(source -> source.hasPermission(2)).then(Commands.literal("status").executes(context -> {
                    String health = AdapterRegistry.all().stream().map(adapter -> adapter.id() + "=" + adapter.health().status() + " (" + adapter.health().detail() + ")").reduce((a, b) -> a + "; " + b).orElse("No adapters");
                    context.getSource().sendSuccess(() -> Component.literal(health), false);
                    return 1;
        })));
        root.then(Commands.literal("object").requires(source -> source.hasPermission(2)).then(Commands.literal("inspect")
                .then(Commands.argument("id", ResourceLocationArgument.id()).executes(context -> {
                    String id = ResourceLocationArgument.getId(context, "id").toString();
                    context.getSource().sendSuccess(() -> Component.literal(
                            PaleMirrorRuntime.forServer(context.getSource().getServer()).inspectObject(id)), false);
                    return 1;
                }))));
        root.then(Commands.literal("explain").requires(source -> source.hasPermission(2)).then(Commands.literal("settlement")
                .then(Commands.argument("id", ResourceLocationArgument.id()).executes(context -> {
                    String id = ResourceLocationArgument.getId(context, "id").toString();
                    context.getSource().sendSuccess(() -> Component.literal(
                            PaleMirrorRuntime.forServer(context.getSource().getServer()).explainSettlement(id)), false);
                    return 1;
                }))));
        root.then(Commands.literal("timeline").requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", ResourceLocationArgument.id()).executes(context -> {
                    String id = ResourceLocationArgument.getId(context, "id").toString();
                    context.getSource().sendSuccess(() -> Component.literal(
                            PaleMirrorRuntime.forServer(context.getSource().getServer()).timeline(id)), false);
                    return 1;
                })));
        LiteralArgumentBuilder<CommandSourceStack> logistics = Commands.literal("logistics")
                .requires(source -> source.hasPermission(2));
        logistics.then(Commands.literal("status").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            PaleMirrorRuntime.forServer(context.getSource().getServer()).logisticsStatus()), false);
                    return 1;
                }));
        LiteralArgumentBuilder<CommandSourceStack> commissionRedValley = Commands.literal("commission_red_valley")
                .requires(source -> source.hasPermission(4))
                .executes(context -> commissionRedValley(context, ""));
        commissionRedValley.then(Commands.argument("region", ResourceLocationArgument.id()).executes(context ->
                commissionRedValley(context, ResourceLocationArgument.getId(context, "region").toString())));
        logistics.then(commissionRedValley);
        root.then(logistics);
        root.then(Commands.literal("settlement").then(Commands.literal("evacuate")
                .then(Commands.argument("id", ResourceLocationArgument.id()).executes(context -> {
                    PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(context.getSource().getServer());
                    String id = ResourceLocationArgument.getId(context, "id").toString();
                    boolean started = runtime.beginSettlementEvacuation(id, audienceFor(context.getSource(), runtime),
                            context.getSource().getEntity() == null ? "command:server"
                                    : "player:" + context.getSource().getEntity().getUUID());
                    if (started) context.getSource().sendSuccess(() -> Component.literal("Evacuation started for " + id), true);
                    else context.getSource().sendFailure(Component.literal("Evacuation is unavailable or belongs to another audience."));
                    return started ? 1 : 0;
                }))));
        root.then(Commands.literal("settlement").then(Commands.literal("prepare_refugee_site")
                .then(Commands.argument("id", ResourceLocationArgument.id()).executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(Component.literal("Only a player can prepare a refugee site."));
                        return 0;
                    }
                    PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(context.getSource().getServer());
                    String id = ResourceLocationArgument.getId(context, "id").toString();
                    boolean issued = runtime.issueRefugeeAnchor(player, id);
                    if (!issued) context.getSource().sendFailure(Component.literal(
                            "A refugee site can only be prepared during this audience's active emergency window."));
                    return issued ? 1 : 0;
                }))));
        DebugCommandRegistrar.attach(root);
        event.getDispatcher().register(root);
    }

    private static StoryAudienceId audienceFor(CommandSourceStack source, PaleMirrorRuntime runtime) {
        return source.getEntity() instanceof ServerPlayer player ? runtime.audienceFor(player) : runtime.defaultAudience();
    }

    private static int commissionRedValley(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String regionId) {
        var result = PaleMirrorRuntime.forServer(context.getSource().getServer())
                .commissionRedValleyExercise(regionId);
        if (result.success()) {
            context.getSource().sendSuccess(() -> Component.literal(result.describe()), true);
            return 1;
        }
        context.getSource().sendFailure(Component.literal(result.describe()));
        return 0;
    }

    private static int registerThreatSite(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                          String sourceName) {
        try {
            io.farfrontier.palemirror.domain.InfectionSourceId source = AdapterRegistry.sourceForAlias(sourceName)
                    .orElseThrow(() -> new IllegalArgumentException("unknown PM source " + sourceName)).source();
            ServerPlayer player = context.getSource().getPlayerOrException();
            String id = ResourceLocationArgument.getId(context, "id").toString();
            TestMineRecord site = PaleMirrorRuntime.forServer(context.getSource().getServer())
                    .registerThreatSite(player, new io.farfrontier.palemirror.domain.WorldObjectId(id), source);
            context.getSource().sendSuccess(() -> Component.literal("Registered PM " + sourceName + " threat site " + site.id().value()), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException | com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            context.getSource().sendFailure(Component.literal("Could not register PM threat site: " + failure.getMessage()));
            return 0;
        }
    }

    private static int createTestMine(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                      String sourceName) {
        try {
            io.farfrontier.palemirror.domain.InfectionSourceId source = AdapterRegistry.sourceForAlias(sourceName)
                    .orElseThrow(() -> new IllegalArgumentException("unknown PM source " + sourceName)).source();
            ServerPlayer player = context.getSource().getPlayerOrException();
            TestMineRecord mine = PaleMirrorRuntime.forServer(context.getSource().getServer()).registerThreatSite(player,
                    new io.farfrontier.palemirror.domain.WorldObjectId("pale_mirror:test_mine"), source);
            context.getSource().sendSuccess(() -> Component.translatable("pale_mirror.command.testmine.created", mine.id().value()), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException | com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            context.getSource().sendFailure(Component.literal("Could not create PM test mine: " + failure.getMessage()));
            return 0;
        }
    }
}
