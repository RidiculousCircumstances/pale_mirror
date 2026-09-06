package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceResource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-block proofs for the bounded source warehouse hand-off. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxWarehouseGameTests {
    private SourceGrayboxWarehouseGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void sourceCreatedContainerCarriesRealStacksAndExactBinding(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        BlockPos position = anchor.offset(2, 1, 2);
        helper.getLevel().setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);

        BarrelBlockEntity barrel = SourceGrayboxWarehouseRuntime.ensureContainer(helper.getLevel(), position,
                "settlement:1:warehouse:ore:0", ReferenceResource.ORE);
        SourceGrayboxWarehouseLedger.Binding binding = new SourceGrayboxWarehouseLedger.Binding("settlement:1:warehouse:ore:0", 1,
                ReferenceResource.ORE, position.getX(), position.getY(), position.getZ(), 0, SourceGrayboxWarehouseLedger.State.ACTIVE);

        helper.assertTrue(barrel != null && SourceGrayboxWarehouseRuntime.matches(barrel, binding),
                "an empty source position must become one exact PM-tagged barrel, not a generic container");
        helper.assertValueEqual(SourceGrayboxWarehouseRuntime.insert(barrel, SourceGrayboxWarehouseRuntime.item(ReferenceResource.ORE), 127), 127,
                "the materializer must use ordinary Minecraft stacks without a hidden inventory type");
        helper.assertValueEqual(SourceGrayboxWarehouseRuntime.count(barrel, SourceGrayboxWarehouseRuntime.item(ReferenceResource.ORE)), 127,
                "the exact ordinary item count is the only value that can later become a warehouse receipt");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void foreignBarrelIsNeverAdoptedAsASourceWarehouse(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        BlockPos position = anchor.offset(6, 1, 2);
        helper.getLevel().setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(position, Blocks.BARREL.defaultBlockState(), 3);

        BarrelBlockEntity adopted = SourceGrayboxWarehouseRuntime.ensureContainer(helper.getLevel(), position,
                "settlement:1:warehouse:food:0", ReferenceResource.FOOD);

        helper.assertTrue(adopted == null,
                "a player or foreign barrel at a source shelf position must remain a visible obstruction, never become an economic boundary");
        helper.assertValueEqual(helper.getLevel().getBlockState(position).getBlock(), Blocks.BARREL,
                "rejecting adoption must not replace or destroy the foreign container");
        helper.succeed();
    }
}
