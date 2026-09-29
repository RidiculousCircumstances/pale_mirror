package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResidentMealProcessTest {
    @Test void secondHungryResidentCanStartAfterFirstClearsAccessWhileReturnContinues() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-depot-service-queue"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId first = settlement.residents().get(0).id();
        SubjectId second = settlement.residents().get(1).id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        var stock = initial.inventory().fungibleResources().transformCold(ReferenceContainerCustody.scopeId(depot),
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(new SubjectId("lot:resident-queue-bread"), settlement.id(),
                        ResidentMeal.BREAD_KIND, 64, "test", List.of()));
        FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(initial.inventory().withFungibleResources(stock))
                .humanPopulation(initial.humanPopulation().accrueHunger(first, 27_000L)
                        .accrueHunger(second, 27_000L)));
        ResidentMealStarted started = ResidentMealProcess.selectSourceAtYield(state, first, 27_000L).orElseThrow();
        state = ResidentMealProcess.reduceStarted(state, first, started);
        assertTrue(ResidentMealProcess.selectSourceAtYield(state, second, 27_000L).isEmpty());
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        boolean releasedDuringReturn = false;
        boolean secondStartedBeforeFirstFinished = false;
        for (int turn = 0; state.humanPopulation().meals().containsKey(first) && turn < 300; turn++) {
            ResidentMealColdStep step = ResidentMealProcess.planColdStep(state, first, 27_001L + turn).orElseThrow();
            state = ResidentMealProcess.reduceColdStep(state, first, step);
            if (!secondStartedBeforeFirstFinished && state.humanPopulation().meals().containsKey(first)
                    && state.humanPopulation().meals().get(first).phase() == ResidentMeal.Phase.RETURN
                    && ResidentMealProcess.selectSourceAtYield(state, second, 27_001L + turn).isPresent()) {
                releasedDuringReturn = true;
                ResidentMealStarted next = ResidentMealProcess.selectSourceAtYield(state, second,
                        27_001L + turn).orElseThrow();
                state = ResidentMealProcess.reduceStarted(state, second, next);
                secondStartedBeforeFirstFinished = true;
                state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
            }
        }
        assertTrue(releasedDuringReturn, "access must release before the first resident finishes returning home");
        assertTrue(secondStartedBeforeFirstFinished);
        assertFalse(state.humanPopulation().meals().containsKey(first));
        assertTrue(state.humanPopulation().meals().containsKey(second),
                "the second resident's independent meal must survive the first return");
        assertEquals(63, state.inventory().fungibleResources().totalQuantity(settlement.id(), ResidentMeal.BREAD_KIND));
    }

    @Test void postTakeNativeFixtureRetainsOneColdHandAndOnlyOneFutureAction() {
        var configuration = FrontierV3FixtureCatalog.configuration("resident-meal-after-cold-take",
                new WorldId("frontier:resident-after-take-fixture"), 41L);
        FrontierWorldState state = configuration.initialState();
        SubjectId resident = new SubjectId("resident:6-1");
        ResidentMeal meal = state.humanPopulation().meals().get(resident);
        assertEquals(ResidentMeal.Phase.CONSUME, meal.phase());
        assertTrue(FrontierWorldStateSupport.workCapable(state, state.humanPopulation().resident(resident)),
                "self-care must not invalidate a previously retained work owner");
        assertFalse(FrontierWorldStateSupport.availableForNewAssignment(state, state.humanPopulation().resident(resident)),
                "a new work owner cannot recruit the resident while the meal owns their activity");
        assertEquals(1, state.inventory().fungibleResources().accounts().get(meal.actorAccountId())
                .lotQuantities().get(meal.lotId()));
        assertEquals(1, state.inventory().fungibleResources().claims().get(meal.claimId()).quantity());
        assertEquals(0, state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(meal.actorAccountId())).count());
        long instant = configuration.initialInstant().ticks();
        assertEquals(List.of(ResidentMealProcess.progress(meal, instant + 1_000L),
                ResidentActivityProcess.review(resident, instant + 1_000L),
                ResidentNeedProcess.review(resident, state.humanPopulation().nutrition(resident).nextThresholdTick(
                        state.bootstrap().ruleset().residentLife(),
                        state.humanPopulation().resident(resident).characteristics().effectiveMetabolismPermille(instant)))),
                configuration.initialSchedules());
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(
                new FrontierWorldStateCodec().encode(state));
        assertEquals(meal, recovered.humanPopulation().meals().get(resident));
    }

    @Test void oneBreadDoesNotSendStillHungryResidentBackToWorkOrWaitForNextDay() {
        WorldId world = new WorldId("frontier:resident-meal-two-portions");
        var base = FrontierWorldRuntimeDefinition.configuration(world, 421L);
        FrontierWorldState initial = base.initialState();
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SurfaceAnchor service = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow()).serviceSurface();
        var actors = new LinkedHashMap<>(initial.actorLocations());
        actors.put(resident, ActorLocation.standingOn(service));
        var stock = initial.inventory().fungibleResources().transformCold(ReferenceContainerCustody.scopeId(depot),
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(new SubjectId("lot:resident-two-portions-bread"), settlement.id(),
                        "minecraft:bread", 64, "test", List.of()));
        FrontierWorldState stocked = initial.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(actors).inventory(initial.inventory().withFungibleResources(stock))
                .humanPopulation(initial.humanPopulation().accrueHunger(resident, 48_000L)));
        assertEquals(2, stocked.humanPopulation().nutrition(resident).hungerDeficit());
        assertEquals(ResidentActivityChoice.Kind.EAT,
                ResidentActivityCoordinator.assess(stocked, resident, 48_001L).kind());
        assertTrue(ResidentMealProcess.selectSourceAtYield(stocked, resident, 48_001L).isPresent());
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                world, stocked, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(48_000L),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                base.projectionMapper(), base.limits(), List.of(
                        ResidentActivityProcess.review(resident, 48_001L),
                        ResidentNeedProcess.review(resident, stocked.humanPopulation().nutrition(resident)
                                .nextThresholdTick(stocked.bootstrap().ruleset().residentLife(),
                                        stocked.humanPopulation().resident(resident).characteristics()
                                                .effectiveMetabolismPermille(48_000L)))),
                base.transactionCommitter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(configuration);
        for (long due = 48_001L; due <= 58_001L; due += 20L)
            engine.advanceTo(new io.farfrontier.palemirror.frontier.v3.api.SimInstant(due),
                    new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(1_024, 4_096));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(62, after.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"),
                "need=" + after.humanPopulation().nutrition(resident) + " meal=" + after.humanPopulation().meals()
                        + " schedules=" + engine.checkpoint().schedules());
        assertEquals(ResidentNutritionStatus.NOURISHED, after.humanPopulation().nutrition(resident).status());
        assertTrue(after.humanPopulation().meals().isEmpty(),
                "meal=" + after.humanPopulation().meals() + " body=" + after.actorLocations().get(resident)
                        + " status=" + engine.status() + " instant=" + engine.checkpoint().instant()
                        + " schedules=" + engine.checkpoint().schedules() + " route="
                        + clearingRoute(after, resident));
    }

    @Test void sparseEngineWakesOneColdResidentThroughAnEntireMealWithoutProvision() {
        WorldId world = new WorldId("frontier:resident-meal-engine");
        var base = FrontierWorldRuntimeDefinition.configuration(world, 421L);
        FrontierWorldState initial = base.initialState();
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SurfaceAnchor service = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow()).serviceSurface();
        var actors = new LinkedHashMap<>(initial.actorLocations());
        actors.put(resident, ActorLocation.standingOn(service));
        var stock = initial.inventory().fungibleResources().transformCold(ReferenceContainerCustody.scopeId(depot),
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(new SubjectId("lot:resident-engine-bread"), settlement.id(),
                        "minecraft:bread", 64, "test", List.of()));
        FrontierWorldState stocked = initial.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(actors).inventory(initial.inventory().withFungibleResources(stock)));
        long firstNeed = initial.humanPopulation().nutrition(resident).nextThresholdTick(
                initial.bootstrap().ruleset().residentLife(),
                initial.humanPopulation().resident(resident).characteristics().effectiveMetabolismPermille(0L));
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                world, stocked, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(),
                base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), List.of(
                ResidentNeedProcess.review(resident, firstNeed),
                ResidentActivityProcess.review(resident, 1L)), base.transactionCommitter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(configuration);
        // The scheduler admits one generation of a rescheduled action per call;
        // drive its 20-tick cadence instead of leaping over hundreds of pending turns.
        for (long due = 1L; due <= firstNeed + 5_000L; due += 20L)
            engine.advanceTo(new io.farfrontier.palemirror.frontier.v3.api.SimInstant(due),
                    new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(1_024, 4_096));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(63, after.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"),
                "need=" + after.humanPopulation().nutrition(resident) + " meal=" + after.humanPopulation().meals()
                        + " schedules=" + engine.checkpoint().schedules());
        assertEquals(ResidentNutritionStatus.NOURISHED, after.humanPopulation().nutrition(resident).status());
        assertTrue(after.humanPopulation().meals().isEmpty(),
                "meal=" + after.humanPopulation().meals() + " body=" + after.actorLocations().get(resident)
                        + " route=" + clearingRoute(after, resident) + " status=" + engine.status()
                        + " schedules=" + engine.checkpoint().schedules());
        assertTrue(after.inventory().fungibleResources().claims().isEmpty());
    }

    private static String clearingRoute(FrontierWorldState state, SubjectId resident) {
        ResidentMeal meal = state.humanPopulation().meals().get(resident);
        if (meal == null) return "finished";
        try { return ResidentMealKnownNavigation.returnPath(state, meal).toString(); }
        catch (RuntimeException blocked) { return blocked.toString(); }
    }

    @Test void hotResidentTakesAndConsumesBoundBreadOnceAfterSnapshotRecovery() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-hot"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SurfaceAnchor service = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow()).serviceSurface();
        var actors = new LinkedHashMap<>(initial.actorLocations());
        actors.put(resident, ActorLocation.standingOn(service));
        SubjectId account = ReferenceContainerCustody.scopeId(depot);
        SubjectId bread = new SubjectId("lot:resident-meal-hot-bread");
        var cold = initial.inventory().fungibleResources().transformCold(account,
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(bread, settlement.id(), "minecraft:bread", 64, "test", List.of()));
        var source = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:bread", 64);
        var bound = cold.rebind(account, 1L, FungiblePhysicalObservation.bind(cold, account, 1L, List.of(source)));
        FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(actors).inventory(initial.inventory().withFungibleResources(bound))
                .humanPopulation(initial.humanPopulation().accrueHunger(resident, 48_000L)));
        PhysicalReplicaRecord replica = PhysicalReplicaRecord.expected(depot,
                ReferenceContainerCustody.semanticKind(state, depot), 7L,
                ReferenceContainerCustody.canonicalFingerprint(state, depot), ReferenceContainerCustody.provenance(depot));
        var custody = PhysicalReplicaCustodyState.empty().declare(replica)
                .observe(depot, 7L, 1L, replica.fingerprint(), replica.provenance(), 7L)
                .acquire(new PhysicalCustodyLease(account, depot, ReferenceContainerCustody.PROVIDER_ID,
                        1L, 7L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null));
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        AmbientActorLease ambient = new AmbientActorLease(resident, service.standingBody(),
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(48_000L), 1L,
                AmbientLeaseStatus.PREPARED, AmbientGoalKind.PATROL, service.standingBody());
        state = AmbientLeaseStateProcess.transition(AmbientLeaseStateProcess.prepare(state, ambient),
                resident, AmbientLeaseStatus.HOT);
        ResidentMealStarted started = ResidentMealProcess.selectSourceAtYield(state, resident, 48_000L).orElseThrow();
        state = ResidentActivityProcess.reduceMealStarted(state, resident, started);
        ResidentMeal meal = started.meal();
        assertEquals(AmbientGoalKind.MEAL, state.ambientLeases().get(resident).goal());
        state = ResidentMealProcess.reduceHotArrived(state, resident,
                new ResidentMealHotArrived(resident, 1L, service.standingBody()));
        assertEquals(ResidentMeal.Phase.TAKE, state.humanPopulation().meals().get(resident).phase());
        state = ResidentMealProcess.reduceHotPrepared(state, resident,
                new ResidentMealHotEffectPrepared(resident,
                        new ResidentMealPhysicalStep(ResidentMeal.Phase.TAKE, 0, 64, 1L, 1L, 1L)));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        var remaining = new FungiblePhysicalObservation.Stack(source.address(), "minecraft:bread", 63);
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(resident,
                SceneLease.deterministicEntityId(state.bootstrap().worldId(), resident)), "minecraft:bread", 1);
        state = ResidentMealProcess.reduceHotObserved(state, resident, new ResidentMealHotEffectObserved(
                resident, ResidentMeal.Phase.TAKE, 1L, service.standingBody(), List.of(remaining), List.of(hand)), 48_001L);
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        state = ResidentMealProcess.reduceHotPrepared(state, resident,
                new ResidentMealHotEffectPrepared(resident,
                        new ResidentMealPhysicalStep(ResidentMeal.Phase.CONSUME, -1, 1, 1L, 0L, 1L)));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        ResidentMealHotEffectObserved consumed = new ResidentMealHotEffectObserved(resident,
                ResidentMeal.Phase.CONSUME, 1L, service.standingBody(), List.of(), List.of());
        var hotEvents = ResidentMealProcess.planHotObserved(state, consumed, 48_002L);
        assertEquals(3, hotEvents.size());
        int rate = state.humanPopulation().resident(resident).characteristics().effectiveMetabolismPermille(48_002L);
        FrontierRuleset.ResidentLife rules = state.bootstrap().ruleset().residentLife();
        long oldNeedDue = state.humanPopulation().nutrition(resident).nextThresholdTick(rules, rate);
        long nextNeedDue = state.humanPopulation().nutrition(resident)
                .consumeBreadAt(48_002L, rules, rate).nextThresholdTick(rules, rate);
        var hotNeed = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                hotEvents.get(1).payload());
        assertEquals(ResidentNeedProcess.review(resident, oldNeedDue).id(), hotNeed.scheduleId());
        assertEquals(ResidentNeedProcess.review(resident, nextNeedDue), hotNeed.replacement());
        var hotProgress = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                hotEvents.get(2).payload());
        assertEquals(ResidentMealProcess.progress(meal, 48_001L).id(), hotProgress.scheduleId());
        assertEquals(ResidentMealProcess.progress(meal, 48_003L), hotProgress.replacement());
        state = ResidentMealProcess.reduceHotObserved(state, resident, consumed, 48_002L);
        assertEquals(63, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        assertEquals(ResidentNutritionStatus.HUNGRY, state.humanPopulation().nutrition(resident).status());
        FrontierWorldState resumedCold = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        resumedCold = AmbientLeaseStateProcess.transition(resumedCold, resident, AmbientLeaseStatus.DRAINING);
        resumedCold = AmbientLeaseStateProcess.release(resumedCold, new AmbientLeaseReleased(resident,
                service.standingBody(), resumedCold.actorLocations().get(resident).condition().health()));
        for (int edge = 0; resumedCold.humanPopulation().meals().containsKey(resident) && edge < 256; edge++) {
            ResidentMealColdStep clearing = ResidentMealProcess.planColdStep(resumedCold, resident, 48_003L + edge)
                    .orElseThrow();
            resumedCold = ResidentMealProcess.reduceColdStep(resumedCold, resident, clearing);
        }
        assertFalse(resumedCold.humanPopulation().meals().containsKey(resident),
                "a saved HOT consumption must clear the depot once after returning to COLD");
        assertEquals(63, resumedCold.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        FrontierWorldState terminal = state;
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                ResidentMealProcess.planProgress(terminal, hotProgress.replacement()).getFirst().payload(),
                "HOT service clearance must wait for a physical arrival, not complete from a timer");
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceHotObserved(terminal, resident, consumed, 48_002L));
        ResidentMealHotReturned cleared = new ResidentMealHotReturned(resident, 1L,
                meal.clearingSurface().standingBody());
        assertEquals(cleared, FrontierWorldRuntimeDefinition.payloadCodecs().decode(cleared.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(cleared)));
        FrontierWorldState stillAtDepot = state;
        assertThrows(IllegalArgumentException.class, () -> ResidentActivityProcess.reduceMealReturned(stillAtDepot,
                resident, new ResidentMealHotReturned(resident, 1L, service.standingBody()), 48_003L),
                "a meal cannot release service access while the body still blocks the depot");
        ServiceAccessBoundary boundary = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow()).accessBoundary();
        SurfaceAnchor exit = ResidentMealKnownNavigation.returnPath(state, state.humanPopulation().meals().get(resident))
                .stream().filter(surface -> boundary.cleared(surface.standingBody())).findFirst().orElseThrow();
        ResidentMealHotAccessCleared accessCleared = new ResidentMealHotAccessCleared(resident, 1L, exit.standingBody());
        assertEquals(accessCleared, FrontierWorldRuntimeDefinition.payloadCodecs().decode(accessCleared.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(accessCleared)));
        state = ResidentMealProcess.reduceHotAccessCleared(state, resident, accessCleared);
        assertEquals(ResidentMeal.Phase.RETURN, state.humanPopulation().meals().get(resident).phase());
        assertEquals(exit.standingBody(), state.actorLocations().get(resident).body());
        FrontierWorldState alreadyCleared = state;
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceHotAccessCleared(
                alreadyCleared, resident, accessCleared), "one physical exit cannot be applied twice");
        var returnedEvents = ResidentMealProcess.planHotReturned(state, cleared, 48_003L);
        assertEquals(3, returnedEvents.size());
        assertEquals(ResidentMealProcess.progress(meal, 48_003L).id(),
                assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Cancelled.class,
                        returnedEvents.getLast().payload()).scheduleId(),
                "an asynchronous HOT clearance cancels its own continuation by identity, not by queue order");
        state = ResidentActivityProcess.reduceMealReturned(state, resident,
                cleared, 48_003L);
        assertFalse(state.humanPopulation().meals().containsKey(resident));
        assertFalse(state.inventory().fungibleResources().claims().containsKey(meal.claimId()));
        assertEquals(AmbientGoalKind.PATROL, state.ambientLeases().get(resident).goal());
        assertEquals(ResidentActivityChoice.Kind.EAT,
                ResidentActivityCoordinator.assess(state, resident, 48_003L).kind());
    }

    @Test void coldMealMovesOneClaimThroughHandAndConsumesExactlyOneBreadAcrossRestart() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-cold"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SettlementStructure depotStructure = settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SurfaceAnchor service = SettlementDepotServicePort.forDepot(depotStructure).serviceSurface();
        var actors = new LinkedHashMap<>(initial.actorLocations());
        actors.put(resident, ActorLocation.standingOn(service));
        SubjectId bread = new SubjectId("lot:resident-meal-cold-bread");
        SubjectId depotAccount = ReferenceContainerCustody.scopeId(depot);
        var ledger = initial.inventory().fungibleResources().transformCold(depotAccount,
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(bread, settlement.id(), "minecraft:bread", 64, "test", List.of()));
        FrontierWorldState stocked = initial.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(actors).inventory(initial.inventory().withFungibleResources(ledger)));
        ResidentMealStarted started = ResidentMealProcess.selectSourceAtYield(stocked, resident, 24_000L).orElseThrow();
        FrontierWorldState state = ResidentMealProcess.reduceStarted(stocked, resident, started);
        ResidentMeal meal = started.meal();
        var due = ResidentMealProcess.progress(meal, 24_001L);
        for (ResidentMeal.Phase phase : List.of(ResidentMeal.Phase.MOVE, ResidentMeal.Phase.TAKE)) {
            var events = ResidentMealProcess.planProgress(state, due);
            var step = (ResidentMealColdStep) events.getFirst().payload();
            assertEquals(phase, step.expectedPhase());
            assertEquals(due.dueAt().ticks(), step.atTick());
            assertEquals(step, FrontierWorldRuntimeDefinition.payloadCodecs().decode(step.type(),
                    FrontierWorldRuntimeDefinition.payloadCodecs().encode(step)));
            state = ResidentMealProcess.reduceColdStep(state, resident, step);
            due = ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled)
                    events.getLast().payload()).replacement();
        }
        assertEquals(ResidentMeal.Phase.CONSUME, state.humanPopulation().meals().get(resident).phase());
        assertEquals(new ResourceCustody.Actor(resident),
                state.inventory().fungibleResources().accounts().get(meal.actorAccountId()).custody());
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        var consumeEvents = ResidentMealProcess.planProgress(state, due);
        ResidentMealColdStep consume = (ResidentMealColdStep) consumeEvents.getFirst().payload();
        assertEquals(ResidentMeal.Phase.CONSUME, consume.expectedPhase());
        int rate = state.humanPopulation().resident(resident).characteristics().effectiveMetabolismPermille(due.dueAt().ticks());
        FrontierRuleset.ResidentLife rules = state.bootstrap().ruleset().residentLife();
        long oldNeedDue = state.humanPopulation().nutrition(resident).nextThresholdTick(rules, rate);
        long nextNeedDue = state.humanPopulation().nutrition(resident)
                .consumeBreadAt(due.dueAt().ticks(), rules, rate).nextThresholdTick(rules, rate);
        var coldNeed = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                consumeEvents.get(1).payload());
        assertEquals(ResidentNeedProcess.review(resident, oldNeedDue).id(), coldNeed.scheduleId());
        assertEquals(ResidentNeedProcess.review(resident, nextNeedDue), coldNeed.replacement());
        state = ResidentMealProcess.reduceColdStep(state, resident, consume);
        due = ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled)
                consumeEvents.getLast().payload()).replacement();
        assertEquals(63, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        assertFalse(state.inventory().fungibleResources().claims().containsKey(meal.claimId()));
        assertFalse(state.inventory().fungibleResources().accounts().containsKey(meal.actorAccountId()));
        assertEquals(ResidentNutritionStatus.NOURISHED, state.humanPopulation().nutrition(resident).status());
        FrontierWorldState afterConsumption = state;
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceColdStep(afterConsumption,
                resident, consume));
        for (int stepIndex = 0; stepIndex < 256; stepIndex++) {
            var returnEvents = ResidentMealProcess.planProgress(state, due);
            ResidentMealColdStep clearing = (ResidentMealColdStep) returnEvents.getFirst().payload();
            state = ResidentMealProcess.reduceColdStep(state, resident, clearing);
            if (clearing.nextSurface().isEmpty()) {
                assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                        returnEvents.get(1).payload(), "meal completion must wake activity arbitration immediately");
                assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Consumed.class,
                        returnEvents.getLast().payload());
                break;
            }
            due = ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled)
                    returnEvents.getLast().payload()).replacement();
        }
        assertFalse(state.humanPopulation().meals().containsKey(resident));
        assertEquals(63, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
    }

    @Test void oneHungryIdleResidentReservesOneDepotBreadUnitAndSurvivesRestart() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-start"), 420L));
        SubjectId settlement = initial.bootstrap().settlements().getFirst().id();
        SubjectId resident = initial.bootstrap().settlements().getFirst().residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement);
        SubjectId account = ReferenceContainerCustody.scopeId(depot);
        SubjectId wheat = new SubjectId("lot:bootstrap-1-wheat");
        SubjectId bread = new SubjectId("lot:resident-meal-test-bread");
        ResourceLot output = new ResourceLot(bread, settlement, "minecraft:bread", 64,
                "test-station-output", List.of());
        FungibleResourceLedger resources = initial.inventory().fungibleResources()
                .transformCold(account, Map.of(wheat, 64), Map.of(), output);
        FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(initial.inventory().withFungibleResources(resources))
                .humanPopulation(initial.humanPopulation().accrueHunger(resident, 24_000L)));
        ResidentMealStarted start = ResidentMealProcess.selectSourceAtYield(state, resident, 24_000L).orElseThrow();
        assertEquals(start, FrontierWorldRuntimeDefinition.payloadCodecs().decode(start.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(start)));
        FrontierWorldState reserved = ResidentMealProcess.reduceStarted(state, resident, start);
        assertEquals(64, reserved.inventory().fungibleResources().totalQuantity(settlement, "minecraft:bread"));
        assertEquals(1, reserved.inventory().fungibleResources().claims().get(start.meal().claimId()).quantity());
        assertEquals(start.meal(), reserved.humanPopulation().meals().get(resident));
        assertFalse(ResidentMealProcess.selectSourceAtYield(reserved, resident, 24_000L).isPresent());
        assertTrue(FrontierWorldRuntimeDefinition.payloadCodecs().types().contains(start.type()));
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(reserved));
        assertEquals(reserved.humanPopulation().meals(), recovered.humanPopulation().meals());
        assertEquals(reserved.inventory().fungibleResources(), recovered.inventory().fungibleResources());
    }
}
