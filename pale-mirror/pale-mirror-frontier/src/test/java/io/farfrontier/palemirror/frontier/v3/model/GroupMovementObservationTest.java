package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupMovementObservationTest {
    private static FrontierWorldState travelling() {
        var config = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:mixed-group-position"), 41);
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        for (int boundary = 0; boundary < 1000; boundary++) {
            var state = engine.canonicalState().state();
            if (state.unitGroups().groups().values().stream().anyMatch(g -> g.phase() == UnitGroup.Phase.TRAVELLING
                    && g.members().stream().allMatch(m -> state.actorMovements().containsKey(m.actorId())
                        && state.actorMovements().get(m.actorId()).coldTravel().isEmpty()))) return state;
            var next = engine.checkpoint().schedules().stream().filter(a -> !FrontierWorldRuntimeDefinition.scheduledHeld(state, a)).sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), next.dueAt().ticks())), new WorkBudget(1, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        throw new AssertionError("ordinary trade did not produce a travelling group");
    }
    @Test void mixedProviderStartRetainsOnlyExactHotFactsAndReplaysWithoutAPathSearch() throws Exception {
        var state = travelling();
        var group = state.unitGroups().groups().values().stream().filter(g -> g.phase() == UnitGroup.Phase.TRAVELLING).findFirst().orElseThrow();
        assertEquals(2, group.members().size());
        var route = group.journey().orElseThrow().route();
        var cold = group.members().getFirst().actorId(); var hot = group.members().getLast().actorId();
        state = state.withActorBody(cold, route.get(20).standingBody()).withActorBody(hot, route.getFirst().standingBody());
        state = ActorBodyAuthority.demand(state, hot);
        var body = ActorBodyAuthority.current(state, hot);
        state = ActorBodyAuthority.running(state, body);
        var movement = state.actorMovements().get(cold);
        assertNotNull(movement); assertTrue(movement.coldTravel().isEmpty());
        long tick = movement.issuedAtTick() + 1;
        var unavailable = GroupTravelCohesion.assessCurrent(state, group, cold, ActorPositionView.canonical(state, tick), MovementPermission.allow());
        assertEquals(MovementPermission.Reason.POSITION_UNAVAILABLE, unavailable.reason());
        var snapshot = new MovementPositionSnapshot(tick, List.of(new MovementPositionSnapshot.HotPoint(body,
                ActorPositionView.TravelPoint.at(route.get(18).standingBody()))));
        var request = new ActorMovementColdRequested(movement.executionId(), movement.order().goalRevision(), snapshot);
        var events = ActorMovementProcess.planColdRequested(state, request, tick);
        var started = assertInstanceOf(ActorMovementColdRouteStarted.class, events.getFirst().payload());
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(request, codecs.decode(request.type(), codecs.encode(request)));
        assertEquals(started, codecs.decode(started.type(), codecs.encode(started)));
        var before = state;
        var advanced = ActorMovementProcess.reduceColdRouteStarted(state, cold, started);
        assertTrue(advanced.actorMovements().get(cold).coldTravel().isPresent());
        assertEquals(before.actorLocations(), advanced.actorLocations(), "a steering witness neither moves a HOT body nor awards COLD arrival");
        assertEquals(before.inventory(), advanced.inventory());
        // Exercise the real closed command/event registry and scheduler, not only
        // a direct reducer call: the retained progress action moves to leg arrival.
        var base = FrontierWorldRuntimeDefinition.configuration(before.bootstrap().worldId(), 41);
        var configured = new FrontierEngineConfiguration<>(before.bootstrap().worldId(), before, new SimInstant(tick),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(ActorMovementProcess.progress(movement, tick + 20)), base.transactionCommitter());
        var engine = FrontierEngines.createCanonicalStateAccess(configured);
        var commandId = new CommandId("command:mixed-provider-route-start");
        var accepted = engine.submit(new FrontierCommand(FrontierCommand.LEGACY_SCHEMA_VERSION, commandId,
                before.bootstrap().worldId(), engine.checkpoint().revision(), new SimInstant(tick),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), request));
        assertInstanceOf(CommandResult.Accepted.class, accepted, accepted::toString);
        assertEquals(advanced, engine.canonicalState().state());
        assertEquals(advanced.actorMovements().get(cold).coldTravel().orElseThrow().arrivalTick(),
                engine.checkpoint().schedules().stream().filter(a -> a.id().equals(ActorMovementProcess.progress(movement, tick).id()))
                        .findFirst().orElseThrow().dueAt().ticks());
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner)) {
            var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(before));
            assertEquals(advanced, ActorMovementProcess.reduceColdRouteStarted(recovered, cold,
                    (ActorMovementColdRouteStarted) codecs.decode(started.type(), codecs.encode(started))));
            assertEquals(0, planner.pendingCount());
        }
        assertThrows(IllegalArgumentException.class, () -> ActorMovementProcess.planColdRequested(before, request, tick + 1));
        var stale = new ActorMovementColdRequested(movement.executionId(), movement.order().goalRevision(),
                new MovementPositionSnapshot(tick, List.of(new MovementPositionSnapshot.HotPoint(new ActorBodyId(hot, body.physicalEpoch() + 1), snapshot.hotPoints().getFirst().point()))));
        assertThrows(IllegalArgumentException.class, () -> ActorMovementProcess.planColdRequested(before, stale, tick));
        var missing = new ActorMovementColdRequested(movement.executionId(), movement.order().goalRevision(), new MovementPositionSnapshot(tick, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ActorMovementProcess.planColdRequested(before, missing, tick));
        var physical = ActorBodyAuthority.demand(before, cold);
        assertThrows(IllegalArgumentException.class, () -> ActorMovementProcess.planColdRequested(physical, request, tick),
                "observations cannot authorize a competing COLD writer");
    }
    @Test void uBendCannotMakeTheLaggingMemberWaitForTheMemberItMustCatch() {
        var state = travelling(); var original = state.unitGroups().groups().values().stream()
                .filter(g -> g.phase() == UnitGroup.Phase.TRAVELLING).findFirst().orElseThrow();
        assertEquals(2, original.members().size());
        var route = new ArrayList<SurfaceAnchor>();
        for (int x = 0; x <= 20; x++) route.add(SurfaceAnchor.at(x, 63, 0));
        for (int z = 1; z <= 20; z++) route.add(SurfaceAnchor.at(20, 63, z));
        for (int x = 19; x >= 0; x--) route.add(SurfaceAnchor.at(x, 63, 20));
        var group = new UnitGroup(original.id(), original.mission(), original.members(), original.formation(), original.phase(),
                original.revision(), original.goalOrdinal(), Optional.of(new UnitGroup.Journey(route.getLast(), route, route.size() - 1,
                        original.journey().orElseThrow().stations())));
        var front = group.members().getFirst().actorId(); var back = group.members().getLast().actorId();
        ActorPositionView positions = actor -> route.get(actor.equals(front) ? 20 : 0).standingBody();
        var first = GroupTravelCohesion.assessCurrent(state, group, front, positions, MovementPermission.allow());
        var second = GroupTravelCohesion.assessCurrent(state, group, back, positions, MovementPermission.allow());
        assertFalse(first.allowed()); assertTrue(second.allowed(), "straight-line closeness to the destination is not travel progress");
        assertEquals(Optional.of(back), first.waitingFor());
    }
    @Test void closingLateralGapCannotDeadlockAtAnEqualRouteRankAfterService() {
        var state = travelling(); var original = state.unitGroups().groups().values().stream()
                .filter(g -> g.phase() == UnitGroup.Phase.TRAVELLING).findFirst().orElseThrow();
        var route = java.util.stream.IntStream.rangeClosed(0, 40).mapToObj(x -> SurfaceAnchor.at(x, 63, 0)).toList();
        var group = new UnitGroup(original.id(), original.mission(), original.members(), original.formation(), original.phase(),
                original.revision(), original.goalOrdinal(), Optional.of(new UnitGroup.Journey(route.getLast(), route, route.size() - 1,
                        original.journey().orElseThrow().stations())));
        var front = group.members().getFirst().actorId(); var back = group.members().getLast().actorId();
        int spacing = UnitGroupMissionPorts.require(group).travelPolicy(state, group).spacing();
        var lead = SurfaceAnchor.at(10, 63, 16); var follower = SurfaceAnchor.at(10 - spacing, 63, 0);
        ActorPositionView positions = actor -> (actor.equals(front) ? lead : follower).standingBody();
        assertFalse(GroupTravelCohesion.assess(state, group, front, lead, positions).allowed());
        assertTrue(GroupTravelCohesion.assess(state, group, back, follower, positions).allowed());
        assertTrue(GroupTravelCohesion.assess(state, group, back,
                SurfaceAnchor.at(follower.x() + 1, 63, 0), positions).allowed(), "a rank-crossing step reduces the lateral gap");
        assertFalse(GroupTravelCohesion.assess(state, group, front,
                SurfaceAnchor.at(lead.x() + 1, 63, 16), positions).allowed(), "the other member cannot widen the same gap");
        assertFalse(GroupTravelCohesion.assess(state, group, back,
                SurfaceAnchor.at(40, 63, 16), positions).allowed(), "closing lateral distance never waives along-route progress limits");
        ActorPositionView aligned = actor -> (actor.equals(front) ? lead : SurfaceAnchor.at(10, 63, 0)).standingBody();
        assertTrue(GroupTravelCohesion.assess(state, group, front, SurfaceAnchor.at(11, 63, 16), aligned).allowed(),
                "after alignment the current lagging member must be allowed to advance, even without a strictly decreasing lateral distance");
        assertFalse(GroupTravelCohesion.assess(state, group, back, SurfaceAnchor.at(11, 63, 0), aligned).allowed());
        var bend = new ArrayList<SurfaceAnchor>();
        for (int x = 0; x <= 20; x++) bend.add(SurfaceAnchor.at(x, 63, 0));
        for (int z = 1; z <= 10; z++) bend.add(SurfaceAnchor.at(20, 63, z));
        for (int x = 19; x >= 0; x--) bend.add(SurfaceAnchor.at(x, 63, 10));
        var folded = new UnitGroup(original.id(), original.mission(), original.members(), original.formation(), original.phase(),
                original.revision(), original.goalOrdinal(), Optional.of(new UnitGroup.Journey(bend.getLast(), bend, bend.size() - 1,
                        original.journey().orElseThrow().stations())));
        ActorPositionView betweenBranches = actor -> (actor.equals(front) ? SurfaceAnchor.at(10, 63, 0)
                : SurfaceAnchor.at(10 - spacing, 63, 5)).standingBody();
        assertTrue(GroupTravelCohesion.assess(state, folded, back,
                SurfaceAnchor.at(10 - spacing, 63, 6), betweenBranches).allowed(),
                "one physical step toward the shared route must not count as jumping dozens of route blocks across a bend");
    }
    @Test void coincidentColdMembersHaveConnectedBirthSpaceWithoutChangingTheirJourneyOrCargo() {
        var state = travelling();
        var group = state.unitGroups().groups().values().stream().filter(g -> g.phase() == UnitGroup.Phase.TRAVELLING).findFirst().orElseThrow();
        var origin = group.journey().orElseThrow().route().get(20);
        var first = group.members().getFirst().actorId();
        var second = group.members().getLast().actorId();
        state = state.withActorBody(first, origin.standingBody()).withActorBody(second, origin.standingBody());
        long tick = state.actorMovements().get(first).issuedAtTick() + 1;
        for (var actor : List.of(first, second)) state = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.prepare(
                state, io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess.nextLease(state, actor, new SimInstant(tick)));
        var lease = state.ambientLeases().get(second);
        var candidates = AmbientPlacementPolicy.candidates(state, lease);
        assertEquals(origin, candidates.getFirst(), "retain the exact projected position as the preferred birth");
        assertTrue(candidates.size() > 1, "an occupied road cell must not be the only declared birth surface");
        var alternative = candidates.get(1);
        assertTrue(LocalNavigationEnvelope.around(origin.standingBody(), origin.standingBody()).contains(alternative.support()));
        var prepared = state;
        state = ModeledActorBodyFacts.present(state, first);
        state = ModeledActorBodyFacts.present(state, second);
        var inspected = ModeledActorBodyFacts.inspected(state, second, alternative.standingBody());
        var admitted = io.farfrontier.palemirror.frontier.v3.process.AmbientBodyConfirmationProcess.reduce(inspected,
                new AmbientBodyConfirmed(second, lease.revision(), AmbientBodyConfirmed.Boundary.ADMISSION,
                        origin.standingBody(), alternative.standingBody(), ActorBodyAuthority.current(inspected, second),
                        AmbientPlacementPolicy.selectedRoute(state, lease, alternative)));
        assertEquals(AmbientLeaseStatus.HOT, admitted.ambientLeases().get(second).status());
        assertEquals(state.actorMovements(), admitted.actorMovements(), "birth grants no route progress or arrival");
        assertEquals(state.shipments(), admitted.shipments(), "birth cannot deliver, sell or release cargo");
        assertEquals(state.inventory(), admitted.inventory());
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(admitted));
        assertEquals(admitted, recovered);
        var blocked = prepared.recordPhysicalDelta(new PhysicalDelta(origin.support().offset(0, 1, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, java.util.Optional.empty(), java.util.Optional.empty(), "test:blocked-retained-birth"));
        var placementProof = AmbientPlacementPolicy.selectedRoute(blocked, lease, alternative);
        var movement = blocked.actorMovements().get(second);
        var knowledge = io.farfrontier.palemirror.frontier.v3.process.ActorMovementProviders.require(movement).placementKnowledge(blocked, movement);
        assertThrows(IllegalArgumentException.class,
                () -> knowledge.requireRoute(placementProof.orElseThrow().route()), "an unavailable birth origin cannot authorize walking");
        blocked = ModeledActorBodyFacts.present(blocked, first);
        blocked = ModeledActorBodyFacts.present(blocked, second);
        blocked = ModeledActorBodyFacts.inspected(blocked, second, alternative.standingBody());
        var placed = io.farfrontier.palemirror.frontier.v3.process.AmbientBodyConfirmationProcess.reduce(blocked,
                new AmbientBodyConfirmed(second, lease.revision(), AmbientBodyConfirmed.Boundary.ADMISSION,
                        origin.standingBody(), alternative.standingBody(), ActorBodyAuthority.current(blocked, second), placementProof));
        assertEquals(AmbientLeaseStatus.HOT, placed.ambientLeases().get(second).status());
        assertEquals(blocked.actorMovements(), placed.actorMovements(), "safe adjacent projection is not route progress");
        assertEquals(blocked.inventory(), placed.inventory());
        assertTrue(GroupTravelCohesion.assessCurrent(recovered, group, second,
                actor -> actor.equals(second) ? alternative.standingBody() : origin.standingBody(), MovementPermission.allow()).allowed());
        var foreign = origin.support().offset(8, 0, 0);
        var finalState = inspected;
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.AmbientBodyConfirmationProcess.reduce(
                finalState.withActorBody(second, BodyPosition.above(new SurfaceAnchor(foreign))),
                new AmbientBodyConfirmed(second, lease.revision(), AmbientBodyConfirmed.Boundary.ADMISSION,
                        origin.standingBody(), BodyPosition.above(new SurfaceAnchor(foreign)), ActorBodyAuthority.current(finalState, second), java.util.Optional.empty())),
                "local placement never authorizes a remote birth");
    }
}
