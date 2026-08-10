package io.farfrontier.palemirror;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.adapter.VanillaAnchorAdapter;
import io.farfrontier.palemirror.internal.adapter.ActorDamageResult;
import io.farfrontier.palemirror.internal.adapter.SourceItemFirewall;
import io.farfrontier.palemirror.internal.combat.PmProjectileRuntime;
import io.farfrontier.palemirror.internal.content.ScenarioDefinitions;
import io.farfrontier.palemirror.internal.content.EncounterDefinitions;
import io.farfrontier.palemirror.internal.content.ThreatTierDefinitions;
import io.farfrontier.palemirror.internal.content.CampaignRegionDefinitions;
import io.farfrontier.palemirror.internal.observation.ThreatControllerDestroyed;
import io.farfrontier.palemirror.internal.observation.EncounterActorDestroyed;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.level.BlockEvent;
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
        event.addListener(ThreatTierDefinitions.INSTANCE);
        event.addListener(CampaignRegionDefinitions.INSTANCE);
        AdapterRegistry.reloadListeners().forEach(event::addListener);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PaleMirrorRuntime.stop(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PaleMirrorRuntime.forServer(event.getServer()).tick();
    }

    /** Source adapters may reject unmanaged native forms at the server boundary. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && AdapterRegistry.rejectsUnmanagedEntity(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** PM-tagged projectile carriers never execute their own hit path. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().level().getServer() != null
                && PmProjectileRuntime.handleImpact(event.getProjectile().level().getServer(), event.getProjectile(),
                event.getRayTraceResult())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().getServer() != null) {
            PaleMirrorRuntime.forServer(event.getEntity().level().getServer())
                    .recordSettlementDeath(event.getEntity(), event.getSource());
        }
        String objectId = event.getEntity().getPersistentData().getString(VanillaAnchorAdapter.OBJECT_ID_KEY);
        if (!objectId.isBlank() && VanillaAnchorAdapter.isAnchor(event.getEntity()) && event.getEntity().level().getServer() != null) {
            PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(event.getEntity().level().getServer());
            if (!runtime.controllerVulnerable(objectId)) {
                event.setCanceled(true);
                return;
            }
            String causationId = "entity:" + event.getEntity().getUUID();
            runtime.publish(new ThreatControllerDestroyed(
                    "controller-destroyed:" + causationId,
                    new io.farfrontier.palemirror.domain.WorldObjectId(objectId), causationId));
        }
        String gateObjectId = event.getEntity().getPersistentData().getString(VanillaAnchorAdapter.OBJECT_ID_KEY);
        String gateSlotId = event.getEntity().getPersistentData().getString("pale_mirror_encounter_slot");
        if (!gateObjectId.isBlank() && !gateSlotId.isBlank() && event.getEntity().level().getServer() != null) {
            AdapterRegistry.sourceAdapters().stream().filter(adapter -> adapter.matchesGatePart(event.getEntity())).findFirst()
                    .ifPresent(adapter -> {
                        adapter.presentDeath(event.getEntity());
                        PaleMirrorRuntime.forServer(event.getEntity().level().getServer())
                                .gateEntityDestroyed(gateObjectId, gateSlotId, event.getEntity().getUUID());
                    });
        }
        String actorObjectId = event.getEntity().getPersistentData().getString(VanillaAnchorAdapter.OBJECT_ID_KEY);
        String slotId = event.getEntity().getPersistentData().getString("pale_mirror_encounter_slot");
        if (!actorObjectId.isBlank() && !slotId.isBlank() && event.getEntity().level().getServer() != null) {
            AdapterRegistry.sourceAdapters().stream().filter(adapter -> adapter.matchesActor(event.getEntity())).findFirst()
                    .ifPresent(adapter -> {
                        adapter.presentDeath(event.getEntity());
                        String causationId = "entity:" + event.getEntity().getUUID();
                        PaleMirrorRuntime.forServer(event.getEntity().level().getServer()).publish(new EncounterActorDestroyed(
                                "encounter-actor-destroyed:" + adapter.source().value() + ":" + causationId,
                                new io.farfrontier.palemirror.domain.WorldObjectId(actorObjectId), adapter.source(), slotId,
                                event.getEntity().getUUID()));
                    });
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player
                && (denyExcludedItem(player, player.getMainHandItem(), "melee attack")
                || denyExcludedItem(player, player.getOffhandItem(), "melee attack"))) {
            event.setCanceled(true);
            return;
        }
        String objectId = event.getEntity().getPersistentData().getString(VanillaAnchorAdapter.OBJECT_ID_KEY);
        if (!objectId.isBlank() && VanillaAnchorAdapter.isAnchor(event.getEntity()) && event.getEntity().level().getServer() != null
                && !PaleMirrorRuntime.forServer(event.getEntity().level().getServer()).controllerVulnerable(objectId)) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity().level().getServer() == null) return;
        ActorDamageResult result = PaleMirrorRuntime.forServer(event.getEntity().level().getServer())
                .receiveSourceActorDamage(event.getEntity(), event.getSource(), event.getAmount());
        if (result.intercepts()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F) return;
        AdapterRegistry.sourceAdapters().forEach(adapter -> {
            adapter.presentDamage(event.getEntity());
            adapter.presentAttack(event.getSource().getEntity());
        });
    }

    @SubscribeEvent
    public static void onExcludedRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (denyReservedTransfer(event.getEntity(), event.getItemStack())
                || denyExcludedItem(event.getEntity(), event.getItemStack(), "right click")) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onExcludedRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (denyReservedTransfer(event.getEntity(), event.getItemStack())
                || denyExcludedItem(event.getEntity(), event.getItemStack(), "use on block")) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide()) {
            PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(player.getServer());
            var transfer = runtime.interactWithSupplyDepot(player, event.getPos());
            if (transfer.handled()) {
                player.sendSystemMessage(Component.literal(transfer.message()));
                event.setCanceled(true);
                event.setCancellationResult(transfer.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL);
            } else if (runtime.presentSettlementJournal(player, event.getPos())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }

    @SubscribeEvent
    public static void onExcludedEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (denyReservedTransfer(event.getEntity(), event.getItemStack())
                || denyExcludedItem(event.getEntity(), event.getItemStack(), "use on entity")) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onExcludedEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (denyReservedTransfer(event.getEntity(), event.getItemStack())
                || denyExcludedItem(event.getEntity(), event.getItemStack(), "specific entity use")) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onExcludedItemUse(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player && (denyReservedTransfer(player, event.getItem())
                || denyExcludedItem(player, event.getItem(), "held use"))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExcludedItemPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player && denyExcludedItem(player, event.getItemEntity().getItem(), "pickup")) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    /** Recipe events are post-craft; zeroing the live output prevents excluded stacks entering the inventory. */
    @SubscribeEvent
    public static void onExcludedCraft(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && (denyReservedTransfer(player, event.getCrafting())
                || denyExcludedItem(player, event.getCrafting(), "craft output"))) {
            event.getCrafting().setCount(0);
            player.containerMenu.broadcastChanges();
        }
    }

    @SubscribeEvent
    public static void onExcludedSmelt(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && (denyReservedTransfer(player, event.getSmelting())
                || denyExcludedItem(player, event.getSmelting(), "smelt output"))) {
            event.getSmelting().setCount(0);
            player.containerMenu.broadcastChanges();
        }
    }

    @SubscribeEvent
    public static void onReservedItemToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && denyReservedTransfer(player, event.getEntity().getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            PaleMirrorRuntime.forServer(level.getServer()).gateBlockDestroyed(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pale_mirror")
                .then(Commands.literal("status").requires(source -> source.hasPermission(2)).executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(PaleMirrorRuntime.forServer(context.getSource().getServer()).status()), false);
                    return 1;
                }));
        root.then(Commands.literal("testmine").then(Commands.literal("create").requires(source -> source.hasPermission(4))
                .then(Commands.argument("source", StringArgumentType.word()).executes(context ->
                        createTestMine(context, StringArgumentType.getString(context, "source"))))));
        root.then(Commands.literal("threatsite").then(Commands.literal("register").requires(source -> source.hasPermission(4))
                .then(Commands.argument("id", StringArgumentType.word())
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
        root.then(Commands.literal("adapter").requires(source -> source.hasPermission(2)).then(Commands.literal("status").executes(context -> {
                    String health = AdapterRegistry.all().stream().map(adapter -> adapter.id() + "=" + adapter.health().status() + " (" + adapter.health().detail() + ")").reduce((a, b) -> a + "; " + b).orElse("No adapters");
                    context.getSource().sendSuccess(() -> Component.literal(health), false);
                    return 1;
        })));
        root.then(Commands.literal("object").requires(source -> source.hasPermission(2)).then(Commands.literal("inspect")
                .then(Commands.argument("id", StringArgumentType.string()).executes(context -> {
                    String id = StringArgumentType.getString(context, "id");
                    context.getSource().sendSuccess(() -> Component.literal(
                            PaleMirrorRuntime.forServer(context.getSource().getServer()).inspectObject(id)), false);
                    return 1;
                }))));
        root.then(Commands.literal("explain").requires(source -> source.hasPermission(2)).then(Commands.literal("settlement")
                .then(Commands.argument("id", StringArgumentType.string()).executes(context -> {
                    String id = StringArgumentType.getString(context, "id");
                    context.getSource().sendSuccess(() -> Component.literal(
                            PaleMirrorRuntime.forServer(context.getSource().getServer()).explainSettlement(id)), false);
                    return 1;
                }))));
        root.then(Commands.literal("timeline").requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.string()).executes(context -> {
                    String id = StringArgumentType.getString(context, "id");
                    context.getSource().sendSuccess(() -> Component.literal(
                            PaleMirrorRuntime.forServer(context.getSource().getServer()).timeline(id)), false);
                    return 1;
                })));
        root.then(Commands.literal("logistics").requires(source -> source.hasPermission(2)).then(Commands.literal("status")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            PaleMirrorRuntime.forServer(context.getSource().getServer()).logisticsStatus()), false);
                    return 1;
                })));
        event.getDispatcher().register(root);
    }

    private static StoryAudienceId audienceFor(CommandSourceStack source, PaleMirrorRuntime runtime) {
        return source.getEntity() instanceof ServerPlayer player ? runtime.audienceFor(player) : runtime.defaultAudience();
    }

    private static boolean denyExcludedItem(Player player, ItemStack stack, String operation) {
        return SourceItemFirewall.classify(stack).map(blocked -> {
            if (player instanceof ServerPlayer serverPlayer) {
                PaleMirrorRuntime.forServer(serverPlayer.getServer()).quarantineLegacyItem(serverPlayer, blocked.sourceId(),
                        blocked.fingerprint(), "Excluded source item attempted " + operation);
            }
            return true;
        }).orElse(false);
    }

    private static boolean denyReservedTransfer(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer) || stack.isEmpty()
                || !PaleMirrorRuntime.forServer(serverPlayer.getServer()).isReservedTransferItem(stack)) return false;
        serverPlayer.sendSystemMessage(Component.literal("This item is reserved by a Pale Mirror resource transfer."));
        return true;
    }

    private static int registerThreatSite(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                          String sourceName) {
        try {
            io.farfrontier.palemirror.domain.InfectionSourceId source = AdapterRegistry.sourceForAlias(sourceName)
                    .orElseThrow(() -> new IllegalArgumentException("unknown PM source " + sourceName)).source();
            ServerPlayer player = context.getSource().getPlayerOrException();
            String id = net.minecraft.resources.ResourceLocation.parse(StringArgumentType.getString(context, "id")).toString();
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
