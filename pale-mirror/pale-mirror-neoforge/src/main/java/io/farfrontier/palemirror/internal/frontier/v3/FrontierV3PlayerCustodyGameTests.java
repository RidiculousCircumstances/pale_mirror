package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceTransition;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceLedger;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceLot;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLease;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaRecord;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Materialized player withdrawal and return evidence for exact v3 inventory custody. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3PlayerCustodyGameTests {
    private FrontierV3PlayerCustodyGameTests() { }

    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void activeChestTransfersOneExactStackToAndFromItsRealPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); WorldId world = new WorldId("frontier:player-withdrawal-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = exactWheatRuntime(world);
        SubjectId container = new SubjectId("container:1-depot"); BlockPos chestPosition = interior(helper);
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, chestPosition, container);
        helper.assertTrue(chest != null, "the player custody fixture needs an owned chest");
        FrontierV3CommandSubmission.submit(runtime, "player-withdrawal-prepare", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.PREPARED));
        FrontierV3CommandSubmission.submit(runtime, "player-withdrawal-active", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.ACTIVE));
        ExactItemStack exact = state(runtime).inventory().itemAt(container, 0).orElseThrow();
        var player = helper.makeMockServerPlayerInLevel();
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(exact));
        player.getInventory().setItem(0, chest.removeItemNoUpdate(0));

        helper.assertTrue(FrontierV3InventoryObservationExecutor.observeOne(level, runtime, state(runtime), FrontierV3HopperCarrierLedger.get(level),
                        new FrontierV3InventoryObservationExecutor.StoreChest(chestPosition, container), chest),
                "one exact stack physically moved to one player must become player custody");
        helper.assertValueEqual(state(runtime).inventory().items().get(exact.id()).custody(), new InventoryCustody.Player(player.getUUID()),
                "withdrawal must retain the exact canonical item identity and player UUID");
        helper.assertTrue(FrontierV3InventoryObservationExecutor.hasExactItem(player, exact),
                "the player must retain the same tagged physical stack after canonical acknowledgement");

        chest.setItem(0, player.getInventory().removeItemNoUpdate(0));
        helper.assertTrue(FrontierV3InventoryObservationExecutor.observeOne(level, runtime, state(runtime), FrontierV3HopperCarrierLedger.get(level),
                        new FrontierV3InventoryObservationExecutor.StoreChest(chestPosition, container), chest),
                "the same tagged player stack returned to the owned chest must be observed once");
        helper.assertValueEqual(state(runtime).inventory().items().get(exact.id()).custody(), new InventoryCustody.ContainerSlot(container, 0),
                "return must restore the original exact slot without creating or aggregating a resource");
        helper.assertTrue(FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(0), exact),
                "the physical return must retain the exact durable tag and count");
        runtime.shutdown(); helper.succeed();
    }

    /** An ordinary player chest move, rather than a constructed canonical conflict, must retain a diagnosable incident. */
    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void ordinaryPlayerForeignChestMoveRetainsOneBlockedDiagnosticAcrossSnapshot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); WorldId world = new WorldId("frontier:player-foreign-slot-diagnostic");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = exactWheatRuntime(world);
        SubjectId container = new SubjectId("container:1-depot"); BlockPos chestPosition = interior(helper);
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, chestPosition, container);
        FrontierV3CommandSubmission.submit(runtime, "player-foreign-slot-prepare", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.PREPARED));
        FrontierV3CommandSubmission.submit(runtime, "player-foreign-slot-active", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.ACTIVE));
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(Items.CARROT, 1));
        chest.setItem(0, player.getInventory().removeItemNoUpdate(0)); chest.setChanged();

        helper.assertTrue(FrontierV3InventoryObservationExecutor.observeOne(level, runtime, state(runtime), FrontierV3HopperCarrierLedger.get(level),
                        new FrontierV3InventoryObservationExecutor.StoreChest(chestPosition, container), chest),
                "the ordinary foreign player stack must be observed as one canonical conflict");
        FrontierWorldState conflicted = state(runtime);
        var conflict = conflicted.inventory().conflicts().values().stream().findFirst().orElseThrow();
        var incident = conflicted.diagnosticIncidents().why(conflict.diagnostic().subject()).orElseThrow();
        helper.assertValueEqual(incident.diagnostic(), conflict.diagnostic(), "why lookup must retain the producer-stamped tuple");
        helper.assertTrue(incident.awaitingReview(), "one retained player conflict must block an otherwise green aggregate");
        FrontierWorldState restarted = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(conflicted));
        helper.assertValueEqual(restarted.diagnosticIncidents().bundle(incident.id()).orElseThrow(), incident.bundle(),
                "snapshot restart must retain the same identity-complete incident bundle");
        runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void activeChestHandsOneFungibleStackToItsObservedHopper(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); WorldId world = new WorldId("frontier:fungible-hopper-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                acquiredFungibleChestRuntime(world);
        SubjectId container = new SubjectId("container:1-depot"); BlockPos chestPosition = interior(helper);
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, chestPosition, container);
        helper.assertTrue(chest != null, "the hopper fixture needs an owned chest");
        chest.setItem(0, new ItemStack(Items.WHEAT, 64)); chest.setChanged();
        observeFungibleChest(level, runtime, chest, container);
        BlockPos hopperPosition = chestPosition.east(); level.setBlock(hopperPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(hopperPosition, Blocks.HOPPER.defaultBlockState(), 3);
        HopperBlockEntity hopper = (HopperBlockEntity) level.getBlockEntity(hopperPosition);
        hopper.setItem(0, chest.removeItemNoUpdate(0)); hopper.setChanged(); chest.setChanged();
        observeFungibleChest(level, runtime, chest, container);
        helper.assertTrue(state(runtime).inventory().fungibleResources().accounts().values().stream()
                        .anyMatch(account -> account.custody() instanceof ResourceCustody.WorldCarrier),
                "one observed hopper stack remains a HOT carrier account instead of unbounded COLD stock");
        helper.assertTrue(state(runtime).inventory().fungibleResources().bindings().values().stream()
                        .anyMatch(binding -> binding.address() instanceof io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress.HopperSlot),
                "the exact hopper slot is the retained physical binding");
        runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void activeChestDropThenRealPlayerPickupKeepsOneFungibleHotLineage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); WorldId world = new WorldId("frontier:fungible-drop-pickup-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                acquiredFungibleChestRuntime(world);
        SubjectId container = new SubjectId("container:1-depot"); BlockPos chestPosition = interior(helper);
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, chestPosition, container);
        helper.assertTrue(chest != null, "the drop fixture needs an owned chest");
        chest.setItem(0, new ItemStack(Items.WHEAT, 64)); chest.setChanged();
        observeFungibleChest(level, runtime, chest, container);
        ItemEntity drop = new ItemEntity(level, chestPosition.getX() + 0.5D, chestPosition.getY() + 0.5D, chestPosition.getZ() + 0.5D,
                chest.removeItemNoUpdate(0)); chest.setChanged(); level.addFreshEntity(drop);
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(level.getEntity(drop.getUUID()) == drop && drop.getItem().getCount() == 64,
                    "the physical drop must be live and retain the exact departed stack before observation");
            observeFungibleChest(level, runtime, chest, container);
            helper.assertTrue(state(runtime).inventory().fungibleResources().bindings().values().stream()
                            .anyMatch(binding -> binding.address() instanceof io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress.WorldEntity),
                    "the world item entity must become the one HOT physical binding before pickup");
            var player = helper.makeMockServerPlayerInLevel(); player.getInventory().setItem(0, drop.getItem().copy()); drop.discard();
            FrontierV3FungibleResourceObservationExecutor.tick(level, runtime);
            helper.assertTrue(state(runtime).inventory().fungibleResources().accounts().values().stream()
                            .anyMatch(account -> account.custody().equals(new ResourceCustody.Player(player.getUUID()))),
                    "one real player pickup transfers the same live fungible portion without reopening COLD custody");
            helper.assertTrue(state(runtime).inventory().fungibleResources().totalQuantity(new SubjectId("settlement:1"), "minecraft:wheat") == 64,
                    "drop and pickup preserve the exact canonical total");
            runtime.shutdown(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void recoveredCanonicalPlayerCustodyRestoresOnlyItsExactEmptyStartupSlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); var player = helper.makeMockServerPlayerInLevel();
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                recoveredPlayerRuntime(new WorldId("frontier:player-custody-recovery-game-test"), player.getUUID());
        FrontierV3PlayerCustodyRecovery.beginRecovery(runtime);
        FrontierV3FungibleResourceObservationExecutor.tick(level, runtime);

        ItemStack restored = player.getInventory().getItem(9);
        helper.assertTrue(restored.is(Items.CARROT) && restored.getCount() == 32,
                "a canonical-first abrupt recovery must restore the exact authenticated player slot, kind and count");
        FrontierWorldState recovered = state(runtime);
        helper.assertTrue(recovered.inventory().fungibleResources().accounts().values().stream()
                        .anyMatch(account -> account.custody().equals(new ResourceCustody.Player(player.getUUID()))),
                "physical reconstitution must retain the same canonical player custody instead of replaying a second handoff");
        helper.assertTrue(recovered.inventory().fungibleResources().accounts().values().stream()
                        .filter(account -> account.custody().equals(new ResourceCustody.Player(player.getUUID())))
                        .flatMapToInt(account -> account.lotQuantities().values().stream().mapToInt(Integer::intValue)).sum() == 32,
                "the restart repair must conserve the one persisted canonical player portion");
        FrontierV3PlayerCustodyRecovery.forget(runtime); runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void ordinarySavedEmptyPlayerSlotRemainsLocalAmbiguityAndCannotReplayCanonicalCustody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); var player = helper.makeMockServerPlayerInLevel();
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                recoveredPlayerRuntime(new WorldId("frontier:player-custody-ordinary-empty-game-test"), player.getUUID());
        FrontierV3PlayerCustodyRecovery.beginRecovery(runtime);
        var expected = FrontierV3PlayerResourceDiagnostic.expected(state(runtime), "custody:player-" + player.getUUID());
        helper.assertTrue(expected != null, "the fixture requires one exact durable player-custody binding");
        var savedFence = new net.minecraft.nbt.CompoundTag();
        savedFence.putString("token", expected.playerSaveFence()); savedFence.putString("account", expected.accountId().value());
        savedFence.putString("binding", expected.bindingId().value()); savedFence.putInt("slot", expected.slot());
        player.getPersistentData().put("pale_mirror.frontier_v3.player_custody_save_fence", savedFence);

        FrontierV3FungibleResourceObservationExecutor.tick(level, runtime);
        helper.assertTrue(player.getInventory().getItem(9).isEmpty(),
                "an ordinary durable empty/moved/consumed player slot must not be replaced from canonical custody");
        FrontierWorldState retained = state(runtime);
        helper.assertTrue(retained.inventory().fungibleResources().accounts().values().stream()
                        .anyMatch(account -> account.custody().equals(new ResourceCustody.Player(player.getUUID()))),
                "the local ambiguity must retain the one canonical custody account rather than minting or replaying a second transfer");
        helper.assertTrue(retained.inventory().fungibleResources().totalQuantity(new SubjectId("settlement:1"), "minecraft:carrot") == 32,
                "the divergent saved slot leaves exact canonical conservation intact without player-stack overwrite");
        FrontierV3PlayerCustodyRecovery.forget(runtime); runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void recoveredPlayerSaveThenOrdinaryMoveCannotReplayItsOriginalFence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); var player = helper.makeMockServerPlayerInLevel();
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> recovered =
                recoveredPlayerRuntime(new WorldId("frontier:player-custody-recovery-completion-game-test"), player.getUUID());
        FrontierV3PlayerCustodyRecovery.beginRecovery(recovered);
        FrontierV3FungibleResourceObservationExecutor.tick(level, recovered);
        var expected = FrontierV3PlayerResourceDiagnostic.expected(state(recovered), "custody:player-" + player.getUUID());
        helper.assertTrue(expected != null && player.getInventory().getItem(9).is(Items.CARROT),
                "the fixture must first materialize exactly the canonical-first missing-save recovery");

        var durablePlayerSave = player.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        helper.assertTrue(durablePlayerSave.toString().contains(expected.playerSaveFence()),
                "the recovered player save must carry the exact completion witness with the restored stack");
        player.getInventory().removeItemNoUpdate(9); player.containerMenu.broadcastChanges();
        FrontierV3PlayerCustodyRecovery.forget(recovered); recovered.shutdown();

        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> restarted =
                recoveredPlayerRuntime(new WorldId("frontier:player-custody-recovery-completion-game-test-restarted"), player.getUUID());
        FrontierV3PlayerCustodyRecovery.beginRecovery(restarted);
        FrontierV3FungibleResourceObservationExecutor.tick(level, restarted);
        helper.assertTrue(player.getInventory().getItem(9).isEmpty(),
                "a saved later move must retain local ambiguity and never re-materialize the old canonical fence");
        helper.assertTrue(state(restarted).inventory().fungibleResources().totalQuantity(new SubjectId("settlement:1"), "minecraft:carrot") == 32,
                "the ambiguity must retain one exact canonical player portion without replaying property");
        FrontierV3PlayerCustodyRecovery.forget(restarted); restarted.shutdown(); helper.succeed();
    }

    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
    }

    /** Withdrawal fixtures start after the reference chest's exact observation/acquisition boundary. */
    private static FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection>
    acquiredFungibleChestRuntime(WorldId world) {
        var base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        SubjectId depot = new SubjectId("container:1-depot");
        FrontierWorldState state = base.initialState().withInventory(base.initialState().inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE));
        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(depot,
                ReferenceContainerCustody.semanticKind(state, depot), 0L,
                ReferenceContainerCustody.canonicalFingerprint(state, depot), ReferenceContainerCustody.provenance(depot));
        PhysicalReplicaCustodyState custody = PhysicalReplicaCustodyState.empty().declare(expected)
                .observe(depot, 0L, 1L, expected.fingerprint(), expected.provenance(), 0L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(depot), depot,
                        ReferenceContainerCustody.PROVIDER_ID, 1L, 0L, 2L,
                        PhysicalCustodyLeaseStatus.ACQUIRED, null));
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        var config = new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                base.projectionMapper(), base.limits(), base.initialSchedules(), base.transactionCommitter(),
                base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(config, new EphemeralStore(), 10_000);
    }

    private static void observeFungibleChest(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                             ChestBlockEntity chest, SubjectId container) {
        FrontierWorldState state = state(runtime);
        var account = state.inventory().fungibleResources().accounts().values().stream().filter(value -> value.custody()
                .equals(new ResourceCustody.Container(container))).findFirst().orElseThrow();
        long epoch = state.inventory().fungibleResources().bindings().values().stream().filter(binding -> binding.accountId().equals(account.id()))
                .mapToLong(io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding::authorityEpoch).findFirst().orElse(1L);
        FrontierV3FungibleResourceObservationExecutor.observe(level, runtime, state, account, chest, epoch);
    }

    /** The tiny bastion template owns this interior cell; distant offsets can leave a neighbour undiscoverable. */
    private static BlockPos interior(GameTestHelper helper) { return helper.absolutePos(new BlockPos(1, 8, 0)); }

    /** Retains the independent exact-item observer fixture after the fresh fungible bootstrap cut. */
    private static FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> exactWheatRuntime(WorldId world) {
        FrontierEngineConfiguration<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> base =
                FrontierWorldRuntimeDefinition.configuration(world, 91L);
        SubjectId settlement = new SubjectId("settlement:1"), account = new SubjectId("custody:container-1-depot"), lot = new SubjectId("lot:bootstrap-1-wheat");
        FungibleResourceLedger resources = base.initialState().inventory().fungibleResources().destroy(account, Map.of(lot, 64), Map.of());
        ExactItemStack wheat = new ExactItemStack(new SubjectId("item:bootstrap-1-wheat"), settlement, "minecraft:wheat", 64,
                new InventoryCustody.ContainerSlot(FrontierWorldState.depotId(settlement), 0));
        FrontierWorldState state = base.initialState().withInventory(base.initialState().inventory().withFungibleResources(resources).store(wheat));
        FrontierEngineConfiguration<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> configuration =
                new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                        base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configuration, new EphemeralStore(), 10_000);
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection>
    recoveredPlayerRuntime(WorldId world, java.util.UUID playerId) {
        FrontierEngineConfiguration<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> base =
                FrontierWorldRuntimeDefinition.configuration(world, 91L);
        SubjectId settlement = new SubjectId("settlement:1"), lotId = new SubjectId("lot:player-recovery-carrot");
        SubjectId accountId = new SubjectId("custody:player-" + playerId), bindingId = new SubjectId("binding:player-recovery-carrot");
        ResourceLot lot = new ResourceLot(lotId, settlement, "minecraft:carrot", 32, "game-test-recovery", List.of());
        CustodyAccount account = new CustodyAccount(accountId, new ResourceCustody.Player(playerId), Map.of(lotId, 32), Map.of());
        PhysicalStackBinding binding = new PhysicalStackBinding(bindingId, accountId, new PhysicalStackAddress.PlayerSlot(playerId, 9), 1L,
                "minecraft:carrot", Map.of(lotId, 32), Map.of(), "00000000-0000-0000-0000-000000000901");
        FungibleResourceLedger prior = base.initialState().inventory().fungibleResources();
        Map<SubjectId, ResourceLot> lots = new LinkedHashMap<>(prior.lots()); lots.put(lotId, lot);
        Map<SubjectId, CustodyAccount> accounts = new LinkedHashMap<>(prior.accounts()); accounts.put(accountId, account);
        Map<SubjectId, PhysicalStackBinding> bindings = new LinkedHashMap<>(prior.bindings()); bindings.put(bindingId, binding);
        FrontierWorldState state = base.initialState().withInventory(base.initialState().inventory()
                .withFungibleResources(new FungibleResourceLedger(lots, prior.claims(), accounts, bindings)));
        FrontierEngineConfiguration<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> configuration =
                new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                        base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configuration, new EphemeralStore(), 10_000);
    }
    /** GameTest-only store; filesystem restart behavior is covered by the server-runtime test. */
    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) {
            throw new UnsupportedOperationException("GameTest does not compact");
        }
    }
}
