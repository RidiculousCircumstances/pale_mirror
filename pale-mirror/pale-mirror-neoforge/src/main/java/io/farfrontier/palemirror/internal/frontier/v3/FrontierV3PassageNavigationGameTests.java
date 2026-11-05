package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** A current physical route must operate a manual closure, and preserve real collision/ownership. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3PassageNavigationGameTests {
    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=220)
    public static void closesAndReopensAPlayerClosedDoorDuringTheSameJourney(GameTestHelper helper) { passage(helper, false); }
    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=220)
    public static void opensAndClosesAFenceGateWithoutASecondMovementController(GameTestHelper helper) { passage(helper, true); }

    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=30)
    public static void ironDoorsAndUnpairedHalvesNeverAcquireManualPassagePermission(GameTestHelper helper) {
        var level = helper.getLevel(); var base = helper.absolutePos(new BlockPos(6,31,6));
        level.setBlock(base.below(), Blocks.STONE.defaultBlockState(), 3);
        var door = Blocks.IRON_DOOR.defaultBlockState();
        level.setBlock(base, door, 3); level.setBlock(base.above(), door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),3);
        helper.assertTrue(FrontierV3PassageBlocks.opened(door).isEmpty(), "redstone-only doors are not manually operable");
        var wood = Blocks.SPRUCE_DOOR.defaultBlockState();
        level.setBlock(base, wood, 3); level.setBlock(base.above(), wood.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),3);
        var evidence = FrontierV3NavigationShapes.capture(level,new BlockPosition(base.getX(),base.getY(),base.getZ()));
        helper.assertTrue(evidence.cell().orElseThrow().passageShape().isPresent(), "COLD receives explicit native passage evidence");
        level.setBlock(base.above(), Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(FrontierV3PassageBlocks.traversalState(wood,base,level).isEmpty(), "damaged closure has no invented partner");
        helper.succeed();
    }

    private static void passage(GameTestHelper helper, boolean gate) {
        var level=helper.getLevel();
        for(int x=3;x<=13;x++) for(int z=4;z<=8;z++) {
            var floor=helper.absolutePos(new BlockPos(x,30,z)); level.setBlock(floor,Blocks.STONE.defaultBlockState(),3);
            for(int y=1;y<=4;y++) level.setBlock(floor.above(y), z==6 ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(),3);
        }
        var base=helper.absolutePos(new BlockPos(8,31,6));
        if(gate) level.setBlock(base,Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING,Direction.EAST),3);
        else {
            var door=Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST).setValue(DoorBlock.OPEN,false);
            level.setBlock(base,door,3); level.setBlock(base.above(),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),3);
        }
        var actor=helper.spawnWithNoFreeWill(EntityType.VILLAGER,new Vec3(4.5,31,6.5));
        var end=helper.absolutePos(new BlockPos(12,30,6));
        var target=SurfaceAnchor.at(end.getX(),end.getY(),end.getZ());
        var scope=new FrontierV3NavigationScope.ObservedWorld(new WorldBounds(base.getX()-6,base.getZ()-2,12,5));
        var goal=FrontierV3GoalNavigation.Goal.station(target,scope);
        boolean[] opened={false},interrupted={false};
        helper.onEachTick(()->{
            if(actor.isRemoved())return;
            FrontierV3BodyObservation.refreshGroundContact(level,actor);
            var result=FrontierV3GoalNavigation.pursueUnmodeled(level,actor,goal,UnitNavigationProfile.VILLAGER);
            if(FrontierV3PassageBlocks.isOpen(level.getBlockState(base))) {
                opened[0]=true;
                // Native external intervention after path admission, before crossing.
                if(!gate && !interrupted[0] && actor.getX()<base.getX()-.6) {
                    ((DoorBlock)level.getBlockState(base).getBlock()).setOpen(null,level,level.getBlockState(base),base,false);
                    interrupted[0]=true;
                }
            }
            FrontierV3GoalNavigation.advanceAtEntityBoundary(actor); actor.aiStep();
            helper.assertTrue(!level.getBlockCollisions(actor,actor.getBoundingBox().deflate(.01)).iterator().hasNext(),"passage cannot waive physical collision: "+actor.position());
            if(FrontierV3SemanticMovement.arrived(level,actor,target) && !FrontierV3PassageBlocks.isOpen(level.getBlockState(base))) {
                helper.assertTrue(opened[0],"the actual native passage was opened");
                helper.assertTrue(gate||interrupted[0],"the closed-after-admission door was encountered");
                helper.assertTrue(actor.isNoAi(),"no vanilla Brain/parallel navigation was enabled");
                FrontierV3GoalNavigation.stop(actor); actor.discard(); helper.succeed();
            }
        });
    }
}
