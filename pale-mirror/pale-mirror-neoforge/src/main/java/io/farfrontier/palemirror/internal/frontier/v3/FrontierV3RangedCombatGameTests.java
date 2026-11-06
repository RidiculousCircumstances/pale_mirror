package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
/** Actual vanilla arrow/obstacle and personal inventory effects. These native unit preconditions are not a whole settlement scenario. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3RangedCombatGameTests {
    private record Actors(Villager shooter,Villager target){}
    private static Actors actors(GameTestHelper h){
        var level=h.getLevel();
        for(int x=1;x<=18;x++)for(int z=2;z<=6;z++){
            var floor=h.absolutePos(new BlockPos(x,30,z));level.setBlock(floor,Blocks.STONE.defaultBlockState(),3);
            for(int y=1;y<5;y++)level.setBlock(floor.above(y),Blocks.AIR.defaultBlockState(),3);
        }
        var shooter=h.spawnWithNoFreeWill(EntityType.VILLAGER,new Vec3(3.5,31,4.5));
        var target=h.spawnWithNoFreeWill(EntityType.VILLAGER,new Vec3(14.5,31,4.5));
        shooter.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.CROSSBOW));
        return new Actors(shooter,target);
    }
    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=100)
    public static void crossbowEmissionConsumesOneActualPocketArrowAndHitsWithVanillaCollision(GameTestHelper h){
        var a=actors(h);var actor=new SubjectId("resident:crossbow-native");var owner=new SubjectId("guard-duty:crossbow-native");
        var execution=new ActorExecutionId(actor,ActorActivityKind.GUARD,owner,1);var permission=new ActorActuationId(new ActorBodyId(actor,1),execution);
        var pocket=new ActorItemSlot.Pocket(2);a.shooter().getInventory().setItem(2,new ItemStack(Items.ARROW,2));
        var account=new SubjectId("custody:crossbow-native");var lot=new SubjectId("lot:crossbow-native");
        var binding=new PhysicalStackBinding(new SubjectId("binding:crossbow-native"),account,new PhysicalStackAddress.ActorPocket(actor,a.shooter().getUUID(),2),1,"minecraft:arrow",Map.of(lot,2),Map.of());
        var target=new CombatTarget(CombatTarget.Kind.HOSTILE_MOB,a.target().getUUID(),h.getLevel().dimension().location().toString());
        var shot=new RangedShot(permission,CombatMethod.CROSSBOW,1,new SubjectId("item:crossbow-native"),new ItemCondition(465,0),target,1,account,lot,"minecraft:arrow",binding,1,0,new CombatProfile(new CombatAttributes(50,50,50,
                50,50),Map.of(CombatSkillId.CROSSBOW,50,CombatSkillId.EVASION,0,CombatSkillId.WEAPON_DEFENCE,0)));
        h.assertTrue(a.shooter().getMainHandItem().getMaxDamage()==FrontierRulesets.installed("frontier-v3-ranged-guards-r33").tools().definitions().get("minecraft:crossbow").durability(),
                "manufactured crossbow profile matches actual native registry durability");
        float health=a.target().getHealth();
        h.assertTrue(FrontierV3RangedCombatExecutor.emit(h.getLevel(),a.shooter(),a.target(),shot,new RangedRules(32,64,5,11,22,25,8,12,5,450000,3150000,0,500),pocket)==FrontierV3NativeEffect.Outcome.APPLIED,
                "launch applies one declared native emission");
        h.assertTrue(a.shooter().getInventory().getItem(2).getCount()==1 && a.shooter().getMainHandItem().getDamageValue()==1,"one real arrow and one durability are spent");
        h.assertTrue(FrontierV3RangedCombatExecutor.emitted(a.shooter(),shot),"persistent actor witness prevents replay after the arrow disappears");
        var saved=new net.minecraft.nbt.CompoundTag();a.shooter().saveWithoutId(saved);a.shooter().load(saved);
        h.assertTrue(FrontierV3RangedCombatExecutor.emitted(a.shooter(),shot),"serialized actor keeps the completed emission witness after native reload");
        var projectile=h.getLevel().getEntity(shot.projectileId());
        h.assertTrue(projectile instanceof net.minecraft.world.entity.projectile.AbstractArrow,"ordinary vanilla arrow exists with the declared identity");
        for(int i=0;i<20 && !projectile.isRemoved() && a.target().getHealth()==health;i++)projectile.tick();
        h.assertTrue(a.target().getHealth()<health,"native projectile collision, not a synthetic melee hit, damages the target");
        projectile.discard();a.shooter().discard();a.target().discard();h.succeed();
    }
    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=100)
    public static void crossbowShotRefusesAnAllyAndSolidCoverBetweenShooterAndTarget(GameTestHelper h){
        var a=actors(h);var rules=RangedRules.initial();
        h.assertTrue(FrontierV3RangedCombatExecutor.clearShot(h.getLevel(),a.shooter(),a.target(),rules),"fixture starts with an open firing lane");
        var ally=h.spawnWithNoFreeWill(EntityType.VILLAGER,new Vec3(8.5,31,4.5));
        h.assertTrue(!FrontierV3RangedCombatExecutor.clearShot(h.getLevel(),a.shooter(),a.target(),rules),"support fire refuses an intervening resident");
        ally.discard();h.getLevel().setBlock(h.absolutePos(new BlockPos(8,32,4)),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!FrontierV3RangedCombatExecutor.clearShot(h.getLevel(),a.shooter(),a.target(),rules),"native collision cover blocks a shot");
        a.shooter().discard();a.target().discard();h.succeed();
    }
    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=100)
    public static void crossbowCannotFireFromContactDistance(GameTestHelper h){
        var a=actors(h);var rules=RangedRules.initial();
        h.assertTrue(FrontierV3RangedCombatExecutor.inRange(a.shooter(),a.target(),rules),"ordinary support distance permits ranged work");
        a.target().setPos(a.shooter().getX()+1,a.shooter().getY(),a.shooter().getZ());
        h.assertTrue(!FrontierV3RangedCombatExecutor.inRange(a.shooter(),a.target(),rules),"contact distance requires retreat, not an imaginary long-range strike");
        a.shooter().discard();a.target().discard();h.succeed();
    }
    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=30)
    public static void rangedLeadRetainsTheMeasuredTargetVelocityForEveryReaderOfTheSameTick(GameTestHelper h){
        var a=actors(h);var id=new SubjectId("resident:lead-native");
        var observer=new ActorActuationId(new ActorBodyId(id,1),new ActorExecutionId(id,ActorActivityKind.GUARD,new SubjectId("guard-duty:lead-native"),1));
        FrontierV3CombatTargetMotion.observe(a.shooter(),a.target(),observer,true,5);
        h.runAfterDelay(1,()->{
            a.target().setPos(a.target().getX(),a.target().getY(),a.target().getZ()+0.4);
            var first=FrontierV3CombatTargetMotion.observe(a.shooter(),a.target(),observer,true,5);
            var repeated=FrontierV3CombatTargetMotion.observe(a.shooter(),a.target(),observer,true,5);
            h.assertTrue(first.z()>0 && repeated.equals(first),"aim preparation and launch share the measured displacement instead of resetting it");
            a.shooter().discard();a.target().discard();h.succeed();
        });
    }
    private FrontierV3RangedCombatGameTests(){}
}
