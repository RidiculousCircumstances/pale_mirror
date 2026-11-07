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
    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=30)
    public static void unloadedCrossbowRemovalKeepsExactHandEvidenceButLoadedProjectilesDoNot(GameTestHelper h){
        var weapon=new ItemStack(Items.CROSSBOW);weapon.setDamageValue(2);
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,weapon,
                tag->tag.putString(FrontierV3ExactItemPresentation.ITEM_ID_KEY,"item:crossbow-hand-recovery"));
        var unloaded=FrontierV3ActorHandEvidence.observed(weapon);
        weapon.remove(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
        h.assertTrue(FrontierV3ActorHandEvidence.capturable(weapon),"a known removed EMPTY charge cannot strand body custody");
        h.assertTrue(FrontierV3ActorHandEvidence.observed(weapon).equals(unloaded),"empty charge representations preserve exact item identity and wear");
        var saved=(net.minecraft.nbt.CompoundTag)weapon.save(h.getLevel().registryAccess());
        h.assertTrue(FrontierV3ActorHandEvidence.saved(saved).equals(unloaded),"actual vanilla save and live hand evidence agree without loading the body");
        weapon.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,
                net.minecraft.world.item.component.ChargedProjectiles.of(new ItemStack(Items.ARROW)));
        h.assertTrue(!FrontierV3ActorHandEvidence.capturable(weapon),"a loaded projectile remains an unfinished weapon effect");
        var actors=actors(h);actors.shooter().setItemSlot(EquipmentSlot.MAINHAND,weapon);
        var exact=new SubjectId("item:crossbow-hand-recovery");
        FrontierV3RangedCombatExecutor.clearUnlaunchedCharge(actors.shooter(),exact);
        h.assertTrue(!CrossbowItem.isCharged(weapon) && FrontierV3ActorHandEvidence.capturable(weapon),
                "actual cancellation leaves a capturable native EMPTY charge and does not spend wear");
        h.assertTrue(weapon.getDamageValue()==2,"cancelling an unlaunched shot cannot wear the weapon");
        weapon.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,
                net.minecraft.world.item.component.ChargedProjectiles.of(new ItemStack(Items.ARROW)));
        FrontierV3RangedCombatExecutor.clearUnlaunchedCharge(actors.shooter(),new SubjectId("item:foreign-crossbow"));
        h.assertTrue(CrossbowItem.isCharged(weapon),"a stale cancellation cannot modify a replacement weapon");
        actors.shooter().discard();actors.target().discard();
        h.succeed();
    }
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
        FrontierV3ActorCarrierComposition.stamp(a.shooter(),new FrontierV3ActorCarrierComposition.Declaration(actor,
                ActorKind.RESIDENT,FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,a.shooter().getUUID(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY,0,1));
        var pocket=new ActorItemSlot.Pocket(2);a.shooter().getInventory().setItem(2,new ItemStack(Items.ARROW,2));
        var account=new SubjectId("custody:crossbow-native");var lot=new SubjectId("lot:crossbow-native");
        var binding=new PhysicalStackBinding(new SubjectId("binding:crossbow-native"),account,new PhysicalStackAddress.ActorPocket(actor,a.shooter().getUUID(),2),1,"minecraft:arrow",Map.of(lot,2),Map.of());
        var target=new CombatTarget(CombatTarget.Kind.HOSTILE_MOB,a.target().getUUID(),h.getLevel().dimension().location().toString());
        var shot=new RangedShot(permission,CombatMethod.CROSSBOW,1,new SubjectId("item:crossbow-native"),new ItemCondition(465,0),target,1,account,lot,"minecraft:arrow",binding,1,0,new CombatProfile(new CombatAttributes(50,50,50,
                50,50),Map.of(CombatSkillId.CROSSBOW,50,CombatSkillId.EVASION,0,CombatSkillId.WEAPON_DEFENCE,0)));
        h.assertTrue(a.shooter().getMainHandItem().getMaxDamage()==FrontierRulesets.installed("frontier-v3-ranged-guards-r33").tools().definitions().get("minecraft:crossbow").durability(),
                "manufactured crossbow profile matches actual native registry durability");
        var rules=new RangedRules(32,64,5,11,22,25,8,12,5,450000,3150000,0,500);
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,a.shooter().getMainHandItem(),
                tag->tag.putString(FrontierV3ExactItemPresentation.ITEM_ID_KEY,shot.weapon().value()));
        var expectedHand=FrontierV3ActorHandEvidence.observed(a.shooter().getMainHandItem());
        float health=a.target().getHealth();
        h.assertTrue(!FrontierV3RangedCombatExecutor.prepare(h.getLevel(),a.shooter(),a.target(),shot,rules).ready(),"reload begins without spending ammunition");
        int reload=RangedMechanics.reloadTicks(shot.actorProfile(),rules);
        h.runAfterDelay(reload+1,()->{
            var aiming=FrontierV3RangedCombatExecutor.prepare(h.getLevel(),a.shooter(),a.target(),shot,rules);
            h.assertTrue(aiming.waitingFor().equals(Optional.of("crossbow-aim")),"actual elapsed reload enters aiming");
            h.assertTrue(!CrossbowItem.isCharged(a.shooter().getMainHandItem()),"aiming may not install a virtual arrow in the native weapon");
            h.assertTrue(a.shooter().getInventory().getItem(2).getCount()==2 && a.shooter().getMainHandItem().getDamageValue()==0,"preparation preserves ammunition and wear");
            h.assertTrue(FrontierV3ActorHandEvidence.capturable(a.shooter().getMainHandItem()),"aiming hand remains classifiable for body departure");
            var savedHand=(net.minecraft.nbt.CompoundTag)a.shooter().getMainHandItem().save(h.getLevel().registryAccess());
            h.assertTrue(FrontierV3ActorHandEvidence.saved(savedHand).equals(expectedHand),"a native save during aim contains no unowned projectile");
            var aimingBody=new net.minecraft.nbt.CompoundTag();a.shooter().saveWithoutId(aimingBody);a.shooter().load(aimingBody);
            h.assertTrue(a.shooter().getInventory().getItem(2).getCount()==2,"native reload during aim preserves the actual pocket arrows");
        });
        h.runAfterDelay(reload+rules.aimTicks()+2,()->{
            h.assertTrue(FrontierV3RangedCombatExecutor.prepare(h.getLevel(),a.shooter(),a.target(),shot,rules).ready(),"native actor reload retains the preparation clock and reaches ready");
            h.assertTrue(FrontierV3RangedCombatExecutor.emit(h.getLevel(),a.shooter(),a.target(),shot,rules,pocket)==FrontierV3NativeEffect.Outcome.APPLIED,
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
        });
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
