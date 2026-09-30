package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResidentActivityProcessTest {
    @Test void depotStockChangeWakesOnlyItsSettlementWaiters() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-depot-addressed-wake"), 421L));
        Settlement first = initial.bootstrap().settlements().getFirst();
        Settlement second = initial.bootstrap().settlements().get(1);
        SubjectId depot = FrontierWorldState.depotId(first.id());
        SubjectId account = ReferenceContainerCustody.scopeId(depot);
        ResourceLot contribution = new ResourceLot(new SubjectId("lot:addressed-wake-bread"), first.id(),
                ResidentMeal.BREAD_KIND, 1, "player-gift", List.of());
        FrontierWorldState restocked = initial.withInventory(initial.inventory().withFungibleResources(
                initial.inventory().fungibleResources().transformCold(account,
                        Map.of(new SubjectId("lot:bootstrap-1-wheat"), 1), Map.of(), contribution)));
        java.util.UUID player = new java.util.UUID(0L, 1L);
        var observed = new FungibleStockContributionObserved(account, depot, 1L, player,
                new java.util.UUID(0L, 2L), contribution,
                List.of(new FungiblePhysicalObservation.Stack(
                        new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 0)),
                        ResidentMeal.BREAD_KIND, 1)));
        FrontierEvent event = new FrontierEvent(FrontierEvent.SCHEMA_VERSION,
                new EventId("event:addressed-wake"), new TransactionId("transaction:addressed-wake"),
                initial.bootstrap().worldId(), new Revision(1L), new SimInstant(24_000L),
                FrontierExecutionSubjects.PHYSICAL_EXECUTOR,
                CauseChain.root(new CommandId("command:addressed-wake")), observed);
        var keys = FrontierWorldRuntimeDefinition.wakeKeys(initial, restocked, event);
        assertTrue(keys.contains(depot));
        assertFalse(keys.contains(FrontierWorldState.depotId(second.id())));
        assertEquals(java.util.Set.of(first.residents().getFirst().id(), depot),
                FrontierWorldRuntimeDefinition.holdWakeKeys(initial,
                        ResidentActivityProcess.review(first.residents().getFirst().id(), 24_000L)));
    }
    @Test void exactHungryResidentStartsOneMealAndOneProgressActionAtDayBoundary() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-activity-clock"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId account = ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(settlement.id()));
        SubjectId bread = new SubjectId("lot:resident-activity-clock-bread");
        FungibleResourceLedger resources = initial.inventory().fungibleResources().transformCold(account,
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(bread, settlement.id(), "minecraft:bread", 64, "test", List.of()));
        FrontierWorldState state = initial.withInventory(initial.inventory().withFungibleResources(resources));
        var action = ResidentActivityProcess.review(resident, 24_000L);
        var planned = FrontierWorldRuntimeDefinition.planScheduled(state, action);
        assertEquals(3, planned.size());
        ResidentMealStarted started = assertInstanceOf(ResidentMealStarted.class, planned.getFirst().payload());
        var progress = assertInstanceOf(ScheduleEffect.Created.class, planned.get(1).payload()).action();
        assertEquals(ResidentMealProcess.PROGRESS, progress.kind());
        assertEquals(24_001L, progress.dueAt().ticks());
        var next = assertInstanceOf(ScheduleEffect.Rescheduled.class, planned.getLast().payload()).replacement();
        assertEquals(action.id(), next.id());
        assertEquals(36_000L, next.dueAt().ticks());
        FrontierWorldState eating = ResidentMealProcess.reduceStarted(state, resident, started);
        assertEquals(started.meal(), eating.humanPopulation().meals().get(resident));
        FrontierEvent startEvent = new FrontierEvent(FrontierEvent.SCHEMA_VERSION,
                new EventId("event:meal-start-wake"), new TransactionId("transaction:meal-start-wake"),
                state.bootstrap().worldId(), new Revision(1L), new SimInstant(24_000L), resident,
                CauseChain.root(new CommandId("command:meal-start-wake")), started);
        assertTrue(FrontierWorldRuntimeDefinition.wakeKeys(state, eating, startEvent)
                .contains(FrontierWorldState.depotId(settlement.id())),
                "meal occupancy change must recheck the next resident waiting for this depot");
        assertEquals(2, FrontierWorldRuntimeDefinition.planScheduled(eating, progress).size());
    }

    @Test void unavailableBreadRetriesOnlyThatResidentWithoutInventingFood() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-activity-no-bread"), 421L));
        SubjectId resident = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        var action = ResidentActivityProcess.review(resident, 24_000L);
        assertTrue(FrontierWorldRuntimeDefinition.scheduledHeld(state, action),
                "an unchanged empty depot must not emit another resident retry transaction");
        var planned = FrontierWorldRuntimeDefinition.planScheduled(state, action);
        assertEquals(1, planned.size());
        var next = assertInstanceOf(ScheduleEffect.Rescheduled.class, planned.getFirst().payload()).replacement();
        assertEquals(action.id(), next.id());
        assertEquals(24_001L, next.dueAt().ticks(),
                "a held waiter must not inherit an avoidable 200-tick delay after stock arrives");
    }

    @Test void retainedHungryWakeUsesCurrentInstantWhenBreadAppearsAfterItsOriginalDueTime() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-activity-late-bread"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        var action = ResidentActivityProcess.review(resident, 24_000L);
        assertTrue(FrontierWorldRuntimeDefinition.scheduledHeld(initial, action));

        SubjectId account = ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(settlement.id()));
        FungibleResourceLedger resources = initial.inventory().fungibleResources().transformCold(account,
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(new SubjectId("lot:late-bread"), settlement.id(),
                        ResidentMeal.BREAD_KIND, 64, "test", List.of()));
        FrontierWorldState restocked = initial.withInventory(initial.inventory().withFungibleResources(resources));
        assertFalse(FrontierWorldRuntimeDefinition.scheduledHeld(restocked, action),
                "stock change makes the same retained exact due action runnable");

        var planned = FrontierWorldRuntimeDefinition.planScheduled(restocked, action, true, new SimInstant(50_000L));
        ResidentMealStarted started = assertInstanceOf(ResidentMealStarted.class, planned.getFirst().payload());
        assertEquals(50_000L, started.meal().startedAtTick(),
                "a held wake must not create a meal before the bread actually existed");
        var progress = assertInstanceOf(ScheduleEffect.Created.class, planned.get(1).payload()).action();
        assertEquals(50_001L, progress.dueAt().ticks());
        assertTrue(FrontierWorldRuntimeDefinition.scheduledHeld(
                ResidentMealProcess.reduceStarted(restocked, resident, started), action),
                "the activity review waits for its retained meal instead of generating retry WAL");
    }

    @Test void staleActivityReviewDoesNotEvaluateBeforeConfirmedNeedClock() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-activity-after-hot-bread"), 421L));
        SubjectId resident = initial.bootstrap().settlements().getFirst().residents().getFirst().id();
        var action = ResidentActivityProcess.review(resident, 24_000L);
        FrontierWorldState state = initial.withHumanPopulation(initial.humanPopulation()
                .accrueHunger(resident, 24_000L).consumeResidentBread(resident, 24_050L));

        assertEquals(ResidentActivityCoordinator.assess(state, resident, 24_050L),
                ResidentActivityCoordinator.assess(state, resident, 24_000L),
                "the shared arbiter cannot run this resident's need clock backwards");

        var planned = FrontierWorldRuntimeDefinition.planScheduled(state, action);

        assertEquals(1, planned.size());
        var next = assertInstanceOf(ScheduleEffect.Rescheduled.class, planned.getFirst().payload()).replacement();
        assertEquals(action.id(), next.id());
        assertTrue(next.dueAt().ticks() > 24_050L);
    }
}
