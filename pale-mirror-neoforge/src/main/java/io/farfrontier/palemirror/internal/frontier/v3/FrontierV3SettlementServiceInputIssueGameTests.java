package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded-world regression for one exact service input leaving its declared depot slot. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3SettlementServiceInputIssueGameTests {
    private FrontierV3SettlementServiceInputIssueGameTests() { }

    @GameTest(batch = "pm-frontier-v3-settlement-service-input", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void movesOnlyTheExactDepotReagentIntoTheEmptyNamedWorkerHand(GameTestHelper helper) {
        // `bastion/mobs/empty` GameTests run in neighbouring tiny cells.  Keep this fixture in
        // this test's own interior; a distant convenience coordinate can become another test's
        // world and turn its result into an order-dependent false failure.
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(1, 8, 0));
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(position); Villager worker = EntityType.VILLAGER.create(level);
        if (worker == null) throw new IllegalStateException("test service worker could not be created");
        worker.setPos(position.getX() + 2.5D, position.getY(), position.getZ() + 0.5D); level.addFreshEntity(worker);
        SubjectId container = new SubjectId("container:service-input-game-test"), itemId = new SubjectId("item:service-reagent-game-test");
        InventoryCustody.ContainerSlot slot = new InventoryCustody.ContainerSlot(container, 0);
        ExactItemStack reagent = new ExactItemStack(itemId, new SubjectId("settlement:northwatch"), "minecraft:glowstone_dust", 1, slot);
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(reagent));
        helper.assertTrue(FrontierV3SettlementServiceInputIssueExecutor.handOff(chest, worker, reagent, slot),
                "the exact declared reagent leaves its depot slot only for the empty named worker hand");
        helper.assertTrue(chest.getItem(0).isEmpty() && FrontierV3CargoHandoffExecutor.exactMatch(worker.getItemBySlot(EquipmentSlot.MAINHAND), reagent),
                "the completed hand-off has one inspectable empty source and the same tagged worker stack");
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(reagent)); worker.setItemSlot(EquipmentSlot.MAINHAND, Items.STICK.getDefaultInstance());
        helper.assertTrue(!FrontierV3SettlementServiceInputIssueExecutor.handOff(chest, worker, reagent, slot),
                "a nonempty worker hand is player/world conflict evidence and is never overwritten");
        // The full suite reuses a dense test grid and the village-observer test deliberately
        // scans nearby real villagers.  This fixture is evidence only, so leave no actor for a
        // later independent observer to mistake for its own input.
        worker.discard();
        helper.succeed();
    }
}
