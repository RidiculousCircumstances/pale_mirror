package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
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

/** Loaded-world proof for the non-replayable exact former-defender hand to depot-slot return. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3EquipmentReturnGameTests {
    private FrontierV3EquipmentReturnGameTests() { }

    @GameTest(batch = "pm-frontier-v3-equipment-return", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void movesOnlyTheExactTaggedVillagerSwordIntoTheNamedEmptyDepotSlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(34, 8, 0));
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(position); Villager resident = EntityType.VILLAGER.create(level);
        if (resident == null) throw new IllegalStateException("test Villager could not be created");
        resident.setPos(position.getX() + 2.5D, position.getY(), position.getZ() + 0.5D); level.addFreshEntity(resident);
        SubjectId container = new SubjectId("container:equipment-return-game-test"), itemId = new SubjectId("item:equipment-return-game-test");
        ExactItemStack sword = new ExactItemStack(itemId, new SubjectId("settlement:northwatch"), "minecraft:iron_sword", 1,
                new InventoryCustody.Actor(new SubjectId("resident:equipment-return-game-test")));
        resident.setItemSlot(EquipmentSlot.MAINHAND, FrontierV3CargoHandoffExecutor.materializedStack(sword));
        FrontierV3EquipmentReturnExecutor.Target target = new FrontierV3EquipmentReturnExecutor.Target(new SubjectId("assault:equipment-return-game-test"),
                new SubjectId("resident:equipment-return-game-test"), sword, new InventoryCustody.ContainerSlot(container, 0), position);
        helper.assertTrue(FrontierV3EquipmentReturnExecutor.handOff(chest, resident, target), "the exact tagged defender stack moves only to its named empty depot slot");
        helper.assertTrue(resident.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(0), sword),
                "restart inspection can prove one empty hand and the same exact depot stack");
        resident.setItemSlot(EquipmentSlot.MAINHAND, FrontierV3CargoHandoffExecutor.materializedStack(sword)); chest.setItem(0, Items.STICK.getDefaultInstance());
        helper.assertTrue(!FrontierV3EquipmentReturnExecutor.handOff(chest, resident, target), "a changed target slot is conflict evidence and is never overwritten");
        helper.assertTrue(FrontierV3EquipmentReturnExecutor.commandId("running", new Revision(9_876L)).value()
                        .equals("executor:equipment-return-running-r9876"),
                "an executor command stays valid even when the semantic intent ID is long");
        helper.succeed();
    }
}
