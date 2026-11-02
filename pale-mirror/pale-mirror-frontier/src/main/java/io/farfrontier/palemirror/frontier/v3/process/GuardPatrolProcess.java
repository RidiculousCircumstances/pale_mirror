package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
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
                    && GuardResponse.recoveringAfterAttack(state,duty,tick) && !GuardResponse.redirect(state,duty,tick))
                return List.of(scheduled(action,duty,Math.addExact(state.combat().nextAttack().get(duty.id()),
                        state.bootstrap().ruleset().security().observationInterval()+1L)));
            if(mission.isPresent() && mission.orElseThrow().response().equals(Optional.of(contact.target()))
                    && !GuardResponse.redirect(state,duty,tick)) {
                if(!duty.missionOrder().arrivedAt(state.actorLocations().get(duty.execution().actorId()).supportingSurface())) return start(state,action,duty,tick);
                return List.of(scheduled(action,duty,tick+10));
            }
            var knowledge=KnownPedestrianRouteKnowledge.forPublicJourney(state,duty.home(),List.of()).forActor(state,duty.execution().actorId());
            var order=CombatComposition.CAPABILITIES.current(state,duty.execution(),tick).orElseThrow();
            var selected=MeleePositions.select(state,order,knowledge,CombatComposition.CAPABILITIES.orders(state,tick),tick,Set.of());
            if(selected.isPresent()) {
                var response=mission.orElseThrow().responding(selected.orElseThrow(),contact.target());
                return List.of(new ProposedEvent(duty.id(),new GuardMissionSelected(duty.id(),duty.revision(),response)),GuardContinuation.wake(duty.id(),tick));
            }
            return List.of(scheduled(action,duty,tick+20));
        }
        if(mission.isPresent() && mission.orElseThrow().response().isPresent()) {
            if (GuardResponse.investigating(state,duty,tick)) return search(state,action,duty,tick);
            var resumed=mission.orElseThrow().resume().orElseThrow().mission();
            if(ProtectionObligations.read(state,duty.home()).stream().anyMatch(target->target.kind()==resumed.targetKind() && target.id().equals(resumed.target())))
                return List.of(new ProposedEvent(duty.id(),new GuardMissionSelected(duty.id(),duty.revision(),resumed)),
                        SecurityReview.wake(duty.home(),tick),GuardContinuation.wake(duty.id(),tick));
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
    private static List<ProposedEvent> search(FrontierWorldState state,ScheduledAction action,GuardDuty duty,long tick) {
        var mission=duty.mission().orElseThrow();var rules=state.bootstrap().ruleset().security();
        var sighting=state.security().contact(duty.home(),mission.response().orElseThrow());
        if(mission.stage()!=GuardMission.Stage.SEARCH || mission.search().orElseThrow().sightingRevision()!=sighting.revision()) {
            var knowledge=KnownPedestrianRouteKnowledge.forPublicJourney(state,duty.home(),List.of()).forActor(state,duty.execution().actorId());
            var station=approach(state,duty,knowledge,sighting.position());
            if(station.isEmpty())return List.of(scheduled(action,duty,tick+rules.observationInterval()));
            var searching=mission.searching(station.orElseThrow(),0,0,sighting.revision());
            return List.of(new ProposedEvent(duty.id(),new GuardMissionSelected(duty.id(),duty.revision(),searching)),GuardContinuation.wake(duty.id(),tick));
        }
        if(!duty.missionOrder().arrivedAt(state.actorLocations().get(duty.execution().actorId()).supportingSurface()))
            return start(state,action,duty,tick);
        var search=mission.search().orElseThrow();
        if(search.pauseUntil()==0) {
            long until=tick+rules.searchPauseTicks();
            return List.of(new ProposedEvent(duty.id(),new GuardMissionSelected(duty.id(),duty.revision(),
                    mission.searching(mission.station(),search.probe(),until,search.sightingRevision()))),scheduled(action,duty,until));
        }
        if(tick<search.pauseUntil())return List.of(scheduled(action,duty,search.pauseUntil()));
        if(search.probe()<rules.searchProbes()) {
            var contact=state.security().contact(duty.home(),mission.response().orElseThrow());
            var knowledge=KnownPedestrianRouteKnowledge.forPublicJourney(state,duty.home(),List.of()).forActor(state,duty.execution().actorId());
            var excluded=new HashSet<>(search.visited());
            GuardEquipment.in(state,duty.home()).stream().filter(g -> !g.id().equals(duty.id()))
                    .flatMap(g -> g.mission().stream()).map(GuardMission::station).forEach(excluded::add);
            var candidates=PedestrianInteractionStations.within(knowledge.geometry(),new SurfaceAnchor(contact.position()),rules.searchRadius(),excluded)
                    .stream().filter(surface -> GuardResponse.permitsStation(state,duty,surface) && !FacilityPedestrianContacts.of(state.bootstrap()).noParking().contains(surface.support())
                            && ServiceBoundaryComposition.turnoverPoint(state,surface).isEmpty())
                    .sorted(Comparator.comparingLong((SurfaceAnchor surface) -> -SecurityCoverage.distance(surface,new SurfaceAnchor(contact.position())))
                            .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z)).toList();
            var selected=reachable(knowledge,mission.station(),candidates);
            if(selected.isPresent())return List.of(new ProposedEvent(duty.id(),new GuardMissionSelected(duty.id(),duty.revision(),
                    mission.searching(selected.orElseThrow(),search.probe()+1,0,search.sightingRevision()))),GuardContinuation.wake(duty.id(),tick));
        }
        var resumed=mission.post().mission();
        return List.of(new ProposedEvent(duty.id(),new GuardMissionSelected(duty.id(),duty.revision(),resumed)),
                SecurityReview.wake(duty.home(),tick),GuardContinuation.wake(duty.id(),tick));
    }
    private static Optional<SurfaceAnchor> approach(FrontierWorldState state,GuardDuty duty,
            KnownPedestrianRouteKnowledge knowledge,BlockPosition position) {
        var reserved=GuardEquipment.in(state,duty.home()).stream().filter(other -> !other.id().equals(duty.id()))
                .flatMap(other -> other.mission().stream()).map(GuardMission::station).collect(java.util.stream.Collectors.toSet());
        var origin=state.actorLocations().get(duty.execution().actorId()).supportingSurface();
        var stations=PedestrianInteractionStations.within(knowledge.geometry(),new SurfaceAnchor(position),
                GuardResponse.MAX_CONTACT_STATION_DISTANCE,reserved).stream().filter(station -> GuardResponse.permitsStation(state,duty,station))
                .sorted(Comparator.comparingLong(station -> SecurityCoverage.distance(station,origin))).toList();
        return reachable(knowledge,origin,stations);
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
                    if (SecurityCoverage.permitsPost(target,station) && !knowledge.geometry().blocked(station)
                            && !reserved.contains(station) && !FacilityPedestrianContacts.of(state.bootstrap()).noParking().contains(station.support())
                            && ServiceBoundaryComposition.turnoverPoint(state, station).isEmpty()) stations.add(station);
                }
            }
            stations.sort(Comparator.comparingLong((SurfaceAnchor point) -> SecurityCoverage.distance(point, origin))
                    .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y));
            var selected=reachable(knowledge,state.actorLocations().get(duty.execution().actorId()).supportingSurface(),stations.stream().limit(8).toList());
            if(selected.isEmpty())continue;
            var next = new GuardMission(target.kind(), target.id(), selected.orElseThrow(), ordinal, 0, GuardMission.Stage.PATROL);
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
        var after=ready.following();List<ProposedEvent> replacement;
        var order=CombatComposition.CAPABILITIES.current(after,duty.execution(),tick);
        if(order.isPresent()){
            var space=new GuardMissionMovementProvider().placementKnowledge(after,movement);
            var station=MeleePositions.select(after,order.orElseThrow(),space,CombatComposition.CAPABILITIES.orders(after,tick),tick,Set.of(goal));
            replacement=station.map(point->List.of(new ProposedEvent(duty.id(),new GuardMissionSelected(duty.id(),duty.revision(),
                    duty.mission().orElseThrow().responding(point,order.orElseThrow().target()))),GuardContinuation.wake(duty.id(),tick)))
                    .orElseGet(()->List.of(scheduled(action,duty,tick+20)));
        }else replacement=selectPost(after,action,duty,tick,duty.mission().orElseThrow().visitOrdinal()+1);
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
