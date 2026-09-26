package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Both farmer hand transitions are observed on an ordinary Minecraft Villager. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3VillagerHandMutationGameTests {
    private FrontierV3VillagerHandMutationGameTests() { }

    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void farmerCanReceiveAndDeliverWheat(GameTestHelper helper) {
        Villager farmer = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(FrontierV3VillagerHandMutation.setOffhand(farmer, new ItemStack(Items.WHEAT, 1))
                            && farmer.getOffhandItem().is(Items.WHEAT) && farmer.getOffhandItem().getCount() == 1,
                    "a physical farmer must receive exactly one harvested wheat");
            helper.assertTrue(FrontierV3VillagerHandMutation.setOffhand(farmer, ItemStack.EMPTY)
                            && farmer.getOffhandItem().isEmpty(),
                    "the same farmer must physically release wheat at the depot");
            helper.succeed();
        });
    }
}
