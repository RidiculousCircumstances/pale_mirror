package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.CombatStyleCatalog;
import io.farfrontier.palemirror.frontier.v3.model.combat.CombatStyleId;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Optional;

/** Lateral candidate selection against actual collision and the shared native navigator. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3LateralNavigationGameTests {
    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=180)
    public static void lateralFootworkRejectsLowRoofAndUsesTheSharedNonFlatPath(GameTestHelper helper){
        var level=helper.getLevel();var origin=helper.absolutePos(new BlockPos(4,30,4));
        for(int x=2;x<=7;x++)for(int z=2;z<=7;z++){
            var floor=helper.absolutePos(new BlockPos(x,30,z));level.setBlock(floor,Blocks.STONE.defaultBlockState(),3);
            for(int y=1;y<=4;y++)level.setBlock(floor.above(y),Blocks.AIR.defaultBlockState(),3);
        }
        var blocked=origin.south();var raised=origin.offset(1,1,1);
        level.setBlock(blocked.above(2),Blocks.STONE.defaultBlockState(),3);level.setBlock(raised,Blocks.STONE.defaultBlockState(),3);
        var actor=helper.spawnWithNoFreeWill(EntityType.VILLAGER,new Vec3(4.5,31,4.5));
        FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(actor);
        var id=new io.farfrontier.palemirror.frontier.v3.api.SubjectId("resident:lateral-path");
        var owner=new io.farfrontier.palemirror.frontier.v3.api.SubjectId("combat:lateral-path");
        var execution=new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(id,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.GUARD,owner,1);
        var target=new CombatTarget(
                CombatTarget.Kind.HOSTILE_MOB,new java.util.UUID(20,21),"minecraft:overworld");
        var source=SurfaceAnchor.at(origin.getX(),origin.getY(),origin.getZ());
        var order=new CombatOrder(execution,1,
                new io.farfrontier.palemirror.frontier.v3.api.SubjectId("item:lateral-sword"),
                new CombatPartyId(owner),
                new CombatEnvelope(target,
                        CombatEnvelope.Kind.SELF_DEFENCE,source,8),
                source.offset(2,0,0),100,100,source,io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile.VILLAGER,Optional.empty(),CombatStyleCatalog.standard().require(
                        new CombatStyleId("frontier:mobile")),
                CombatProfileGeneration.create(139,1),CombatSkillId.SWORD);
        var scope=new FrontierV3NavigationScope.ObservedWorld(new io.farfrontier.palemirror.frontier.v3.model.WorldBounds(origin.getX()-2,origin.getZ()-2,6,6));
        var ranked=MeleeManeuvers.rank(order,
                MeleeManeuver.Kind.SIDESTEP_RIGHT,
                List.of(source.offset(0,0,1),source.offset(1,1,1)),ManeuverRules.initial());
        helper.assertTrue(ranked.size()==2 && ranked.getFirst().equals(source.offset(0,0,1)),"fixture presents a preferred but physically obstructed side");
        var chosen=MeleePositions.reachable(ranked,3,point->{
            if(!FrontierV3GoalNavigation.canReach(level,actor,FrontierV3GoalNavigation.Goal.station(point,scope)))
                throw new io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable("actual low roof");
        }).orElseThrow();
        helper.assertTrue(chosen.equals(source.offset(1,1,1)),"unavailable side cannot hide the reachable raised step");
        for(int turn=0;turn<140 && !FrontierV3SemanticMovement.arrived(level,actor,chosen);turn++){
            FrontierV3GoalNavigation.pursue(level,actor,FrontierV3GoalNavigation.Goal.station(chosen,scope));
            FrontierV3MobMotionLifecycle.advanceAtEntityBoundary(actor);actor.aiStep();
        }
        helper.assertTrue(FrontierV3SemanticMovement.arrived(level,actor,chosen),"lateral goal uses real shared navigation and grade collision: "+actor.position());
        FrontierV3GoalNavigation.stop(actor);actor.discard();helper.succeed();
    }
    private FrontierV3LateralNavigationGameTests() { }
}
