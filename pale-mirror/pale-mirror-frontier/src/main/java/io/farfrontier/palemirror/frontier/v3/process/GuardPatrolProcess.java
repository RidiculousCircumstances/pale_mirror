package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;
/** Guard coverage continuation. No body writer or path-search implementation. */
final class GuardPatrolProcess {
    static List<ProposedEvent> progress(FrontierWorldState state, ScheduledAction action, GuardDuty duty, long tick) {
        var mission = duty.mission();
        if(mission.isPresent() && !GuardResponse.targetRetained(state,duty)) mission=Optional.empty();
        var threat = GuardResponse.contact(state,duty,tick);
        if(threat.isPresent() && mission.isPresent()) {
            var contact=threat.orElseThrow();
            if(mission.orElseThrow().response().equals(Optional.of(contact.target()))
                    && GuardResponse.recoveringAfterAttack(state,duty,tick))
                return List.of(scheduled(action,duty,Math.addExact(state.security().nextAttack().get(duty.id()),
                        state.bootstrap().ruleset().security().observationInterval()+1L)));
            if(mission.isPresent() && mission.orElseThrow().response().equals(Optional.of(contact.target()))
                    && !GuardResponse.redirect(state,duty,tick)) {
                if(!duty.missionOrder().arrivedAt(state.actorLocations().get(duty.execution().actorId()).supportingSurface())) return start(state,action,duty,tick);
                return List.of(scheduled(action,duty,tick+10));
            }
            var knowledge=KnownPedestrianRouteKnowledge.forPublicJourney(state,duty.home(),List.of()).forActor(state,duty.execution().actorId());
            var stations=new ArrayList<SurfaceAnchor>();var position=contact.position();
            var reserved=GuardEquipment.in(state,duty.home()).stream().filter(other->!other.id().equals(duty.id()))
                    .flatMap(other->other.mission().stream()).map(GuardMission::station).collect(java.util.stream.Collectors.toSet());
            stations.addAll(PedestrianInteractionStations.within(knowledge.geometry(),new SurfaceAnchor(position),
                    GuardResponse.MAX_CONTACT_STATION_DISTANCE,reserved));
            stations.sort(Comparator.comparingLong(station->SecurityCoverage.distance(station,state.actorLocations().get(duty.execution().actorId()).supportingSurface())));
            var selected=reachable(knowledge,state.actorLocations().get(duty.execution().actorId()).supportingSurface(),stations);
            if(selected.isPresent()) {
                var response=mission.orElseThrow().responding(selected.orElseThrow(),contact.target());
                return List.of(new ProposedEvent(duty.id(),new GuardMissionSelected(duty.id(),duty.revision(),response)),GuardContinuation.wake(duty.id(),tick));
            }
            return List.of(scheduled(action,duty,tick+20));
        }
        if(mission.isPresent() && mission.orElseThrow().response().isPresent()) {
            if (GuardResponse.investigating(state,duty,tick)) {
                if (!duty.missionOrder().arrivedAt(state.actorLocations().get(duty.execution().actorId()).supportingSurface()))
                    return start(state,action,duty,tick);
                return List.of(scheduled(action,duty,tick+state.bootstrap().ruleset().security().observationInterval()));
            }
            var resumed=mission.orElseThrow().resume().orElseThrow().mission();
            if(ProtectionObligations.read(state,duty.home()).stream().anyMatch(target->target.kind()==resumed.targetKind() && target.id().equals(resumed.target())))
                return List.of(new ProposedEvent(duty.id(),new GuardMissionSelected(duty.id(),duty.revision(),resumed)),GuardContinuation.wake(duty.id(),tick));
            mission=Optional.empty();
        }
        if (mission.isPresent()) {
            var goal = mission.orElseThrow();
            if (goal.watchUntil() == 0) {
                if (!duty.missionOrder().arrivedAt(state.actorLocations().get(duty.execution().actorId()).supportingSurface()))
                    return start(state, action, duty, tick);
                var watching = goal.watching(tick + state.bootstrap().ruleset().security().watchTicks());
                return List.of(new ProposedEvent(duty.id(), new GuardMissionSelected(duty.id(), duty.revision(), watching)),
                        scheduled(action, duty, watching.watchUntil()));
            }
            if (tick < goal.watchUntil()) return List.of(scheduled(action, duty, goal.watchUntil()));
        }
        return selectPost(state,action,duty,tick,mission.map(value->value.visitOrdinal()+1).orElse(0L));
    }
    private static List<ProposedEvent> selectPost(FrontierWorldState state,ScheduledAction action,GuardDuty duty,long tick,long ordinal) {
        var targets=SecurityCoverage.posts(state,duty.home());
        if(targets.isEmpty())return List.of(scheduled(action,duty,tick+state.bootstrap().ruleset().security().watchTicks()));
        int offset = GuardEquipment.in(state, duty.home()).stream().map(GuardDuty::id).toList().indexOf(duty.id());
        var knowledge = KnownPedestrianRouteKnowledge.forPublicJourney(state, duty.home(), List.of()).forActor(state, duty.execution().actorId());
        var reserved = GuardEquipment.in(state, duty.home()).stream().filter(other -> !other.id().equals(duty.id()))
                .flatMap(other -> other.mission().stream()).map(GuardMission::station).collect(java.util.stream.Collectors.toSet());
        for (int trial=0; trial<targets.size(); trial++) {
            var target = targets.get(Math.floorMod(ordinal + offset + trial, targets.size()));
            var origin = target.observationAnchor(); var stations = new ArrayList<SurfaceAnchor>();
            for (int dx=-6; dx<=6; dx++) for (int dz=-6; dz<=6; dz++) {
                if (Math.max(Math.abs(dx),Math.abs(dz))<3) continue;
                for (var station : knowledge.surfacesAt(origin.x()+dx, origin.z()+dz)) {
                    if (Math.abs(station.y()-origin.y()) <= 4 && !knowledge.geometry().blocked(station)
                            && !reserved.contains(station) && ServiceBoundaryComposition.turnoverPoint(state, station).isEmpty()) stations.add(station);
                }
            }
            stations.sort(Comparator.comparingLong((SurfaceAnchor point) -> SecurityCoverage.distance(point, origin))
                    .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y));
            var selected=reachable(knowledge,state.actorLocations().get(duty.execution().actorId()).supportingSurface(),stations.stream().limit(8).toList());
            if(selected.isEmpty())continue;
            var next = new GuardMission(target.kind(), target.id(), selected.orElseThrow(), ordinal, 0);
            return List.of(new ProposedEvent(duty.id(), new GuardMissionSelected(duty.id(), duty.revision(), next)), GuardContinuation.wake(duty.id(), tick));
        }
        return List.of(scheduled(action, duty, tick + state.bootstrap().ruleset().security().watchTicks()));
    }
    private static List<ProposedEvent> start(FrontierWorldState state, ScheduledAction action, GuardDuty duty, long tick) {
        var knowledge=KnownPedestrianRouteKnowledge.forPublicJourney(state,duty.home(),List.of()).forActor(state,duty.execution().actorId());
        try { knowledge.plannedBetween(state.actorLocations().get(duty.execution().actorId()).supportingSurface(),duty.mission().orElseThrow().station()); }
        catch(KnownPedestrianNavigation.RouteUnavailable failure) {
            if(failure.status()!=PedestrianRouteResult.Status.NO_PATH)throw failure;
            return selectPost(state,action,duty,tick,duty.mission().orElseThrow().visitOrdinal()+1);
        }
        var move = new ActorMovement(duty.missionOrder(), tick, new ActorMovementContext.GuardMissionLeg(duty.id(), duty.revision()), duty.execution());
        var result = new ArrayList<>(ActorMovementProcess.planStart(move));
        result.add(scheduled(action,duty,tick+20)); return List.copyOf(result);
    }
    static List<ProposedEvent> moving(FrontierWorldState state,ScheduledAction action,GuardDuty duty,ActorMovement movement,long tick) {
        var knowledge=new GuardMissionMovementProvider().placementKnowledge(state,movement);
        var body=ActorMovementProcess.bodyAt(state,duty.execution().actorId(),tick);var goal=duty.mission().orElseThrow().station();
        boolean blocked=knowledge.geometry().clearanceKnown(goal) && !knowledge.clearStation(goal)
                || PedestrianRoutePlanning.peek(knowledge.geometry(),body.supportingSurface(),goal)
                        .filter(result->result.status()==PedestrianRouteResult.Status.NO_PATH).isPresent();
        if(!blocked)return List.of(scheduled(action,duty,tick+20));
        var assessment=new ActorMovementInterruptionPlanner().assessForOwnerTransition(state,duty.execution(),tick);
        if(assessment instanceof ActivityInterruptionPlanner.Waiting)return List.of(scheduled(action,duty,tick+20));
        var ready=((ActivityInterruptionPlanner.Ready)assessment).validate(state,duty.execution().actorId());
        var replacement=selectPost(ready.following(),action,duty,tick,duty.mission().orElseThrow().visitOrdinal()+1);
        var events=new ArrayList<>(ready.events());
        events.addAll(replacement);return List.copyOf(events);
    }
    private static Optional<SurfaceAnchor> reachable(KnownPedestrianRouteKnowledge knowledge,SurfaceAnchor origin,List<SurfaceAnchor> candidates) {
        for(var station:candidates)try { knowledge.plannedBetween(origin,station);return Optional.of(station); }
        catch(KnownPedestrianNavigation.RouteUnavailable failure) { if(failure.status()!=PedestrianRouteResult.Status.NO_PATH)throw failure; }
        return Optional.empty();
    }
    private static ProposedEvent scheduled(ScheduledAction action, GuardDuty duty, long tick) {
        return new ProposedEvent(duty.id(), new ScheduleEffect.Rescheduled(action.id(), GuardContinuation.progress(duty.id(), tick)));
    }
    private GuardPatrolProcess() { }
}
