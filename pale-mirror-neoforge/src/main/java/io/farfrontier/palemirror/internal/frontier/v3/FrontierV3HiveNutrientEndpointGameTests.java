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

/** Physical endpoint proof: one real tagged stack leaves and later enters only its named slots. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3HiveNutrientEndpointGameTests {
    private FrontierV3HiveNutrientEndpointGameTests() { }

    @GameTest(batch = "pm-frontier-v3-hive-nutrient", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void movesOneExactNutrientAcrossSeparateLoadedEndpointVisits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos sourcePosition = helper.absolutePos(new BlockPos(38, 8, 0)); BlockPos targetPosition = helper.absolutePos(new BlockPos(42, 8, 0));
        for (BlockPos position : java.util.List.of(sourcePosition, targetPosition)) { level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(position, Blocks.CHEST.defaultBlockState(), 3); }
        ChestBlockEntity source = (ChestBlockEntity) level.getBlockEntity(sourcePosition), target = (ChestBlockEntity) level.getBlockEntity(targetPosition);
        ExactItemStack nutrient = new ExactItemStack(new SubjectId("item:hive-nutrient-endpoint-game-test"), new SubjectId("hive:frontier"), "minecraft:rotten_flesh", 64,
                new InventoryCustody.ContainerSlot(new SubjectId("container:hive-east-store"), 0));
        source.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(nutrient));
        helper.assertTrue(FrontierV3HiveNutrientEndpointExecutor.removeExact(source, 0, nutrient), "only the owned exact source stack may depart after durable RUNNING");
        helper.assertTrue(source.getItem(0).isEmpty(), "source restart inspection sees the deliberate exact empty slot");
        helper.assertTrue(FrontierV3HiveNutrientEndpointExecutor.insertExact(target, 0, nutrient), "the same tagged stack enters its named empty destination slot");
        helper.assertTrue(FrontierV3CargoHandoffExecutor.exactMatch(target.getItem(0), nutrient), "arrival retains the canonical item identity");
        target.setItem(1, FrontierV3CargoHandoffExecutor.materializedStack(nutrient));
        helper.assertTrue(!FrontierV3HiveNutrientEndpointExecutor.insertExact(target, 1, nutrient), "a foreign/nonempty target slot is conflict evidence and is never overwritten");
        helper.succeed();
    }
}
