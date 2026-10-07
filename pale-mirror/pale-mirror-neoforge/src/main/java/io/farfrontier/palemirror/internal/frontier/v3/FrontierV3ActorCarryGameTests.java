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
        helper.succeed();
    }
}
