package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ResourceSiteDeliveryGameTests {
    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/mobs/empty",timeoutTicks=40)
    public static void fieldDeliveryChecksItsPreimageBeforeAnyWitnessExistsAndRefusesPlayerOccupancy(GameTestHelper h) {
        var state=FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:delivery-preimage"),139));
        var owner=state.bootstrap().settlements().getFirst().id();var depot=FrontierWorldState.depotId(owner);
        var container=new SimpleContainer(state.inventory().containers().get(depot).slotCount());
        for(int i=0;i<container.getContainerSize();i++) {
            var exact=state.inventory().itemAt(depot,i);
            var fungible=ReferenceContainerCustody.expectedFungibleSlot(state,depot,i);
            if(exact.isPresent())container.setItem(i,FrontierV3ExactItemPresentation.materializedStack(exact.orElseThrow()));
            else if(fungible.isPresent())container.setItem(i,FrontierV3FungibleItemStacks.stack(fungible.orElseThrow().itemKind(),fungible.orElseThrow().quantity()));
        }
        int slot=java.util.stream.IntStream.range(0,container.getContainerSize())
                .filter(i -> state.inventory().itemAt(depot,i).isEmpty()
                    && ReferenceContainerCustody.expectedFungibleSlot(state,depot,i).isEmpty()).findFirst().orElseThrow();
        h.assertTrue(FrontierV3ResourceSiteDeliveryExecutor.classifyBefore(state,container,depot,slot,5,"minecraft:wheat")
                ==FrontierV3ResourceSiteDeliveryExecutor.ChestState.BEFORE,"ordinary pre-effect check needs the job's explicit resource, not a nonexistent witness");
        container.setItem(slot,new ItemStack(Items.STONE));
        h.assertTrue(FrontierV3ResourceSiteDeliveryExecutor.classifyBefore(state,container,depot,slot,5,"minecraft:wheat")
                ==FrontierV3ResourceSiteDeliveryExecutor.ChestState.FOREIGN,"player occupancy is preserved and prevents delivery");
        container.setItem(slot,new ItemStack(Items.WHEAT,5));
        h.assertTrue(FrontierV3ResourceSiteDeliveryExecutor.classifyBefore(state,container,depot,slot,5,"minecraft:wheat")
                ==FrontierV3ResourceSiteDeliveryExecutor.ChestState.FOREIGN,"a matching stack without a retained operation is never a delivery receipt");
        h.succeed();
    }
    private FrontierV3ResourceSiteDeliveryGameTests() { }
}
