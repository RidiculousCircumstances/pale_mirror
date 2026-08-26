package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceResource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-block proof for the exact operation and field-post cargo hand-off. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxCargoGameTests {
    private SourceGrayboxCargoGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void sourceCargoUsesOneTaggedRealBarrelAndNeverAdoptsAForeignContainer(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        BlockPos sourcePosition = anchor.offset(2, 1, 2);
        helper.getLevel().setBlock(sourcePosition.below(), Blocks.STONE.defaultBlockState(), 3);
        String id = "cargo-container:operation:991:cargo:food";
        BarrelBlockEntity barrel = SourceGrayboxWarehouseRuntime.ensureContainer(helper.getLevel(), sourcePosition, id, ReferenceResource.FOOD);
        SourceGrayboxCargoLedger.Binding binding = new SourceGrayboxCargoLedger.Binding(id, "operation:991:cargo:food", "operation", 991,
                ReferenceResource.FOOD, sourcePosition.getX(), sourcePosition.getY(), sourcePosition.getZ(), 0,
                SourceGrayboxCargoLedger.State.ACTIVE);

        helper.assertTrue(barrel != null && SourceGrayboxWarehouseRuntime.matches(barrel, binding.id(), binding.resource()),
                "an operation pallet must receive one exact PM-tagged ordinary barrel, not an adapter-only counter");
        helper.assertValueEqual(SourceGrayboxWarehouseRuntime.insert(barrel, SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD), 127), 127,
                "field cargo must use ordinary item stacks with the shared one-unit-per-stack scale");
        helper.assertValueEqual(SourceGrayboxWarehouseRuntime.count(barrel, SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD)), 127,
                "the exact real item count is the only value eligible to enter a cargo observation");

        BlockPos foreignPosition = anchor.offset(6, 1, 2);
        helper.getLevel().setBlock(foreignPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(foreignPosition, Blocks.BARREL.defaultBlockState(), 3);
        helper.assertTrue(SourceGrayboxWarehouseRuntime.ensureContainer(helper.getLevel(), foreignPosition,
                        "cargo-container:operation:991:cargo:medicine", ReferenceResource.MEDICINE) == null,
                "a player barrel at a source cargo slot must remain foreign instead of silently entering operation custody");
        helper.assertValueEqual(helper.getLevel().getBlockState(foreignPosition).getBlock(), Blocks.BARREL,
                "rejecting foreign cargo ownership must not remove or replace the player's barrel");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void retiredCargoNeverDeletesAPlayerItemFromItsFormerContainer(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        BlockPos position = anchor.offset(2, 1, 2);
        helper.getLevel().setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        String id = "cargo-container:operation:991:cargo:food";
        BarrelBlockEntity barrel = SourceGrayboxWarehouseRuntime.ensureContainer(helper.getLevel(), position, id, ReferenceResource.FOOD);
        helper.assertTrue(barrel != null, "the PM-owned cargo barrel must exist before its source owner retires");
        SourceGrayboxWarehouseRuntime.insert(barrel, SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD), 64);
        barrel.setItem(1, new ItemStack(Items.COAL, 5));

        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        SourceGrayboxCargoLedger.Binding binding = new SourceGrayboxCargoLedger.Binding(id, "operation:991:cargo:food", "operation", 991,
                ReferenceResource.FOOD, position.getX(), position.getY(), position.getZ(), 64, SourceGrayboxCargoLedger.State.ACTIVE);
        helper.assertTrue(data.cargoLedger().put(binding), "the former physical cargo slot must have exact durable ownership");
        new SourceGrayboxCargoRuntime().materialize(helper.getLevel(), data, new SourceGrayboxMaterializer());

        helper.assertValueEqual(helper.getLevel().getBlockState(position).getBlock(), Blocks.BARREL,
                "retiring a source cargo must retain the container when it contains a player item");
        BarrelBlockEntity retained = (BarrelBlockEntity) helper.getLevel().getBlockEntity(position);
        helper.assertValueEqual(retained.getItem(1).getItem(), Items.COAL,
                "a source materializer may remove only its own retired resource, never a player item in the same container");
        helper.assertValueEqual(SourceGrayboxWarehouseRuntime.count(retained, SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD)), 0,
                "canonical source consumption must still remove only the retired PM resource stack");
        helper.assertValueEqual(data.cargoLedger().binding(id).state(), SourceGrayboxCargoLedger.State.BLOCKED,
                "the mixed container must become a durable visible conflict instead of being retried over the player's item");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void retiredCargoReconcilesBeforeControlledCleanupInsteadOfBecomingAForeignConflict(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        BlockPos position = anchor.offset(2, 1, 2);
        helper.getLevel().setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        String id = "cargo-container:operation:991:cargo:food";
        BarrelBlockEntity barrel = SourceGrayboxWarehouseRuntime.ensureContainer(helper.getLevel(), position, id, ReferenceResource.FOOD);
        helper.assertTrue(barrel != null, "the PM-owned cargo barrel must exist before a source-day operation retires it");

        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        SourceGrayboxCargoLedger.Binding binding = new SourceGrayboxCargoLedger.Binding(id, "operation:991:cargo:food", "operation", 991,
                ReferenceResource.FOOD, position.getX(), position.getY(), position.getZ(), 0, SourceGrayboxCargoLedger.State.ACTIVE);
        helper.assertTrue(data.cargoLedger().put(binding), "the cargo lifecycle starts from a durable active binding");
        SourceGrayboxCargoRuntime runtime = new SourceGrayboxCargoRuntime();
        helper.assertTrue(!runtime.reconcileInbound(helper.getLevel(), data, new SourceGrayboxMaterializer()),
                "an absent source descriptor is controlled retirement, not a false foreign-container receipt");

        runtime.materialize(helper.getLevel(), data, new SourceGrayboxMaterializer());
        helper.assertValueEqual(helper.getLevel().getBlockState(position).getBlock(), Blocks.AIR,
                "an empty PM container may retire after its canonical operation cargo disappears");
        helper.assertTrue(data.cargoLedger().binding(id) == null,
                "controlled retirement must compact the durable hand-off instead of retaining a stale conflict forever");
        helper.succeed();
    }
}
