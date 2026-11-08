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
    public static void fullManeuverLifetimeSurvivesNativePresentationAndClientWireDecode(GameTestHelper helper){
        var level=helper.getLevel();var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(5,8,4));target.setNoAi(true);float health=target.getHealth();
        var rules=MeleeRules.initial().maneuvers();
        for(var kind:MeleeManeuver.Kind.values()){
            var identity=strike(level.getGameTime(),MeleeAttack.QUICK,"presentation-"+kind.name().toLowerCase(Locale.ROOT));
            var origin=new io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor(
                    new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(4,7,4));
            var maneuver=new MeleeManeuver(identity.attacker(),identity.weapon(),identity.target(),kind,1,
                    origin,origin.offset(1,0,0),level.getGameTime(),0,0,0,0,level.getGameTime(),0);
            FrontierV3CombatPresentation.begin(level,fighter,maneuver.owner().value(),maneuver.attemptOrdinal(),
                    maneuver.actor().body().physicalEpoch(),io.farfrontier.palemirror.internal.network.CombatGesture.maneuver(kind),
                    rules.preparation(kind),rules.travelTicks(kind),rules.recovery(kind),target);
            FrontierV3CombatPresentation.maneuverMoving(level,fighter,maneuver.launched(level.getGameTime()));
            var marker=fighter.getPersistentData().getCompound("pm_v3_combat_windup").copy();
            var cue=FrontierV3CombatPresentation.cue(level,fighter,marker);
            helper.assertTrue(cue.active()==rules.travelTicks(kind),"native cue retains full maneuver lifetime: "+kind);
            helper.assertTrue(cue.phase()==io.farfrontier.palemirror.internal.network.CombatGesturePayload.Phase.IMPACT,"moving cue starts");
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
            try{
                var codec=io.farfrontier.palemirror.internal.network.CombatGesturePayload.STREAM_CODEC;
                codec.encode(buffer,cue);
                helper.assertTrue(codec.decode(buffer).equals(cue),"client decoder accepts the exact native maneuver cue: "+kind);
            }finally{buffer.release();}
            FrontierV3CombatPresentation.maneuverEnded(level,fighter,maneuver.closed(level.getGameTime()));
            helper.assertTrue(FrontierV3CombatPresentation.cue(level,fighter,fighter.getPersistentData().getCompound("pm_v3_combat_windup"))
                    .phase()==io.farfrontier.palemirror.internal.network.CombatGesturePayload.Phase.CANCELLED,"closed maneuver retracts its cue");
        }
        helper.assertTrue(target.getHealth()==health,"movement presentation cannot cause damage");
        fighter.discard();target.discard();helper.succeed();
    }
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=100)
    public static void swordUsesTheSameEyeToHitboxRangeAsTheSurvivalPlayer(GameTestHelper helper){
        var player=helper.makeMockServerPlayerInLevel();player.setGameMode(GameType.SURVIVAL);
        var point=helper.absolutePos(new BlockPos(4,8,4));player.setPos(point.getX()+0.5,point.getY(),point.getZ()+0.5);
        var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);fighter.setPos(player.position());
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(7,8,4));target.setNoAi(true);
        var weapon=FrontierV3WeaponProfiles.read(new ItemStack(Items.IRON_SWORD),CombatSkillId.SWORD);
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
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=400)
    public static void quickContactRetainsVisiblePreparationDurableAdmissionAndNoReplay(GameTestHelper helper){
        durableAdmission(helper,MeleeAttack.QUICK);
    }
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=100)
    public static void movingServerPlayerWithZeroNativeImpulseStillSuppliesObservedPursuitVelocity(GameTestHelper helper){
        var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);fighter.setNoGravity(true);
        var player=helper.makeMockServerPlayerInLevel();var p=helper.absolutePos(new BlockPos(6,8,4));player.setPos(p.getX(),p.getY(),p.getZ());
        var strike=strike(helper.getLevel().getGameTime(),MeleeAttack.QUICK,"observed-motion");
        var first=FrontierV3CombatExecutor.observedMotion(fighter,player,strike.attacker(),true,4);
        helper.assertTrue(first.x()==0 && first.z()==0,"one observation has no invented velocity");
        helper.runAfterDelay(1,()->{
            player.setPos(player.getX()+.3,player.getY(),player.getZ()+.2);player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            var motion=FrontierV3CombatExecutor.observedMotion(fighter,player,strike.attacker(),true,4);
            helper.assertTrue(motion.x()>0 && motion.z()>0,"player coordinates, not zero native impulse, drive pursuit prediction");
            var changed=new ActorActuationId(new ActorBodyId(strike.attacker().body().actorId(),strike.attacker().body().physicalEpoch()+1),strike.attacker().execution());
            var reset=FrontierV3CombatExecutor.observedMotion(fighter,player,changed,true,4);
            helper.assertTrue(reset.x()==0 && reset.z()==0,"replacement body cannot inherit old target motion");
            fighter.discard();player.discard();helper.succeed();
        });
    }
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=400)
    public static void risingStrikeUsesDurableContactAndCannotReplayKnockback(GameTestHelper helper){durableAdmission(helper,MeleeAttack.RISING);}
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=400)
    public static void kickUsesShortPhysicalContactAndCannotReplay(GameTestHelper helper){durableAdmission(helper,MeleeAttack.KICK);}
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=400)
    public static void circularContactUsesItsOwnDurableCueAndCannotReplay(GameTestHelper helper){durableAdmission(helper,MeleeAttack.CIRCLE);}
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=400)
    public static void crossbowKickUsesCommonCueAndDurableKnockbackWithoutAmmoOrWear(GameTestHelper helper){durableAdmission(helper,MeleeAttack.KICK,true,false);}
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=400)
    public static void crossbowKickMissesWhenTheEnemyLeavesContact(GameTestHelper helper){durableAdmission(helper,MeleeAttack.KICK,true,true);}
    private static void durableAdmission(GameTestHelper helper,MeleeAttack attack){durableAdmission(helper,attack,false,false);}
    private static void durableAdmission(GameTestHelper helper,MeleeAttack attack,boolean crossbow,boolean retreat){
        var level=helper.getLevel();var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);fighter.setNoGravity(true);
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(5,8,4));target.setNoAi(true);target.setNoGravity(true);
        var rules=MeleeRules.initial();var raw=strike(level.getGameTime(),attack,"durable-"+attack.name().toLowerCase(Locale.ROOT)+(crossbow?"-crossbow":"")+(retreat?"-miss":""));
        var held=new ItemStack(crossbow?Items.CROSSBOW:Items.IRON_SWORD);fighter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,held);
        var actorProfile=new CombatProfile(new CombatAttributes(50,50,50,50,50),Map.of(CombatSkillId.SWORD,100,CombatSkillId.CROSSBOW,100,CombatSkillId.EVASION,100,CombatSkillId.WEAPON_DEFENCE,100));
        var strike=new MeleeStrike(raw.owner(),raw.ownerRevision(),raw.attacker(),raw.target(),raw.weapon(),new ItemCondition(held.getMaxDamage(),0),
                FrontierV3WeaponProfiles.read(held,crossbow?CombatSkillId.CROSSBOW:CombatSkillId.SWORD),attack,raw.targetIncarnation(),raw.attemptOrdinal(),raw.preparedAt(),actorProfile,List.of());
        if(crossbow)fighter.getInventory().setItem(2,new ItemStack(Items.ARROW,7));
        if(attack==MeleeAttack.KICK)target.setPos(fighter.getX()+0.8,fighter.getY(),fighter.getZ());
        helper.assertTrue(io.farfrontier.palemirror.internal.network.CombatGesture.strike(attack).name().equals(attack.name()),"the attack has its own warning");

        var actorPort=FrontierV3OperationEntityParticipant.nativeObject(fighter);
        var targetPort=FrontierV3OperationEntityParticipant.nativeObject(target);var ports=List.of(actorPort,targetPort);
        var declaration=new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MELEE_ATTACK,strike.owner(),
                "strike-"+strike.attemptOrdinal(),strike.toString(),Set.of(actorPort.identity(),targetPort.identity()));
        long admittedAfter=level.getGameTime()+20;var admissionSince=new AtomicLong(-1);var visibleSince=new AtomicLong(-1);
        var hits=new AtomicInteger();float health=target.getHealth();
        var effect=new FrontierV3NativeEffect(()->{
            if(level.getGameTime()<admittedAfter)return FrontierV3NativeEffect.Admission.waiting("TEST_ADMISSION");
            admissionSince.compareAndSet(-1,level.getGameTime());
            // An asynchronous canonical predecessor may hold Prepared for much
            // longer than the entire native warning/contact interval.
            long canonical=strike.preparedAt()+Math.max(0,level.getGameTime()-admissionSince.get()-30);
            if(retreat&&visibleSince.get()>=0&&level.getGameTime()>visibleSince.get())target.setPos(fighter.getX()+4,fighter.getY(),fighter.getZ());
            var admission=FrontierV3CombatExecutor.prepareImpact(level,fighter,target,strike,rules,canonical);
            if(canonical<strike.impactAt(rules))helper.assertFalse(fighter.getPersistentData().contains("pm_v3_combat_windup"),
                    "canonical waiting cannot expose a warning whose contact window will expire before admission");
            else visibleSince.compareAndSet(-1,fighter.getPersistentData().getCompound("pm_v3_combat_windup").getLong("startedAtTick"));
            if(!retreat&&visibleSince.get()>=0&&level.getGameTime()-visibleSince.get()==strike.timing(rules).preparationTicks())
                helper.assertTrue(admission.ready(),"a full native warning reaches its still-live contact window after canonical admission");
            return admission;
        },()->{
            helper.assertTrue(level.getGameTime()-visibleSince.get()>=strike.timing(rules).preparationTicks(),"native cue must last its complete preparation after admission");
            if(!retreat)helper.assertTrue(FrontierV3CombatPresentation.strikeWindow(level,fighter,strike,rules),"effect needs a live finite impact window");
            boolean applied=FrontierV3CombatExecutor.applyImpact(level,fighter,target,strike,rules);
            helper.assertTrue(applied!=retreat,"common physical contact hits nearby enemy and misses after retreat");hits.incrementAndGet();
            if(crossbow){
                helper.assertTrue(fighter.getInventory().getItem(2).getCount()==7&&fighter.getMainHandItem().getDamageValue()==0,"a foot strike never spends ammunition or weapon wear");
                if(applied)helper.assertTrue(target.getDeltaMovement().horizontalDistance()>0.1,"native kick produces actual horizontal knockback");
            }
            return FrontierV3NativeEffect.Outcome.APPLIED;
        });
        helper.onEachTick(()->{
            var result=FrontierV3MinecraftOperation.applyDeferred(level,declaration,ports,
                    ()->FrontierV3CombatPresentation.preparationAvailable(level,fighter,strike),effect,()->hits.get()==1);
            if(level.getGameTime()<admittedAfter)helper.assertTrue(visibleSince.get()==-1 && target.getHealth()==health,"waiting for admission must neither animate nor hurt");
            if(!result.complete())return;
            helper.assertTrue(Math.abs(target.getHealth()-(health-(retreat?0:CombatMechanics.damage(strike.actorProfile(),strike.profile(),strike.attack()))))<0.001 && hits.get()==1,
                "one calibrated native hit, not merely a swing");
            var observed=FrontierV3MinecraftOperation.applyDeferred(level,declaration,ports,()->false,effect,()->hits.get()==1);
            helper.assertTrue(observed.complete() && hits.get()==1,"completed native receipts never repeat the damage");
            FrontierV3CombatPresentation.cancel(level,fighter,strike);
            helper.assertFalse(FrontierV3CombatPresentation.preparationRetained(level,fighter,strike),"a cancelled exact cue cannot be restarted");
            helper.assertFalse(FrontierV3CombatPresentation.preparationAvailable(level,fighter,strike),"cancelled exact presentation cannot enter native admission again");
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
            if(level.getGameTime()-began<=strike.timing(rules).preparationTicks()+strike.timing(rules).activeTicks())return;
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
        helper.runAfterDelay(strike.timing(rules).preparationTicks(),()->{
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
        helper.runAfterDelay(strike.timing(rules).preparationTicks()+strike.timing(rules).activeTicks(),()->{
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
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=400)
    public static void circularStrikeAccountsEveryParticipantAndSkipsRevokedTargetsWithoutReplay(GameTestHelper helper){
        var level=helper.getLevel();var fighter=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,4));fighter.setNoAi(true);fighter.setNoGravity(true);
        fighter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
        var primary=helper.spawn(EntityType.VILLAGER,new BlockPos(4,8,6));primary.setNoAi(true);primary.setNoGravity(true);
        var other=helper.spawn(EntityType.VILLAGER,new BlockPos(6,8,4));other.setNoAi(true);other.setNoGravity(true);
        var revoked=helper.spawn(EntityType.VILLAGER,new BlockPos(2,8,4));revoked.setNoAi(true);revoked.setNoGravity(true);
        var second=new CombatTarget(CombatTarget.Kind.HOSTILE_MOB,other.getUUID(),level.dimension().location().toString());
        var third=new CombatTarget(CombatTarget.Kind.HOSTILE_MOB,revoked.getUUID(),level.dimension().location().toString());
        var raw=strike(level.getGameTime(),MeleeAttack.CIRCLE,"area-participants");
        var strike=new MeleeStrike(raw.owner(),raw.ownerRevision(),raw.attacker(),raw.target(),raw.weapon(),raw.before(),raw.profile(),raw.attack(),raw.targetIncarnation(),
            raw.attemptOrdinal(),raw.preparedAt(),raw.actorProfile(),List.of(new CombatVictim(second,FrontierV3OperationEntityParticipant.nativeObject(other).identity().incarnation()),
                new CombatVictim(third,FrontierV3OperationEntityParticipant.nativeObject(revoked).identity().incarnation())));
        var ports=List.of(FrontierV3OperationEntityParticipant.nativeObject(fighter),FrontierV3OperationEntityParticipant.nativeObject(primary),
            FrontierV3OperationEntityParticipant.nativeObject(other),FrontierV3OperationEntityParticipant.nativeObject(revoked));
        var declaration=new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MELEE_ATTACK,strike.owner(),"strike-"+strike.attemptOrdinal(),strike.toString(),
            ports.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet()));
        var applied=new AtomicBoolean();var attempts=new AtomicInteger();float untouched=revoked.getHealth();var rules=MeleeRules.initial();
        var effect=new FrontierV3NativeEffect(()->FrontierV3CombatExecutor.prepareImpact(level,fighter,primary,strike,rules,level.getGameTime()),()->{
            attempts.incrementAndGet();helper.assertTrue(FrontierV3CombatExecutor.applyDeclaredImpact(level,fighter,primary,strike,rules,Set.of(second),ignored->{}),"real area contact");
            applied.set(true);return FrontierV3NativeEffect.Outcome.APPLIED;
        });
        helper.onEachTick(()->{
            var result=FrontierV3MinecraftOperation.applyDeferred(level,declaration,ports,()->FrontierV3CombatPresentation.preparationAvailable(level,fighter,strike),effect,applied::get);
            if(!result.complete())return;
            helper.assertTrue(primary.getHealth()<20 && other.getHealth()<20,"both explicitly permitted physical targets receive damage");
            helper.assertTrue(revoked.getHealth()==untouched,"revoked permission must prevent collateral damage");
            var again=FrontierV3MinecraftOperation.applyDeferred(level,declaration,ports,()->false,effect,applied::get);
            helper.assertTrue(again.complete() && attempts.get()==1,"multi-participant operation cannot replay any impact");
            fighter.discard();primary.discard();other.discard();revoked.discard();helper.succeed();
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
                FrontierV3WeaponProfiles.read(new ItemStack(Items.IRON_SWORD),CombatSkillId.SWORD),attack,1,tick+1,tick,combatProfile(),java.util.List.of());
    }
    private FrontierV3MeleeBoundaryGameTests() { }
    private static CombatProfile combatProfile(){
        var attributes=new CombatAttributes(50,50,50,50,50);
        return new CombatProfile(attributes,java.util.Map.of(
            CombatSkillId.SWORD,100,
            CombatSkillId.EVASION,100,
            CombatSkillId.WEAPON_DEFENCE,100));
    }
}
