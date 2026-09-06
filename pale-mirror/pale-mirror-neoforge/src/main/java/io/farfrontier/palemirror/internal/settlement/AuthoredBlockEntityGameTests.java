package io.farfrontier.palemirror.internal.settlement;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Crash-replay coverage for finite authored container and machine payloads. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AuthoredBlockEntityGameTests {
    private AuthoredBlockEntityGameTests() { }

    @GameTest(batch = "pm-authored-payload", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void authoredPayloadIsAppliedOnceAndNeverRefillsAfterLooting(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(position);
        helper.assertTrue(chest != null, "test chest must materialize its block entity");
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        var payload = chest.saveWithFullMetadata(helper.getLevel().registryAccess());
        chest.clearContent();

        String marker = "pm:test:authored-payload";
        helper.assertTrue(AuthoredSettlementProjectRuntime.applyBlockEntityPayloadOnce(
                chest, payload, helper.getLevel().registryAccess(), marker),
                "first materialization must apply the curated payload");
        helper.assertValueEqual(chest.getItem(0).getCount(), 3,
                "first materialization must retain finite curated contents");

        chest.clearContent();
        helper.assertTrue(!AuthoredSettlementProjectRuntime.applyBlockEntityPayloadOnce(
                chest, payload, helper.getLevel().registryAccess(), marker),
                "crash replay must observe the persisted idempotency marker");
        helper.assertTrue(chest.getItem(0).isEmpty(),
                "replayed materialization must not refill a looted container");
        helper.succeed();
    }
}
