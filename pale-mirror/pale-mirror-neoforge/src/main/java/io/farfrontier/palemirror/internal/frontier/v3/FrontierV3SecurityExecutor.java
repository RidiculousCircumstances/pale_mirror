package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.InteractionHand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.*;

/** Native sensors and melee effects. Movement is exclusively guard missions through UAE/navigation. */
public final class FrontierV3SecurityExecutor {
    private static final String ATTEMPT="pm_v3_combat_attempt";
    private static final int MAX_GUARDS_PER_PASS=32;
    private static final Map<ServerLevel,Integer> OBSERVATION_CURSOR=new WeakHashMap<>();
    public static void observedDamage(ServerLevel level,LivingEntity victim,Entity attacker,float damage) {
        if(!(attacker instanceof ServerPlayer player)||damage<=0 || !FrontierV3PhysicalWorld.isPhysical(level))return;
        var runtime=FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if(runtime==null||runtime.status().kind()!=FrontierV3RuntimeStatus.Kind.ACTIVE)return;
        var state=runtime.decodedState().orElseThrow();var declaration=FrontierV3ActorCarrierComposition.declaredBy(victim).orElse(null);
        if(declaration==null || declaration.owner()!=FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY)return;
        var resident=state.humanPopulation().resident(declaration.actorId());if(resident==null)return;
        var body=new ActorBodyId(resident.id(),declaration.epoch());
        if(!victim.getUUID().equals(ActorBodyId.entityId(state.bootstrap().worldId(),body.actorId())))return;
        var binding=state.fencedRecovery().current().get(ActorBodyId.recoveryBindingId(body.actorId()));
        if(binding==null || binding.authorityEpoch()!=body.physicalEpoch())return; // A stale replica is not this resident's body.
        ActorBodyAuthority.require(state,body); // The victim need not have an active job or a running actuator.
        FrontierV3ConfirmedNativeEvent.capture(level,resident.settlementId(),
                new SecurityEvent.Incident(resident.settlementId(),body,player.getUUID(),damage));
    }
    static void tick(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime) {
        var state=runtime.decodedState().orElseThrow();
        // Prepared effects recover before another observation or attack is admitted.
        for(var strike:state.security().pending().values().stream().limit(MAX_GUARDS_PER_PASS).toList()) execute(level,runtime,strike);
        if(level.getGameTime()%state.bootstrap().ruleset().security().observationInterval()!=0)return;
        state=runtime.decodedState().orElseThrow();int visited=0;
        var duties=state.strategicPlans().guards().values().stream().sorted(Comparator.comparing(GuardDuty::id)).toList();
        if(duties.isEmpty()) { OBSERVATION_CURSOR.remove(level); return; }
        int cursor=Math.floorMod(OBSERVATION_CURSOR.getOrDefault(level,0),duties.size());
        for(int inspected=0;inspected<duties.size() && visited<MAX_GUARDS_PER_PASS;inspected++) {
            var duty=duties.get(cursor);cursor=(cursor+1)%duties.size();OBSERVATION_CURSOR.put(level,cursor);
            if(duty.phase()!=GuardDuty.Phase.ON_DUTY || !GuardEquipment.held(state,duty)
                    || !state.actorExecutions().actors().get(duty.execution().actorId()).current().equals(Optional.of(duty.execution()))
                    || !ActorBodyAuthority.retainsPhysicalCustody(state,duty.execution().actorId()))continue;
            var bodyId=ActorBodyAuthority.current(state,duty.execution().actorId());
            if(!FrontierV3ActorBodyController.readyForExecution(level,state,List.of(bodyId)))continue;
            var entity=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),duty.execution().actorId()));
            if(!(entity instanceof Mob guard))continue;
            visited++;var actuation=FrontierV3ActorActuation.capture(state,guard,duty.execution(),runtime::decodedState).id();
            int observationRadius=state.bootstrap().ruleset().security().observationRadius();
            var candidates=level.getEntitiesOfClass(LivingEntity.class,guard.getBoundingBox().inflate(observationRadius),
                    target->target!=guard && target.isAlive() && !(target instanceof ServerPlayer p && (p.isCreative()||p.isSpectator())));
            var basis=state;
            var target=candidates.stream().filter(candidate->candidate instanceof ServerPlayer
                    ?basis.security().hostile(duty.home(),candidate.getUUID()):candidate instanceof Enemy)
                    .filter(candidate->guard.distanceToSqr(candidate)<=observationRadius*(double)observationRadius)
                    .filter(guard::hasLineOfSight).min(Comparator.comparingDouble((LivingEntity candidate)->guard.distanceToSqr(candidate)).thenComparing(Entity::getUUID)).orElse(null);
            long tick=runtime.executionView().orElseThrow().instant().ticks();
            if(target!=null) {
                var key=new CombatTarget(target instanceof ServerPlayer?CombatTarget.Kind.PLAYER:CombatTarget.Kind.HOSTILE_MOB,
                        target.getUUID(),level.dimension().location().toString());
                var previous=state.security().contacts().get(duty.home());var pos=target.blockPosition().below();
                var contact=new SecurityState.Contact(duty.home(),key,new BlockPosition(pos.getX(),pos.getY(),pos.getZ()),tick,previous==null?1:previous.revision()+1);
                if(previous==null || !previous.target().equals(key) || !previous.position().equals(contact.position()) || tick-previous.atTick()>=state.bootstrap().ruleset().security().contactRefreshTicks()) {
                    FrontierV3CommandSubmission.submit(runtime,"security-contact",duty.id().value(),new SecurityEvent.Seen(actuation,contact));
                    state=runtime.decodedState().orElseThrow();
                }
            }
            var contact=GuardResponse.contact(state,duty,tick).orElse(null);
            if(contact==null || state.actorMovements().containsKey(duty.execution().actorId()) || state.security().pending().containsKey(duty.id())
                    || tick<state.security().nextAttack().getOrDefault(duty.id(),0L))continue;
            var actual=level.getEntity(contact.target().entity());
            if(!(actual instanceof LivingEntity living)||!living.isAlive()||!guard.hasLineOfSight(living) )continue;
            var item=state.inventory().items().get(duty.weapon());var stack=guard.getMainHandItem();
            var profile=FrontierV3WeaponProfiles.read(stack);
            if(!FrontierV3MeleeReach.contains(guard,living,profile) || !FrontierV3ExactItemPresentation.exactMatch(stack,item)
                    || item.condition().remaining()<profile.wear())continue;
            // The canonical prepared tick is a unique guard attempt boundary, not a random retry identity.
            var strike=new SecurityState.Strike(duty.id(),duty.revision(),actuation,contact.target(),duty.weapon(),item.condition(),profile,targetPort(living).identity().incarnation(),tick+1,tick);
            FrontierV3CommandSubmission.submit(runtime,"combat-prepare",duty.id().value(),new SecurityEvent.Prepared(strike));
            state=runtime.decodedState().orElseThrow();
        }
    }
    private static void execute(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime,SecurityState.Strike strike) {
        var state=runtime.decodedState().orElseThrow();
        var actor=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),strike.attacker().body().actorId()));
        var opponent=level.getEntity(strike.target().entity());
        var retained=FrontierV3OperationLedger.get(level).find(FrontierV3OperationReceipt.Protocol.MELEE_ATTACK,strike.duty(),"strike-"+strike.attemptOrdinal());
        var ledger=FrontierV3OperationLedger.get(level);
        if(retained.isPresent()) {
            var prior=retained.orElseThrow(); var command=ledger.ownerCommand(prior.ticket());
            if(command.isPresent()) { FrontierV3OperationConfirmation.submitRequired(level,runtime,prior.ticket(),command.orElseThrow().consequence()); return; }
            if(prior.cancelled()) { confirmMiss(level,runtime,strike,prior.ticket()); return; }
            if(actor instanceof Mob guard && attempt(guard,strike)) {
                boolean hit=guard.getPersistentData().getCompound(ATTEMPT).getBoolean("hit");
                FrontierV3OperationConfirmation.submitRequired(level,runtime,prior.ticket(),new SecurityEvent.Completed(strike.duty(),strike.attemptOrdinal(),hit,
                        hit&&strike.profile().wear()>0?strike.before().wear(strike.profile().wear()):strike.before()));return;
            }
        }
        if(!(actor instanceof Mob guard) || !(opponent instanceof LivingEntity target)) {
            if(retained.isEmpty()) FrontierV3CommandSubmission.submit(runtime,"combat-unattempted",strike.duty().value(),
                    new SecurityEvent.Completed(strike.duty(),strike.attemptOrdinal(),false,strike.before()));
            else cancelUnusedOrWait(level,runtime,strike,retained.orElseThrow());
            return;
        }
        if(retained.isEmpty() && (!canActuate(state,strike) || !guard.isAlive() || !target.isAlive()
                || targetPort(target).identity().incarnation()!=strike.targetIncarnation())) {
            FrontierV3CommandSubmission.submit(runtime,"combat-unattempted",strike.duty().value(),new SecurityEvent.Completed(strike.duty(),strike.attemptOrdinal(),false,strike.before()));return;
        }
        // Continuation retains the exact declaration and native incarnations admitted before the effect.
        // A respawned player or replacement body cannot become the participant of an old strike.
        List<FrontierV3MinecraftOperation.Participant> ports;
        FrontierV3OperationReceipt.Declaration declaration;
        if(retained.isPresent()) {
            declaration=retained.orElseThrow().declaration();
            ports=FrontierV3OperationParticipantLookup.current(level,declaration.participants());
            if(ports.isEmpty()) { cancelUnusedOrWait(level,runtime,strike,retained.orElseThrow()); return; }
        } else {
            var actorPort=FrontierV3OperationEntityParticipant.current(guard,strike.attacker().body().physicalEpoch());
            var targetPort=targetPort(target);
            ports=List.of(actorPort,targetPort);
            declaration=new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MELEE_ATTACK,strike.duty(),"strike-"+strike.attemptOrdinal(),
                    strike.toString(),ports.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet()));
        }
        var result=FrontierV3MinecraftOperation.applyDeferred(level,declaration,ports,()->{
                    var current=runtime.decodedState().orElseThrow();
                    return canActuate(current,strike) && strike.equals(current.security().pending().get(strike.duty())) && target.isAlive() && guard.isAlive()
                            && guard.hasLineOfSight(target)&&FrontierV3MeleeReach.contains(guard,target,strike.profile())
                            && current.inventory().items().containsKey(strike.weapon())
                            && FrontierV3ExactItemPresentation.exactMatch(guard.getMainHandItem(),current.inventory().items().get(strike.weapon()));
                },new FrontierV3NativeEffect(()->FrontierV3NativeEffect.Admission.READY,()->{
                    var source=guard.damageSources().mobAttack(guard);
                    float damage=EnchantmentHelper.modifyDamage(level,guard.getMainHandItem(),target,source,(float)strike.profile().damage());
                    guard.swing(InteractionHand.MAIN_HAND);
                    boolean hit=target.hurt(source,damage);
                    if(hit) {
                        if(strike.profile().wear()>0)guard.getMainHandItem().setDamageValue(strike.before().damage()+strike.profile().wear());
                        EnchantmentHelper.doPostAttackEffects(level,target,source);guard.setLastHurtMob(target);
                    }
                    var receipt=new CompoundTag();receipt.putString("owner",strike.duty().value());receipt.putLong("ordinal",strike.attemptOrdinal());receipt.putBoolean("hit",hit);
                    guard.getPersistentData().put(ATTEMPT,receipt);
                    return FrontierV3NativeEffect.Outcome.APPLIED;
                }),()->attempt(guard,strike));
        if(result.pending())return;
        if(result.complete()) {
            boolean hit=guard.getPersistentData().getCompound(ATTEMPT).getBoolean("hit");
            var after=hit&&strike.profile().wear()>0?strike.before().wear(strike.profile().wear()):strike.before();
            FrontierV3OperationConfirmation.submitRequired(level,runtime,result.ticket(),new SecurityEvent.Completed(strike.duty(),strike.attemptOrdinal(),hit,after));
        } else if(result.status()==FrontierV3MinecraftOperation.Status.NOT_APPLIED||result.status()==FrontierV3MinecraftOperation.Status.SOURCE_CHANGED) {
            FrontierV3OperationConfirmation.submitRequired(level,runtime,result.ticket(),new SecurityEvent.Completed(strike.duty(),strike.attemptOrdinal(),false,strike.before()));
        }
    }
    private static FrontierV3MinecraftOperation.Participant targetPort(LivingEntity target) {
        return target instanceof ServerPlayer player?FrontierV3OperationPlayers.currentHealth(player):FrontierV3OperationEntityParticipant.nativeObject(target);
    }
    private static void confirmMiss(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime,
            SecurityState.Strike strike, UUID ticket) {
        FrontierV3OperationConfirmation.submitRequired(level,runtime,ticket,
                new SecurityEvent.Completed(strike.duty(),strike.attemptOrdinal(),false,strike.before()));
    }
    private static void cancelUnusedOrWait(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime,
            SecurityState.Strike strike, FrontierV3OperationReceipt retained) {
        var cancelled=FrontierV3MinecraftOperation.cancelUnattempted(level,retained.declaration());
        if(cancelled.isPresent()) confirmMiss(level,runtime,strike,cancelled.orElseThrow().ticket());
        // An attempted effect without complete native receipts remains a visible local recovery case.
    }
    private static boolean canActuate(FrontierWorldState state,SecurityState.Strike strike) {
        try { ActorBodyAuthority.requireActuation(state,strike.attacker()); return true; }
        catch(IllegalArgumentException retired) { return false; }
    }
    private static boolean attempt(Mob body,SecurityState.Strike strike){
        var receipt=body.getPersistentData().getCompound(ATTEMPT);
        return receipt.getString("owner").equals(strike.duty().value())&&receipt.getLong("ordinal")==strike.attemptOrdinal();
    }
    private FrontierV3SecurityExecutor(){}
}
