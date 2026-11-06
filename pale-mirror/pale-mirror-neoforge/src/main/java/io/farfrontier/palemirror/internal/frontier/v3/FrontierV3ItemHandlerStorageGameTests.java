package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.StorageBlockKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ItemHandlerStorageGameTests {
    private static final class Owner extends BarrelBlockEntity implements FrontierV3ItemHandlerOwner {
        private final ItemStackHandler inventory;
        Owner(BlockPos position,ItemStackHandler inventory) { super(position,Blocks.BARREL.defaultBlockState());this.inventory=inventory; }
        public IItemHandlerModifiable frontierV3$inventory() { return inventory; }
        public StorageBlockKind frontierV3$storageKind() { return StorageBlockKind.BARREL; }
    }
    @GameTest(batch="pm-frontier-v3-container-socket",templateNamespace="minecraft",template="bastion/mobs/empty",timeoutTicks=30)
    public static void installedRackExposesItsRealInventoryAndNativeMenu(GameTestHelper helper) {
        var at=helper.absolutePos(new BlockPos(1,1,1));var level=helper.getLevel();
        var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("minecolonies:blockminecoloniesrack"));
        helper.assertTrue(block!=Blocks.AIR,"the pinned optional rack dependency must be installed for this provider test");
        level.setBlock(at.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(at,block.defaultBlockState(),3);
        var entity=level.getBlockEntity(at);var candidate=FrontierV3FixedContainerProviders.candidate(entity).orElseThrow();
        helper.assertTrue(candidate.kind()==StorageBlockKind.RACK && candidate.inventory().getContainerSize()==27,"real rack owns one exact 27-slot member");
        candidate.inventory().setItem(0,new ItemStack(Items.COAL,13));
        var handler=((FrontierV3ItemHandlerOwner)entity).frontierV3$inventory();
        helper.assertTrue(handler.getStackInSlot(0).getCount()==13 && FrontierV3ItemHandlerStorage.owner(handler)==entity,"native and domain adapter share the same stock and owner");
        var player=helper.makeMockServerPlayerInLevel();
        var menu=((net.minecraft.world.MenuProvider)entity).createMenu(1,player.getInventory(),player);
        helper.assertTrue(FrontierV3ContainerMenuTarget.supported(menu),"native rack menu enters the common click protocol");
        var slot=(SlotItemHandler)menu.getSlot(0);var leaf=FrontierV3ItemHandlerStorage.leaf(slot.getItemHandler(),slot.getSlotIndex());
        helper.assertTrue(FrontierV3ItemHandlerStorage.owner(leaf.handler())==entity && leaf.index()==0,"menu exposes the producer-declared leaf inventory");
        helper.succeed();
    }
    @GameTest(batch="pm-frontier-v3-container-socket",templateNamespace="minecraft",template="bastion/mobs/empty",timeoutTicks=30)
    public static void nativeCompositionAndInventoryBridgeRetainExactOwners(GameTestHelper helper) {
        var first=new ItemStackHandler(27);var second=new ItemStackHandler(27);
        var owner=new Owner(helper.absolutePos(new BlockPos(1,1,1)),first);
        var inventory=FrontierV3ItemHandlerStorage.inventory(owner);
        inventory.setItem(3,new ItemStack(Items.COAL,17));
        helper.assertTrue(first.getStackInSlot(3).getCount()==17,"ordinary storage writes the real native slot");
        helper.assertTrue(inventory.removeItem(3,5).getCount()==5 && first.getStackInSlot(3).getCount()==12,"withdrawal preserves the remaining native stock");
        var combined=new CombinedInvWrapper(first,second);
        var leaf=FrontierV3ItemHandlerStorage.leaf(combined,30);
        helper.assertTrue(leaf.handler()==second && leaf.index()==3,"composition uses declared native offsets");
        try {
            FrontierV3ItemHandlerStorage.register(new Owner(owner.getBlockPos(),first),StorageBlockKind.BARREL,first);
            throw new IllegalStateException("competing owner accepted");
        } catch(IllegalStateException expected) { helper.assertTrue(expected.getMessage().contains("competing"),"exact owner conflict is explicit"); }
        helper.assertTrue(FrontierV3ItemHandlerStorage.owner(first)==owner,"rejected registration cannot replace the existing owner");
        helper.succeed();
    }
}
