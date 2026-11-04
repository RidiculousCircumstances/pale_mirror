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
    public static void effectAdmissionPreservesTheFullWindupAndTheActualHitCannotReplay(GameTestHelper helper){
        durableAdmission(helper,MeleeAttack.LUNGE);
    }
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=400)
    public static void recoveryStrikeUsesItsOwnCueAndCannotReplayItsHit(GameTestHelper helper){
        durableAdmission(helper,MeleeAttack.FOLLOWUP);
    }
    private static void durableAdmission(GameTestHelper helper,MeleeAttack attack){
        var level=helper.getLevel();var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);fighter.setNoGravity(true);
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(5,8,4));target.setNoAi(true);target.setNoGravity(true);
        var rules=MeleeRules.initial();var strike=strike(level.getGameTime(),attack);
        helper.assertTrue(io.farfrontier.palemirror.internal.network.CombatGesture.strike(attack)
                ==(attack==MeleeAttack.FOLLOWUP?io.farfrontier.palemirror.internal.network.CombatGesture.FOLLOWUP
                    :io.farfrontier.palemirror.internal.network.CombatGesture.LUNGE),"the recovery response has its own warning");

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
            helper.assertTrue(FrontierV3CombatExecutor.applyImpact(level,fighter,target,strike,rules),"actual production contact effect accepted");hits.incrementAndGet();
            return FrontierV3NativeEffect.Outcome.APPLIED;
        });
        helper.onEachTick(()->{
            var result=FrontierV3MinecraftOperation.applyDeferred(level,declaration,ports,
                    ()->true,effect,()->hits.get()==1);
            if(level.getGameTime()<admittedAfter)helper.assertTrue(visibleSince.get()==-1 && target.getHealth()==health,"waiting for admission must neither animate nor hurt");
            if(!result.complete())return;
            helper.assertTrue(target.getHealth()==health-6 && hits.get()==1,"one native hit, not merely a swing");
            var observed=FrontierV3MinecraftOperation.applyDeferred(level,declaration,ports,()->false,effect,()->hits.get()==1);
            helper.assertTrue(observed.complete() && hits.get()==1,"completed native receipts never repeat the damage");
            FrontierV3CombatPresentation.cancel(level,fighter,strike);
            helper.assertFalse(FrontierV3CombatPresentation.preparationRetained(level,fighter,strike),"a cancelled exact cue cannot be restarted");
            fighter.discard();target.discard();helper.succeed();
        });
    }
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=400)
    public static void expiredWindupSettlesAsOneMissThroughTheJournalWithoutLateDamage(GameTestHelper helper){
        var level=helper.getLevel();var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);fighter.setNoGravity(true);
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(5,8,4));target.setNoAi(true);target.setNoGravity(true);
        var rules=MeleeRules.initial();var strike=strike(level.getGameTime());float health=target.getHealth();var attempts=new AtomicInteger();
        long began=FrontierV3CombatPresentation.start(level,fighter,strike,rules,target);
        var actorPort=FrontierV3OperationEntityParticipant.nativeObject(fighter);var targetPort=FrontierV3OperationEntityParticipant.nativeObject(target);
        var ports=List.of(actorPort,targetPort);
        var declaration=new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MELEE_ATTACK,strike.owner(),
                "expired-strike-"+strike.attemptOrdinal(),strike.toString(),Set.of(actorPort.identity(),targetPort.identity()));
        var effect=new FrontierV3NativeEffect(()->FrontierV3CombatExecutor.prepareImpact(level,fighter,target,strike,rules,
                strike.preparedAt()+level.getGameTime()-began),()->{
            helper.assertFalse(FrontierV3CombatExecutor.applyImpact(level,fighter,target,strike,rules),"expired visible attempt is a miss, never late damage");
            attempts.incrementAndGet();return FrontierV3NativeEffect.Outcome.APPLIED;
        });
        helper.onEachTick(()->{
            if(level.getGameTime()-began<=rules.windup(strike.attack())+rules.techniques().require(strike.attack()).activeTicks())return;
            var result=FrontierV3MinecraftOperation.applyDeferred(level,declaration,ports,
                    ()->FrontierV3CombatPresentation.preparationRetained(level,fighter,strike),effect,()->attempts.get()==1);
            if(!result.complete())return;
            helper.assertTrue(attempts.get()==1 && target.getHealth()==health,"one journaled miss settles without damage or cancellation");
            var observed=FrontierV3MinecraftOperation.applyDeferred(level,declaration,ports,()->false,effect,()->attempts.get()==1);
            helper.assertTrue(observed.complete() && attempts.get()==1,"even an expired native attempt cannot replay");
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
            target.setPos(fighter.getX()+0.5,fighter.getY(),fighter.getZ()+2);
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
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=100)
    public static void completedMissIsVisibleButApproachAllowanceNeverDealsRemoteDamage(GameTestHelper helper){
        var level=helper.getLevel();var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);fighter.setNoGravity(true);
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(8,8,4));target.setNoAi(true);target.setNoGravity(true);
        var rules=MeleeRules.initial();var strike=strike(level.getGameTime(),MeleeAttack.FOLLOWUP);float health=target.getHealth();
        FrontierV3CombatPresentation.start(level,fighter,strike,rules,target);
        helper.runAfterDelay(rules.windup(strike.attack())+rules.techniques().require(strike.attack()).activeTicks(),()->{
            helper.assertTrue(FrontierV3CombatExecutor.prepareImpact(level,fighter,target,strike,rules,strike.impactAt(rules)).ready(),
                    "a finite completed swing can be recorded as a miss");
            helper.assertFalse(FrontierV3CombatExecutor.applyImpact(level,fighter,target,strike,rules),"short step allowance is not damage reach");
            helper.assertValueEqual(target.getHealth(),health,"a visible miss deals no remote damage");
            fighter.discard();target.discard();helper.succeed();
        });
    }
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=100)
    public static void interposedBodiesBlockContactAndVisibleSwingsRequireFacingTheDefender(GameTestHelper helper){
        var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);fighter.setNoGravity(true);
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,7));target.setNoAi(true);target.setNoGravity(true);
        var peer=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,5));peer.setNoAi(true);peer.setNoGravity(true);
        helper.assertFalse(FrontierV3MeleeReach.clearContact(fighter,target),"a friendly body in a choke cannot be struck through");
        peer.setPos(fighter.getX()+2,fighter.getY(),fighter.getZ());
        helper.assertTrue(FrontierV3MeleeReach.clearContact(fighter,target),"another nearby body outside the contact segment is harmless");
        target.swinging=true;target.swingTime=1;target.setYRot(180);target.setXRot(0);
        helper.assertTrue(FrontierV3CombatExecutor.threateningSwing(fighter,target,MeleeRules.initial()),"an already visible directed swing can invite physical defence");
        target.setYRot(0);
        helper.assertFalse(FrontierV3CombatExecutor.threateningSwing(fighter,target,MeleeRules.initial()),"swinging away from the guard does not reveal a future attack");
        fighter.discard();target.discard();peer.discard();helper.succeed();
    }
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=100)
    public static void sweepHasItsOwnCueAndCannotHitBehindTheCommittedFacing(GameTestHelper helper){
        var level=helper.getLevel();var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);fighter.setNoGravity(true);
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,6));target.setNoAi(true);target.setNoGravity(true);
        var rules=MeleeRules.initial();var strike=strike(level.getGameTime(),MeleeAttack.SWEEP);
        FrontierV3CombatPresentation.start(level,fighter,strike,rules,target);
        helper.assertTrue(fighter.getPersistentData().getCompound("pm_v3_combat_windup").getInt("attack")
                ==io.farfrontier.palemirror.internal.network.CombatGesture.SWEEP.wireTag(),"sweep uses an explicit distinct client cue");
        helper.runAfterDelay(rules.windup(MeleeAttack.SWEEP),()->{
            target.setPos(fighter.getX()+2,fighter.getY(),fighter.getZ());
            helper.assertTrue(FrontierV3CombatPresentation.strikeArc(fighter,target,strike,rules),"wide frontal sweep covers close circling");
            target.setPos(fighter.getX(),fighter.getY(),fighter.getZ()-2);
            helper.assertFalse(FrontierV3CombatPresentation.strikeArc(fighter,target,strike,rules),"successful dodge behind the commitment stays safe");
            helper.assertFalse(FrontierV3CombatExecutor.applyImpact(level,fighter,target,strike,rules),"no damage through the guard's back");
            fighter.discard();target.discard();helper.succeed();
        });
    }
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=100)
    public static void evasiveWithdrawalCancelsUnusedPreparationButCannotWithdrawAnActiveStrike(GameTestHelper helper){
        var level=helper.getLevel();var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);fighter.setNoGravity(true);
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(5,8,4));target.setNoAi(true);target.setNoGravity(true);
        fighter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
        var rules=MeleeRules.initial();var first=strike(level.getGameTime(),MeleeAttack.QUICK,"evasive-withdrawal");
        var actorPort=FrontierV3OperationEntityParticipant.nativeObject(fighter);var targetPort=FrontierV3OperationEntityParticipant.nativeObject(target);
        var ports=List.of(actorPort,targetPort);
        var declaration=new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MELEE_ATTACK,first.owner(),
                "strike-"+first.attemptOrdinal(),first.toString(),Set.of(actorPort.identity(),targetPort.identity()));
        FrontierV3CombatPresentation.start(level,fighter,first,rules,target);
        var cancelled=FrontierV3CombatExecutor.withdrawPreparation(level,fighter,first,rules,declaration);
        helper.assertTrue(cancelled.isPresent() && cancelled.orElseThrow().cancelled(),"unused damage authority must be sealed for cancellation before evasion");
        var hydrated=FrontierV3OperationLedger.load(FrontierV3OperationLedger.get(level).save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()),level.registryAccess());
        helper.assertTrue(hydrated.find(declaration).orElseThrow().cancelled(),"journal hydration retains exact cancellation rather than a replayable preparation");
        float health=target.getHealth();int wear=fighter.getMainHandItem().getDamageValue();var attempts=new AtomicInteger();
        var effect=new FrontierV3NativeEffect(()->FrontierV3NativeEffect.Admission.READY,()->{
            attempts.incrementAndGet();target.hurt(fighter.damageSources().mobAttack(fighter),6);return FrontierV3NativeEffect.Outcome.APPLIED;
        });
        FrontierV3MinecraftOperation.applyDeferred(level,declaration,ports,()->true,effect,()->false);
        helper.assertTrue(attempts.get()==0 && target.getHealth()==health && fighter.getMainHandItem().getDamageValue()==wear,
                "cancelled preparation cannot later apply damage or weapon wear");
        helper.runAfterDelay(1,()->{
            var active=strike(level.getGameTime(),MeleeAttack.QUICK,"evasive-withdrawal");
            var activeDeclaration=new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MELEE_ATTACK,active.owner(),
                    "strike-"+active.attemptOrdinal(),active.toString(),declaration.participants());
            FrontierV3CombatPresentation.start(level,fighter,active,rules,target);
            helper.runAfterDelay((int)rules.windup(active.attack()),()->{
                helper.assertTrue(FrontierV3CombatPresentation.strikeWindow(level,fighter,active,rules),"fixture reached actual visible contact commitment");
                helper.assertTrue(FrontierV3CombatExecutor.withdrawPreparation(level,fighter,active,rules,activeDeclaration).isEmpty(),
                        "tactics cannot retract an active strike to dodge its recovery");
                helper.assertTrue(FrontierV3OperationLedger.get(level).find(activeDeclaration).isEmpty(),
                        "a refused withdrawal cannot fabricate a cancellation receipt");
                fighter.discard();target.discard();helper.succeed();
            });
        });
    }
    private static MeleeStrike strike(long tick){return strike(tick,MeleeAttack.LUNGE);}
    private static MeleeStrike strike(long tick,MeleeAttack attack){
        return strike(tick,attack,attack==MeleeAttack.FOLLOWUP?"recovery-test":"windup-test");
    }
    private static MeleeStrike strike(long tick,MeleeAttack attack,String purpose){
        var owner=new SubjectId("guard:"+purpose);
        var actor=new ActorExecutionId(new SubjectId("resident:windup-test"),
                ActorActivityKind.GUARD,owner,1);
        return new MeleeStrike(owner,1,
                new ActorActuationId(new ActorBodyId(actor.actorId(),1),actor),
                new CombatTarget(CombatTarget.Kind.HOSTILE_MOB,new UUID(1,2),"minecraft:overworld"),
                new SubjectId("item:windup-sword"),new ItemCondition(250,0),
                FrontierV3WeaponProfiles.read(new ItemStack(Items.IRON_SWORD)),attack,1,tick+1,tick);
    }
    private FrontierV3MeleeBoundaryGameTests() { }
}
