package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess;
import io.farfrontier.palemirror.frontier.v3.process.ActivityInterruptionPlanner;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.*;
/** Role-independent native weapon/LOS facts and exact melee effects. Never owns locomotion. */
public final class FrontierV3CombatExecutor {
    private static final String ATTEMPT="pm_v3_combat_attempt";
    private static final int MAX_PER_PASS=32;
    private static final Map<ServerLevel,Long> NEXT=new WeakHashMap<>();
    private static final Map<ServerLevel,Integer> CURSOR=new WeakHashMap<>();
    static void tick(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime){
        var state=runtime.decodedState().orElseThrow();
        for(var strike:state.combat().pending().values().stream().limit(MAX_PER_PASS).toList())execute(level,runtime,strike);
        if(level.getGameTime()<NEXT.getOrDefault(level,0L))return;
        NEXT.put(level,level.getGameTime()+state.bootstrap().ruleset().melee().sensingInterval());
        var capabilities=CombatComposition.CAPABILITIES;
        var orders=capabilities.orders(state,runtime.executionView().orElseThrow().instant().ticks());
        if(orders.isEmpty()){CURSOR.remove(level);return;}
        int cursor=Math.floorMod(CURSOR.getOrDefault(level,0),orders.size()),visited=0;
        for(int i=0;i<orders.size() && visited<MAX_PER_PASS;i++){
            var nominated=orders.get(cursor);cursor=(cursor+1)%orders.size();CURSOR.put(level,cursor);
            state=runtime.decodedState().orElseThrow();long tick=runtime.executionView().orElseThrow().instant().ticks();
            var order=capabilities.current(state,nominated.execution(),tick).orElse(null);
            if(order==null)continue;
            var declared=state.combat().armaments().get(order.owner());
            if(declared==null || !declared.matches(order)){
                var item=state.inventory().items().get(order.weapon());if(item==null)continue;
                var nativeItem=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(item.itemKind()));
                var definition=FrontierV3WeaponProfiles.read(new net.minecraft.world.item.ItemStack(nativeItem));
                FrontierV3CommandSubmission.submit(runtime,"combat-weapon-definition",order.owner().value(),new CombatEvent.WeaponDefined(order.execution(),order.weapon(),definition));
                state=runtime.decodedState().orElseThrow();order=capabilities.current(state,order.execution(),tick).orElse(null);if(order==null)continue;
            }
            if(!ActorBodyAuthority.retainsPhysicalCustody(state,order.execution().actorId()))continue;
            var body=ActorBodyAuthority.current(state,order.execution().actorId());
            if(!FrontierV3ActorBodyController.readyForExecution(level,state,List.of(body)))continue;
            var actor=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),order.execution().actorId()));
            if(!(actor instanceof Mob fighter) || !fighter.isAlive())continue;
            visited++;var actuation=FrontierV3ActorActuation.capture(state,fighter,order.execution(),runtime::decodedState).id();
            var item=state.inventory().items().get(order.weapon());var stack=fighter.getMainHandItem();
            if(item==null || !FrontierV3ExactItemPresentation.exactMatch(stack,item))continue;
            var profile=FrontierV3WeaponProfiles.read(stack);var known=state.combat().armaments().get(order.owner());
            if(known==null || !known.matches(order) || !known.profile().equals(profile)){
                FrontierV3CommandSubmission.submit(runtime,"combat-weapon",order.owner().value(),new CombatEvent.WeaponObserved(actuation,order.weapon(),profile));
                state=runtime.decodedState().orElseThrow();
                order=capabilities.current(state,order.execution(),tick).orElse(null);if(order==null)continue;
            }
            var actual=level.getEntity(order.target().entity());
            if(!(actual instanceof LivingEntity target) || !target.isAlive())continue;
            boolean visible=fighter.hasLineOfSight(target),inReach=FrontierV3MeleeReach.contains(fighter,target,profile);
            var decision=MeleeTactics.decide(order,new MeleeTactics.Facts(tick,target.isAlive(),visible,inReach,
                    state.combat().pending().containsKey(order.owner()),state.combat().nextAttack().getOrDefault(order.owner(),0L),item.condition().remaining()>=profile.wear()));
            if(decision==MeleeTactics.Decision.APPROACH){
                var station=order.station().orElse(null);
                if(station!=null && FrontierV3SemanticMovement.at(level,fighter,station)==SemanticTraversalArrival.Disposition.ARRIVED && !state.combat().rejected(order,station,tick))
                    FrontierV3CommandSubmission.submit(runtime,"combat-blocked-position",order.owner().value(),new CombatEvent.PositionBlocked(actuation,order.target(),station));
                continue;
            }
            if(decision!=MeleeTactics.Decision.ATTACK)continue;
            var approach=state.actorMovements().get(order.execution().actorId());
            if(approach!=null && (!capabilities.require(order.execution()).permitsApproach(state,approach)
                    || ActorMovementProcess.ownerTransitionCheckpoint(state,order.execution(),tick) instanceof ActivityInterruptionPlanner.Waiting))continue;
            var strike=new MeleeStrike(order.owner(),order.revision(),actuation,order.target(),order.weapon(),item.condition(),profile,
                    targetPort(target).identity().incarnation(),tick+1,tick);
            FrontierV3CommandSubmission.submit(runtime,"combat-prepare",order.owner().value(),new CombatEvent.Prepared(strike));
        }
    }
    private static void execute(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime,MeleeStrike strike) {
        var state=runtime.decodedState().orElseThrow();
        CombatComposition.CAPABILITIES.require(strike.attacker().execution()).requirePending(state,strike);
        var actor=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),strike.attacker().body().actorId()));
        var opponent=level.getEntity(strike.target().entity());
        var retained=FrontierV3OperationLedger.get(level).find(FrontierV3OperationReceipt.Protocol.MELEE_ATTACK,strike.owner(),"strike-"+strike.attemptOrdinal());
        var ledger=FrontierV3OperationLedger.get(level);
        if(retained.isPresent()) {
            var prior=retained.orElseThrow(); var command=ledger.ownerCommand(prior.ticket());
            if(command.isPresent()) { FrontierV3OperationConfirmation.submitRequired(level,runtime,prior.ticket(),command.orElseThrow().consequence()); return; }
            if(prior.cancelled()) { confirmMiss(level,runtime,strike,prior.ticket()); return; }
            if(actor instanceof Mob fighter && attempt(fighter,strike)) {
                boolean hit=fighter.getPersistentData().getCompound(ATTEMPT).getBoolean("hit");
                FrontierV3OperationConfirmation.submitRequired(level,runtime,prior.ticket(),new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),hit,
                        hit&&strike.profile().wear()>0?strike.before().wear(strike.profile().wear()):strike.before()));return;
            }
        }
        if(!(actor instanceof Mob fighter) || !(opponent instanceof LivingEntity target)) {
            if(retained.isEmpty()) FrontierV3CommandSubmission.submit(runtime,"combat-unattempted",strike.owner().value(),
                    new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),false,strike.before()));
            else cancelUnusedOrWait(level,runtime,strike,retained.orElseThrow());
            return;
        }
        if(retained.isEmpty() && (!canActuate(state,strike) || !fighter.isAlive() || !target.isAlive()
                || targetPort(target).identity().incarnation()!=strike.targetIncarnation())) {
            FrontierV3CommandSubmission.submit(runtime,"combat-unattempted",strike.owner().value(),new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),false,strike.before()));return;
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
            var actorPort=FrontierV3OperationEntityParticipant.current(fighter,strike.attacker().body().physicalEpoch());
            var targetPort=targetPort(target);
            ports=List.of(actorPort,targetPort);
            declaration=new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MELEE_ATTACK,strike.owner(),"strike-"+strike.attemptOrdinal(),
                    strike.toString(),ports.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet()));
        }
        var result=FrontierV3MinecraftOperation.applyDeferred(level,declaration,ports,()->{
                    var current=runtime.decodedState().orElseThrow();
                    return canActuate(current,strike) && strike.equals(current.combat().pending().get(strike.owner())) && target.isAlive() && fighter.isAlive()
                            && fighter.hasLineOfSight(target)&&FrontierV3MeleeReach.contains(fighter,target,strike.profile())
                            && current.inventory().items().containsKey(strike.weapon())
                            && FrontierV3ExactItemPresentation.exactMatch(fighter.getMainHandItem(),current.inventory().items().get(strike.weapon()));
                },new FrontierV3NativeEffect(()->FrontierV3NativeEffect.Admission.READY,()->{
                    var source=fighter.damageSources().mobAttack(fighter);
                    float damage=EnchantmentHelper.modifyDamage(level,fighter.getMainHandItem(),target,source,(float)strike.profile().damage());
                    fighter.swing(InteractionHand.MAIN_HAND);
                    boolean hit=target.hurt(source,damage);
                    if(hit) {
                        if(strike.profile().wear()>0)fighter.getMainHandItem().setDamageValue(strike.before().damage()+strike.profile().wear());
                        EnchantmentHelper.doPostAttackEffects(level,target,source);fighter.setLastHurtMob(target);
                    }
                    var receipt=new CompoundTag();receipt.putString("owner",strike.owner().value());receipt.putLong("ordinal",strike.attemptOrdinal());receipt.putBoolean("hit",hit);
                    fighter.getPersistentData().put(ATTEMPT,receipt);
                    if(hit && !target.isAlive()) {
                        // The confirmed terminal hit must outlive Minecraft's short corpse
                        // window and possible departure of the last presentation observer.
                        var after=runtime.decodedState().orElseThrow();
                        CombatComposition.CAPABILITIES.require(strike.attacker().execution()).targetEnded(after,strike)
                                .ifPresent(fact->FrontierV3ConfirmedNativeEvent.capture(level,fact.subject(),fact.payload()));
                    }
                    return FrontierV3NativeEffect.Outcome.APPLIED;
                }),()->attempt(fighter,strike));
        if(result.pending())return;
        if(result.complete()) {
            boolean hit=fighter.getPersistentData().getCompound(ATTEMPT).getBoolean("hit");
            var after=hit&&strike.profile().wear()>0?strike.before().wear(strike.profile().wear()):strike.before();
            FrontierV3OperationConfirmation.submitRequired(level,runtime,result.ticket(),new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),hit,after));
        } else if(result.status()==FrontierV3MinecraftOperation.Status.NOT_APPLIED||result.status()==FrontierV3MinecraftOperation.Status.SOURCE_CHANGED) {
            FrontierV3OperationConfirmation.submitRequired(level,runtime,result.ticket(),new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),false,strike.before()));
        }
    }
    private static FrontierV3MinecraftOperation.Participant targetPort(LivingEntity target) {
        return target instanceof ServerPlayer player?FrontierV3OperationPlayers.currentHealth(player):FrontierV3OperationEntityParticipant.nativeObject(target);
    }
    private static void confirmMiss(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime,
            MeleeStrike strike, UUID ticket) {
        FrontierV3OperationConfirmation.submitRequired(level,runtime,ticket,
                new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),false,strike.before()));
    }
    private static void cancelUnusedOrWait(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime,
            MeleeStrike strike, FrontierV3OperationReceipt retained) {
        var cancelled=FrontierV3MinecraftOperation.cancelUnattempted(level,retained.declaration());
        if(cancelled.isPresent()) confirmMiss(level,runtime,strike,cancelled.orElseThrow().ticket());
        // An attempted effect without complete native receipts remains a visible local recovery case.
    }
    private static boolean canActuate(FrontierWorldState state,MeleeStrike strike) {
        try { ActorBodyAuthority.requireActuation(state,strike.attacker()); return true; }
        catch(IllegalArgumentException retired) { return false; }
    }
    private static boolean attempt(Mob body,MeleeStrike strike){
        var receipt=body.getPersistentData().getCompound(ATTEMPT);
        return receipt.getString("owner").equals(strike.owner().value())&&receipt.getLong("ordinal")==strike.attemptOrdinal();
    }
    private FrontierV3CombatExecutor(){}
}
