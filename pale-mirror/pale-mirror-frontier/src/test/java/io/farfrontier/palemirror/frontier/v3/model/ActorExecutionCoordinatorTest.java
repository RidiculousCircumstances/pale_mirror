package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ActorExecutionCoordinatorTest {
    private static final SubjectId SETTLEMENT = new SubjectId("settlement:1");
    private static final SubjectId SITE = new SubjectId("site:1-wheat-field");

    @Test void historicalHandoffDoesNotPreventNextHarvestAtReachedHome() {
        FrontierWorldState state = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial());
        ResidentProfile farmer = FrontierWorldStateSupport.availableFieldResident(state, SETTLEMENT,
                ResidentProfession.AGRICULTURAL_WORKER).orElseThrow();
        BodyPosition home = state.actorLocations().get(farmer.id()).body();
        BodyPosition oldDepot = new BodyPosition(home.x() + 3, home.y(), home.z());
        AmbientActorLease lease = new AmbientActorLease(farmer.id(), oldDepot, new SimInstant(1),
                1, AmbientLeaseStatus.HOT, AmbientGoalKind.PATROL, home);
        state = state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(Map.of(farmer.id(), lease)));
        assertInstanceOf(ActorExecutionCoordinator.AmbientTransfer.class,
                ActorExecutionCoordinator.ordinaryWorkAdmission(state, farmer.id()));
        assertFalse(ActorExecutionCoordinator.coldAvailable(state, farmer.id()),
                "admission is a transfer, never permission for a second physical mover");
        var opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(SITE), 27_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, SETTLEMENT,
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, SETTLEMENT,
                (StrategicTaskPlanned) opportunity.get(1).payload());
        var task = state.strategicPlans().tasks().values().stream()
                .filter(t -> t.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
        assertTrue(ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(task, 27_085L))
                .stream().map(ProposedEvent::payload).anyMatch(ResourceSiteHarvestStarted.class::isInstance));
        var codec = new FrontierWorldStateCodec();
        FrontierWorldState recovered = codec.decode(codec.encode(state));
        assertEquals(ActorExecutionCoordinator.ordinaryWorkAdmission(state, farmer.id()),
                ActorExecutionCoordinator.ordinaryWorkAdmission(recovered, farmer.id()));
        assertEquals(oldDepot, recovered.ambientLeases().get(farmer.id()).handoffBody());
    }

    @Test void unresolvedAmbientEpochAndForeignPurposeCannotBeTakenByWork() {
        FrontierWorldState initial = ResourceSiteHarvestProcessTest.initial();
        ResidentProfile farmer = FrontierWorldStateSupport.availableFieldResident(initial, SETTLEMENT,
                ResidentProfession.AGRICULTURAL_WORKER).orElseThrow();
        BodyPosition body = initial.actorLocations().get(farmer.id()).body();
        for (AmbientLeaseStatus status : new AmbientLeaseStatus[]{AmbientLeaseStatus.PREPARED,
                AmbientLeaseStatus.DRAINING, AmbientLeaseStatus.UNKNOWN_AFTER_RESTART}) {
            var lease = new AmbientActorLease(farmer.id(), body, new SimInstant(1), 1,
                    status, AmbientGoalKind.PATROL, body);
            var state = initial.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(Map.of(farmer.id(), lease)));
            assertEquals(new ActorExecutionCoordinator.Waiting(ActorExecutionCoordinator.Wait.AMBIENT_RECOVERY),
                    ActorExecutionCoordinator.ordinaryWorkAdmission(state, farmer.id()));
            assertFalse(ActorExecutionCoordinator.coldAvailable(state, farmer.id()));
        }
        var lease = new AmbientActorLease(farmer.id(), body, new SimInstant(1), 1,
                AmbientLeaseStatus.HOT, AmbientGoalKind.GUARD, body);
        var state = initial.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(Map.of(farmer.id(), lease)));
        assertEquals(new ActorExecutionCoordinator.Waiting(ActorExecutionCoordinator.Wait.FOREIGN_AMBIENT_PURPOSE),
                ActorExecutionCoordinator.ordinaryWorkAdmission(state, farmer.id()));
    }

    @Test void existingAssignmentCannotBeReplacedByAnotherJob() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        assertEquals(new ActorExecutionCoordinator.Waiting(ActorExecutionCoordinator.Wait.ASSIGNED_WORK),
                ActorExecutionCoordinator.ordinaryWorkAdmission(fixture.state(), fixture.job().workerId()));
    }

    @Test void transferClosesOneEpochAndRetainsSceneAuthorityAcrossRecovery() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        SubjectId actor = fixture.job().workerId();
        BodyPosition observed = fixture.state().actorLocations().get(actor).body();
        var ambient = new AmbientActorLease(actor, new BodyPosition(observed.x() + 3, observed.y(), observed.z()),
                new SimInstant(22_000L), 1, AmbientLeaseStatus.HOT, AmbientGoalKind.PATROL, observed);
        var state = fixture.state().withChanges(FrontierWorldStateUpdate.begin().ambientLeases(Map.of(actor, ambient)));
        var incoming = ResourceSiteHarvestProcessTest.newHarvestLease(state, fixture.site(), fixture.job(), "execution-transfer");
        var handoff = new SceneLeaseHandoff(incoming, java.util.List.of(new SceneMemberPosition(actor, observed,
                state.actorLocations().get(actor).condition().health())));
        var transferred = ActorExecutionCoordinator.transferToScene(state, handoff);
        assertEquals(AmbientLeaseStatus.CLOSED, transferred.ambientLeases().get(actor).status());
        assertEquals(SceneLeaseStatus.PREPARED, transferred.sceneLeases().get(incoming.id()).status());
        assertEquals(observed, transferred.actorLocations().get(actor).body());
        assertFalse(ActorExecutionCoordinator.coldAvailable(transferred, actor));
        var codec = new FrontierWorldStateCodec();
        var recovered = codec.decode(codec.encode(transferred));
        assertFalse(ActorExecutionCoordinator.coldAvailable(recovered, actor));
        assertThrows(IllegalArgumentException.class, () -> ActorExecutionCoordinator.transferToScene(recovered, handoff));
    }
}
