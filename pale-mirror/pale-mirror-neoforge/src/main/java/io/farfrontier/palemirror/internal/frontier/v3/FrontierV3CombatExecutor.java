package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
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
    /** Disposable sighting pairs; no future input, hidden tracking or recovered motion is invented. */
    private record Sighting(ActorActuationId observer,UUID target,int runtimeId,long at,net.minecraft.world.phys.Vec3 position){}
    private static final Map<Mob,Sighting> SIGHTINGS=new WeakHashMap<>();
    static void tick(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime){
        FrontierV3CombatManeuvers.tick(level,runtime);
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
            if(!ActorBodyAuthority.retainsPhysicalCustody(state,order.execution().actorId())){trace(order,"NO_BODY","");continue;}
            var body=ActorBodyAuthority.current(state,order.execution().actorId());
            if(!FrontierV3ActorBodyController.readyForExecution(level,state,List.of(body))){trace(order,"BODY_WAIT",ActorBodyAuthority.require(state,body).phase());continue;}
            var actor=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),order.execution().actorId()));
            if(!(actor instanceof Mob fighter) || !fighter.isAlive())continue;
            visited++;var actuation=FrontierV3ActorActuation.capture(state,fighter,order.execution(),runtime::decodedState).id();
            var item=state.inventory().items().get(order.weapon());var stack=fighter.getMainHandItem();
            if(item==null || !FrontierV3ExactItemPresentation.exactMatch(stack,item)){trace(order,"WEAPON_WAIT",stack);continue;}
            var profile=FrontierV3WeaponProfiles.read(stack);var known=state.combat().armaments().get(order.owner());
            if(known==null || !known.matches(order) || !known.profile().equals(profile)){
                FrontierV3CommandSubmission.submit(runtime,"combat-weapon",order.owner().value(),new CombatEvent.WeaponObserved(actuation,order.weapon(),profile));
                state=runtime.decodedState().orElseThrow();
                order=capabilities.current(state,order.execution(),tick).orElse(null);if(order==null)continue;
            }
            var actual=level.getEntity(order.target().entity());
            if(!(actual instanceof LivingEntity target) || !target.isAlive())continue;
            boolean visible=fighter.hasLineOfSight(target);
            double distance=Math.sqrt(target.getBoundingBox().distanceToSqr(fighter.getEyePosition()));
            var motion=observedMotion(fighter,target,actuation,visible,state.bootstrap().ruleset().melee().sensingInterval());
            double separating=motion.separating();
            var attack=MeleeTactics.attack(state.combat(),order,actuation,targetPort(target).identity().incarnation(),tick,
                    profile,distance,separating,motion.lateral(),state.bootstrap().ruleset().melee());
            var rules=state.bootstrap().ruleset().melee();
            if(!state.combat().affords(order.owner(),tick,rules.readiness().cost(attack),rules))attack=MeleeAttack.QUICK;
            boolean canSpend=state.combat().affords(order.owner(),tick,rules.readiness().cost(attack),rules);
            var previous=state.combat().stances().get(order.owner());
            boolean wasHolding=previous!=null && previous.observer().equals(actuation) && previous.current(order,tick,rules) && previous.holding();
            double holdReach=rules.holdReach(profile,wasHolding);
            boolean tacticalReady=MeleeTactics.readyAt(state.combat(),order,new SurfaceAnchor(new BlockPosition(fighter.getOnPos().getX(),fighter.getOnPos().getY(),fighter.getOnPos().getZ())),tick,rules);
            // A preferred flank is a movement goal, never a veto on real close contact.
            boolean holding=visible && distance<=holdReach;
            boolean attackReady=visible && MeleeTactics.opportunity(profile,attack,distance,rules);
            if(previous==null || !previous.observer().equals(actuation) || !previous.target().equals(order.target())
                    || previous.holding()!=holding || previous.attackReady()!=attackReady || tick-previous.atTick()>=2L*rules.sensingInterval()){
                FrontierV3CommandSubmission.submit(runtime,"combat-stance",order.owner().value(),new CombatEvent.StanceObserved(new MeleeStance(actuation,order.target(),holding,attackReady,tick)));
                state=runtime.decodedState().orElseThrow();
                order=capabilities.current(state,order.execution(),tick).orElse(null);if(order==null)continue;
            }
            if(state.combat().maneuverBusy(order.owner(),tick,rules.maneuvers()))continue;
            var maneuverFacts=new MeleeManeuvers.Facts(distance,separating,visible && threateningSwing(fighter,target,rules));
            if(Boolean.getBoolean("pale_mirror.frontier_v3.combat_trace")){
                var harmed=capabilities.require(order.execution()).harmedAt(state,order);
                if(harmed.isPresent() && tick-harmed.getAsLong()<=rules.maneuvers().harmLifetime())
                    trace(order,"MANEUVER_WINDOW","tick="+tick+" harm="+harmed.getAsLong()+" facts="+maneuverFacts+" preferred="+MeleeManeuvers.preference(state,order,maneuverFacts,tick,capabilities.require(order.execution())));
            }
            // A defensive reaction may yield a hit; an optional gap-closing burst may not.
            boolean retreat=MeleeManeuvers.preference(state,order,maneuverFacts,tick,capabilities.require(order.execution()))
                    .filter(kind->kind==MeleeManeuver.Kind.BACKSTEP).isPresent();
            if((!attackReady || retreat) && visible && (state.actorMovements().get(order.execution().actorId())==null
                    || capabilities.require(order.execution()).permitsManeuverInterruption(state,state.actorMovements().get(order.execution().actorId()),order))
                    && MeleeManeuvers.preference(state,order,maneuverFacts,tick,capabilities.require(order.execution())).isPresent()){
                var origin=FrontierV3SupportedBodyCapture.observe(level,fighter);if(origin.isEmpty())continue;
                var requested=FrontierV3CommandSubmission.propose(runtime,"combat-maneuver",order.owner().value(),new CombatEvent.ManeuverRequested(actuation,order.target(),origin.orElseThrow().supportingSurface(),maneuverFacts));
                if(!(requested instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected))continue;
                trace(order,"MANEUVER_REFUSED",requested);
            }
            var decision=MeleeTactics.decide(order,new MeleeTactics.Facts(tick,target.isAlive(),visible,attackReady,
                    state.combat().pending().containsKey(order.owner()),state.combat().nextAttack().getOrDefault(order.owner(),0L),canSpend && item.condition().remaining()>=profile.wear()));
            if(Boolean.getBoolean("pale_mirror.frontier_v3.combat_trace"))trace(order,"OPPORTUNITY",
                    "distance="+distance+" visible="+visible+" tactical="+tacticalReady+" attack="+attack+" decision="+decision+" actor="+fighter.position()+" target="+target.position());
            if(decision==MeleeTactics.Decision.APPROACH){
                var station=order.station().orElse(null);
                if(station!=null && MeleePositions.legal(order,profile,station) && FrontierV3SemanticMovement.at(level,fighter,station)==SemanticTraversalArrival.Disposition.ARRIVED
                        && !state.combat().rejected(order,station,tick,state.bootstrap().ruleset().melee()))
                    FrontierV3CommandSubmission.submit(runtime,"combat-blocked-position",order.owner().value(),new CombatEvent.PositionBlocked(actuation,order.target(),station));
                continue;
            }
            if(!canSpend || decision!=MeleeTactics.Decision.ATTACK || !CombatEngagementPlanner.permitsStrike(state.combat(),order,actuation,tick,rules))continue;
            var approach=state.actorMovements().get(order.execution().actorId());
            if(approach!=null && !capabilities.require(order.execution()).permitsApproach(state,approach))continue;
            var strike=new MeleeStrike(order.owner(),order.revision(),actuation,order.target(),order.weapon(),item.condition(),profile,attack,
                    targetPort(target).identity().incarnation(),tick+1,tick);
            var result=FrontierV3CommandSubmission.submit(runtime,"combat-prepare",order.owner().value(),new CombatEvent.Prepared(strike));
            trace(order,"PREPARE",result);
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
                FrontierV3OperationConfirmation.submitRequired(level,runtime,prior.ticket(),new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),hit?MeleeOutcome.HIT:MeleeOutcome.MISS,
                        hit&&strike.profile().wear()>0?strike.before().wear(strike.profile().wear()):strike.before()));return;
            }
        }
        if(!(actor instanceof Mob fighter) || !(opponent instanceof LivingEntity target)) {
            if(retained.isEmpty()) FrontierV3CommandSubmission.submit(runtime,"combat-unattempted",strike.owner().value(),
                    new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),MeleeOutcome.CANCELLED,strike.before()));
            else cancelUnusedOrWait(level,runtime,strike,retained.orElseThrow());
            return;
        }
        if(retained.isEmpty() && (!canActuate(state,strike) || !fighter.isAlive() || !target.isAlive()
                || targetPort(target).identity().incarnation()!=strike.targetIncarnation())) {
            FrontierV3CommandSubmission.submit(runtime,"combat-unattempted",strike.owner().value(),new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),MeleeOutcome.CANCELLED,strike.before()));return;
        }
        if(retained.isEmpty()){
            var permission=CombatComposition.CAPABILITIES.current(state,strike.attacker().execution(),runtime.executionView().orElseThrow().instant().ticks());
            if(permission.filter(order->order.target().equals(strike.target()) && order.weapon().equals(strike.weapon())).isEmpty()
                    || !fighter.hasLineOfSight(target)
                    || !MeleeTactics.opportunity(strike.profile(),strike.attack(),Math.sqrt(target.getBoundingBox().distanceToSqr(fighter.getEyePosition())),state.bootstrap().ruleset().melee())){
                FrontierV3CombatPresentation.cancel(level,fighter,strike);
                FrontierV3CommandSubmission.submit(runtime,"combat-windup-cancelled",strike.owner().value(),
                        new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),MeleeOutcome.CANCELLED,strike.before()));return;
            }

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
                            && fighter.hasLineOfSight(target)
                            && FrontierV3CombatPresentation.preparationOpen(level,fighter,strike,current.bootstrap().ruleset().melee())
                            && CombatComposition.CAPABILITIES.current(current,strike.attacker().execution(),runtime.executionView().orElseThrow().instant().ticks())
                                .filter(order->order.target().equals(strike.target()) && order.weapon().equals(strike.weapon())).isPresent()
                            && current.inventory().items().containsKey(strike.weapon())
                            && FrontierV3ExactItemPresentation.exactMatch(fighter.getMainHandItem(),current.inventory().items().get(strike.weapon()));
                },new FrontierV3NativeEffect(()->prepareStrike(level,runtime,fighter,target,strike),()->{
                    boolean hit=applyImpact(level,fighter,target,strike,runtime.decodedState().orElseThrow().bootstrap().ruleset().melee());
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
        trace(strike,"RESULT",result.status());
        if(result.complete()) {
            boolean hit=fighter.getPersistentData().getCompound(ATTEMPT).getBoolean("hit");
            var after=hit&&strike.profile().wear()>0?strike.before().wear(strike.profile().wear()):strike.before();
            FrontierV3OperationConfirmation.submitRequired(level,runtime,result.ticket(),new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),hit?MeleeOutcome.HIT:MeleeOutcome.MISS,after));
        } else if(result.status()==FrontierV3MinecraftOperation.Status.NOT_APPLIED||result.status()==FrontierV3MinecraftOperation.Status.SOURCE_CHANGED) {
            FrontierV3CombatPresentation.cancel(level,fighter,strike);
            FrontierV3OperationConfirmation.submitRequired(level,runtime,result.ticket(),new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),MeleeOutcome.CANCELLED,strike.before()));
        }
    }
    /** The transaction's actual native effect: approach latitude can never extend damage reach. */
    static boolean applyImpact(ServerLevel level,Mob fighter,LivingEntity target,MeleeStrike strike,MeleeRules rules){
        boolean contact=FrontierV3MeleeReach.contains(fighter,target,strike.profile())
                && FrontierV3MeleeReach.clearContact(fighter,target)
                && FrontierV3CombatPresentation.strikeWindow(level,fighter,strike,rules)
                && FrontierV3CombatPresentation.strikeArc(fighter,target,strike,rules);
        FrontierV3CombatPresentation.impact(level,fighter,strike,target);
        fighter.swing(InteractionHand.MAIN_HAND);
        var source=fighter.damageSources().mobAttack(fighter);
        float damage=contact?EnchantmentHelper.modifyDamage(level,fighter.getMainHandItem(),target,source,(float)strike.profile().damage()):0;
        float health=target.getHealth();boolean hit=contact && target.hurt(source,damage);
        if(Boolean.getBoolean("pale_mirror.frontier_v3.combat_trace"))trace(strike,"NATIVE_HIT","hit="+hit+" health="+health+"->"+target.getHealth());
        if(hit){
            if(strike.profile().wear()>0)fighter.getMainHandItem().setDamageValue(strike.before().damage()+strike.profile().wear());
            EnchantmentHelper.doPostAttackEffects(level,target,source);fighter.setLastHurtMob(target);
        }
        return hit;
    }
    private static FrontierV3NativeEffect.Admission prepareStrike(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime,
            Mob fighter,LivingEntity target,MeleeStrike strike){
        var state=runtime.decodedState().orElseThrow();var rules=state.bootstrap().ruleset().melee();
        var admission=prepareImpact(level,fighter,target,strike,rules,runtime.executionView().orElseThrow().instant().ticks());
        FrontierV3MeleeGuidance.observe(level,state,runtime::decodedState,fighter,target,strike,
                FrontierV3CombatPresentation.track(level,fighter,strike,rules,target),runtime.executionView().orElseThrow().instant().ticks());
        return admission;
    }
    /** Runs only after durable intent, before spending the operation's live attempt. */
    static FrontierV3NativeEffect.Admission prepareImpact(ServerLevel level,Mob fighter,LivingEntity target,
            MeleeStrike strike,MeleeRules rules,long canonicalTick){
        long started=FrontierV3CombatPresentation.start(level,fighter,strike,rules,target);
        fighter.getLookControl().setLookAt(target,30,30);
        FrontierV3CombatPresentation.track(level,fighter,strike,rules,target);
        // Both clocks advance concurrently after durable intent; never two serial windups.
        if(canonicalTick<strike.impactAt(rules))return FrontierV3NativeEffect.Admission.waiting("MELEE_CANONICAL_ADMISSION");
        // Journal admission cannot consume the visible contact window.
        if(level.getGameTime()-started<rules.windup(strike.attack()))
            return FrontierV3NativeEffect.Admission.waiting("MELEE_WINDUP");
        if(level.getGameTime()-started>=rules.windup(strike.attack())+rules.techniques().require(strike.attack()).activeTicks())
            return FrontierV3NativeEffect.Admission.READY; // finite observed whiff, still no damage without contact
        if(!FrontierV3MeleeReach.contains(fighter,target,strike.profile())
                || !FrontierV3CombatPresentation.strikeWindow(level,fighter,strike,rules)
                || !FrontierV3CombatPresentation.strikeArc(fighter,target,strike,rules))
            return FrontierV3NativeEffect.Admission.waiting("MELEE_CONTACT_WINDOW");
        return FrontierV3NativeEffect.Admission.READY;
    }
    private static void trace(MeleeStrike strike,String reason,Object detail){
        if(Boolean.getBoolean("pale_mirror.frontier_v3.combat_trace"))io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info(
                "PMV3_MELEE actor={} owner={} ordinal={} reason={} detail={}",strike.attacker().execution().actorId().value(),strike.owner().value(),strike.attemptOrdinal(),reason,detail);
    }
    /** Opt-in bounded native diagnosis; never changes admission or persists another combat state. */
    private static void trace(CombatOrder order,String reason,Object detail){
        if(Boolean.getBoolean("pale_mirror.frontier_v3.combat_trace"))io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info(
                "PMV3_MELEE actor={} owner={} reason={} detail={}",order.execution().actorId().value(),order.owner().value(),reason,detail);
    }
    private record ObservedMotion(double separating,double lateral) { }
    private static ObservedMotion observedMotion(Mob fighter,LivingEntity target,ActorActuationId observer,boolean visible,int interval){
        if(!visible){SIGHTINGS.remove(fighter);return new ObservedMotion(0,0);}
        long now=fighter.level().getGameTime();
        var prior=SIGHTINGS.put(fighter,new Sighting(observer,target.getUUID(),target.getId(),now,target.position()));
        if(prior==null || !prior.observer().equals(observer) || !prior.target().equals(target.getUUID()) || prior.runtimeId()!=target.getId()
                || now<=prior.at() || now-prior.at()>2L*interval)return new ObservedMotion(0,0);
        var velocity=target.position().subtract(prior.position()).scale(1.0/(now-prior.at()));
        var radial=target.position().subtract(fighter.position()).multiply(1,0,1).normalize();
        return new ObservedMotion(velocity.dot(radial),Math.abs(velocity.x*radial.z-velocity.z*radial.x));
    }
    /** Only an already visible native swing aimed toward this body can invite a dodge. */
    static boolean threateningSwing(Mob fighter,LivingEntity target,MeleeRules rules){
        return target.swinging && target.swingTime>=0
                && Math.sqrt(fighter.getBoundingBox().distanceToSqr(target.getEyePosition()))<=rules.maneuvers().hurtGapMicros()/1_000_000.0
                && target.getLookAngle().normalize().dot(fighter.getBoundingBox().getCenter().subtract(target.getEyePosition()).normalize())
                    >=Math.cos(Math.toRadians(rules.engagements().quickTurnDegrees()));
    }
    private static FrontierV3MinecraftOperation.Participant targetPort(LivingEntity target) {
        return target instanceof ServerPlayer player?FrontierV3OperationPlayers.currentHealth(player):FrontierV3OperationEntityParticipant.nativeObject(target);
    }
    private static void confirmMiss(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime,
            MeleeStrike strike, UUID ticket) {
        FrontierV3OperationConfirmation.submitRequired(level,runtime,ticket,
                new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),MeleeOutcome.CANCELLED,strike.before()));
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
