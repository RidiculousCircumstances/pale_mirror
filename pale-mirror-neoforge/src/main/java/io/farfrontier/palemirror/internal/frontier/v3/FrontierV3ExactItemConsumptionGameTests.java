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

/** Loaded-world evidence for the non-replayable full-stack biomass boundary. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ExactItemConsumptionGameTests {
    private FrontierV3ExactItemConsumptionGameTests() { }

    @GameTest(batch = "pm-frontier-v3-exact-consumption", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void consumesOnlyTheExactTaggedStackAndLeavesARecoverableEmptySlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos chestPosition = helper.absolutePos(new BlockPos(30, 8, 0));
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(chestPosition, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPosition);
        SubjectId container = new SubjectId("container:exact-consumption-game-test"), itemId = new SubjectId("item:exact-consumption-game-test");
        ExactItemStack item = new ExactItemStack(itemId, new SubjectId("organ:west-store"), "minecraft:rotten_flesh", 64, new InventoryCustody.ContainerSlot(container, 0));
        FrontierV3ExactItemConsumptionExecutor.Target target = new FrontierV3ExactItemConsumptionExecutor.Target(item, container, 0, chestPosition);
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(item));
        helper.assertTrue(FrontierV3ExactItemConsumptionExecutor.consume(chest, target), "the identity-tagged canonical stack is physically consumed");
        helper.assertTrue(FrontierV3ExactItemConsumptionExecutor.consumed(chest, target), "an empty exact slot is an inspectable restart postcondition");
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(item)); chest.getItem(0).shrink(1);
        helper.assertTrue(!FrontierV3ExactItemConsumptionExecutor.consume(chest, target), "an altered stack is conflict evidence and is never consumed");
        helper.succeed();
    }
}
