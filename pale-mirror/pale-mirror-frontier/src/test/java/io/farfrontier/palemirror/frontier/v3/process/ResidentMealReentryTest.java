package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ResidentMealReentryTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void reenteredPassageClearsAgainWithoutRetakingOrLosingFood(boolean hotReentry) {
        var fixture = FrontierV3FixtureCatalog.configuration("resident-meal-after-cold-take",
                new WorldId("frontier:meal-reentered-passage"), 41L);
        SubjectId resident = new SubjectId("resident:6-1");
        var state = fixture.initialState();
        long now = fixture.initialInstant().ticks();
        for (int turn = 0; state.humanPopulation().meals().get(resident).phase() != ResidentMeal.Phase.CONSUME && turn < 16; turn++) {
            var meal = state.humanPopulation().meals().get(resident);
            now = meal.coldTravel().map(route -> route.arrivalTick()).orElse(now + 1L);
            state = ResidentMealProcess.reduceColdStep(state, resident,
                    ResidentMealProcess.planColdStep(state, resident, now).orElseThrow());
        }
        var meal = state.humanPopulation().meals().get(resident);
        assertEquals(ResidentMeal.Phase.CONSUME, meal.phase());
        var lease = AmbientActorProcess.nextLease(state, resident, new SimInstant(++now));
        state = AmbientLeaseStateProcess.prepare(state, lease);
        state = ModeledActorBodyFacts.present(state, resident);
        state = AmbientLeaseStateProcess.transition(state, resident, AmbientLeaseStatus.HOT);
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorPocket(resident,
                SceneLease.deterministicEntityId(state.bootstrap().worldId(), resident), 0),
                meal.portion().itemKind(), meal.portion().quantity());
        state = ResidentMealProcess.reduceHotHandMaterialized(state, resident,
                new ResidentMealHotHandMaterialized(resident, lease.revision(), hand, meal.executionId()));
        var inside = ResidentMealProcess.serviceSurface(state, meal).standingBody();
        state = ModeledActorBodyFacts.inspected(state, resident, inside);
        assertFalse(ResidentMealProcess.mayConsumeAt(state, meal, inside));
        var required = new ResidentMealClearanceRequired(meal.executionId(), inside);
        var payloads = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(required, payloads.decode(required.type(), payloads.encode(required)));
        DiagnosticProducerContract.requireAdmitted(required);
        var before = state;
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceClearanceRequired(before, resident,
                new ResidentMealClearanceRequired(meal.executionId(), meal.clearingSurface().standingBody())));
        var pending = state.withHumanPopulation(state.humanPopulation().advanceMeal(meal, meal.prepare(
                new ResidentMealPhysicalStep(ResidentMeal.Phase.CONSUME, -1, meal.portion().quantity(),
                        lease.revision(), 0L, lease.revision(), meal.executionId()))));
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceClearanceRequired(pending, resident, required),
                "an unresolved physical effect cannot be discarded by movement recovery");
        if (hotReentry) {
            var command = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, new CommandId("test:meal-reentry"),
                    state.bootstrap().worldId(), new Revision(0), new SimInstant(now),
                    io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                    CauseChain.root(new CommandId("test:meal-reentry")), required);
            var plan = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.planCommand(state, command);
            assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class, plan);
            state = ResidentMealProcess.reduceClearanceRequired(state, resident, required);
            assertEquals(before.inventory(), state.inventory());
        }
        // Resource release is independent of the eating-position predicate.
        state = ResidentMealProcess.reduceHotHandReleased(state, resident,
                new ResidentMealHotHandReleased(resident, lease.revision(), hand, meal.executionId()));
        state = AmbientLeaseStateProcess.transition(state, resident, AmbientLeaseStatus.DRAINING);
        state = AmbientLeaseStateProcess.release(state, new AmbientLeaseReleased(resident, inside,
                state.actorLocations().get(resident).condition().health()));
        state = ModeledActorBodyFacts.unloaded(state, resident);
        var codec = new FrontierWorldStateCodec();
        state = codec.decode(codec.encode(state));
        if (!hotReentry) {
            assertTrue(ResidentMealProcess.planColdStep(state, resident, ++now).isEmpty(),
                    "an invalid consumption must not be proposed to the reducer");
            var events = ResidentMealProcess.planProgress(state, ResidentMealProcess.progress(meal, ++now));
            assertEquals(required, events.getFirst().payload());
            assertInstanceOf(ScheduleEffect.Rescheduled.class, events.getLast().payload());
            state = ResidentMealProcess.reduceClearanceRequired(state, resident, required);
        }
        assertEquals(ResidentMeal.Phase.CLEAR_ACCESS, state.humanPopulation().meals().get(resident).phase());
        var inventory = state.inventory();
        assertEquals(meal.executionId(), state.humanPopulation().meals().get(resident).executionId());
        state = codec.decode(codec.encode(state));
        for (int turn = 0; state.humanPopulation().meals().containsKey(resident) && turn < 16; turn++) {
            var current = state.humanPopulation().meals().get(resident);
            assertNotEquals(ResidentMeal.Phase.TAKE, current.phase());
            now = current.coldTravel().map(route -> route.arrivalTick()).orElse(now + 1L);
            state = ResidentMealProcess.reduceColdStep(state, resident,
                    ResidentMealProcess.planColdStep(state, resident, now).orElseThrow());
        }
        assertFalse(state.humanPopulation().meals().containsKey(resident));
        assertFalse(state.inventory().fungibleResources().claims().containsKey(meal.claimId()));
        assertEquals(inventory.fungibleResources().totalQuantity(meal.settlementId(), meal.portion().itemKind()) - meal.portion().quantity(),
                state.inventory().fungibleResources().totalQuantity(meal.settlementId(), meal.portion().itemKind()));
        assertTrue(ServiceAccessCoordinator.boundary(state, meal.depotId()).cleared(state.actorLocations().get(resident).body()));
    }
}
