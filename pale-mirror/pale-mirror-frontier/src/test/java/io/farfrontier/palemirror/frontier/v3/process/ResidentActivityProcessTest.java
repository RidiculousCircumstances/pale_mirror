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
    @Test void ordinaryReviewAdmitsPresenceOnceWithoutMaterializingMovingOrInventingWork() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:passive-execution-admission"), 421L));
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var action = ResidentActivityProcess.review(actor, 1L);
        var events = ResidentActivityProcess.plan(state, action);
        var presence = events.stream().map(proposed -> proposed.payload())
                .filter(io.farfrontier.palemirror.frontier.v3.model.execution.ActorPresenceStarted.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.model.execution.ActorPresenceStarted.class::cast)
                .findFirst().orElseThrow();
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(presence, codecs.decode(presence.type(), codecs.encode(presence)));
        assertEquals("actor-execution", FrontierWorldRuntimeDefinition.processRegistry().requireReducedEventOwner(presence.type()));
        var event = new FrontierEvent(FrontierEvent.SCHEMA_VERSION, new EventId("event:presence-admission"),
                new TransactionId("transaction:presence-admission"), state.bootstrap().worldId(), new Revision(1L),
                new SimInstant(1L), actor, CauseChain.root(new CommandId("command:presence-admission")), presence);
        var admitted = FrontierWorldProcessCatalog.reduce("actor-execution", state, event);
        assertEquals(presence.execution(), admitted.actorExecutions().actors().get(actor).current().orElseThrow());
        assertSame(state.actorLocations(), admitted.actorLocations());
        assertSame(state.inventory(), admitted.inventory());
        assertSame(state.fencedRecovery(), admitted.fencedRecovery());
        assertSame(state.ambientLeases(), admitted.ambientLeases());
        assertSame(state.sceneLeases(), admitted.sceneLeases());
        var snapshotCodec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var snapshot = snapshotCodec.encode(admitted);
        assertEquals(admitted.actorExecutions(), snapshotCodec.decode(snapshot).actorExecutions());
        snapshot[4] = (byte) 226;
        assertThrows(IllegalArgumentException.class, () -> snapshotCodec.decode(snapshot),
                "old disposable worlds cannot recover with incomplete resume authority");
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldProcessCatalog.reduce("actor-execution", admitted, event));
        assertTrue(ResidentActivityProcess.plan(admitted, action).stream().noneMatch(proposed ->
                proposed.payload() instanceof io.farfrontier.palemirror.frontier.v3.model.execution.ActorPresenceStarted));
    }
    @Test void unavailableClearanceParksWithoutTransactionsAndBodyChangeStartsSameDueMeal() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:meal-eligibility-without-geometry"), 421L));
        var settlement = initial.bootstrap().settlements().getFirst();
        var resident = settlement.residents().getFirst().id();
        var account = ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(settlement.id()));
        var resources = initial.inventory().fungibleResources().transformCold(account,
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 1), Map.of(),
                new ResourceLot(new SubjectId("lot:eligibility-bread"), settlement.id(),
                        ResidentMeal.BREAD_KIND, 1, "test", List.of()));
        var available = initial.withInventory(initial.inventory().withFungibleResources(resources));
        var state = available;
        var depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow();
        var surfaces = SettlementServiceAccessPoints.forDepot(state, settlement, depot).waitingSurfaces();
        var blockers = java.util.stream.Stream.concat(settlement.residents().stream(),
                initial.bootstrap().settlements().stream().filter(value -> !value.id().equals(settlement.id()))
                        .flatMap(value -> value.residents().stream()))
                .filter(value -> !value.id().equals(resident)).toList();
        assertTrue(blockers.size() >= surfaces.size());
        for (int index = 0; index < surfaces.size(); index++)
            state = state.withActorBody(blockers.get(index).id(), surfaces.get(index).standingBody());
        assertTrue(ResidentMealOpportunity.candidate(state, resident, 24_000L).isPresent());
        assertTrue(ResidentMealOpportunity.find(state, resident, 24_000L).isEmpty());
        var action = ResidentActivityProcess.review(resident, 24_000L);
        var wait = assertInstanceOf(ResidentActivityAdmission.Waiting.class,
                ResidentActivityProcess.admission(state, action));
        assertEquals(ResidentActivityAdmission.Reason.MEAL_CLEARANCE, wait.reason());
        assertTrue(wait.dependencies().contains(settlement.id()));
        var blocked = state;
        var queue = new io.farfrontier.palemirror.frontier.v3.kernel.ScheduledActionQueue();
        queue.schedule(action);
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Predicate<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> eligible = due -> {
            checks.incrementAndGet();
            return !FrontierWorldRuntimeDefinition.scheduledHeld(blocked, due);
        };
        for (long tick = 24_000L; tick < 25_000L; tick++)
            assertTrue(queue.selectDue(new SimInstant(tick),
                    new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(1, 1), eligible,
                    due -> FrontierWorldRuntimeDefinition.holdWakeKeys(blocked, due)).admitted().isEmpty());
        assertEquals(1, checks.get(), "unchanged geometry is not searched or committed every tick");
        assertEquals(List.of(action), queue.snapshot(), "waiting retains the original durable action");

        var departing = blockers.getFirst().id();
        var restored = blocked.withActorBody(departing, available.actorLocations().get(departing).body());
        FrontierEvent event = new FrontierEvent(FrontierEvent.SCHEMA_VERSION,
                new EventId("event:clearance-restored"), new TransactionId("transaction:clearance-restored"),
                initial.bootstrap().worldId(), new Revision(1L), new SimInstant(25_000L),
                departing,
                CauseChain.root(new CommandId("command:clearance-restored")),
                new AmbientActorObserved(departing, restored.actorLocations().get(departing).body(),
                        io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ONE));
        queue.wake(FrontierWorldRuntimeDefinition.wakeKeys(blocked, restored, event));
        assertEquals(List.of(action), queue.selectDue(new SimInstant(25_000L),
                new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(1, 1),
                due -> !FrontierWorldRuntimeDefinition.scheduledHeld(restored, due),
                due -> FrontierWorldRuntimeDefinition.holdWakeKeys(restored, due)).admitted());
        assertTrue(ResidentActivityProcess.plan(restored, action, 25_000L).stream()
                .anyMatch(planned -> planned.payload() instanceof ResidentMealStarted),
                "restoring clearance admits the same retained resident without reconnect or replay");
        var recovered = new io.farfrontier.palemirror.frontier.v3.kernel.ScheduledActionQueue();
        queue.snapshot().forEach(recovered::schedule);
        assertEquals(List.of(action), recovered.selectDue(new SimInstant(25_000L),
                new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(1, 1),
                due -> !FrontierWorldRuntimeDefinition.scheduledHeld(restored, due),
                due -> FrontierWorldRuntimeDefinition.holdWakeKeys(restored, due)).admitted(),
                "the derived wait index needs no persisted compatibility state");
        var delta = new PhysicalDelta(surfaces.getFirst().support(), PhysicalDeltaKind.UNKNOWN_SCAR,
                java.util.Optional.empty(), java.util.Optional.empty(), "test:changed-clearance");
        var changedGeometry = restored.recordPhysicalDelta(delta);
        var geometryEvent = new FrontierEvent(FrontierEvent.SCHEMA_VERSION,
                new EventId("event:geometry-wake"), new TransactionId("transaction:geometry-wake"),
                initial.bootstrap().worldId(), new Revision(2L), new SimInstant(25_001L),
                FrontierExecutionSubjects.PHYSICAL_EXECUTOR,
                CauseChain.root(new CommandId("command:geometry-wake")), new PhysicalDeltaObserved(delta));
        assertTrue(FrontierWorldRuntimeDefinition.wakeKeys(restored, changedGeometry, geometryEvent)
                .contains(settlement.id()), "geometry observation needs no resident event subject to wake admission");
    }
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
        assertEquals(java.util.Set.of(first.residents().getFirst().id(), depot, first.id()),
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
        assertEquals(ResidentActivityAdmission.Reason.FOOD_STOCK,
                assertInstanceOf(ResidentActivityAdmission.Waiting.class,
                        ResidentActivityProcess.admission(state, action)).reason());
        var planned = FrontierWorldRuntimeDefinition.planScheduled(state, action);
        assertEquals(3, planned.size());
        assertTrue(planned.stream().noneMatch(proposed -> proposed.payload() instanceof ResidentMealStarted));
        assertEquals(1L, planned.stream().filter(proposed -> proposed.payload()
                instanceof io.farfrontier.palemirror.frontier.v3.model.execution.ActorPresenceStarted).count());
        var availability = planned.stream().map(proposed -> proposed.payload()).filter(ScheduleEffect.Created.class::isInstance)
                .map(ScheduleEffect.Created.class::cast).findFirst().orElseThrow().action();
        assertEquals("frontier.objective.stock_reconsider", availability.kind());
        var next = assertInstanceOf(ScheduleEffect.Rescheduled.class, planned.getLast().payload()).replacement();
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

        assertEquals(3, planned.size());
        var presence = planned.stream().map(proposed -> proposed.payload())
                .filter(io.farfrontier.palemirror.frontier.v3.model.execution.ActorPresenceStarted.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.model.execution.ActorPresenceStarted.class::cast).findFirst().orElseThrow();
        assertEquals(24_050L, presence.atTick());
        var availability = planned.stream().map(proposed -> proposed.payload()).filter(ScheduleEffect.Created.class::isInstance)
                .map(ScheduleEffect.Created.class::cast).findFirst().orElseThrow().action();
        assertEquals("frontier.objective.stock_reconsider", availability.kind());
        var next = assertInstanceOf(ScheduleEffect.Rescheduled.class, planned.getLast().payload()).replacement();
        assertEquals(action.id(), next.id());
        assertTrue(next.dueAt().ticks() > 24_050L);
    }
}
