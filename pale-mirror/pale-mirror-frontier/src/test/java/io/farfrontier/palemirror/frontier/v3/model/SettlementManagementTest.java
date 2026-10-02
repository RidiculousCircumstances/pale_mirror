package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SettlementManagementTest {
    @Test void actualFoodDecisionRetainsOneDurableCommitmentAndRecoveryTruth() {
        FrontierWorldState state = initial();
        Settlement settlement = state.bootstrap().settlements().getFirst();
        var before = new FrontierWorldStateCodec().encode(state);
        var decision = SettlementManagementComposition.MANAGEMENT.decide(state, settlement);
        assertEquals(StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, decision.selected().orElseThrow().kind());
        assertArrayEquals(before, new FrontierWorldStateCodec().encode(state), "planning cannot mutate commitments");
        for (var event : StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(settlement.id(), 1, 60L))) {
            if (event.payload() instanceof StrategicObjectiveSelected selected)
                state = StrategicObjectiveProcess.reduceObjective(state, event.subject(), selected);
            if (event.payload() instanceof StrategicTaskPlanned planned)
                state = StrategicObjectiveProcess.reduceTask(state, event.subject(), planned);
        }
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(1, state.strategicPlans().requireDecisionAuthority(settlement.id()).commitmentIds().size());
        assertTrue(SettlementManagementComposition.MANAGEMENT.decide(state, settlement).selected().isEmpty());
    }

    @Test void plannerOrderDoesNotChangePriorityAndScopedHoldDoesNotStopIndependentWork() {
        FrontierWorldState state = initial(); Settlement settlement = state.bootstrap().settlements().getFirst();
        var food = new StrategicOperationProposal(StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, Optional.empty(), 10);
        var field = new StrategicOperationProposal(StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE,
                Optional.empty(), Optional.of(new SubjectId("site:1-wheat-field")), 10);
        var first = planner("test:food", SettlementOperationPlanner.Assessment.offer(settlement.id(), food, SettlementOperationPlanner.Priority.CRITICAL));
        var second = planner("test:field", SettlementOperationPlanner.Assessment.offer(settlement.id(), field, SettlementOperationPlanner.Priority.BACKGROUND));
        assertEquals(food, new SettlementManagement(List.of(second, first), SettlementManagementPolicy.standard())
                .decide(state, settlement).selected().orElseThrow());
        assertEquals(food, new SettlementManagement(List.of(first, second), SettlementManagementPolicy.standard())
                .decide(state, settlement).selected().orElseThrow());
        var hold = planner("test:hold", SettlementOperationPlanner.Assessment.held(
                SettlementOperationPlanner.Reason.ROUTE_RECOVERY_ALREADY_OWNED));
        var decision = new SettlementManagement(List.of(first, hold, second), SettlementManagementPolicy.standard()).decide(state, settlement);
        assertEquals(field, decision.selected().orElseThrow());
        assertEquals(1, decision.holds().size());
    }

    @Test void foreignOfferCannotBorrowSettlementAuthority() {
        FrontierWorldState state = initial(); Settlement settlement = state.bootstrap().settlements().getFirst();
        var foreign = planner("test:foreign", SettlementOperationPlanner.Assessment.offer(new SubjectId("settlement:2"),
                new StrategicOperationProposal(StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, Optional.empty(), 10),
                SettlementOperationPlanner.Priority.CRITICAL));
        assertThrows(IllegalArgumentException.class, () -> new SettlementManagement(List.of(foreign),
                SettlementManagementPolicy.standard()).decide(state, settlement));
    }

    @Test void retainedFarmerAndFacilityCannotBeBorrowedByAnotherAdmission() {
        var prepared = ResourceSiteHarvestProcessTest.coldHarvestWithCargo(91L);
        FrontierWorldState state = prepared.state(); var job = prepared.job();
        var field = state.resourceSite(job.siteId());
        assertThrows(IllegalArgumentException.class, () -> SettlementWorkforce.requireAvailable(state,
                field.settlementId(), List.of(job.workerId())));
        SubjectId other = state.bootstrap().settlements().getFirst().residents().stream()
                .map(resident -> resident.id()).filter(id -> !id.equals(job.workerId()))
                .filter(id -> HumanAssignmentProjection.compile(state).idle(id)).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> SettlementCommitmentComposition.ADMISSION.require(state,
                new SettlementCommitmentAdmission.Request(job.taskId(), field.settlementId(), field.facilityId(), List.of(other))));
        assertEquals(job, state.resourceSites().site(job.siteId()).activeWork().orElseThrow());
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:management"), 407L));
    }
    private static SettlementOperationPlanner planner(String id, SettlementOperationPlanner.Assessment result) {
        return new SettlementOperationPlanner() {
            @Override public String id() { return id; }
            @Override public Assessment assess(FrontierWorldState state, Settlement settlement) { return result; }
        };
    }
}
