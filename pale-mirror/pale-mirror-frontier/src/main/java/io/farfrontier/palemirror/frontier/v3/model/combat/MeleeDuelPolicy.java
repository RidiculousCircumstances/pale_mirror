package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** One common local arbiter; roles grant capabilities, geometry supplies destinations, navigation moves. */
public final class MeleeDuelPolicy {
    public enum Action { ATTACK, MANEUVER, APPROACH, WAIT }
    public record Choice(Action action, Optional<MeleeManeuver.Kind> maneuver) {
        public Choice { if ((action==Action.MANEUVER)!=maneuver.isPresent()) throw new IllegalArgumentException("invalid duel choice"); }
        static Choice of(Action action) { return new Choice(action,Optional.empty()); }
        static Choice maneuver(MeleeManeuver.Kind kind) { return new Choice(Action.MANEUVER,Optional.of(kind)); }
    }
    public static MeleeExchange memory(FrontierWorldState state,CombatOrder order,long tick,CombatCapability capability) {
        return memory(state.combat(),order,tick,state.bootstrap().ruleset().melee(),capability.harmedAt(state,order));
    }
    private static MeleeExchange memory(CombatState combat,CombatOrder order,long tick,MeleeRules rules,OptionalLong harm) {
        var prior=combat.exchanges().get(order.owner());
        return (prior!=null && prior.matches(order)?prior:MeleeExchange.first(order)).observed(harm,tick,rules.maneuvers().duel());
    }
    /** Does not authorize strike cancellation. The native journal and exact visible phase do that. */
    public static Optional<MeleeManeuver.Kind> lateral(FrontierWorldState state,CombatOrder order,MeleeManeuvers.Facts facts,long tick,CombatCapability capability) {
        return lateral(state.combat(),order,facts,tick,state.bootstrap().ruleset().melee(),capability.harmedAt(state,order),capability.maneuvers());
    }
    private static Optional<MeleeManeuver.Kind> lateral(CombatState combat,CombatOrder order,MeleeManeuvers.Facts facts,long tick,
            MeleeRules rules,OptionalLong harm,Set<MeleeManeuver.Kind> allowed) {
        var duel=rules.maneuvers().duel();var prior=combat.maneuvers().get(order.owner());var memory=memory(combat,order,tick,rules,harm);
        if(!memory.pressured(tick,duel) || facts.distance()>duel.maximumGapMicros()/1_000_000.0
                || prior!=null && prior.busy(tick,rules.maneuvers()))return Optional.empty();
        int side=memory.side();
        if(side==0){
            var sector=MeleeTactics.sector(combat,order,tick,rules);
            side=switch(sector.role()){
                case FLANK_LEFT -> 1;case FLANK_RIGHT -> -1;
                default -> (order.execution().actorId().value().hashCode()&1)==0?1:-1;
            };
        }
        var kind=side>0?MeleeManeuver.Kind.SIDESTEP_LEFT:MeleeManeuver.Kind.SIDESTEP_RIGHT;
        if(!allowed.contains(kind))kind=kind==MeleeManeuver.Kind.SIDESTEP_LEFT?MeleeManeuver.Kind.SIDESTEP_RIGHT:MeleeManeuver.Kind.SIDESTEP_LEFT;
        return allowed.contains(kind) && (prior==null || prior.ready(kind,tick))
                && combat.affords(order.owner(),tick,rules.readiness().cost(kind),rules)?Optional.of(kind):Optional.empty();
    }
    public static Choice choose(FrontierWorldState state,CombatOrder order,MeleeManeuvers.Facts facts,long tick,CombatCapability capability) {
        var profile=MeleePositions.weapon(state.combat(),order);
        boolean opportunity=profile.filter(weapon->MeleeTactics.opportunity(weapon,
                MeleeTactics.attack(weapon,facts.distance(),facts.separatingSpeed(),state.bootstrap().ruleset().melee()),facts.distance(),state.bootstrap().ruleset().melee())).isPresent();
        return choose(state,order,facts,tick,capability,opportunity);
    }
    public static Choice choose(FrontierWorldState state,CombatOrder order,MeleeManeuvers.Facts facts,long tick,CombatCapability capability,boolean attackReady) {
        return choose(state.combat(),order,facts,tick,state.bootstrap().ruleset().melee(),capability.harmedAt(state,order),capability.maneuvers(),attackReady);
    }
    /** Pure arbitration over retained mechanics and owner-supplied observations/capabilities. */
    public static Choice choose(CombatState combat,CombatOrder order,MeleeManeuvers.Facts facts,long tick,MeleeRules rules,
            OptionalLong harm,Set<MeleeManeuver.Kind> allowed,boolean attackReady) {
        boolean contact=MeleePositions.weapon(combat,order).filter(profile->facts.distance()<=profile.reach()).isPresent();
        if(combat.pending().containsKey(order.owner()))return Choice.of(Action.WAIT);
        var prior=combat.maneuvers().get(order.owner());
        if(prior!=null && prior.busy(tick,rules.maneuvers()))
            return Choice.of(prior.kind().closing() && (!prior.open() || prior.launchedAt()>0 || prior.kind()==MeleeManeuver.Kind.PURSUIT_RUN) && contact && attackReady
                    ?Action.ATTACK:Action.WAIT);
        var lateral=lateral(combat,order,facts,tick,rules,harm,allowed);
        if(lateral.isPresent())return Choice.maneuver(lateral.orElseThrow());
        // Footwork can use weapon recovery; another weapon attack cannot.
        if(combat.recovering(order.owner(),tick))return Choice.of(Action.WAIT);
        if(contact)return Choice.of(attackReady?Action.ATTACK:Action.APPROACH);
        if(allowed.contains(MeleeManeuver.Kind.PURSUIT_RUN) && (prior==null || prior.ready(MeleeManeuver.Kind.PURSUIT_RUN,tick))
                && combat.affords(order.owner(),tick,rules.readiness().cost(MeleeManeuver.Kind.PURSUIT_RUN),rules))
            return Choice.maneuver(MeleeManeuver.Kind.PURSUIT_RUN);
        var ordinary=MeleeManeuvers.ordinaryPreference(combat,order,facts,tick,rules,harm,allowed);
        return ordinary.map(Choice::maneuver).orElseGet(()->Choice.of(attackReady?Action.ATTACK:Action.APPROACH));
    }
    private MeleeDuelPolicy() { }
}
