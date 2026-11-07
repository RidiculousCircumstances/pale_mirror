package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** Shared ranged mechanics. Role capabilities retain political permission, assignments and supplies. */
public final class RangedTransitions {
    public static FrontierWorldState apply(FrontierWorldState state,SubjectId subject,RangedEvent event,long tick,CombatCapabilities capabilities) {
        var combat=state.combat();var ranged=combat.ranged();var update=FrontierWorldStateUpdate.begin();
        if(event instanceof RangedEvent.Completed done) {
            var shot=Optional.ofNullable(ranged.pending().get(done.owner())).orElseThrow(()->new IllegalArgumentException("no exact pending ranged shot"));
            capabilities.require(shot.attacker().execution()).requirePendingRanged(state,shot);
            if(!subject.equals(shot.owner()) || done.attemptOrdinal()!=shot.attemptOrdinal())throw new IllegalArgumentException("foreign ranged outcome");
            if(done.emitted()) {
                var ledger=ActorConsumableUse.observed(state,shot.attacker().execution().actorId(),
                        state.inventory().items().get(shot.weapon()).economicOwnerId(),shot.ammunition(),shot.binding(),shot.ammunitionLot(),done.remaining());
                var item=Objects.requireNonNull(state.inventory().items().get(shot.weapon()),"fired weapon");
                if(!item.condition().equals(shot.before()))throw new IllegalArgumentException("fired weapon condition changed before receipt");
                update.inventory(state.inventory().changeCondition(item,shot.before().wear(1)).withFungibleResources(ledger));
            }
            return state.withChanges(update.combat(combat.withRanged(ranged.completed(shot,tick,state.bootstrap().ruleset().ranged(),done.emitted()))));
        }
        var actor=event instanceof RangedEvent.Observed observed?observed.stance().observer():((RangedEvent.Prepared)event).shot().attacker();
        var order=CombatTransitions.requireOrder(state,capabilities,actor,tick);
        if(!subject.equals(order.owner()) || order.method()!=CombatMethod.CROSSBOW || !(order.use() instanceof CombatUse.Crossbow use))throw new IllegalArgumentException("ranged event has no declared weapon permission");
        var rules=state.bootstrap().ruleset().ranged();
        if(event instanceof RangedEvent.Observed observed) {
            var sight=observed.stance();
            if(sight.atTick()!=tick || !sight.target().equals(order.target()) || !sight.targetPosition().equals(order.targetPosition()))throw new IllegalArgumentException("stale ranged sighting");
            ranged=ranged.observed(sight);
        }else{
            var shot=((RangedEvent.Prepared)event).shot();var sight=ranged.stances().get(subject);
            if(sight==null)throw new IllegalArgumentException("missing ranged sight");
            var weapon=state.inventory().items().get(order.weapon());var binding=state.inventory().fungibleResources().bindings().get(shot.binding().id());
            if(!shot.origin().equals(order.actorPosition()) || !shot.technique().equals(RangedTactics.choose(ranged,order,sight,tick,rules))
                    || !shot.actorProfile().equals(order.profile()) || shot.ownerRevision()!=order.revision() || !shot.weapon().equals(order.weapon()) || !shot.target().equals(order.target())
                    || !shot.ammunition().equals(use.ammunition()) || !shot.before().equals(weapon.condition()) || !shot.binding().equals(binding)
                    || shot.binding().authorityEpoch()!=actor.body().physicalEpoch() || !state.inventory().fungibleResources().accounts().get(shot.ammunitionAccount()).custody().equals(new ResourceCustody.Actor(actor.execution().actorId()))
                    || shot.preparedAt()!=tick || shot.attemptOrdinal()!=tick+1 || tick<ranged.nextAttack().getOrDefault(subject,0L)
                    || RangedTactics.relocate(ranged,order,tick) || ranged.unsettled(subject) || ranged.supplies().containsKey(subject) || combat.pending().containsKey(subject) || combat.recovering(subject,tick) || combat.maneuverOpen(subject)
                    || state.actorMovements().containsKey(order.execution().actorId()) || sight==null || !sight.current(order,tick,rules) || !sight.visible() || !sight.clearShot()
                    || !CombatPositioning.legal(state,order,order.actorPosition(),tick))throw new IllegalArgumentException("ranged shot lacks exact ammo, current sight, stationary range or readiness");
            var lot=state.inventory().fungibleResources().lots().get(shot.ammunitionLot());
            if(lot==null || !lot.economicOwnerId().equals(weapon.economicOwnerId()) || !lot.itemKind().equals(use.ammunition()))throw new IllegalArgumentException("foreign ammunition ownership");
            ranged=ranged.prepared(shot);
        }
        return state.withChanges(update.combat(combat.withRanged(ranged)));
    }
    private RangedTransitions(){}
}
