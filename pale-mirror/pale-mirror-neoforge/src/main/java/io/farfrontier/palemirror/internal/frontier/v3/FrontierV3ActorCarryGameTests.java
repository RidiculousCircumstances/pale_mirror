package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ActorCarryGameTests {
    private FrontierV3ActorCarryGameTests() { }

    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void pocketInteractionAndEntitySavePreserveBothEquipmentHands(GameTestHelper helper) {
        var position = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(position, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(position);
        SubjectId container = new SubjectId("container:portable-resource-test");
        chest.getPersistentData().putString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY, container.value());
        chest.setItem(4, new ItemStack(Items.BREAD, 8));
        Villager actor = EntityType.VILLAGER.create(helper.getLevel());
        if (actor == null) throw new IllegalStateException("native carry test has no actor body");
        actor.setNoAi(true);
        actor.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.WHEAT, 37));
        actor.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT, 21));
        SubjectId actorId = new SubjectId("resident:portable-resource-test");
        var pocket = new ActorItemSlot.Pocket(0);
        var order = new ActorContainerItemOrder(actorId, actorId, ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(new SubjectId("custody:portable-source"),
                        new ResourceCustody.Container(container), new SubjectId("custody:portable-portion"),
                        new ResourceCustody.Actor(actorId), Optional.empty(), "minecraft:bread",
                        Map.of(new SubjectId("lot:portable-bread"), 2)),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(container), SurfaceAnchor.at(2, 2, 2), pocket, 1, 1);
        var source = List.of(new MaterialSourceSelection.Slice(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(container, 4)), 8, 2, 1));
        var transfer = new FrontierV3ActorItemTransfer.FungibleStep(order, chest, actor, actor.getUUID(), source, -1);
        helper.assertTrue(transfer.before() && transfer.apply() && transfer.after(), "physical pocket TAKE completes");
        helper.assertTrue(!transfer.apply() && chest.getItem(4).getCount() == 6, "retry cannot take a second portion");
        var saved = new CompoundTag(); actor.saveWithoutId(saved);
        Villager restored = EntityType.VILLAGER.create(helper.getLevel());
        if (restored == null) throw new IllegalStateException("native carry test cannot restore actor");
        restored.load(saved);
        helper.assertTrue(FrontierV3ActorResourceSlots.get(restored, pocket).is(Items.BREAD)
                        && FrontierV3ActorResourceSlots.get(restored, pocket).getCount() == 2,
                "physical inventory survives native entity save/load");
        FrontierV3ActorResourceSlots.set(restored, pocket, ItemStack.EMPTY);
        helper.assertTrue(restored.getOffhandItem().is(Items.WHEAT) && restored.getOffhandItem().getCount() == 37
                        && restored.getMainHandItem().is(Items.IRON_INGOT) && restored.getMainHandItem().getCount() == 21,
                "consuming the pocket portion leaves work cargo and equipment untouched");
        var workActor = new SubjectId("resident:prepared-place-test");
        var cargo = new SubjectId("custody:prepared-place-cargo");
        var bread = new SubjectId("lot:prepared-place-bread");
        var place = new ActorContainerItemOrder(workActor, workActor, ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Fungible(cargo, new ResourceCustody.Actor(workActor),
                        new SubjectId("custody:prepared-place-depot"), new ResourceCustody.Container(container),
                        Optional.empty(), "minecraft:bread", Map.of(bread, 64)),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(container), SurfaceAnchor.at(2, 2, 2),
                new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN), 1, 1);
        actor.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BREAD, 64));
        var delivery = new FrontierV3ActorItemTransfer.FungibleStep(place, chest, actor, actor.getUUID(),
                List.of(new MaterialSourceSelection.Slice(new PhysicalStackAddress.ActorHand(workActor, actor.getUUID(),
                        ActorContainerItemOrder.Hand.MAIN), 64, 64, 1)), 5);
        helper.assertTrue(delivery.before(), "prepared destination starts empty");
        chest.setItem(5, new ItemStack(Items.STONE, 1));
        helper.assertTrue(delivery.unappliedDestinationOccupied() && !delivery.after() && !delivery.apply(),
                "player occupancy is a provably unapplied transfer, not success or permission to overwrite");
        helper.assertTrue(actor.getMainHandItem().getCount() == 64 && chest.getItem(5).is(Items.STONE),
                "failed precondition preserves both cargo and player stock");
        chest.setItem(5, ItemStack.EMPTY);
        helper.assertTrue(delivery.placeDestination() && !delivery.placeDestination()
                        && delivery.destinationAppliedWithSourceRetained(), "destination-first split is exact and cannot duplicate stock");
        var splitSave = new CompoundTag(); actor.saveWithoutId(splitSave);
        Villager splitRestored = EntityType.VILLAGER.create(helper.getLevel());
        if (splitRestored == null) throw new IllegalStateException("native transfer cannot restore source body");
        splitRestored.load(splitSave);
        var resumed = new FrontierV3ActorItemTransfer.FungibleStep(place, chest, splitRestored, splitRestored.getUUID(),
                List.of(new MaterialSourceSelection.Slice(new PhysicalStackAddress.ActorHand(workActor, splitRestored.getUUID(),
                        ActorContainerItemOrder.Hand.MAIN), 64, 64, 1)), 5);
        helper.assertTrue(resumed.releasePlacedSource() && resumed.after() && !resumed.releasePlacedSource(),
                "native source save split completes once without repeating destination");
        helper.assertTrue(splitRestored.getOffhandItem().getCount() == 37 && chest.getItem(5).getCount() == 64,
                "recovery preserves unrelated equipment and exact destination quantity");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void horseMenuMapsOnlyCargoAndLeavesEquipmentOutsideTheResourcePort(GameTestHelper helper) {
        var donkey = EntityType.DONKEY.create(helper.getLevel());
        if (donkey == null || !donkey.getSlot(499).set(new ItemStack(Items.CHEST)))
            throw new IllegalStateException("native menu test has no chest donkey");
        var player = helper.makeMockServerPlayerInLevel();
        var menu = new net.minecraft.world.inventory.HorseInventoryMenu(1, player.getInventory(), donkey.getInventory(), donkey, 5);
        helper.assertTrue(menu instanceof io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3HorseMenuAccessor,
                "the real HorseInventoryMenu must expose its exact body through the registered accessor");
        var access = (io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3HorseMenuAccessor) menu;
        helper.assertTrue(access.frontierV3$horse() == donkey, "menu body is exact, not nearby discovery");
        var container = new SubjectId("container:menu-cargo");
        var target = new FrontierV3ContainerMenuTarget(new ContainerRecord(container, new SubjectId("settlement:menu"), 15),
                FrontierV3PhysicalContainer.attached(container, 15, donkey), 2);
        helper.assertTrue(target.equipmentSlot(0) && target.equipmentSlot(1) && target.cargoSlot(0) == -1
                        && target.cargoSlot(2) == 0 && target.cargoSlot(16) == 14 && target.cargoSlot(17) == -1,
                "equipment, cargo and player inventory have distinct declared menu ranges");
        menu.getSlot(2).set(new ItemStack(Items.COBBLESTONE, 9));
        helper.assertTrue(target.physical().inventory().getItem(0).getCount() == 9
                        && donkey.getInventory().getItem(0).isEmpty(), "cargo mapping never writes the saddle");
        helper.succeed();
    }
}
