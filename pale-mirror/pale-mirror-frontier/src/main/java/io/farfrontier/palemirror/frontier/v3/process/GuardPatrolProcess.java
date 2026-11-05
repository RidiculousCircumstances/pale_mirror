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
    /** Keep the exact admitted approach during windup; no retarget, role switch or body handoff. */
    static List<ProposedEvent> preparing(FrontierWorldState state,ScheduledAction action,GuardDuty duty,long tick) {
        var strike=state.combat().pending().get(duty.id());
        if(strike==null || !strike.attacker().execution().equals(duty.execution()))throw new IllegalArgumentException("foreign preparing guard");
        var move=state.actorMovements().get(duty.execution().actorId());
        if(state.actorLocations().get(duty.execution().actorId()).condition().status()!=ActorLifeStatus.ALIVE
                || !state.actorExecutions().actors().get(duty.execution().actorId()).current().equals(Optional.of(duty.execution()))
                || CombatComposition.CAPABILITIES.current(state,duty.execution(),tick).filter(order->order.target().equals(strike.target())).isEmpty()
                || move!=null || tick>=strike.impactAt(state.bootstrap().ruleset().melee()) || duty.mission().isEmpty()
                || !duty.mission().orElseThrow().response().equals(Optional.of(strike.target()))
                || duty.missionOrder().arrivedAt(state.actorLocations().get(duty.execution().actorId()).supportingSurface()))
            return List.of(scheduled(action,duty,tick+state.bootstrap().ruleset().melee().sensingInterval()));
        // No mission reselection while an exact effect is retained. The shared navigator
        // owns route waiting/blockage for this already admitted goal.
        return admit(action,duty,tick);
    }
    static List<ProposedEvent> progress(FrontierWorldState state, ScheduledAction action, GuardDuty duty, long tick) {
        var mission = duty.mission();
        if(mission.isPresent() && !GuardResponse.targetRetained(state,duty)) mission=Optional.empty();
        var threat = GuardResponse.contact(state,duty,tick);
        var combatOrder=CombatComposition.CAPABILITIES.current(state,duty.execution(),tick);
        if(combatOrder.filter(order->state.combat().deferred(order,tick)).isPresent())threat=Optional.empty();
        if(threat.isPresent() && combatOrder.filter(order->MeleePositions.holding(state,order,tick)).isPresent())
            return List.of(scheduled(action,duty,tick+state.bootstrap().ruleset().melee().sensingInterval()));
        if(threat.isPresent() && mission.isPresent()) {
            var contact=threat.orElseThrow();
            if(mission.isPresent() && mission.orElseThrow().response().equals(Optional.of(contact.target()))
                    && !GuardResponse.redirect(state,duty,tick)) {
                if(!duty.missionOrder().arrivedAt(state.actorLocations().get(duty.execution().actorId()).supportingSurface())) return start(state,action,duty,tick);
                return retainPosition(state,action,duty,combatOrder.orElseThrow(),tick);
            }
            var knowledge=KnownPedestrianRouteKnowledge.forPublicJourney(state,duty.home(),List.of()).forActor(state,duty.execution().actorId());
            var order=CombatComposition.CAPABILITIES.current(state,duty.execution(),tick).orElseThrow();
            return combatChoice(state,action,duty,order,knowledge,tick,Set.of());
        }
        if(mission.isPresent() && mission.orElseThrow().response().isPresent()) {
            if (combatOrder.filter(order->state.combat().deferred(order,tick)).isEmpty()
                    && GuardResponse.investigating(state,duty,tick)) return search(state,action,duty,tick);
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
                .sorted(Comparator.comparingInt(knowledge.geometry()::traversalPenalty)
                        .thenComparingLong(station -> SecurityCoverage.distance(station,origin))).toList();
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
            stations.sort(Comparator.comparingInt(knowledge.geometry()::traversalPenalty)
                    .thenComparingLong((SurfaceAnchor point) -> SecurityCoverage.distance(point, origin))
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
            var combat=CombatComposition.CAPABILITIES.current(state,duty.execution(),tick);
            if(failure.status()!=PedestrianRouteResult.Status.NO_PATH){
                if(combat.isPresent() && duty.mission().orElseThrow().response().isPresent()){
                    var order=combat.orElseThrow();
                    return List.of(new ProposedEvent(duty.id(),new CombatEvent.PositionPlanned(order.execution(),order.target(),order.targetPosition(),Optional.empty(),MeleePosition.Reason.ROUTE_PENDING)),
                            scheduled(action,duty,tick+state.bootstrap().ruleset().melee().sensingInterval()));
                }
                throw failure;
            }
            if(combat.isPresent() && duty.mission().orElseThrow().response().isPresent())
                return combatChoice(state,action,duty,combat.orElseThrow(),knowledge,tick,Set.of(duty.mission().orElseThrow().station()));
            return selectPost(state,action,duty,tick,duty.mission().orElseThrow().visitOrdinal()+1);
        }
        return admit(action,duty,tick);
    }
    private static List<ProposedEvent> admit(ScheduledAction action,GuardDuty duty,long tick){
        var move = new ActorMovement(duty.missionOrder(), tick, new ActorMovementContext.GuardMissionLeg(duty.id(), duty.revision()), duty.execution());
        var result = new ArrayList<>(ActorMovementProcess.planStart(move));
        result.add(scheduled(action,duty,tick+20)); return List.copyOf(result);
    }
    static List<ProposedEvent> moving(FrontierWorldState state,ScheduledAction action,GuardDuty duty,ActorMovement movement,long tick) {
        var knowledge=new GuardMissionMovementProvider().placementKnowledge(state,movement);
        var body=ActorMovementProcess.bodyAt(state,duty.execution().actorId(),tick);var goal=duty.mission().orElseThrow().station();
        var combat=CombatComposition.CAPABILITIES.current(state,duty.execution(),tick);
        boolean hold=combat.filter(order->MeleePositions.holding(state,order,tick)).isPresent();
        boolean blocked=hold || combat.filter(order->state.combat().deferred(order,tick) && duty.mission().orElseThrow().response().isPresent() || GuardResponse.redirect(state,duty,tick)).isPresent()
        ||knowledge.geometry().clearanceKnown(goal) && !knowledge.clearStation(goal)
                || PedestrianRoutePlanning.peek(knowledge.geometry(),body.supportingSurface(),goal)
                        .filter(result->result.status()==PedestrianRouteResult.Status.NO_PATH).isPresent();
        if(!blocked)return combat.isPresent() && duty.mission().orElseThrow().response().isPresent()?retainPosition(state,action,duty,combat.orElseThrow(),tick):List.of(scheduled(action,duty,tick+20));
        var assessment=new ActorMovementInterruptionPlanner().assessForOwnerTransition(state,duty.execution(),tick);
        if(assessment instanceof ActivityInterruptionPlanner.Waiting)return List.of(scheduled(action,duty,tick+20));
        var ready=((ActivityInterruptionPlanner.Ready)assessment).validate(state,duty.execution().actorId());
        var after=ready.following();List<ProposedEvent> replacement;
        if(hold){var events=new ArrayList<>(ready.events());events.add(scheduled(action,duty,tick+state.bootstrap().ruleset().melee().sensingInterval()));return List.copyOf(events);}
        var order=CombatComposition.CAPABILITIES.current(after,duty.execution(),tick);
        if(order.isPresent() && (!after.combat().deferred(order.orElseThrow(),tick) || duty.mission().orElseThrow().response().isPresent())){
            var space=new GuardMissionMovementProvider().placementKnowledge(after,movement);
            replacement=combatChoice(after,action,duty,order.orElseThrow(),space,tick,Set.of(goal));
        }else replacement=selectPost(after,action,duty,tick,duty.mission().orElseThrow().visitOrdinal()+1);
        var events=new ArrayList<>(ready.events());
        events.addAll(replacement);return List.copyOf(events);
    }
    private static List<ProposedEvent> combatChoice(FrontierWorldState state,ScheduledAction action,GuardDuty duty,CombatOrder order,
            KnownPedestrianRouteKnowledge knowledge,long tick,Set<SurfaceAnchor> failed){
        if(state.combat().deferred(order,tick)){
            var resumed=duty.mission().orElseThrow().post().mission();
            return List.of(new ProposedEvent(duty.id(),new GuardMissionSelected(duty.id(),duty.revision(),resumed)),GuardContinuation.wake(duty.id(),tick));
        }
        var choice=MeleePositions.plan(state,order,knowledge,CombatComposition.CAPABILITIES.orders(state,tick),tick,failed);
        if(choice.station().isPresent())return List.of(new ProposedEvent(duty.id(),new GuardMissionSelected(duty.id(),duty.revision(),
                duty.mission().orElseThrow().responding(choice.station().orElseThrow(),order.target()))),GuardContinuation.wake(duty.id(),tick));
        return List.of(new ProposedEvent(duty.id(),choice.event(order)),scheduled(action,duty,tick+state.bootstrap().ruleset().melee().sensingInterval()));
    }
    private static List<ProposedEvent> retainPosition(FrontierWorldState state,ScheduledAction action,GuardDuty duty,CombatOrder order,long tick){
        var prior=state.combat().positions().get(order.owner());var rules=state.bootstrap().ruleset().melee();
        if(prior==null || !prior.matches(order) || tick-prior.atTick()>=rules.positionLeaseTicks()/2)
            return List.of(new ProposedEvent(duty.id(),new CombatEvent.PositionPlanned(order.execution(),order.target(),order.targetPosition(),order.station(),MeleePosition.Reason.RESERVED)),
                    scheduled(action,duty,tick+rules.sensingInterval()));
        return List.of(scheduled(action,duty,tick+rules.sensingInterval()));
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
