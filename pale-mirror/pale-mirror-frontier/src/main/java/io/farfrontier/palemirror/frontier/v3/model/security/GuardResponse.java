package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
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
        return permitted(state,assigned.orElseThrow(),contact)?Optional.of(contact):Optional.empty();
    }
    /** The same assigned sighting permits finite search, never live tracking through walls. */
    public static boolean investigating(FrontierWorldState state,GuardDuty duty,long tick) {
        var retained=duty.mission().flatMap(GuardMission::response);
        var assigned=state.security().response(duty.id());
        if(retained.isEmpty() || assigned.isEmpty() || !assigned.orElseThrow().contact().home().equals(duty.home())
                || !assigned.orElseThrow().contact().target().equals(retained.orElseThrow()))return false;
        var sighting=state.security().contacts().get(assigned.orElseThrow().contact());
        return sighting!=null && sighting.current(tick,state.bootstrap().ruleset().security().investigationTicks())
                && permitted(state,assigned.orElseThrow(),sighting);
    }
    private static boolean permitted(FrontierWorldState state,SecurityState.Response response,SecurityState.Contact contact) {
        return response.engagement().contains(new SurfaceAnchor(contact.position()))
                && (response.engagement().kind().local() || inScope(state,response.contact().home(),contact));
    }
    public static boolean permitsStation(FrontierWorldState state,GuardDuty duty,SurfaceAnchor station) {
        return state.security().response(duty.id()).filter(r -> r.contact().home().equals(duty.home()) && r.engagement().contains(station)).isPresent();
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
        var mission=duty.mission().orElseThrow();
        boolean deferred=CombatComposition.CAPABILITIES.current(state,duty.execution(),tick).filter(order->state.combat().deferred(order,tick)).isPresent();
        var contact=contact(state,duty,tick).filter(ignored->!deferred);
        if(deferred && mission.response().isPresent())return true;
        if(mission.response().isPresent() && state.security().response(duty.id())
                .filter(r -> !r.engagement().contains(mission.station())).isPresent())return true;
        if(mission.response().isEmpty())return contact.isPresent();
        if(contact.isPresent()){
            var order=CombatComposition.CAPABILITIES.current(state,duty.execution(),tick).orElseThrow();
            if(MeleePositions.holding(state,order,tick))return false;
            var claim=state.combat().positions().get(duty.id());
            if(claim==null || !claim.sameOpponent(order) || !claim.active(tick,state.bootstrap().ruleset().melee())
                    || !MeleePositions.available(state,order,mission.station(),tick))return true;
        }
        if(contact.isPresent() && io.farfrontier.palemirror.frontier.v3.model.CombatComposition.CAPABILITIES.current(state,duty.execution(),tick)
                .filter(order->MeleePositions.needsReplacement(state.combat(),order,tick,state.bootstrap().ruleset().melee())).isPresent())return true;
        if(contact.isEmpty()) {
            if(!investigating(state,duty,tick))return true;
            var last=state.security().contact(duty.home(),mission.response().orElseThrow());
            return mission.search().map(search -> search.sightingRevision()!=last.revision())
                    .orElseGet(() -> SecurityCoverage.distance(mission.station(),new SurfaceAnchor(last.position()))>MAX_CONTACT_STATION_DISTANCE);
        }
        return mission.stage()!=GuardMission.Stage.INTERCEPT || !mission.response().orElseThrow().equals(contact.orElseThrow().target());
    }
    private GuardResponse(){}
}
