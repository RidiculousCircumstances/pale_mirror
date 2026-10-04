package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

/** State-machine checks for the transient COLD segment boundary between two HOT scenes. */
class RouteOperationHotColdTransitionTest {
    @Test void savedDepartureRejoinsTheOriginalFormationGoalWithoutMovingCargoOrCreditingTheRoute() {
        var world = new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:logistics-owned-rejoin");
        var configuration = FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 41L);
        var initial = configuration.initialState();
        var operation = FrontierDevelopmentScenarios.initialNorthwatchShipment(initial).orElseThrow();
        var original = operation.activeTravel().orElseThrow();
        var scope = FrontierTestSceneLeases.exact(initial,
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:logistics-owned-rejoin"),
                operation.id(), operation.cargoId(), operation.currentPosition(), configuration.initialInstant(),
                1L, java.util.Optional.empty(), operation.participantIds());
        var state = initial.prepareSceneLease(scope);
        state = FrontierTestActorBodies.present(state, scope).transitionSceneLease(scope.id(), SceneLeaseStatus.HOT);
        var actor = operation.participantIds().getFirst();
        var goal = original.nextFormationBody(actor).supportingSurface();
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        var ground = KnownPedestrianGround.forFrontier(state);
        var order = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(operation.id(), actor,
                0L, 1L, java.util.List.of(goal), TraversalCapability.PEDESTRIAN,
                io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
        var origin = java.util.List.of(goal.support().offset(0, 0, 1), goal.support().offset(0, 0, -1),
                        goal.support().offset(1, 0, 0), goal.support().offset(-1, 0, 0)).stream()
                .map(cell -> ground.at(cell.x(), cell.z())).filter(surface -> !surface.equals(goal)
                        && !surface.equals(original.formation().get(actor).supportingSurface()))
                .filter(surface -> {
                    try { return !knowledge.path(surface, order).isEmpty(); }
                    catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) { return false; }
                }).findFirst().orElseThrow();
        state = ModeledActorBodyFacts.inspected(state, actor, origin.standingBody());
        state = state.transitionSceneLease(scope.id(), SceneLeaseStatus.DRAINING);
        var releaseState = state;
        var positions = scope.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                releaseState.actorLocations().get(member.actorId()).body(), releaseState.actorLocations().get(member.actorId()).condition().health())).toList();
        state = state.releaseSceneLease(scope.id(), positions);
        for (var member : operation.participantIds()) state = ModeledActorBodyFacts.unloaded(state, member);
        operation = state.operations().get(operation.id());
        var checkpoint = operation.activeTravel().orElseThrow();
        assertEquals(original.formation(), checkpoint.formation());
        assertEquals(goal, checkpoint.approaches().get(actor).approach().orElseThrow().target());
        assertEquals(origin.standingBody(), checkpoint.memberCheckpoint(actor));
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        state = codec.decode(codec.encode(state));
        var observation = OperationTravelObservation.ColdApproach.capture(state, operation);
        var approached = OperationTravelContinuation.coldApproached(state, operation);
        assertEquals(checkpoint.cursor(), approached.cursor());
        assertEquals(checkpoint.formation(), approached.formation());
        assertEquals(checkpoint.cargoAnchor(), approached.cargoAnchor());
        var before = state;
        state = state.advanceOperationTravel(operation.id(), approached, observation);
        assertSame(before.inventory(), state.inventory());
        assertSame(before.actorExecutions(), state.actorExecutions());
        assertEquals(goal, state.actorLocations().get(actor).supportingSurface());
        var repeated = state;
        var operationId = operation.id();
        assertThrows(IllegalArgumentException.class, () -> repeated.advanceOperationTravel(operationId, approached, observation));
        var payload = new OperationTravelAdvanced(operationId, approached, observation);
        var payloads = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(payload, payloads.decode(payload.type(), payloads.encode(payload)));
        assertEquals(state, codec.decode(codec.encode(state)));
    }
    @Test void compactedSceneCannotRestoreRouteOwnershipOfALoadedActorPosition() {
        var world = new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:route-body-compaction");
        var configuration = FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(world, 91L);
        var initial = configuration.initialState();
        var candidate = initial.coldEngagementSceneCandidates().getFirst();
        var lease = FrontierTestSceneLeases.exact(initial,
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:route-body-compaction"),
                candidate.operationId(), candidate.cargoId(), candidate.handoffPosition(), configuration.initialInstant(),
                1L, java.util.Optional.of(candidate.engagementId()), candidate.actorIds());
        var state = initial.prepareSceneLease(lease);
        for (var member : lease.members()) state = ModeledActorBodyFacts.present(state, member.actorId());
        state = state.transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        var operation = state.operations().get(candidate.operationId());
        var actor = operation.participantIds().getFirst();
        var body = ActorBodyAuthority.current(state, actor);
        var location = state.actorLocations().get(actor);
        var execution = state.actorExecutions().actors().get(actor).current();
        var observedPosition = location.body().offset(1, 0, 1);
        state = ActorBodyAuthority.inspected(state,
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected(body,
                        io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.INDEXED_LIVING,
                        location.body(), location.condition().health(), observedPosition, location.condition().health(), execution));
        state = state.transitionSceneLease(lease.id(), SceneLeaseStatus.DRAINING);
        var positions = new java.util.ArrayList<SceneMemberPosition>();
        for (var member : lease.members()) positions.add(new SceneMemberPosition(member.actorId(),
                state.actorLocations().get(member.actorId()).body(), state.actorLocations().get(member.actorId()).condition().health()));
        state = state.releaseSceneLease(lease.id(), positions);
        var compacted = state.withChanges(FrontierWorldStateUpdate.begin().sceneLeases(java.util.Map.of()));
        assertEquals(observedPosition, compacted.actorLocations().get(actor).body());
        assertSame(state.operations(), compacted.operations(), "compaction cannot rewrite route progress or move a loaded NPC");
        assertEquals(body, ActorBodyAuthority.current(compacted, actor));
        assertFalse(ActorExecutionCoordinator.coldAvailable(compacted, actor));
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        assertEquals(compacted, codec.decode(codec.encode(compacted)));
        var cargoBinding = FrontierSceneLeaseStateSupport.cargoRecoveryBindingId(candidate.cargoId());
        var withoutCargo = new java.util.LinkedHashMap<>(compacted.fencedRecovery().tombstones());
        withoutCargo.remove(cargoBinding);
        var missingCargoAuthorization = compacted.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(
                new FencedRecoveryState(compacted.fencedRecovery().current(), withoutCargo, CargoProjectionRetirements.empty())));
        assertEquals(body, ActorBodyAuthority.current(missingCargoAuthorization, actor),
                "missing cargo authorization must not fabricate missing actor custody");
        assertThrows(IllegalArgumentException.class, () -> compacted.withChanges(
                FrontierWorldStateUpdate.begin().fencedRecovery(FencedRecoveryState.empty())));
        var divergent = state;
        assertThrows(IllegalArgumentException.class, () -> divergent.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(
                divergent.fencedRecovery().revokeToCold(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.recoveryBindingId(actor), body.physicalEpoch()))),
                "closed scene history cannot authorize an unreconciled COLD route checkpoint");
        assertThrows(IllegalArgumentException.class, () -> compacted.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(
                compacted.fencedRecovery().revokeToCold(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.recoveryBindingId(actor), body.physicalEpoch()))),
                "compaction cannot authorize an unreconciled COLD route checkpoint either");

        var originalTravel = operation.activeTravel().orElseThrow();
        var cold = ActorBodyAuthority.unloaded(compacted,
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyUnloaded(body,
                        observedPosition, location.condition().health(), observedPosition, location.condition().health(), execution));
        var settledOperation = cold.operations().get(operation.id());
        var checkpoint = settledOperation.activeTravel().orElseThrow();
        assertEquals(observedPosition, cold.actorLocations().get(actor).body());
        assertEquals(observedPosition, checkpoint.memberCheckpoint(actor));
        assertEquals(originalTravel.formation(), checkpoint.formation(), "saved absence cannot move the next formation goal");
        checkpoint.approaches().get(actor).approach().ifPresent(approach ->
                assertEquals(originalTravel.nextFormationBody(actor).supportingSurface(), approach.target()));
        assertSame(originalTravel.topology(), checkpoint.topology());
        assertEquals(originalTravel.cursor(), checkpoint.cursor(), "saved absence cannot certify route progress");
        assertEquals(originalTravel.cargoAnchor(), checkpoint.cargoAnchor(), "saved actor absence cannot move cargo");
        assertEquals(operation.stage(), settledOperation.stage());
        assertEquals(operation.routeIndex(), settledOperation.routeIndex());
        assertSame(compacted.actorExecutions(), cold.actorExecutions());
        assertSame(compacted.inventory(), cold.inventory());
        assertFalse(ActorExecutionCoordinator.coldAvailable(cold, operation.participantIds()),
                "another independently held crew body still excludes COLD");
        assertEquals(cold, codec.decode(codec.encode(cold)));
        assertThrows(IllegalArgumentException.class, () -> originalTravel.checkpointMember(
                new io.farfrontier.palemirror.frontier.v3.api.SubjectId("resident:foreign-checkpoint"),
                new StationApproachState(1L, java.util.Optional.empty(), java.util.Optional.of(observedPosition.supportingSurface()))));
        for (var member : operation.participantIds()) {
            if (member.equals(actor)) continue;
            var position = cold.actorLocations().get(member);
            cold = ActorBodyAuthority.unloaded(cold,
                    new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyUnloaded(
                            ActorBodyAuthority.current(cold, member), position.body(), position.condition().health(),
                            position.body(), position.condition().health(), cold.actorExecutions().actors().get(member).current()));
        }
        assertTrue(ActorExecutionCoordinator.coldAvailable(cold, operation.participantIds()));
        assertEquals(cold, codec.decode(codec.encode(cold)));
    }

    @Test
    void arrivedColdSegmentCannotBeClaimedByHotBeforeItsAtomicNextSegmentTransition() {
        RouteOperation operation = FrontierV3OperationFixture.routeSceneReturnOperation();
        assertTrue(operation.hasInProgressTravel(), "the unfinished initial corridor is HOT-eligible");

        OperationTravel initial = operation.activeTravel().orElseThrow();
        OperationTravel travel = initial;
        while (!travel.arrived()) travel = FrontierV3OperationFixture.advanceCold(travel);
        RouteOperation awaitingNextSegment = operation.withTravel(travel);

        assertTrue(travel.arrived());
        assertFalse(awaitingNextSegment.hasInProgressTravel(),
                "COLD owns an arrived segment until it atomically opens the next corridor");
        assertEquals(Set.copyOf(operation.participantIds()), travel.formation().keySet(),
                "the hand-off retains the exact formation identities");
        var displaced = travel.checkpointMember(operation.participantIds().getFirst(),
                new StationApproachState(1L, java.util.Optional.empty(), java.util.Optional.of(
                        travel.formation().get(operation.participantIds().getFirst()).supportingSurface().offset(1, 0, 1))));
        assertThrows(IllegalArgumentException.class, () -> operation.withTravel(displaced).completeTravelSegment(),
                "an arrived cargo cursor cannot complete a still-unavailable member departure approach");
        awaitingNextSegment.completeTravelSegment();
        BodyPosition initialCarrier = initial.formation().get(operation.cargoCarrierId());
        BodyPosition arrivedCarrier = travel.formation().get(operation.cargoCarrierId());
        assertEquals(initial.cargoAnchor().x() - initialCarrier.x(), travel.cargoAnchor().x() - arrivedCarrier.x());
        assertEquals(initial.cargoAnchor().z() - initialCarrier.z(), travel.cargoAnchor().z() - arrivedCarrier.z(),
                "the exact cargo retains its carrier-relative anchor through COLD movement");
    }

    @Test
    void everyBoundedColdAdvanceTranslatesAllExactMembersAndCargoTogether() {
        RouteOperation operation = FrontierV3OperationFixture.routeSceneReturnOperation();
        OperationTravel travel = operation.activeTravel().orElseThrow();
        while (!travel.arrived()) {
            OperationTravel next = FrontierV3OperationFixture.advanceCold(travel);
            int deltaX = next.currentPosition().x() - travel.currentPosition().x();
            int deltaZ = next.currentPosition().z() - travel.currentPosition().z();
            assertTrue(next.cursor() > travel.cursor() && next.cursor() <= travel.nextColdCursor());
            for (var member : travel.formation().entrySet()) {
                assertEquals(member.getValue().offset(deltaX, 0, deltaZ), next.formation().get(member.getKey()));
            }
            assertEquals(travel.cargoAnchor().offset(deltaX, 0, deltaZ), next.cargoAnchor());
            travel = next;
        }
    }
}
