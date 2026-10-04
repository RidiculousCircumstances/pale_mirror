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
        assertEquals(observedPosition, checkpoint.formation().get(actor));
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
                new io.farfrontier.palemirror.frontier.v3.api.SubjectId("resident:foreign-checkpoint"), observedPosition));
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
