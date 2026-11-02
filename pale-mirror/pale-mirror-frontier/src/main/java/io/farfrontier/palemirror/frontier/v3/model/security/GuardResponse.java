package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** Shared contact selection and protected-area pursuit envelope; never tracks an invisible target. */
public final class GuardResponse {
    /** Admitted standing area includes every known contact within this support-space distance. */
    public static final int MAX_CONTACT_STATION_DISTANCE = 2;
    public static Optional<SecurityState.Contact> contact(FrontierWorldState state,GuardDuty duty,long tick){
        var assigned=state.security().response(duty.id());
        if(assigned.isEmpty() || !assigned.orElseThrow().contact().home().equals(duty.home()))return Optional.empty();
        var contact=state.security().contacts().get(assigned.orElseThrow().contact());
        if(contact==null || !contact.current(tick,state.bootstrap().ruleset().security().contactLifetime()))return Optional.empty();
        return inScope(state,duty.home(),contact)?Optional.of(contact):Optional.empty();
    }
    /** The same assigned sighting permits finite search, never live tracking through walls. */
    public static boolean investigating(FrontierWorldState state,GuardDuty duty,long tick) {
        var retained=duty.mission().flatMap(GuardMission::response);
        var assigned=state.security().response(duty.id());
        if(retained.isEmpty() || assigned.isEmpty() || !assigned.orElseThrow().contact().home().equals(duty.home())
                || !assigned.orElseThrow().contact().target().equals(retained.orElseThrow()))return false;
        var sighting=state.security().contacts().get(assigned.orElseThrow().contact());
        return sighting!=null && sighting.current(tick,state.bootstrap().ruleset().security().investigationTicks())
                && inScope(state,duty.home(),sighting);
    }
    static boolean inScope(FrontierWorldState state,io.farfrontier.palemirror.frontier.v3.api.SubjectId homeId,SecurityState.Contact contact) {
        var position=new SurfaceAnchor(contact.position());
        var home=new SurfaceAnchor(FrontierWorldStateSupport.settlement(state.bootstrap(),homeId).anchor());
        return SecurityCoverage.distance(home,position)<=state.bootstrap().ruleset().security().homePursuitLimit()
                && ProtectionObligations.read(state,homeId).stream().anyMatch(target->
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
        if(contact.isEmpty()) {
            if(!investigating(state,duty,tick))return true;
            var last=state.security().contact(duty.home(),mission.response().orElseThrow());
            return mission.search().map(search -> search.sightingRevision()!=last.revision())
                    .orElseGet(() -> SecurityCoverage.distance(mission.station(),new SurfaceAnchor(last.position()))>MAX_CONTACT_STATION_DISTANCE);
        }
        return mission.stage()!=GuardMission.Stage.INTERCEPT || !mission.response().orElseThrow().equals(contact.orElseThrow().target())
                || SecurityCoverage.distance(mission.station(),new SurfaceAnchor(contact.orElseThrow().position()))>MAX_CONTACT_STATION_DISTANCE;
    }
    /** Brief recovery gives native sensing one opportunity after cooldown before restarting an approach. */
    public static boolean recoveringAfterAttack(FrontierWorldState state,GuardDuty duty,long tick) {
        var next=state.security().nextAttack().get(duty.id());
        return next!=null && tick<=Math.addExact(next,state.bootstrap().ruleset().security().observationInterval());
    }
    private GuardResponse(){}
}
