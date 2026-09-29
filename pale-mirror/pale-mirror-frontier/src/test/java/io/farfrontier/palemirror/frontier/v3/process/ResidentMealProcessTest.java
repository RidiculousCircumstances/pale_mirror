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
                        ResidentNeedProcess.review(resident, 72_000L)),
                base.transactionCommitter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(configuration);
        for (long due = 48_001L; due <= 48_301L; due += 20L)
            engine.advanceTo(new io.farfrontier.palemirror.frontier.v3.api.SimInstant(due),
                    new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(1_024, 4_096));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(62, after.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"),
                "need=" + after.humanPopulation().nutrition(resident) + " meal=" + after.humanPopulation().meals()
                        + " schedules=" + engine.checkpoint().schedules());
        assertEquals(ResidentNutritionStatus.NOURISHED, after.humanPopulation().nutrition(resident).status());
        assertTrue(after.humanPopulation().meals().isEmpty());
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
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                world, stocked, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(),
                base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), List.of(
                ResidentNeedProcess.firstReviewAfter(resident, 0L, initial.bootstrap().ruleset().residentLife()),
                ResidentActivityProcess.review(resident, 1L)), base.transactionCommitter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(configuration);
        for (long due : List.of(1L, 12_000L, 24_000L, 24_001L, 24_021L, 24_041L, 24_061L, 24_100L))
            engine.advanceTo(new io.farfrontier.palemirror.frontier.v3.api.SimInstant(due),
                    new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(1_024, 4_096));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(63, after.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"),
                "need=" + after.humanPopulation().nutrition(resident) + " meal=" + after.humanPopulation().meals()
                        + " schedules=" + engine.checkpoint().schedules());
        assertEquals(ResidentNutritionStatus.NOURISHED, after.humanPopulation().nutrition(resident).status());
        assertTrue(after.humanPopulation().meals().isEmpty());
        assertTrue(after.inventory().fungibleResources().claims().isEmpty());
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
        assertEquals(2, hotEvents.size());
        var hotNeed = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                hotEvents.get(1).payload());
        assertEquals(ResidentNeedProcess.review(resident, 72_000L).id(), hotNeed.scheduleId());
        assertEquals(ResidentNeedProcess.review(resident, 72_000L), hotNeed.replacement());
        state = ResidentMealProcess.reduceHotObserved(state, resident, consumed, 48_002L);
        assertEquals(63, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        assertEquals(ResidentNutritionStatus.HUNGRY, state.humanPopulation().nutrition(resident).status());
        FrontierWorldState terminal = state;
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceHotObserved(terminal, resident, consumed, 48_002L));
        state = ResidentActivityProcess.reduceMealReturned(state, resident,
                new ResidentMealHotReturned(resident, 1L), 48_003L);
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
        var coldNeed = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                consumeEvents.get(1).payload());
        assertEquals(ResidentNeedProcess.review(resident, 24_000L).id(), coldNeed.scheduleId());
        assertEquals(ResidentNeedProcess.review(resident, 48_000L), coldNeed.replacement());
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
        var returnEvents = ResidentMealProcess.planProgress(state, due);
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                returnEvents.get(1).payload(), "meal completion must wake activity arbitration immediately");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Consumed.class,
                returnEvents.getLast().payload());
        state = ResidentMealProcess.reduceColdStep(state, resident,
                (ResidentMealColdStep) returnEvents.getFirst().payload());
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
