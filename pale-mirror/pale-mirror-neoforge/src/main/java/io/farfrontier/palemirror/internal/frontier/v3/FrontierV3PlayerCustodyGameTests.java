package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceTransition;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
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
import java.util.Optional;

/** Materialized player withdrawal and return evidence for exact v3 inventory custody. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3PlayerCustodyGameTests {
    private FrontierV3PlayerCustodyGameTests() { }

    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void activeChestTransfersOneExactStackToAndFromItsRealPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); WorldId world = new WorldId("frontier:player-withdrawal-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(world, 91L), new EphemeralStore(), 10_000);
        SubjectId container = new SubjectId("container:1-depot"); BlockPos chestPosition = helper.absolutePos(new BlockPos(58, 8, 0));
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

    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void activeChestTransfersOneFungiblePortionToItsRealPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); WorldId world = new WorldId("frontier:fungible-player-withdrawal-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(world, 91L), new EphemeralStore(), 10_000);
        SubjectId container = new SubjectId("container:1-depot"); BlockPos chestPosition = helper.absolutePos(new BlockPos(60, 8, 0));
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, chestPosition, container);
        helper.assertTrue(chest != null, "the fungible fixture needs an owned chest");
        FrontierV3CommandSubmission.submit(runtime, "fungible-player-withdrawal-prepare", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.PREPARED));
        FrontierV3CommandSubmission.submit(runtime, "fungible-player-withdrawal-active", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.ACTIVE));
        chest.setItem(0, new ItemStack(Items.WHEAT, 64)); chest.setChanged();
        FrontierV3FungibleResourceObservationExecutor.tick(level, runtime);
        FrontierWorldState bound = state(runtime);
        helper.assertTrue(!bound.inventory().fungibleResources().bindings().isEmpty(), "the full observed stack must become HOT before a player may split it");

        var player = helper.makeMockServerPlayerInLevel();
        chest.setItem(0, new ItemStack(Items.WHEAT, 32)); player.getInventory().setItem(0, new ItemStack(Items.WHEAT, 32)); chest.setChanged();
        FrontierV3FungibleResourceObservationExecutor.tick(level, runtime);
        FrontierWorldState moved = state(runtime);
        var playerAccount = moved.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody().equals(new ResourceCustody.Player(player.getUUID()))).findFirst().orElse(null);
        helper.assertTrue(playerAccount != null && playerAccount.lotQuantities().values().stream().mapToInt(Integer::intValue).sum() == 32,
                "one physical partial withdrawal must create one exact player portion");
        helper.assertTrue(moved.inventory().fungibleResources().bindings().values().stream().anyMatch(binding -> binding.accountId().equals(playerAccount.id())),
                "the player portion remains HOT and therefore cannot become concurrent COLD stock");
        helper.assertTrue(moved.inventory().fungibleResources().totalQuantity(new SubjectId("settlement:1"), "minecraft:wheat") == 64,
                "the split must conserve every ordinary item unit");
        chest.setItem(0, new ItemStack(Items.WHEAT, 64)); player.getInventory().setItem(0, ItemStack.EMPTY); chest.setChanged();
        FrontierV3FungibleResourceObservationExecutor.tick(level, runtime);
        FrontierWorldState returned = state(runtime);
        helper.assertTrue(returned.inventory().fungibleResources().accounts().values().stream()
                        .noneMatch(account -> account.custody().equals(new ResourceCustody.Player(player.getUUID()))),
                "the same player portion must merge back into its retained depot account exactly once");
        helper.assertTrue(returned.inventory().fungibleResources().totalQuantity(new SubjectId("settlement:1"), "minecraft:wheat") == 64,
                "the physical merge must retain the original conserved total");
        runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void activeChestHandsOneFungibleStackToItsObservedHopper(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); WorldId world = new WorldId("frontier:fungible-hopper-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(world, 91L), new EphemeralStore(), 10_000);
        SubjectId container = new SubjectId("container:1-depot"); BlockPos chestPosition = helper.absolutePos(new BlockPos(62, 8, 0));
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, chestPosition, container);
        helper.assertTrue(chest != null, "the hopper fixture needs an owned chest");
        FrontierV3CommandSubmission.submit(runtime, "fungible-hopper-prepare", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.PREPARED));
        FrontierV3CommandSubmission.submit(runtime, "fungible-hopper-active", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.ACTIVE));
        chest.setItem(0, new ItemStack(Items.WHEAT, 64)); chest.setChanged();
        FrontierV3FungibleResourceObservationExecutor.tick(level, runtime);
        BlockPos hopperPosition = chestPosition.east(); level.setBlock(hopperPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(hopperPosition, Blocks.HOPPER.defaultBlockState(), 3);
        HopperBlockEntity hopper = (HopperBlockEntity) level.getBlockEntity(hopperPosition);
        hopper.setItem(0, chest.removeItemNoUpdate(0)); hopper.setChanged(); chest.setChanged();
        FrontierV3FungibleResourceObservationExecutor.tick(level, runtime);
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
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(world, 91L), new EphemeralStore(), 10_000);
        SubjectId container = new SubjectId("container:1-depot"); BlockPos chestPosition = helper.absolutePos(new BlockPos(64, 8, 0));
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, chestPosition, container);
        helper.assertTrue(chest != null, "the drop fixture needs an owned chest");
        FrontierV3CommandSubmission.submit(runtime, "fungible-drop-prepare", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.PREPARED));
        FrontierV3CommandSubmission.submit(runtime, "fungible-drop-active", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.ACTIVE));
        chest.setItem(0, new ItemStack(Items.WHEAT, 64)); chest.setChanged();
        FrontierV3FungibleResourceObservationExecutor.tick(level, runtime);
        ItemEntity drop = new ItemEntity(level, chestPosition.getX() + 0.5D, chestPosition.getY() + 0.5D, chestPosition.getZ() + 0.5D,
                chest.removeItemNoUpdate(0)); chest.setChanged(); level.addFreshEntity(drop);
        FrontierV3FungibleResourceObservationExecutor.tick(level, runtime);
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
    }

    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
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
