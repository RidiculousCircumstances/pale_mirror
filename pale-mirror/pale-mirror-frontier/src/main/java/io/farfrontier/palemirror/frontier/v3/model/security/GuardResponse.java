package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** Shared contact selection and protected-area pursuit envelope; never tracks an invisible target. */
public final class GuardResponse {
    /** Admitted standing area includes every known contact within this support-space distance. */
    public static final int MAX_CONTACT_STATION_DISTANCE = 2;
    public static Optional<SecurityState.Contact> contact(FrontierWorldState state,GuardDuty duty,long tick){
        var contact=state.security().contacts().get(duty.home());
        if(contact==null || !contact.current(tick,state.bootstrap().ruleset().security().contactLifetime()))return Optional.empty();
        return inScope(state,duty,contact)?Optional.of(contact):Optional.empty();
    }
    /** A stale sighting is a bounded investigation goal, never permission to strike an unseen target. */
    public static boolean investigating(FrontierWorldState state,GuardDuty duty,long tick) {
        var retained=duty.mission().flatMap(GuardMission::response);
        var sighting=state.security().contacts().get(duty.home());
        return retained.isPresent() && sighting!=null && retained.orElseThrow().equals(sighting.target())
                && sighting.current(tick,state.bootstrap().ruleset().security().investigationTicks())
                && inScope(state,duty,sighting);
    }
    private static boolean inScope(FrontierWorldState state,GuardDuty duty,SecurityState.Contact contact) {
        var position=new SurfaceAnchor(contact.position());
        var home=new SurfaceAnchor(FrontierWorldStateSupport.settlement(state.bootstrap(),duty.home()).anchor());
        return SecurityCoverage.distance(home,position)<=state.bootstrap().ruleset().security().homePursuitLimit()
                && ProtectionObligations.read(state,duty.home()).stream().anyMatch(target->
                SecurityCoverage.distance(target.observationAnchor(),position)<=state.bootstrap().ruleset().security().pursuitMargin());
    }
    public static boolean targetRetained(FrontierWorldState state,GuardDuty duty) {
        if (duty.mission().isEmpty()) return false;
        var post = duty.mission().orElseThrow().post();
        return ProtectionObligations.read(state,duty.home()).stream().anyMatch(target ->
                target.kind() == post.kind() && target.id().equals(post.target())
                        && SecurityCoverage.permitsPost(target, post.station()));
    }
    public static boolean redirect(FrontierWorldState state,GuardDuty duty,long tick){
        if(duty.mission().isEmpty())return contact(state,duty,tick).isPresent();
        if(!targetRetained(state,duty))return true;
        var mission=duty.mission().orElseThrow();var contact=contact(state,duty,tick);
        if(mission.response().isEmpty())return contact.isPresent();
        if(contact.isEmpty())return !investigating(state,duty,tick);
        return !mission.response().orElseThrow().equals(contact.orElseThrow().target())
                || SecurityCoverage.distance(mission.station(),new SurfaceAnchor(contact.orElseThrow().position()))>MAX_CONTACT_STATION_DISTANCE;
    }
    /** Brief recovery gives native sensing one opportunity after cooldown before restarting an approach. */
    public static boolean recoveringAfterAttack(FrontierWorldState state,GuardDuty duty,long tick) {
        var next=state.security().nextAttack().get(duty.id());
        return next!=null && tick<=Math.addExact(next,state.bootstrap().ruleset().security().observationInterval());
    }
    private GuardResponse(){}
}
