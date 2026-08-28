package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Physical ownership and exact-stack postconditions for the first v3 executor. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3CargoHandoffGameTests {
    private FrontierV3CargoHandoffGameTests() { }

    @GameTest(batch = "pm-frontier-v3-cargo-ownership", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void cargoReceiverClaimsOnlyItsFreshChestAndBindsExactStackIdentity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos receiver = helper.absolutePos(new BlockPos(0, 8, 0));
        level.setBlock(receiver.below(), Blocks.STONE.defaultBlockState(), 3);
        SubjectId container = new SubjectId("container:frontier-v3-game-test-store");
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.ownedChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(receiver, container));
        helper.assertTrue(chest != null, "a supported air cell may receive the executor's fresh receiver chest");
        helper.assertValueEqual(chest.getPersistentData().getString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY), container.value(),
                "the fresh chest must carry its canonical receiver identity");

        ExactItemStack item = new ExactItemStack(new SubjectId("item:frontier-v3-game-test-bread"), "minecraft:bread", 7,
                new InventoryCustody.ContainerSlot(container, 0));
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(item));
        helper.assertTrue(FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(0), item),
                "confirmation requires the exact materialized item identity, not merely its kind and count");
        chest.getItem(0).setCount(6);
        helper.assertFalse(FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(0), item),
                "a changed physical stack must not confirm canonical cargo delivery");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-cargo-conflict", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void cargoReceiverNeverClaimsAnExistingPlayerChest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos receiver = helper.absolutePos(new BlockPos(4, 8, 0));
        level.setBlock(receiver.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(receiver, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity playerChest = (ChestBlockEntity) level.getBlockEntity(receiver);
        helper.assertTrue(playerChest != null, "the test must create an unowned chest");

        ChestBlockEntity claimed = FrontierV3CargoHandoffExecutor.ownedChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(receiver, new SubjectId("container:frontier-v3-game-test-store")));
        helper.assertTrue(claimed == null, "an existing unowned chest is a conflict, even when it is empty");
        helper.assertTrue(playerChest.getPersistentData().getString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY).isBlank(),
                "the executor must leave a player/world chest unmodified");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-player-custody", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void playerInventoryObservationRequiresTheExactTaggedStack(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        SubjectId container = new SubjectId("container:frontier-v3-player-custody");
        ExactItemStack expected = new ExactItemStack(new SubjectId("item:frontier-v3-player-bread"), "minecraft:bread", 7,
                new InventoryCustody.ContainerSlot(container, 0));
        player.getInventory().setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(expected));
        helper.assertTrue(FrontierV3InventoryObservationExecutor.hasExactItem(player, expected),
                "player custody observation accepts only the exact canonical NBT-tagged stack");
        player.getInventory().getItem(0).setCount(6);
        helper.assertFalse(FrontierV3InventoryObservationExecutor.hasExactItem(player, expected),
                "a changed stack must not be adopted as the canonical player-held item");
        helper.succeed();
    }
}
