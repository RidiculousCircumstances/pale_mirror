package io.farfrontier.palemirror;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import io.farfrontier.palemirror.internal.world.SourceGrayboxEntityAdmission;
import io.farfrontier.palemirror.internal.world.SourceGrayboxRuntime;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.adapter.VanillaAnchorAdapter;
import io.farfrontier.palemirror.internal.adapter.ActorDamageResult;
import io.farfrontier.palemirror.internal.adapter.SourceItemFirewall;
import io.farfrontier.palemirror.internal.combat.PmProjectileRuntime;
import io.farfrontier.palemirror.internal.content.ScenarioDefinitions;
import io.farfrontier.palemirror.internal.content.EncounterDefinitions;
import io.farfrontier.palemirror.internal.content.ThreatTierDefinitions;
import io.farfrontier.palemirror.internal.content.CampaignRegionDefinitions;
import io.farfrontier.palemirror.internal.debug.DebugCommandRegistrar;
import io.farfrontier.palemirror.internal.observation.ThreatControllerDestroyed;
import io.farfrontier.palemirror.internal.observation.EncounterActorDestroyed;
import io.farfrontier.palemirror.internal.presentation.ScenarioCommandPresentation;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import io.farfrontier.palemirror.internal.settlement.RefugeeCampRuntime;
import io.farfrontier.palemirror.internal.integration.vanilla.VanillaMinecartRailAdapter;
import io.farfrontier.palemirror.internal.world.AmbientSpawnThrottle;
import io.farfrontier.palemirror.internal.world.SettlementTerritoryPolicy;

@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID)
public final class PaleMirrorEvents {
    private PaleMirrorEvents() { }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        io.farfrontier.palemirror.internal.world.ProductProfilePreflight.verify();
        PaleMirrorSavedData.assertCompatibleData(event.getServer().getWorldPath(LevelResource.ROOT));
        SourceGrayboxRuntime.assertCompatibleData(event.getServer().getWorldPath(LevelResource.ROOT));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
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
        AmbientSpawnThrottle.clear();
        PaleMirrorRuntime.stop(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        AmbientSpawnThrottle.tick(event.getServer());
        PaleMirrorRuntime.forServer(event.getServer()).tick();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (SettlementTerritoryPolicy.evaluate(event)) return;
        AmbientSpawnThrottle.evaluate(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockGrowFeature(BlockGrowFeatureEvent event) {
        SettlementTerritoryPolicy.evaluate(event);
    }

    /** Source adapters may reject unmanaged native forms at the server boundary. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && SourceGrayboxEntityAdmission.rejects(level, event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (AdapterRegistry.rejectsUnmanagedEntity(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && SourceGrayboxRuntime.recognizesManagedEntity(event.getEntity())) {
            SourceGrayboxRuntime.forServer(level.getServer()).observeEntityJoin(level, event.getEntity());
        }
    }

    /**
     * Keep an exact managed-body departure observable. A body which leaves a
     * loaded HOT scene without the actor executor initiating a drain is an
     * invariant breach: source custody must recover on its normal cadence,
     * but the operator also needs the physical evidence rather than a silent
     * one-frame re-admission loop.
     */
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && SourceGrayboxRuntime.recognizesManagedEntity(event.getEntity())) {
            SourceGrayboxRuntime.forServer(level.getServer()).observeEntityLeave(level, event.getEntity());
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
            SourceGrayboxRuntime source = SourceGrayboxRuntime.forServer(event.getEntity().level().getServer());
            if (source.activated()) {
                String actor = event.getSource().getEntity() == null ? "environment" : event.getSource().getEntity().getUUID().toString();
                if (event.getSource().getEntity() instanceof ServerPlayer player) {
                    source.observeEntityDeathWithReceipt(event.getEntity(), actor).ifPresent(message -> player.sendSystemMessage(Component.literal(message)));
                } else source.observeEntityDeath(event.getEntity(), actor);
                return;
            }
            PaleMirrorRuntime.forServer(event.getEntity().level().getServer())
                    .recordSettlementDeath(event.getEntity(), event.getSource());
        }
        var visualObjectId = io.farfrontier.palemirror.api.PaleMirrorVisuals.provider()
                .flatMap(provider -> provider.threatControllerObjectId(event.getEntity())).orElse(null);
        if (visualObjectId != null && event.getEntity().level().getServer() != null) {
            PaleMirrorRuntime.forServer(event.getEntity().level().getServer())
                    .receiveThreatControllerDamage(event.getEntity(), event.getSource(), Integer.MAX_VALUE);
            event.setCanceled(true);
            return;
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

    /** A declared graybox interaction slot contributes its exact source fact before Minecraft removes the cube. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFrontierBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            SourceGrayboxRuntime source = SourceGrayboxRuntime.forServer(player.getServer());
            if (source.activated()) source.observeBlockBreakWithReceipt(level, event.getPos(), "player:" + player.getUUID())
                    .ifPresent(message -> player.sendSystemMessage(Component.literal(message)));
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
        if (event.getEntity().level().getServer() != null) {
            ActorDamageResult controller = PaleMirrorRuntime.forServer(event.getEntity().level().getServer())
                    .receiveThreatControllerDamage(event.getEntity(), event.getSource(), event.getAmount());
            if (controller.intercepts()) { event.setCanceled(true); return; }
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
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide()) {
            SourceGrayboxRuntime source = SourceGrayboxRuntime.forServer(player.getServer());
            if (source.presentBriefing(player, event.getPos())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
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
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide()
                && SourceGrayboxRuntime.forServer(player.getServer()).presentBriefing(player, event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (RefugeeCampRuntime.isRepresentative(event.getTarget())
                || VanillaMinecartRailAdapter.isRepresentative(event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (denyReservedTransfer(event.getEntity(), event.getItemStack())
                || denyExcludedItem(event.getEntity(), event.getItemStack(), "use on entity")) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onExcludedEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide()
                && SourceGrayboxRuntime.forServer(player.getServer()).presentBriefing(player, event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (RefugeeCampRuntime.isRepresentative(event.getTarget())
                || VanillaMinecartRailAdapter.isRepresentative(event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
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
            PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(level.getServer());
            runtime.railTopologyChanged(level, event.getPos());
            runtime.gateBlockDestroyed(level, event.getPos());
            runtime.settlementBlockDamaged(level, event.getPos(), event.getPlayer().getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            PaleMirrorRuntime.forServer(level.getServer()).railTopologyChanged(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onChunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            PaleMirrorRuntime.forServer(level.getServer()).railChunkLoaded(level, event.getChunk().getPos());
            if (SourceGrayboxRuntime.isGrayboxLevel(level)) {
                SourceGrayboxRuntime.forServer(level.getServer()).observeChunkLoaded(level);
            }
        }
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

}
