package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;
public final class CombatTransitions {
    public static CombatOrder requireOrder(FrontierWorldState state,CombatCapabilities capabilities,ActorActuationId actor,long tick){
        ActorBodyAuthority.requireActuation(state,actor);state.actorExecutions().requireCurrent(actor.execution());
        var order=capabilities.current(state,actor.execution(),tick).orElseThrow(()->new IllegalArgumentException("no current combat permission"));
        if(!order.current(tick))throw new IllegalArgumentException("expired combat permission");requireEquipped(state,order);return order;
    }
    public static FrontierWorldState apply(FrontierWorldState state,SubjectId subject,CombatEvent event,long tick,CombatCapabilities capabilities){
        var combat=state.combat();var update=FrontierWorldStateUpdate.begin();
        if(event instanceof CombatEvent.Prepared prepared){
            var strike=prepared.strike();var order=requireOrder(state,capabilities,strike.attacker(),tick);var item=state.inventory().items().get(strike.weapon());
            if(!subject.equals(strike.owner()) || !order.owner().equals(strike.owner()) || order.revision()!=strike.ownerRevision()
                    || !order.target().equals(strike.target()) || !order.weapon().equals(strike.weapon()) || strike.preparedAt()!=tick
                    || item==null || !item.condition().equals(strike.before()) || !item.itemKind().equals(strike.profile().itemKind())
                    || tick<combat.nextAttack().getOrDefault(strike.owner(),0L) || state.actorMovements().containsKey(order.execution().actorId()))
                throw new IllegalArgumentException("melee lacks current owner/target/weapon/readiness");
            combat=combat.prepared(strike);
        }else if(event instanceof CombatEvent.Completed outcome){
            var strike=Objects.requireNonNull(combat.pending().get(outcome.owner()),"pending melee attempt");
            capabilities.require(strike.attacker().execution()).requirePending(state,strike);
            if(!subject.equals(outcome.owner()) || outcome.attemptOrdinal()!=strike.attemptOrdinal()
                    || !outcome.after().equals(outcome.applied() && strike.profile().wear()>0?strike.before().wear(strike.profile().wear()):strike.before()))
                throw new IllegalArgumentException("melee outcome lost its exact effect/wear declaration");
            var item=state.inventory().items().get(strike.weapon());
            if(outcome.applied() && item!=null){
                if(!item.itemKind().equals(strike.profile().itemKind()) || !item.condition().equals(strike.before()) && !item.condition().equals(outcome.after()))
                    throw new IllegalArgumentException("melee receipt conflicts with current exact weapon condition");
                if(!item.condition().equals(outcome.after()))update.inventory(state.inventory().changeCondition(item,outcome.after()));
            }
            combat=combat.completed(strike,tick);
        }else if(event instanceof CombatEvent.WeaponDefined defined){
            state.actorExecutions().requireCurrent(defined.execution());
            var order=capabilities.current(state,defined.execution(),tick).filter(value->value.current(tick))
                    .orElseThrow(()->new IllegalArgumentException("no current combat definition consumer"));
            requireEquipped(state,order);
            var item=state.inventory().items().get(defined.weapon());
            if(!subject.equals(order.owner()) || !order.weapon().equals(defined.weapon()) || item==null || !item.itemKind().equals(defined.profile().itemKind()))
                throw new IllegalArgumentException("weapon definition lost exact current equipment");
            combat=combat.observed(new CombatState.Armament(order.execution(),defined.weapon(),defined.profile()));
        }else{
            var observer=event instanceof CombatEvent.WeaponObserved weapon?weapon.observer():((CombatEvent.PositionBlocked)event).observer();
            var order=requireOrder(state,capabilities,observer,tick);
            if(!subject.equals(order.owner()))throw new IllegalArgumentException("foreign combat fact owner");
            if(event instanceof CombatEvent.WeaponObserved weapon){
                var item=state.inventory().items().get(weapon.weapon());
                if(!order.weapon().equals(weapon.weapon()) || item==null || !item.itemKind().equals(weapon.profile().itemKind()))
                    throw new IllegalArgumentException("combat weapon fact lost its exact current item");
                combat=combat.observed(new CombatState.Armament(order.execution(),weapon.weapon(),weapon.profile()));
            }else{
                var blocked=(CombatEvent.PositionBlocked)event;
                if(!blocked.target().equals(order.target()) || !order.station().equals(Optional.of(blocked.station())))
                    throw new IllegalArgumentException("rejected melee station lost its exact target/goal");
                combat=combat.blocked(order,blocked.station(),tick);
            }
        }
        return state.withChanges(update.combat(combat));
    }
    private static void requireEquipped(FrontierWorldState state,CombatOrder order){
        var item=state.inventory().items().get(order.weapon());
        if(item==null || !item.condition().usable() || !item.custody().equals(new InventoryCustody.Actor(order.execution().actorId())))
            throw new IllegalArgumentException("combat requires this actor's actual usable equipment custody");
    }
    private CombatTransitions(){}
}
