package io.farfrontier.palemirror;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.debug.DebugCommandRegistrar;
import io.farfrontier.palemirror.internal.presentation.ScenarioCommandPresentation;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
import io.farfrontier.palemirror.internal.world.SourceGrayboxRuntime;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Owns the Brigadier command tree; event subscriptions only delegate here. */
final class PaleMirrorCommandRegistrar {
    private PaleMirrorCommandRegistrar() { }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(commandTree());
    }

    /** Builds the complete tree before Brigadier receives it; package-visible for structural tests. */
    static LiteralArgumentBuilder<CommandSourceStack> commandTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pale_mirror")
                .then(Commands.literal("status").requires(source -> source.hasPermission(2)).executes(context -> {
                    String status = FrontierV3ServerLifecycle.ownsPhysicalWorld(context.getSource().getServer())
                            ? FrontierV3ServerLifecycle.status(context.getSource().getServer())
                            : PaleMirrorRuntime.forServer(context.getSource().getServer()).status();
                    context.getSource().sendSuccess(() -> Component.literal(status), false);
                    return 1;
                }));
        LiteralArgumentBuilder<CommandSourceStack> v3 = Commands.literal("v3")
                .requires(source -> source.hasPermission(2) && FrontierV3ServerLifecycle.ownsPhysicalWorld(source.getServer()));
        LiteralArgumentBuilder<CommandSourceStack> inspect = Commands.literal("inspect");
        inspect.then(Commands.literal("summary").executes(context -> v3Diagnostic(context, "summary", "")));
        inspect.then(diagnosticObject("site")); inspect.then(diagnosticObject("settlement")); inspect.then(diagnosticObject("actor")); inspect.then(diagnosticObject("item")); inspect.then(diagnosticObject("container"));
        inspect.then(diagnosticObject("operation")); inspect.then(diagnosticObject("intent")); inspect.then(diagnosticObject("trace"));
        v3.then(inspect);
        v3.then(Commands.literal("advance").requires(source -> source.hasPermission(4))
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, FrontierV3ServerLifecycle.MAX_FAST_FORWARD_TICKS)).executes(context -> {
                    int ticks = IntegerArgumentType.getInteger(context, "ticks");
                    if (!FrontierV3ServerLifecycle.requestFastForward(context.getSource().getServer(), ticks)) {
                        context.getSource().sendFailure(Component.literal("Frontier v3 cannot fast-forward while physical work is pending or another request is active."));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal("Queued Frontier v3 fast-forward for " + ticks + " tick(s)."), true);
                    return ticks;
                })));
        // Attach the finished mutable v3 branch only after every child is present. Brigadier
        // copies a child branch when it is attached, so attaching it earlier would omit advance.
        root.then(v3);
        root.then(Commands.literal("performance").requires(source -> source.hasPermission(2)).executes(context -> {
            context.getSource().sendSuccess(() -> Component.literal(PaleMirrorRuntime
                    .forServer(context.getSource().getServer()).performanceStatus()), false);
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
        // The source-parity runtime is frozen legacy. It must never receive a command which
        // publishes into the dimension selected for a v3 launch.
        LiteralArgumentBuilder<CommandSourceStack> frontier = Commands.literal("frontier").requires(source -> source.hasPermission(2)
                && !FrontierV3ServerLifecycle.ownsPhysicalWorld(source.getServer()));
        frontier.then(Commands.literal("status").executes(context -> {
            context.getSource().sendSuccess(() -> Component.literal(SourceGrayboxRuntime.forServer(context.getSource().getServer()).status()), false);
            return 1;
        }));
        frontier.then(Commands.literal("inspect").executes(context -> {
            SourceGrayboxRuntime runtime = SourceGrayboxRuntime.forServer(context.getSource().getServer());
            if (!runtime.activated()) {
                context.getSource().sendFailure(Component.literal("Activate the source graybox before inspecting it."));
                return 0;
            }
            Vec3 position = context.getSource().getPosition();
            String report = runtime.inspect((int) Math.floor(position.x), (int) Math.floor(position.z));
            context.getSource().sendSuccess(() -> Component.literal(report), false);
            return 1;
        }));
        frontier.then(Commands.literal("step").requires(source -> source.hasPermission(4))
                .then(Commands.argument("days", IntegerArgumentType.integer(1, 72)).executes(context -> {
                    int days = IntegerArgumentType.getInteger(context, "days");
                    SourceGrayboxRuntime runtime = SourceGrayboxRuntime.forServer(context.getSource().getServer());
                    if (!runtime.activated()) {
                        context.getSource().sendFailure(Component.literal("Activate the source graybox before advancing it."));
                        return 0;
                    }
                    runtime.advance(days);
                    context.getSource().sendSuccess(() -> Component.literal("Advanced source Frontier " + days + " day(s)."), true);
                    return days;
                })));
        frontier.then(Commands.literal("clock").requires(source -> source.hasPermission(4))
                .then(Commands.literal("gameplay").executes(context -> changeFrontierClock(context, "gameplay")))
                .then(Commands.literal("fast_graybox").executes(context -> changeFrontierClock(context, "fast_graybox"))));
        frontier.then(Commands.literal("activate_graybox").requires(source -> source.hasPermission(4)).executes(context -> {
            SourceGrayboxRuntime.forServer(context.getSource().getServer()).activate();
            context.getSource().sendSuccess(() -> Component.literal("Source-parity Frontier graybox activated in pale_mirror:frontier_graybox."), true);
            return 1;
        }));
        frontier.then(Commands.literal("enter_graybox").executes(context -> {
            if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                context.getSource().sendFailure(Component.literal("Only a player can enter the source graybox."));
                return 0;
            }
            try {
                SourceGrayboxRuntime.forServer(context.getSource().getServer()).enter(player);
            } catch (IllegalStateException failure) {
                context.getSource().sendFailure(Component.literal("Could not enter the source graybox: " + failure.getMessage()));
                return 0;
            }
            context.getSource().sendSuccess(() -> Component.literal("Entered pale_mirror:frontier_graybox."), false);
            return 1;
        }));
        LiteralArgumentBuilder<CommandSourceStack> frontierAudit = Commands.literal("audit");
        frontierAudit.then(Commands.literal("list").executes(context -> {
            SourceGrayboxRuntime runtime = SourceGrayboxRuntime.forServer(context.getSource().getServer());
            if (!runtime.activated()) {
                context.getSource().sendFailure(Component.literal("Activate the source graybox before requesting audit views."));
                return 0;
            }
            var views = runtime.auditViewLines();
            if (views.isEmpty()) {
                context.getSource().sendFailure(Component.literal("No current source-graybox audit scenes are available."));
                return 0;
            }
            context.getSource().sendSuccess(() -> Component.literal("Source-graybox semantic audit views:"), false);
            views.forEach(view -> context.getSource().sendSuccess(() -> Component.literal(view), false));
            return views.size();
        }));
        // Semantic ids deliberately contain slash and colon separators, so one
        // operator argument must consume the complete identifier.
        frontierAudit.then(Commands.literal("tp").then(Commands.argument("view", StringArgumentType.greedyString())
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(Component.literal("Only a player can enter a source-graybox audit view."));
                        return 0;
                    }
                    String viewId = StringArgumentType.getString(context, "view");
                    SourceGrayboxRuntime runtime = SourceGrayboxRuntime.forServer(context.getSource().getServer());
                    if (!runtime.enterAuditView(player, viewId)) {
                        context.getSource().sendFailure(Component.literal("Unknown or unavailable source-graybox audit view " + viewId));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal("Entered source-graybox audit view " + viewId + "."), false);
                    return 1;
                })));
        frontier.then(frontierAudit);
        root.then(frontier);
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
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> diagnosticObject(String view) {
        return Commands.literal(view).then(Commands.argument("id", StringArgumentType.greedyString())
                .executes(context -> v3Diagnostic(context, view, StringArgumentType.getString(context, "id"))));
    }

    private static int v3Diagnostic(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String view, String id) {
        context.getSource().sendSuccess(() -> Component.literal(FrontierV3ServerLifecycle.diagnostic(context.getSource().getServer(), view, id)), false);
        return 1;
    }

    private static int changeFrontierClock(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String profile) {
        SourceGrayboxRuntime runtime = SourceGrayboxRuntime.forServer(context.getSource().getServer());
        boolean changed = runtime.changeClockProfile(profile);
        String message = changed
                ? "Source Frontier clock set to " + profile + "; next source day rebased from this game tick."
                : "Source Frontier clock already uses " + profile + ".";
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return changed ? 1 : 0;
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
