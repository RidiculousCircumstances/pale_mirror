package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Compares the actual vanilla player and PM hitbox boundaries, not a copied reach formula. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3MeleeBoundaryGameTests {
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=100)
    public static void swordUsesTheSameEyeToHitboxRangeAsTheSurvivalPlayer(GameTestHelper helper){
        var player=helper.makeMockServerPlayerInLevel();player.setGameMode(GameType.SURVIVAL);
        var point=helper.absolutePos(new BlockPos(4,8,4));player.setPos(point.getX()+0.5,point.getY(),point.getZ()+0.5);
        var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);fighter.setPos(player.position());
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(7,8,4));target.setNoAi(true);
        var weapon=FrontierV3WeaponProfiles.read(new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(Math.abs(weapon.reach()-player.entityInteractionRange())<0.0001,"weapon and survival player use the same interaction attribute");
        for(double distance:new double[]{2.5,2.9,3.1,3.4}){
            target.setPos(player.getX()+distance+target.getBbWidth()/2.0,player.getY(),player.getZ());
            helper.assertTrue(FrontierV3MeleeReach.contains(fighter,target,weapon)==player.canInteractWithEntity(target,0.0),
                    "native range differs at eye-to-box distance "+distance);
        }
        fighter.discard();target.discard();player.discard();helper.succeed();
    }
    private FrontierV3MeleeBoundaryGameTests() { }
}
