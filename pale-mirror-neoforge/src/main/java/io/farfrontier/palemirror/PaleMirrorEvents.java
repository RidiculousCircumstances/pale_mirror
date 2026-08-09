package io.farfrontier.palemirror;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.adapter.VanillaAnchorAdapter;
import io.farfrontier.palemirror.internal.integration.crimson.CrimsonSandboxAdapter;
import io.farfrontier.palemirror.internal.content.ScenarioDefinitions;
import io.farfrontier.palemirror.internal.content.EncounterDefinitions;
import io.farfrontier.palemirror.internal.observation.ThreatControllerDestroyed;
import io.farfrontier.palemirror.internal.observation.CrimsonEncounterActorDestroyed;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID)
public final class PaleMirrorEvents {
    private PaleMirrorEvents() { }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        PaleMirrorSavedData.assertCompatibleData(event.getServer().getWorldPath(LevelResource.ROOT));
        PaleMirrorRuntime.forServer(event.getServer());
    }

    @SubscribeEvent
    public static void onReloadListeners(AddReloadListenerEvent event) {
        event.addListener(EncounterDefinitions.INSTANCE);
        event.addListener(ScenarioDefinitions.INSTANCE);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PaleMirrorRuntime.stop(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PaleMirrorRuntime.forServer(event.getServer()).tick();
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        String objectId = event.getEntity().getPersistentData().getString(VanillaAnchorAdapter.OBJECT_ID_KEY);
        if (!objectId.isBlank() && VanillaAnchorAdapter.isAnchor(event.getEntity()) && event.getEntity().level().getServer() != null) {
            String causationId = "entity:" + event.getEntity().getUUID();
            PaleMirrorRuntime.forServer(event.getEntity().level().getServer()).publish(new ThreatControllerDestroyed(
                    "controller-destroyed:" + causationId,
                    new io.farfrontier.palemirror.domain.WorldObjectId(objectId), causationId));
        }
        String actorObjectId = event.getEntity().getPersistentData().getString(CrimsonSandboxAdapter.OBJECT_ID_KEY);
        String slotId = event.getEntity().getPersistentData().getString(CrimsonSandboxAdapter.SLOT_KEY);
        if (!actorObjectId.isBlank() && !slotId.isBlank() && CrimsonSandboxAdapter.isActor(event.getEntity())
                && event.getEntity().level().getServer() != null) {
            String causationId = "entity:" + event.getEntity().getUUID();
            PaleMirrorRuntime.forServer(event.getEntity().level().getServer()).publish(new CrimsonEncounterActorDestroyed(
                    "crimson-actor-destroyed:" + causationId, new io.farfrontier.palemirror.domain.WorldObjectId(actorObjectId),
                    slotId, event.getEntity().getUUID()));
        }
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pale_mirror")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(PaleMirrorRuntime.forServer(context.getSource().getServer()).status()), false);
                    return 1;
                }));
        root.then(Commands.literal("testmine").then(Commands.literal("create").requires(source -> source.hasPermission(4)).executes(context -> {
                    try {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        TestMineRecord mine = PaleMirrorRuntime.forServer(context.getSource().getServer()).createTestMine(player);
                        context.getSource().sendSuccess(() -> Component.translatable("pale_mirror.command.testmine.created", mine.id().value()), true);
                        return 1;
                    } catch (IllegalStateException failure) {
                        context.getSource().sendFailure(Component.translatable("pale_mirror.command.testmine.no_space"));
                        return 0;
                    }
                })));
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
                            context.getSource().sendSuccess(() -> Component.literal(offered.isEmpty() ? "No offered scenarios." : offered.stream().map(value -> value.id() + " [" + value.status() + "]").reduce((a, b) -> a + ", " + b).orElseThrow()), false);
                            return offered.size();
        }));
        scenario.then(Commands.literal("accept").then(Commands.argument("id", StringArgumentType.word()).executes(context -> {
                            String id = StringArgumentType.getString(context, "id");
                            PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(context.getSource().getServer());
                            boolean accepted = runtime.accept(id, audienceFor(context.getSource(), runtime));
                            if (accepted) context.getSource().sendSuccess(() -> Component.translatable("pale_mirror.command.scenario.accepted", id), true);
                            else context.getSource().sendFailure(Component.translatable("pale_mirror.command.scenario.missing"));
                            return accepted ? 1 : 0;
        })));
        root.then(scenario);
        root.then(Commands.literal("adapter").then(Commands.literal("status").executes(context -> {
                    String health = AdapterRegistry.all().stream().map(adapter -> adapter.id() + "=" + adapter.health().status() + " (" + adapter.health().detail() + ")").reduce((a, b) -> a + "; " + b).orElse("No adapters");
                    context.getSource().sendSuccess(() -> Component.literal(health), false);
                    return 1;
        })));
        root.then(Commands.literal("object").then(Commands.literal("inspect")
                .then(Commands.argument("id", StringArgumentType.string()).executes(context -> {
                    String id = StringArgumentType.getString(context, "id");
                    context.getSource().sendSuccess(() -> Component.literal(
                            PaleMirrorRuntime.forServer(context.getSource().getServer()).inspectObject(id)), false);
                    return 1;
                }))));
        event.getDispatcher().register(root);
    }

    private static StoryAudienceId audienceFor(CommandSourceStack source, PaleMirrorRuntime runtime) {
        return source.getEntity() instanceof ServerPlayer player ? runtime.audienceFor(player) : runtime.defaultAudience();
    }
}
