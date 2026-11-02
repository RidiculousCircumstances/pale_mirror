package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess;
import io.farfrontier.palemirror.frontier.v3.process.ActivityInterruptionPlanner;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.InteractionHand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.*;

/** Native sensors and melee effects. Movement is exclusively guard missions through UAE/navigation. */
public final class FrontierV3SecurityExecutor {
    private static final int MAX_GUARDS_PER_PASS=32;
    private static final Map<ServerLevel,Integer> OBSERVATION_CURSOR=new WeakHashMap<>();
    private static final Map<ServerLevel,Long> NEXT_OBSERVATION_TICK=new WeakHashMap<>();
    public static void observedDamage(ServerLevel level,LivingEntity victim,Entity attacker,float damage) {
        if(!(attacker instanceof ServerPlayer) && !(attacker instanceof Enemy) || damage<=0 || !FrontierV3PhysicalWorld.isPhysical(level))return;
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
        var target=new CombatTarget(attacker instanceof ServerPlayer?CombatTarget.Kind.PLAYER:CombatTarget.Kind.HOSTILE_MOB,
                attacker.getUUID(),level.dimension().location().toString());
        int radius=state.bootstrap().ruleset().security().observationRadius();
        var seen=victim.distanceToSqr(attacker)<=radius*(double)radius && victim.hasLineOfSight(attacker)
                ?Optional.of(position(attacker)):Optional.<BlockPosition>empty();
        FrontierV3ConfirmedNativeEvent.capture(level,resident.settlementId(),
                new SecurityEvent.Incident(resident.settlementId(),body,target,position(victim),seen,damage));
    }
    static void tick(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime) {
        var state=runtime.decodedState().orElseThrow();
        // Cooperative passes can resume on any tick. A modulo-only gate can miss
        // every opportunity at a stable nonzero phase; sample once the interval is due.
        long nativeTick=level.getGameTime();
        if(nativeTick<NEXT_OBSERVATION_TICK.getOrDefault(level,0L))return;
        NEXT_OBSERVATION_TICK.put(level,Math.addExact(nativeTick,state.bootstrap().ruleset().security().observationInterval()));
        state=runtime.decodedState().orElseThrow();int visited=0;
        var duties=state.strategicPlans().guards().values().stream().sorted(Comparator.comparing(GuardDuty::id)).toList();
        if(duties.isEmpty()) { OBSERVATION_CURSOR.remove(level); NEXT_OBSERVATION_TICK.remove(level); return; }
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
            for(var retainedContact:state.security().contactsIn(duty.home())) {
                var player=retainedContact.target().kind()==CombatTarget.Kind.PLAYER
                        ?level.getServer().getPlayerList().getPlayer(retainedContact.target().entity()):null;
                // Death removes the body from the level entity index after twenty ticks.
                // A connected player's actual dead incarnation remains in PlayerList until
                // respawn/disconnect; use that exact native object, not a missed corpse window.
                var known=retainedContact.target().kind()==CombatTarget.Kind.PLAYER
                        ?player:level.getEntity(retainedContact.target().entity());
                boolean departed=retainedContact.target().kind()==CombatTarget.Kind.PLAYER
                        && (player==null || player.serverLevel()!=level);
                boolean witnessedDeath=known instanceof LivingEntity living && !living.isAlive()
                        && guard.distanceToSqr(living)<=observationRadius*(double)observationRadius && guard.hasLineOfSight(living);
                if(departed || witnessedDeath) {
                    FrontierV3CommandSubmission.submit(runtime,"security-contact-ended",duty.id().value(),
                            new SecurityEvent.ContactEnded(actuation,duty.home(),retainedContact.target(),retainedContact.revision()));
                    state=runtime.decodedState().orElseThrow();
                }
            }
            var candidates=level.getEntitiesOfClass(LivingEntity.class,guard.getBoundingBox().inflate(observationRadius),
                    target->target!=guard && target.isAlive() && !(target instanceof ServerPlayer p && (p.isCreative()||p.isSpectator())));
            var basis=state;
            var visible=candidates.stream().filter(candidate->candidate instanceof ServerPlayer
                    ?basis.security().hostile(duty.home(),candidate.getUUID()):candidate instanceof Enemy)
                    .filter(candidate->guard.distanceToSqr(candidate)<=observationRadius*(double)observationRadius)
                    .filter(guard::hasLineOfSight).sorted(Comparator.comparingDouble((LivingEntity candidate)->guard.distanceToSqr(candidate))
                            .thenComparing(Entity::getUUID)).limit(SecurityState.MAX_CONTACTS_PER_HOME).toList();
            long tick=runtime.executionView().orElseThrow().instant().ticks();
            for(var target:visible) {
                var key=new CombatTarget(target instanceof ServerPlayer?CombatTarget.Kind.PLAYER:CombatTarget.Kind.HOSTILE_MOB,
                        target.getUUID(),level.dimension().location().toString());
                var previous=state.security().contact(duty.home(),key);var pos=target.blockPosition().below();
                if(previous==null && state.security().contactsIn(duty.home()).size()>=SecurityState.MAX_CONTACTS_PER_HOME)continue;
                var contact=new SecurityState.Contact(duty.home(),key,new BlockPosition(pos.getX(),pos.getY(),pos.getZ()),tick,
                        Math.incrementExact(state.security().observationRevision()));
                var personal=state.security().awareness().sight(duty.home(),duty.execution().actorId(),key);
                if(personal.isEmpty() || tick-personal.orElseThrow().atTick()>=state.bootstrap().ruleset().security().contactRefreshTicks()
                        || previous==null || !previous.position().equals(contact.position()) || tick-previous.atTick()>=state.bootstrap().ruleset().security().contactRefreshTicks()) {
                    FrontierV3CommandSubmission.submit(runtime,"security-contact",duty.id().value(),new SecurityEvent.Seen(actuation,contact,position(guard)));
                    state=runtime.decodedState().orElseThrow();
                }
            }
        }
    }
    private static BlockPosition position(Entity entity) {
        var p=entity.blockPosition().below();return new BlockPosition(p.getX(),p.getY(),p.getZ());
    }
    private FrontierV3SecurityExecutor(){}
}
