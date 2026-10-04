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
    @Test void hotOwnerCanYieldBeforePresentationReleaseButKeepsPhysicalEffectFence() {
        var fixture = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        var job = fixture.job();
        var state = fixture.state();
        var goal = ResourceSiteHarvestGoal.current(state, job);
        state = ResourceSiteHarvestProcessTest.inspectGoal(state, fixture.site(), job, fixture.lease().id());
        state = ResourceSiteHarvestProcessTest.completeLabourHot(state, fixture.site(), fixture.lease().id());
        long hungryAt = 27_000;
        assertTrue(state.humanPopulation().nutrition(job.workerId()).accrueThrough(hungryAt,
                state.bootstrap().ruleset().residentLife(), state.humanPopulation().resident(job.workerId())
                        .characteristics().effectiveMetabolismPermille(hungryAt)).wantsFood(state.bootstrap().ruleset().residentLife()));
        org.junit.jupiter.api.Assertions.assertFalse(ResidentActivityCoordinator.requestsYield(state, job.workerId(), hungryAt),
                "no food means hunger alone cannot make the food producer abandon work");
        var ledger = state.inventory().fungibleResources().transformCold(
                ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(new SubjectId("settlement:1"))),
                java.util.Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), java.util.Map.of(),
                new ResourceLot(new SubjectId("lot:yield-bread"), new SubjectId("settlement:1"),
                        "minecraft:bread", 64, "test", List.of()));
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        assertTrue(ResidentMealOpportunity.find(state, job.workerId(), hungryAt).isPresent());
        var waiting = ResidentActivityCoordinator.assess(state, job.workerId(), hungryAt);
        assertEquals(ResidentActivityChoice.Kind.EAT, waiting.kind());
        assertEquals(Optional.empty(), waiting.pending());
        assertTrue(ResidentActivityCoordinator.requestsYield(state, job.workerId(), hungryAt));
        assertTrue(ResidentActivityCoordinator.shouldYieldAtOwnerCheckpoint(state, job.workerId(), hungryAt));
        var selected = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.selectSourceAtYield(
                state, job.workerId(), hungryAt).orElseThrow();
        var yielded = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.reduceStarted(
                state, job.workerId(), selected);
        org.junit.jupiter.api.Assertions.assertFalse(yielded.actorExecutions().owns(job.workerId(),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST, job.id()));
        assertTrue(yielded.actorExecutions().retainsSuspended(job.workerId(),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST, job.id()));
        assertTrue(yielded.actorExecutions().owns(job.workerId(),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEAL, selected.meal().claimId()));
        org.junit.jupiter.api.Assertions.assertSame(state.actorLocations().get(job.workerId()),
                yielded.actorLocations().get(job.workerId()), "activity switch does not move or recreate its body");
        assertEquals(SceneLeaseStatus.HOT, yielded.sceneLeases().get(fixture.lease().id()).status(),
                "old presentation may still drain, but it cannot authorize the suspended work");
        state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess.reduceCropPrepared(state,
                fixture.site(), new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex(), job.target().generation()));
        assertTrue(ResidentActivityCoordinator.requestsYield(state, job.workerId(), hungryAt));
        org.junit.jupiter.api.Assertions.assertFalse(ResidentActivityCoordinator.shouldYieldAtOwnerCheckpoint(
                state, job.workerId(), hungryAt), "an unfinished physical crop effect still prevents transfer");
    }
    private static final SubjectId RESIDENT = new SubjectId("resident:one");
    private static final SubjectId JOB = new SubjectId("job:field-one");
    private static final HumanAssignment ASSIGNED = new HumanAssignment(RESIDENT,
            HumanAssignmentKind.FIELD_HARVEST, Optional.of(JOB));

    @Test void freeLowersWorkPriorityButDoesNotAbandonRetainedJobForIdle() {
        var policy = SettlementDailySchedule.initial();
        assertEquals(SettlementDailySchedule.Window.WORK, policy.windowAt(11_999));
        assertEquals(SettlementDailySchedule.Window.FREE, policy.windowAt(12_000));
        assertEquals(12_000, policy.nextWindowBoundaryAfter(0));
        assertEquals(24_000, policy.nextWindowBoundaryAfter(12_000));
        var free = ResidentActivityCoordinator.choose(policy, 12_000,
                ResidentNutrition.nourishedAtTick(12_000), ASSIGNED, true);
        assertEquals(ResidentActivityChoice.Kind.WORK, free.kind());
        assertEquals(Optional.empty(), free.pending());
        assertTrue(ResidentActivityPreferences.work(policy, 11_999)
                .outranks(ResidentActivityPreferences.work(policy, 12_000)));
        assertEquals(Optional.of(JOB), free.retainedWorkOwner());
        var pending = ResidentActivityCoordinator.choose(policy, 12_000,
                ResidentNutrition.nourishedAtTick(12_000), ASSIGNED, false);
        assertEquals(ResidentActivityChoice.Kind.WORK, pending.kind());
        assertEquals(Optional.empty(), pending.pending());
        var eating = ResidentActivityCoordinator.choose(policy, 37_000,
                ResidentNutrition.nourishedAtTick(0).accrueThrough(37_000), ASSIGNED, true);
        assertEquals(ResidentActivityChoice.Kind.EAT, eating.kind());
        var waitingForCheckpoint = ResidentActivityCoordinator.choose(policy, 37_000,
                ResidentNutrition.nourishedAtTick(0).accrueThrough(37_000), ASSIGNED, false);
        assertEquals(Optional.of(ResidentActivityChoice.Wait.SAFE_CHECKPOINT), waitingForCheckpoint.pending());
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

    @Test void unconfirmedMealKeepsCurrentActivityUntilConsumptionEvenIfNutritionIsAlreadyRelieved() {
        SubjectId settlement = new SubjectId("settlement:one");
        SubjectId depot = FrontierWorldState.depotId(settlement);
        ResidentMeal meal = new ResidentMeal(RESIDENT, settlement, depot, SurfaceAnchor.at(0, 64, 0),
                ReferenceContainerCustody.scopeId(depot),
                new SubjectId("custody:resident-meal-one"),
                new FoodPortion(FoodCatalog.BREAD, 1_000, java.util.Map.of(new SubjectId("lot:bread-one"), 1)), new SubjectId("claim:meal-one"),
                Optional.of(JOB), ResidentMeal.Phase.CONSUME, 24_000L, Optional.empty(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(RESIDENT, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEAL, new SubjectId("claim:meal-one"), 1L));
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
        assertTrue(ResidentActivityCoordinator.mayStartOrdinaryWork(state, resident, 12_000L),
                "FREE permits available work when there is no competing activity");
        assertEquals(12_020L, ResidentActivityCoordinator.nextOrdinaryWorkAdmission(state, resident, 12_000L));
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
