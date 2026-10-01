package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidentActivityCoordinatorTest {
    private static final SubjectId RESIDENT = new SubjectId("resident:one");
    private static final SubjectId JOB = new SubjectId("job:field-one");
    private static final HumanAssignment ASSIGNED = new HumanAssignment(RESIDENT,
            HumanAssignmentKind.FIELD_HARVEST, Optional.of(JOB));

    @Test void scheduleSelectsFreeWithoutDiscardingRetainedJob() {
        var policy = SettlementDailySchedule.initial();
        assertEquals(SettlementDailySchedule.Window.WORK, policy.windowAt(11_999));
        assertEquals(SettlementDailySchedule.Window.FREE, policy.windowAt(12_000));
        assertEquals(12_000, policy.nextWindowBoundaryAfter(0));
        assertEquals(24_000, policy.nextWindowBoundaryAfter(12_000));
        var free = ResidentActivityCoordinator.choose(policy, 12_000,
                ResidentNutrition.nourishedAtTick(12_000), ASSIGNED, true);
        assertEquals(ResidentActivityChoice.Kind.IDLE, free.kind());
        assertEquals(Optional.of(JOB), free.retainedWorkOwner());
        var pending = ResidentActivityCoordinator.choose(policy, 12_000,
                ResidentNutrition.nourishedAtTick(12_000), ASSIGNED, false);
        assertEquals(ResidentActivityChoice.Kind.WORK, pending.kind());
        assertEquals(Optional.of(ResidentActivityChoice.Wait.SAFE_CHECKPOINT), pending.pending());
    }

    @Test void hungerWinsWorkWindowOnlyAtSafeCheckpoint() {
        var policy = SettlementDailySchedule.initial();
        var hungry = ResidentNutrition.nourishedAt(0).accrueThrough(24_000);
        var pending = ResidentActivityCoordinator.choose(policy, 24_000, hungry, ASSIGNED, false);
        assertEquals(ResidentActivityChoice.Kind.WORK, pending.kind());
        assertEquals(Optional.of(ResidentActivityChoice.Wait.SAFE_CHECKPOINT), pending.pending());
        var eating = ResidentActivityCoordinator.choose(policy, 24_000, hungry, ASSIGNED, true);
        assertEquals(ResidentActivityChoice.Kind.EAT, eating.kind());
        assertEquals(Optional.of(JOB), eating.retainedWorkOwner());
        var resumed = ResidentActivityCoordinator.choose(policy, 24_000,
                hungry.consumeBreadAt(24_000), ASSIGNED, true);
        assertEquals(ResidentActivityChoice.Kind.WORK, resumed.kind());
    }

    @Test void retainedMealKeepsTheBodyUntilReturnEvenAfterHungerIsRelieved() {
        SubjectId settlement = new SubjectId("settlement:one");
        SubjectId depot = FrontierWorldState.depotId(settlement);
        ResidentMeal meal = new ResidentMeal(RESIDENT, settlement, depot, SurfaceAnchor.at(0, 64, 0),
                ReferenceContainerCustody.scopeId(depot),
                new SubjectId("custody:resident-meal-one"),
                new SubjectId("lot:bread-one"), new SubjectId("claim:meal-one"),
                Optional.of(JOB), ResidentMeal.Phase.RETURN, 24_000L, Optional.empty());
        var activity = ResidentActivityCoordinator.choose(SettlementDailySchedule.initial(),
                24_001L, ResidentNutrition.nourishedAt(1), ASSIGNED,
                Optional.of(meal), false);
        assertEquals(ResidentActivityChoice.Kind.EAT, activity.kind());
        assertEquals(Optional.of(JOB), activity.retainedWorkOwner());
        assertThrows(IllegalArgumentException.class, () -> ResidentActivityCoordinator.choose(
                SettlementDailySchedule.initial(), 24_001L, ResidentNutrition.nourishedAt(1),
                HumanAssignment.idle(RESIDENT), Optional.of(meal), true));
    }

    @Test void policyMayContainSeveralAlternatingWindowsWithoutChangingArbitration() {
        var policy = new SettlementDailySchedule(24_000, List.of(
                new SettlementDailySchedule.Segment(0, 4_000, SettlementDailySchedule.Window.FREE),
                new SettlementDailySchedule.Segment(4_000, 10_000, SettlementDailySchedule.Window.WORK),
                new SettlementDailySchedule.Segment(10_000, 12_000, SettlementDailySchedule.Window.FREE),
                new SettlementDailySchedule.Segment(12_000, 18_000, SettlementDailySchedule.Window.WORK),
                new SettlementDailySchedule.Segment(18_000, 24_000, SettlementDailySchedule.Window.FREE)));
        assertEquals(SettlementDailySchedule.Window.WORK, policy.windowAt(4_000));
        assertEquals(SettlementDailySchedule.Window.FREE, policy.windowAt(10_000));
        assertEquals(12_000, policy.nextWindowBoundaryAfter(10_000));
        assertEquals(28_000, policy.nextWindowBoundaryAfter(24_000));
    }

    @Test void stateAssessmentUsesExactResidentPolicyAndFailsClosedForUnadaptedWork() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-activity-assessment"), 422L));
        SubjectId resident = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        assertEquals(ResidentWorkYield.Status.READY,
                ResidentWorkYield.assess(state, HumanAssignment.idle(resident)).status());
        assertEquals(ResidentActivityChoice.Kind.IDLE,
                ResidentActivityCoordinator.assess(state, resident, 12_000L).kind());
        assertEquals(ResidentActivityChoice.Kind.IDLE,
                ResidentActivityCoordinator.assess(state, resident, 24_000L).kind());
        assertTrue(ResidentActivityCoordinator.mayStartOrdinaryWork(state, resident, 24_000L),
                "hunger without any executable meal must not fence new work");
        for (HumanAssignmentKind family : List.of(HumanAssignmentKind.CARGO_TRANSPORT,
                HumanAssignmentKind.ESCORT, HumanAssignmentKind.ROUTE_PATROL,
                HumanAssignmentKind.SETTLEMENT_DEFENCE, HumanAssignmentKind.ENGINEERING_RECOVERY,
                HumanAssignmentKind.SETTLEMENT_SERVICE, HumanAssignmentKind.MEDICAL_EVACUATION,
                HumanAssignmentKind.TRANSIT)) {
            var unadapted = new HumanAssignment(resident, family,
                    Optional.of(new SubjectId("job:unadapted-" + family.name().toLowerCase(java.util.Locale.ROOT))));
            assertThrows(IllegalArgumentException.class,
                    () -> ResidentWorkYield.assess(state, unadapted), family.name());
        }
    }
}
