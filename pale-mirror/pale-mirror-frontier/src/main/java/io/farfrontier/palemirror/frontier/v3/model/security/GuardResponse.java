package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** Shared contact selection and protected-area pursuit envelope; never tracks an invisible target. */
public final class GuardResponse {
    /** One cardinal neighbour and at most one support-height step, as admitted by response placement. */
    public static final int MAX_CONTACT_STATION_DISTANCE = 2;
    public static Optional<SecurityState.Contact> contact(FrontierWorldState state,GuardDuty duty,long tick){
        var contact=state.security().contacts().get(duty.home());
        if(contact==null || !contact.current(tick,state.bootstrap().ruleset().security().contactLifetime()))return Optional.empty();
        var position=new SurfaceAnchor(contact.position());
        var home=new SurfaceAnchor(FrontierWorldStateSupport.settlement(state.bootstrap(),duty.home()).anchor());
        boolean inScope=SecurityCoverage.distance(home,position)<=state.bootstrap().ruleset().security().homePursuitLimit() && ProtectionObligations.read(state,duty.home()).stream().anyMatch(target->
                SecurityCoverage.distance(target.observationAnchor(),position)<=state.bootstrap().ruleset().security().pursuitMargin());
        return inScope?Optional.of(contact):Optional.empty();
    }
    public static boolean targetRetained(FrontierWorldState state,GuardDuty duty) {
        return duty.mission().isPresent() && ProtectionObligations.read(state,duty.home()).stream().anyMatch(target->
                target.kind()==duty.mission().orElseThrow().targetKind() && target.id().equals(duty.mission().orElseThrow().target()));
    }
    public static boolean redirect(FrontierWorldState state,GuardDuty duty,long tick){
        if(duty.mission().isEmpty())return contact(state,duty,tick).isPresent();
        if(!targetRetained(state,duty))return true;
        var mission=duty.mission().orElseThrow();var contact=contact(state,duty,tick);
        if(mission.response().isEmpty())return contact.isPresent();
        return contact.isEmpty() || !mission.response().orElseThrow().equals(contact.orElseThrow().target())
                || SecurityCoverage.distance(mission.station(),new SurfaceAnchor(contact.orElseThrow().position()))>MAX_CONTACT_STATION_DISTANCE;
    }
    private GuardResponse(){}
}
