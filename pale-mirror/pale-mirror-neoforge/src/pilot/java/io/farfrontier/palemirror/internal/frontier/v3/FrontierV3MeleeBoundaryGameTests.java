package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ItemCondition;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;
import java.util.concurrent.atomic.*;
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
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=400)
    public static void durableAdmissionStartsTheFullWindupAndTheActualHitCannotReplay(GameTestHelper helper){
        var level=helper.getLevel();var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);fighter.setNoGravity(true);
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(5,8,4));target.setNoAi(true);target.setNoGravity(true);
        var rules=MeleeRules.initial();var strike=strike(level.getGameTime());

        var actorPort=FrontierV3OperationEntityParticipant.nativeObject(fighter);
        var targetPort=FrontierV3OperationEntityParticipant.nativeObject(target);var ports=List.of(actorPort,targetPort);
        var declaration=new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MELEE_ATTACK,strike.owner(),
                "strike-"+strike.attemptOrdinal(),strike.toString(),Set.of(actorPort.identity(),targetPort.identity()));
        long admittedAfter=level.getGameTime()+20;var visibleSince=new AtomicLong(-1);
        var hits=new AtomicInteger();float health=target.getHealth();
        var effect=new FrontierV3NativeEffect(()->{
            if(level.getGameTime()<admittedAfter)return FrontierV3NativeEffect.Admission.waiting("TEST_ADMISSION");
            visibleSince.compareAndSet(-1,level.getGameTime());
            // Keep canonical time initially at Prepared: both preparations must run concurrently.
            long canonical=strike.preparedAt()+level.getGameTime()-visibleSince.get();
            var admission=FrontierV3CombatExecutor.prepareImpact(level,fighter,target,strike,rules,canonical);
            if(level.getGameTime()-visibleSince.get()==rules.windup(strike.attack()))
                helper.assertTrue(admission.ready(),"aligned clocks need one windup, not canonical then native");
            return admission;
        },()->{
            helper.assertTrue(level.getGameTime()-visibleSince.get()>=rules.windup(strike.attack()),"native cue must last its complete preparation after admission");
            helper.assertTrue(FrontierV3CombatPresentation.strikeWindow(level,fighter,strike,rules),"effect needs a live finite impact window");
            helper.assertTrue(target.hurt(fighter.damageSources().mobAttack(fighter),6),"actual native damage accepted");hits.incrementAndGet();
            return FrontierV3NativeEffect.Outcome.APPLIED;
        });
        helper.onEachTick(()->{
            var result=FrontierV3MinecraftOperation.applyDeferred(level,declaration,ports,
                    ()->FrontierV3CombatPresentation.preparationOpen(level,fighter,strike,rules),effect,()->hits.get()==1);
            if(level.getGameTime()<admittedAfter)helper.assertTrue(visibleSince.get()==-1 && target.getHealth()==health,"waiting for admission must neither animate nor hurt");
            if(!result.complete())return;
            helper.assertTrue(target.getHealth()==health-6 && hits.get()==1,"one native hit, not merely a swing");
            var observed=FrontierV3MinecraftOperation.applyDeferred(level,declaration,ports,()->false,effect,()->hits.get()==1);
            helper.assertTrue(observed.complete() && hits.get()==1,"completed native receipts never repeat the damage");
            FrontierV3CombatPresentation.cancel(level,fighter,strike);
            helper.assertFalse(FrontierV3CombatPresentation.preparationOpen(level,fighter,strike,rules),"a cancelled exact cue cannot be restarted");
            fighter.discard();target.discard();helper.succeed();
        });
    }
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=100)
    public static void expiredWindupCannotTurnIntoALateHit(GameTestHelper helper){
        var level=helper.getLevel();var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(5,8,4));target.setNoAi(true);
        var rules=MeleeRules.initial();var strike=strike(level.getGameTime());
        FrontierV3CombatPresentation.start(level,fighter,strike,rules,target);
        helper.runAfterDelay(rules.windup(strike.attack())+rules.techniques().require(strike.attack()).activeTicks()+1,()->{
            helper.assertFalse(FrontierV3CombatPresentation.preparationOpen(level,fighter,strike,rules),"expired preparation closes unused operation");
            helper.assertFalse(FrontierV3CombatPresentation.strikeWindow(level,fighter,strike,rules),"no late hit even against a stationary target");
            fighter.discard();target.discard();helper.succeed();
        });
    }
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=100)
    public static void lungeTracksEarlyButCannotTurnAfterCommittingItsWideArc(GameTestHelper helper){
        var level=helper.getLevel();var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);fighter.setNoGravity(true);
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,6));target.setNoAi(true);target.setNoGravity(true);
        var rules=MeleeRules.initial();var strike=strike(level.getGameTime());var yaw=new AtomicReference<Float>();
        FrontierV3CombatPresentation.start(level,fighter,strike,rules,target);
        helper.runAfterDelay(1,()->{
            target.setPos(fighter.getX()+2,fighter.getY(),fighter.getZ());
            helper.assertTrue(FrontierV3CombatPresentation.track(level,fighter,strike,rules,target),"early preparation can correct the observed aim");
            float corrected=fighter.getPersistentData().getCompound("pm_v3_combat_windup").getFloat("facingYaw");
            helper.assertTrue(corrected<0 && corrected>=-rules.engagements().aimTurnDegreesPerTick(),"no instantaneous right-angle homing");yaw.set(corrected);
        });
        helper.runAfterDelay(rules.windup(strike.attack()),()->{
            target.setPos(fighter.getX()+2,fighter.getY(),fighter.getZ()+0.5);
            helper.assertFalse(FrontierV3CombatPresentation.track(level,fighter,strike,rules,target),"impact cannot keep tracking a circling player");
            helper.assertTrue(fighter.getPersistentData().getCompound("pm_v3_combat_windup").getFloat("facingYaw")==yaw.get(),"committed facing remains frozen");
            helper.assertTrue(FrontierV3CombatPresentation.strikeWindow(level,fighter,strike,rules)
                    && FrontierV3CombatPresentation.strikeArc(fighter,target,strike,rules) && FrontierV3MeleeReach.contains(fighter,target,strike.profile()),
                    "sideways target inside the broad front arc and ordinary reach is hittable");
            target.setPos(fighter.getX(),fighter.getY(),fighter.getZ()-2);
            helper.assertFalse(FrontierV3CombatPresentation.strikeArc(fighter,target,strike,rules),"dodging behind the commitment is still effective");
            fighter.discard();target.discard();helper.succeed();
        });
    }
    private static MeleeStrike strike(long tick){
        var owner=new SubjectId("guard:windup-test");
        var actor=new ActorExecutionId(new SubjectId("resident:windup-test"),
                ActorActivityKind.GUARD,owner,1);
        return new MeleeStrike(owner,1,
                new ActorActuationId(new ActorBodyId(actor.actorId(),1),actor),
                new CombatTarget(CombatTarget.Kind.HOSTILE_MOB,new UUID(1,2),"minecraft:overworld"),
                new SubjectId("item:windup-sword"),new ItemCondition(250,0),
                FrontierV3WeaponProfiles.read(new ItemStack(Items.IRON_SWORD)),MeleeAttack.LUNGE,1,tick+1,tick);
    }
    private FrontierV3MeleeBoundaryGameTests() { }
}
