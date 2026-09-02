package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
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

/** Loaded-world proof for the non-replayable exact depot-to-resident hand-off. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3EquipmentIssueGameTests {
    private FrontierV3EquipmentIssueGameTests() { }

    @GameTest(batch = "pm-frontier-v3-equipment-issue", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void movesOnlyTheExactTaggedDepotSwordIntoAnEmptyVillagerHand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(34, 8, 0));
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(position); Villager resident = EntityType.VILLAGER.create(level);
        if (resident == null) throw new IllegalStateException("test Villager could not be created");
        resident.setPos(position.getX() + 2.5D, position.getY(), position.getZ() + 0.5D); level.addFreshEntity(resident);
        SubjectId container = new SubjectId("container:equipment-issue-game-test"), itemId = new SubjectId("item:equipment-issue-game-test");
        ExactItemStack sword = new ExactItemStack(itemId, new SubjectId("settlement:northwatch"), "minecraft:iron_sword", 1,
                new InventoryCustody.ContainerSlot(container, 0));
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(sword));
        FrontierV3EquipmentIssueExecutor.Target target = new FrontierV3EquipmentIssueExecutor.Target(new SubjectId("assault:equipment-issue-game-test"),
                new SubjectId("resident:equipment-issue-game-test"), sword, new InventoryCustody.ContainerSlot(container, 0), position);
        helper.assertTrue(FrontierV3EquipmentIssueExecutor.handOff(chest, resident, target), "the exact tagged depot stack moves to the empty named hand");
        helper.assertTrue(chest.getItem(0).isEmpty() && FrontierV3CargoHandoffExecutor.exactMatch(resident.getItemBySlot(EquipmentSlot.MAINHAND), sword),
                "restart inspection can prove one empty source and the same exact hand stack");
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(sword)); resident.setItemSlot(EquipmentSlot.MAINHAND, Items.STICK.getDefaultInstance());
        helper.assertTrue(!FrontierV3EquipmentIssueExecutor.handOff(chest, resident, target), "a nonempty hand is conflict evidence and is never overwritten");
        helper.assertTrue(FrontierV3EquipmentIssueExecutor.commandId("running", new Revision(9_876L)).value()
                        .equals("executor:equipment-issue-running-r9876"),
                "an executor command stays valid even when the semantic intent ID is long");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-equipment-issue", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void engineeringHandOffRequiresTheNamedServiceStationRatherThanChestDistance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager resident = EntityType.VILLAGER.create(level);
        if (resident == null) throw new IllegalStateException("test Villager could not be created");
        BlockPosition assignedStation = new BlockPosition(41, 8, 0);
        resident.setPos(41.5D, 9.0D, 0.5D);
        helper.assertTrue(FrontierV3EngineeringDepotServicePort.bodyAtAssignedStation(resident, assignedStation),
                "an exact HOT body at its compiled station may use the depot service");
        resident.setPos(43.5D, 9.0D, 0.5D);
        helper.assertTrue(!FrontierV3EngineeringDepotServicePort.bodyAtAssignedStation(resident, assignedStation),
                "a body beside an incidental chest-radius location may not substitute for its assigned station");
        helper.succeed();
    }
}
