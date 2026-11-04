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
                var definition=FrontierV3WeaponProfiles.read(new net.minecraft.world.item.ItemStack(nativeItem),order.weaponSkill());
                FrontierV3CommandSubmission.submit(runtime,"combat-weapon-definition",order.owner().value(),new CombatEvent.WeaponDefined(order.execution(),order.weapon(),definition));
                state=runtime.decodedState().orElseThrow();order=capabilities.current(state,order.execution(),tick).orElse(null);if(order==null)continue;
            }
            if(!ActorBodyAuthority.retainsPhysicalCustody(state,order.execution().actorId())){FrontierV3CombatTrace.unavailable(level,order,"NO_BODY");trace(order,"NO_BODY","");continue;}
            var body=ActorBodyAuthority.current(state,order.execution().actorId());
            if(!FrontierV3ActorBodyController.readyForExecution(level,state,List.of(body))){
                FrontierV3CombatTrace.unavailable(level,order,"BODY_WAIT");trace(order,"BODY_WAIT",ActorBodyAuthority.require(state,body).phase());continue;
            }
            var actor=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),order.execution().actorId()));
            if(!(actor instanceof Mob fighter) || !fighter.isAlive())continue;
            visited++;var actuation=FrontierV3ActorActuation.capture(state,fighter,order.execution(),runtime::decodedState).id();
            var item=state.inventory().items().get(order.weapon());var stack=fighter.getMainHandItem();
            if(item==null || !FrontierV3ExactItemPresentation.exactMatch(stack,item)){
                FrontierV3CombatTrace.unavailable(level,order,"WEAPON_WAIT");trace(order,"WEAPON_WAIT",stack);continue;
            }
            var profile=FrontierV3WeaponProfiles.read(stack,order.weaponSkill());var known=state.combat().armaments().get(order.owner());
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
            if(!state.combat().affords(order.owner(),tick,CombatMechanics.cost(order.profile(),rules.readiness().cost(attack)),rules))attack=order.fallback();
            boolean canSpend=state.combat().affords(order.owner(),tick,CombatMechanics.cost(order.profile(),rules.readiness().cost(attack)),rules);
            var previous=state.combat().stances().get(order.owner());
            boolean wasHolding=previous!=null && previous.observer().equals(actuation) && previous.current(order,tick,rules) && previous.holding();
            double holdReach=rules.holdReach(profile,wasHolding,order.style());
            boolean tacticalReady=MeleeTactics.readyAt(state.combat(),order,new SurfaceAnchor(new BlockPosition(fighter.getOnPos().getX(),fighter.getOnPos().getY(),fighter.getOnPos().getZ())),tick,rules);
            // A preferred flank is a movement goal, never a veto on real close contact.
            boolean holding=visible && distance<=holdReach;
            boolean contact=visible && distance<=profile.reach();
            boolean attackReady=visible && CombatMechanics.unlocked(order.profile(),profile,attack) && MeleeTactics.opportunity(profile,attack,distance,rules);
            final var primaryTarget=order.target();
            var allowedTargets=capabilities.require(order.execution()).areaTargets(state,order,tick);
            int opponents=1+(int)allowedTargets.stream().filter(t->!t.equals(primaryTarget)).map(t->level.getEntity(t.entity()))
                .filter(e->e instanceof LivingEntity living && living.isAlive() && fighter.distanceToSqr(living)<=profile.reach()*profile.reach() && fighter.hasLineOfSight(living)).limit(7).count();
            var threat=FrontierV3CombatObservation.observe(fighter,target,actuation,visible,visible && threateningSwing(fighter,target,rules),tick,opponents);
            var targetFacing=visible?CombatFacing.observed(target.getLookAngle().x,target.getLookAngle().z):java.util.Optional.<CombatFacing>empty();
            if(previous==null || !previous.observer().equals(actuation) || !previous.target().equals(order.target())
                    || !previous.threat().equals(threat) || previous.holding()!=holding || previous.attackReady()!=attackReady || previous.targetFacing().isPresent() && targetFacing.isEmpty()
                    || tick-previous.atTick()>=2L*rules.sensingInterval()){
                FrontierV3CommandSubmission.submit(runtime,"combat-stance",order.owner().value(),new CombatEvent.StanceObserved(new MeleeStance(actuation,order.target(),holding,attackReady,tick,targetFacing,threat)));
                state=runtime.decodedState().orElseThrow();
                order=capabilities.current(state,order.execution(),tick).orElse(null);if(order==null)continue;
            }
            boolean contactHandoff=CombatEngagementPlanner.contactHandoff(state.combat(),order,actuation,attack,tick,rules);
            if(state.combat().maneuverBlocksStrike(order.owner(),tick,rules.maneuvers()) && !contactHandoff){
                FrontierV3CombatTrace.decision(level,fighter,target,order,attack,"MANEUVER_BUSY",distance,separating,motion.lateral(),motion.x(),motion.z(),canSpend);continue;
            }
            var maneuverFacts=new MeleeManeuvers.Facts(distance,separating,visible && threateningSwing(fighter,target,rules),motion.x(),motion.z());
            if(Boolean.getBoolean("pale_mirror.frontier_v3.combat_trace")){
                var harmed=capabilities.require(order.execution()).harmedAt(state,order);
                if(harmed.isPresent() && tick-harmed.getAsLong()<=rules.maneuvers().harmLifetime())
                    trace(order,"MANEUVER_WINDOW","tick="+tick+" harm="+harmed.getAsLong()+" facts="+maneuverFacts+" preferred="+MeleeManeuvers.preference(state,order,maneuverFacts,tick,capabilities.require(order.execution())));
            }
            var tactic=MeleeDuelPolicy.choose(state,order,maneuverFacts,tick,capabilities.require(order.execution()),attackReady);
            if(tactic.action()==MeleeDuelPolicy.Action.WAIT){
                FrontierV3CombatTrace.decision(level,fighter,target,order,attack,
                        state.combat().pending().containsKey(order.owner())?"PENDING_EFFECT":state.combat().recovering(order.owner(),tick)?"RECOVERY":"MANEUVER_BUSY",
                        distance,separating,motion.lateral(),motion.x(),motion.z(),canSpend);continue;
            }
            if(tactic.action()==MeleeDuelPolicy.Action.MANEUVER && visible && (state.actorMovements().get(order.execution().actorId())==null
                    || capabilities.require(order.execution()).permitsManeuverInterruption(state,state.actorMovements().get(order.execution().actorId()),order))){
                FrontierV3CombatTrace.decision(level,fighter,target,order,attack,"TACTIC_"+tactic.maneuver().orElseThrow(),distance,separating,motion.lateral(),motion.x(),motion.z(),canSpend);
                var origin=FrontierV3SupportedBodyCapture.observe(level,fighter);
                if(origin.isPresent()){
                    var requested=FrontierV3CommandSubmission.propose(runtime,"combat-maneuver",order.owner().value(),
                            new CombatEvent.ManeuverRequested(actuation,order.target(),origin.orElseThrow().supportingSurface(),maneuverFacts));
                    if(!(requested instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected))continue;
                    FrontierV3CombatTrace.decision(level,fighter,target,order,attack,"TACTIC_REFUSED:"+requested,distance,separating,motion.lateral(),motion.x(),motion.z(),canSpend);
                    trace(order,"MANEUVER_REFUSED",requested);
                }
                // A physically unavailable evasion must not prevent an otherwise safe contact attack.
            }
            var decision=MeleeTactics.decide(order,new MeleeTactics.Facts(tick,target.isAlive(),visible,attackReady && tactic.action()!=MeleeDuelPolicy.Action.APPROACH,
                    state.combat().pending().containsKey(order.owner()),state.combat().nextAttack().getOrDefault(order.owner(),0L),canSpend && item.condition().remaining()>=profile.wear()));
            boolean slotReady=decision==MeleeTactics.Decision.ATTACK && canSpend && CombatEngagementPlanner.permitsStrike(state.combat(),order,actuation,attack,tick,rules);
            String traceGate=decision==MeleeTactics.Decision.WAIT
                    ?state.combat().pending().containsKey(order.owner())?"PENDING_EFFECT":!canSpend?"EFFORT":item.condition().remaining()<profile.wear()?"WEAPON_WORN":"RECOVERY"
                    :decision==MeleeTactics.Decision.ATTACK && !slotReady?"WAVE_SLOT":decision.name();
            FrontierV3CombatTrace.decision(level,fighter,target,order,attack,traceGate,distance,separating,motion.lateral(),motion.x(),motion.z(),canSpend);
            if(Boolean.getBoolean("pale_mirror.frontier_v3.combat_trace"))trace(order,"OPPORTUNITY",
                    "distance="+distance+" visible="+visible+" tactical="+tacticalReady+" attack="+attack+" decision="+decision+" actor="+fighter.position()+" target="+target.position());
            if(decision==MeleeTactics.Decision.APPROACH){
                var station=order.station().orElse(null);
                if(station!=null && MeleePositions.legal(order,profile,station) && FrontierV3SemanticMovement.at(level,fighter,station)==SemanticTraversalArrival.Disposition.ARRIVED
                        && !state.combat().rejected(order,station,tick,state.bootstrap().ruleset().melee()))
                    FrontierV3CommandSubmission.submit(runtime,"combat-blocked-position",order.owner().value(),new CombatEvent.PositionBlocked(actuation,order.target(),station));
                continue;
            }
            if(!canSpend || decision!=MeleeTactics.Decision.ATTACK || !slotReady)continue;
            var approach=state.actorMovements().get(order.execution().actorId());
            if(approach!=null && !contactHandoff && !capabilities.require(order.execution()).permitsApproach(state,approach)){
                FrontierV3CombatTrace.decision(level,fighter,target,order,attack,"MOVEMENT_CHECKPOINT",distance,separating,motion.lateral(),motion.x(),motion.z(),canSpend);continue;
            }
            var additional=attack==MeleeAttack.CIRCLE?allowedTargets.stream().filter(t->!t.equals(primaryTarget))
                .sorted(java.util.Comparator.comparing(t->t.entity().toString())).filter(t->{var e=level.getEntity(t.entity());
                    return e instanceof LivingEntity living && living.isAlive() && fighter.distanceToSqr(living)<=profile.reach()*profile.reach() && fighter.hasLineOfSight(living);})
                .limit(4).map(t->new CombatVictim(t,targetPort((LivingEntity)level.getEntity(t.entity())).identity().incarnation())).toList():java.util.List.<CombatVictim>of();
            var strike=new MeleeStrike(order.owner(),order.revision(),actuation,order.target(),order.weapon(),item.condition(),profile,attack,
                    targetPort(target).identity().incarnation(),tick+1,tick,order.profile(),additional);
            var result=FrontierV3CommandSubmission.propose(runtime,"combat-prepare",order.owner().value(),new CombatEvent.Prepared(strike));
            // Queue the exact native intent as soon as canonical publication completes.
            // Its own durable admission, not queueing, starts the visible contact clock.
            runtime.afterCommand(result,()->execute(level,runtime,strike));
            FrontierV3CombatTrace.strike(level,fighter,target,strike,"PREPARE_SUBMITTED",
                    result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Pending pending?"PENDING_"+pending.reason().name()
                        :result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected rejected?rejected.toString()
                        :contactHandoff?"CONTACT_HANDOFF_"+result.getClass().getSimpleName():result.getClass().getSimpleName());
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
                        hit&&strike.wear()>0?strike.before().wear(strike.wear()):strike.before()));return;
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
        if(withdrawForEvasion(level,runtime,state,strike,fighter,target,retained))return;
        if(retained.isEmpty()){
            var permission=CombatComposition.CAPABILITIES.current(state,strike.attacker().execution(),runtime.executionView().orElseThrow().instant().ticks());
            boolean permissionMatches=permission.filter(order->order.techniques().contains(strike.attack()) && order.target().equals(strike.target()) && order.weapon().equals(strike.weapon())).isPresent();
            if(!permissionMatches
                    || !fighter.hasLineOfSight(target)
                    || !MeleeTactics.opportunity(strike.profile(),strike.attack(),Math.sqrt(target.getBoundingBox().distanceToSqr(fighter.getEyePosition())),state.bootstrap().ruleset().melee())){
                FrontierV3CombatTrace.strike(level,fighter,target,strike,"CANCELLED",
                        !permissionMatches?"PERMISSION":!fighter.hasLineOfSight(target)?"LOS":"OUTSIDE_APPROACH_ALLOWANCE");
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
            var participants=new ArrayList<FrontierV3MinecraftOperation.Participant>();participants.add(actorPort);participants.add(targetPort);
            for(var v:strike.additional()){
                var e=level.getEntity(v.target().entity());
                if(!(e instanceof LivingEntity living) || targetPort(living).identity().incarnation()!=v.incarnation()){
                    FrontierV3CommandSubmission.submit(runtime,"combat-area-unavailable",strike.owner().value(),new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),MeleeOutcome.CANCELLED,strike.before()));return;
                }
                participants.add(targetPort(living));
            }
            ports=List.copyOf(participants);
            declaration=new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MELEE_ATTACK,strike.owner(),"strike-"+strike.attemptOrdinal(),
                    strike.toString(),ports.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet()));
        }
        FrontierV3CombatTrace.strike(level,fighter,target,strike,"CANONICAL_READY","PUBLISHED");
        // Native preparation begins in prepareStrike only after this intent is durable.
        // Storage waiting cannot consume the visible windup or its finite contact window.
        var result=FrontierV3MinecraftOperation.applyDeferred(level,declaration,ports,()->{
                    var current=runtime.decodedState().orElseThrow();
                    return canActuate(current,strike) && strike.equals(current.combat().pending().get(strike.owner())) && target.isAlive() && fighter.isAlive()
                            && fighter.hasLineOfSight(target)
                            && FrontierV3CombatPresentation.preparationAvailable(level,fighter,strike)
                            && CombatComposition.CAPABILITIES.current(current,strike.attacker().execution(),runtime.executionView().orElseThrow().instant().ticks())
                                .filter(order->order.techniques().contains(strike.attack()) && order.target().equals(strike.target()) && order.weapon().equals(strike.weapon())).isPresent()
                            && current.inventory().items().containsKey(strike.weapon())
                            && FrontierV3ExactItemPresentation.exactMatch(fighter.getMainHandItem(),current.inventory().items().get(strike.weapon()));
                },new FrontierV3NativeEffect(()->prepareStrike(level,runtime,fighter,target,strike),()->{
                    var current=runtime.decodedState().orElseThrow();var policy=current.bootstrap().ruleset().melee();
                    var permission=CombatComposition.CAPABILITIES.current(current,strike.attacker().execution(),runtime.executionView().orElseThrow().instant().ticks()).orElseThrow();
                    var allowed=CombatComposition.CAPABILITIES.require(permission.execution()).areaTargets(current,permission,runtime.executionView().orElseThrow().instant().ticks());
                    boolean hit=applyDeclaredImpact(level,fighter,target,strike,policy,allowed,killed->{
                        CombatComposition.CAPABILITIES.require(strike.attacker().execution()).targetEnded(current,strike.attacker(),killed)
                            .ifPresent(fact->FrontierV3ConfirmedNativeEvent.capture(level,fact.subject(),fact.payload()));
                    });
                    var receipt=new CompoundTag();receipt.putString("owner",strike.owner().value());receipt.putLong("ordinal",strike.attemptOrdinal());receipt.putBoolean("hit",hit);
                    fighter.getPersistentData().put(ATTEMPT,receipt);
                    return FrontierV3NativeEffect.Outcome.APPLIED;
                }),()->attempt(fighter,strike));
        FrontierV3CombatTrace.strike(level,fighter,target,strike,"OPERATION",result.status().name());
        if(result.pending())return;
        trace(strike,"RESULT",result.status());
        if(result.complete()) {
            boolean hit=fighter.getPersistentData().getCompound(ATTEMPT).getBoolean("hit");
            var after=hit&&strike.wear()>0?strike.before().wear(strike.wear()):strike.before();
            FrontierV3OperationConfirmation.submitRequired(level,runtime,result.ticket(),new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),hit?MeleeOutcome.HIT:MeleeOutcome.MISS,after));
        } else if(result.status()==FrontierV3MinecraftOperation.Status.NOT_APPLIED||result.status()==FrontierV3MinecraftOperation.Status.SOURCE_CHANGED) {
            FrontierV3CombatPresentation.cancel(level,fighter,strike);
            FrontierV3OperationConfirmation.submitRequired(level,runtime,result.ticket(),new CombatEvent.Completed(strike.owner(),strike.attemptOrdinal(),MeleeOutcome.CANCELLED,strike.before()));
        }
    }
    /** Every area participant was declared before durable admission. Current permission may revoke a secondary target. */
    static boolean applyDeclaredImpact(ServerLevel level,Mob fighter,LivingEntity primary,MeleeStrike strike,MeleeRules rules,
            Set<CombatTarget> allowed,java.util.function.Consumer<CombatTarget> killed){
        boolean hit=applyImpact(level,fighter,primary,strike,rules);
        if(hit && !primary.isAlive())killed.accept(strike.target());
        for(var victim:strike.additional()){
            var other=level.getEntity(victim.target().entity());
            if(other instanceof LivingEntity living && living.isAlive() && targetPort(living).identity().incarnation()==victim.incarnation() && allowed.contains(victim.target())){
                boolean applied=applyImpact(level,fighter,living,strike,rules);hit|=applied;
                if(applied && !living.isAlive())killed.accept(victim.target());
            }
        }
        return hit;
    }
    /** The transaction's actual native effect: approach latitude can never extend damage reach. */
    static boolean applyImpact(ServerLevel level,Mob fighter,LivingEntity target,MeleeStrike strike,MeleeRules rules){
        boolean inReach=Math.sqrt(target.getBoundingBox().distanceToSqr(fighter.getEyePosition()))<=CombatMechanics.reach(strike.profile(),strike.attack());
        boolean clear=inReach && FrontierV3MeleeReach.clearContact(fighter,target);
        boolean window=FrontierV3CombatPresentation.strikeWindow(level,fighter,strike,rules);
        boolean arc=FrontierV3CombatPresentation.strikeArc(fighter,target,strike,rules);
        boolean contact=inReach && clear && window && arc;
        FrontierV3CombatPresentation.impact(level,fighter,strike,target);
        fighter.swing(InteractionHand.MAIN_HAND);
        var source=fighter.damageSources().mobAttack(fighter);
        float damage=contact?EnchantmentHelper.modifyDamage(level,fighter.getMainHandItem(),target,source,(float)CombatMechanics.damage(strike.actorProfile(),strike.profile(),strike.attack())):0;
        float health=target.getHealth();boolean hit=contact && target.hurt(source,damage);
        if(Boolean.getBoolean("pale_mirror.frontier_v3.combat_trace"))trace(strike,"NATIVE_HIT","hit="+hit+" health="+health+"->"+target.getHealth());
        FrontierV3CombatTrace.strike(level,fighter,target,strike,hit?"HIT":"MISS",
                !inReach?"OUT_OF_REACH":!clear?"BODY_BLOCKED":!window?"WINDOW_CLOSED":!arc?"OUTSIDE_ARC":hit?"DAMAGE_APPLIED":"NATIVE_DAMAGE_REFUSED");
        if(hit){
            if(strike.wear()>0)fighter.getMainHandItem().setDamageValue(strike.before().damage()+strike.wear());
            double knockback=CombatMechanics.knockback(strike.actorProfile(),strike.attack());
            if(knockback>0)target.knockback(knockback,fighter.getX()-target.getX(),fighter.getZ()-target.getZ());
            EnchantmentHelper.doPostAttackEffects(level,target,source);fighter.setLastHurtMob(target);
        }
        return hit;
    }
    private static FrontierV3NativeEffect.Admission prepareStrike(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime,
            Mob fighter,LivingEntity target,MeleeStrike strike){
        var state=runtime.decodedState().orElseThrow();var rules=state.bootstrap().ruleset().melee();
        var admission=prepareImpact(level,fighter,target,strike,rules,runtime.executionView().orElseThrow().instant().ticks());
        FrontierV3MeleeGuidance.observe(level,state,runtime,fighter,target,strike,
                FrontierV3CombatPresentation.track(level,fighter,strike,rules,target),runtime.executionView().orElseThrow().instant().ticks());
        FrontierV3CombatTrace.strike(level,fighter,target,strike,"ADMISSION",admission.waitingFor().orElse("READY"));
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
        if(level.getGameTime()-started<strike.timing(rules).preparationTicks())
            return FrontierV3NativeEffect.Admission.waiting("MELEE_WINDUP");
        if(level.getGameTime()-started>=strike.timing(rules).preparationTicks()+strike.timing(rules).activeTicks())
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
    record ObservedMotion(double x,double z,double separating,double lateral) { }
    static ObservedMotion observedMotion(Mob fighter,LivingEntity target,ActorActuationId observer,boolean visible,int interval){
        if(!visible){SIGHTINGS.remove(fighter);return new ObservedMotion(0,0,0,0);}
        long now=fighter.level().getGameTime();
        var prior=SIGHTINGS.put(fighter,new Sighting(observer,target.getUUID(),target.getId(),now,target.position()));
        if(prior==null || !prior.observer().equals(observer) || !prior.target().equals(target.getUUID()) || prior.runtimeId()!=target.getId()
                || now<=prior.at() || now-prior.at()>2L*interval)return new ObservedMotion(0,0,0,0);
        var velocity=CombatMotion.between(prior.position().x,prior.position().z,target.getX(),target.getZ(),now-prior.at());
        var radial=target.position().subtract(fighter.position()).multiply(1,0,1).normalize();
        return new ObservedMotion(velocity.x(),velocity.z(),velocity.separating(radial.x,radial.z),velocity.lateral(radial.x,radial.z));
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
    /** A reversible preparation may yield only after its unused journal authority is withdrawn. */
    private static boolean withdrawForEvasion(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime,
            FrontierWorldState state,MeleeStrike strike,Mob fighter,LivingEntity target,java.util.Optional<FrontierV3OperationReceipt> retained){
        var tick=runtime.executionView().orElseThrow().instant().ticks();
        var order=CombatComposition.CAPABILITIES.current(state,strike.attacker().execution(),tick).orElse(null);
        if(order==null || !order.target().equals(strike.target()) || !order.weapon().equals(strike.weapon())
                || !fighter.hasLineOfSight(target) || !FrontierV3CombatPresentation.reversiblePreparation(level,fighter,strike,state.bootstrap().ruleset().melee()))return false;
        var capability=CombatComposition.CAPABILITIES.require(order.execution());
        var memory=MeleeDuelPolicy.memory(state,order,tick,capability);
        if(memory.signal()<=memory.withdrawnThrough())return false;
        double distance=Math.sqrt(target.getBoundingBox().distanceToSqr(fighter.getEyePosition()));
        var facts=new MeleeManeuvers.Facts(distance,0,threateningSwing(fighter,target,state.bootstrap().ruleset().melee()));
        var lateral=MeleeDuelPolicy.lateral(state,order,facts,tick,capability);
        if(lateral.isEmpty())return false;
        var origin=FrontierV3SupportedBodyCapture.observe(level,fighter);if(origin.isEmpty())return false;
        try{
            var assessment=io.farfrontier.palemirror.frontier.v3.process.CombatManeuverProcess.evasion(state,
                    new CombatEvent.ManeuverRequested(strike.attacker(),strike.target(),origin.orElseThrow().supportingSurface(),facts),tick);
            if(assessment.candidate().isEmpty()){
                FrontierV3CombatTrace.strike(level,fighter,target,strike,"EVASION_REFUSED",assessment.detail());return false;
            }
        }catch(io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable pending){
            FrontierV3CombatTrace.strike(level,fighter,target,strike,"EVASION_REFUSED","route "+pending.status());return false;
        }
        var declaration=retained.map(FrontierV3OperationReceipt::declaration).orElseGet(()->{
            var actorPort=FrontierV3OperationEntityParticipant.current(fighter,strike.attacker().body().physicalEpoch());
            var targetPort=targetPort(target);
            return new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MELEE_ATTACK,strike.owner(),
                    "strike-"+strike.attemptOrdinal(),strike.toString(),java.util.Set.of(actorPort.identity(),targetPort.identity()));
        });
        var cancelled=withdrawPreparation(level,fighter,strike,state.bootstrap().ruleset().melee(),declaration);
        if(cancelled.isEmpty())return false;
        FrontierV3CombatTrace.strike(level,fighter,target,strike,"CANCELLED","TACTICAL_EVASION");
        confirmMiss(level,runtime,strike,cancelled.orElseThrow().ticket());
        return true;
    }
    /** Phase alone is insufficient: only a positive unused-operation cancellation withdraws damage authority. */
    static java.util.Optional<FrontierV3OperationReceipt> withdrawPreparation(ServerLevel level,Mob fighter,MeleeStrike strike,MeleeRules rules,
            FrontierV3OperationReceipt.Declaration declaration){
        if(declaration.protocol()!=FrontierV3OperationReceipt.Protocol.MELEE_ATTACK || !declaration.owner().equals(strike.owner())
                || !declaration.operation().equals("strike-"+strike.attemptOrdinal()) || !declaration.fingerprint().equals(strike.toString()))
            throw new IllegalArgumentException("withdrawal lost its exact melee declaration");
        if(!FrontierV3CombatPresentation.reversiblePreparation(level,fighter,strike,rules))return java.util.Optional.empty();
        var cancelled=FrontierV3MinecraftOperation.cancelUnattemptedDeferred(level,declaration);
        if(cancelled.isPresent())FrontierV3CombatPresentation.cancel(level,fighter,strike);
        return cancelled;
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
