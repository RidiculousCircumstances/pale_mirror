package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.CargoLoadingStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.ContractStatus;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.SupplyContract;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded-chunk proof for depot-to-cargo physical removal and its conservative restart predicate. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3CargoLoadingGameTests {
    private FrontierV3CargoLoadingGameTests() { }

    @GameTest(batch = "pm-frontier-v3-cargo-loading", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void removesOnlyTheExactTaggedDepotStackAndLeavesInspectablePostcondition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(34, 8, 0));
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(position);
        SubjectId settlement = new SubjectId("settlement:1"), container = new SubjectId("container:cargo-loading-game-test");
        SupplyContract contract = new SupplyContract(new SubjectId("contract:cargo-loading-game-test"), settlement, new SubjectId("hive:frontier"),
                new SubjectId("cargo:cargo-loading-game-test"), "minecraft:bread", 64, ContractStatus.ORDERED);
        ExactItemStack item = new ExactItemStack(new SubjectId("item:cargo-loading-game-test"), settlement, "minecraft:bread", 64,
                new InventoryCustody.ContainerSlot(container, 0));
        CargoLoadingStateSupport.Target target = new CargoLoadingStateSupport.Target(contract, item, (InventoryCustody.ContainerSlot) item.custody(),
                new BlockPosition(position.getX(), position.getY(), position.getZ()));
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(item));
        helper.assertTrue(FrontierV3CargoLoadingExecutor.matches(chest, target), "only the exact tagged canonical stack satisfies the precondition");
        helper.assertTrue(FrontierV3CargoLoadingExecutor.remove(chest, target), "the executor removes exactly that one physical stack after durable RUNNING");
        helper.assertTrue(chest.getItem(0).isEmpty(), "an empty owned slot is the sole successful restart postcondition");
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(item)); chest.getItem(0).shrink(1);
        helper.assertTrue(!FrontierV3CargoLoadingExecutor.remove(chest, target), "a player/world-altered stack is conflict evidence and is never removed");
        helper.succeed();
    }
}
