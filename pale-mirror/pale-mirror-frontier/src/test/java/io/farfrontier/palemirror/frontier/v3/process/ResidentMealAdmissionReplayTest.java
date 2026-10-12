package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ResidentMealAdmissionReplayTest {
    @Test void retainedMealReplaysWithoutSearchOrReplacingItsSelectedLots() throws Exception {
        var state = fixture();
        var resident = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        var started = ResidentMealProcess.selectSourceAtYield(state, resident, 24_000L).orElseThrow();
        var codec = FrontierWorldRuntimeDefinition.payloadCodecs();
        var replay = (ResidentMealStarted) codec.decode(started.type(), codec.encode(started));
        assertEquals(started, replay);
        var account = started.meal().sourceAccountId();
        // A new lexicographically earlier lot would win a fresh source selection.
        // Replay must still reserve the exact portion carried by the committed event.
        var stock = state.inventory().fungibleResources().transformCold(account,
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 1), Map.of(),
                new ResourceLot(new SubjectId("lot:aaa-later-bread"), started.meal().settlementId(),
                        ResidentMeal.BREAD_KIND, 1, "test", List.of()));
        var updated = state.withInventory(state.inventory().withFungibleResources(stock));
        try (var binding = PedestrianRoutePlanning.bind((geometry, from, to) -> {
            throw new AssertionError("meal event reduction must not query a planner");
        })) {
            var admitted = ResidentMealProcess.reduceStarted(updated, resident, replay);
            assertEquals(started.meal(), admitted.humanPopulation().meals().get(resident));
            assertEquals(started.meal().portion().lotQuantities(), admitted.inventory().fungibleResources()
                    .claims().get(started.meal().claimId()).lotQuantities());
            var snapshot = new FrontierWorldStateCodec();
            assertEquals(admitted.humanPopulation().meals(), snapshot.decode(snapshot.encode(admitted)).humanPopulation().meals());
            assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceStarted(admitted, resident, replay));
            assertThrows(IllegalArgumentException.class, () -> new ResidentMealStarted(started.meal(), Optional.empty()));
            var reversed = new PedestrianRouteReceipt(started.clearingAccess().orElseThrow().route().reversed());
            assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceStarted(updated, resident,
                    new ResidentMealStarted(started.meal(), Optional.of(reversed))));
            var missingLots = started.meal().portion().lotQuantities().keySet().stream().collect(
                    java.util.stream.Collectors.toMap(id -> id, id -> state.inventory().fungibleResources().accounts()
                            .get(account).lotQuantities().get(id)));
            var lost = state.withInventory(state.inventory().withFungibleResources(state.inventory().fungibleResources()
                    .destroy(account, missingLots, Map.of())));
            assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceStarted(lost, resident, replay));
        }
        var bytes = codec.encode(started);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(started.type(),
                java.util.Arrays.copyOf(bytes, bytes.length - 1)));
    }

    @Test void queueEligibilityDoesNotSearchAndPendingOwnerRestoresItsExactCalculationWait() throws Exception {
        var state = fixture();
        var resident = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        var action = ResidentActivityProcess.review(resident, 24_000L);
        var queries = new AtomicInteger();
        var waits = new PedestrianPlanningWakeIndex();
        try (var binding = PedestrianRoutePlanning.bind((geometry, from, to) -> {
            queries.incrementAndGet();
            return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 1, 1, "pending");
        }); var continuation = PedestrianPlanningContinuations.bind(waits)) {
            assertFalse(FrontierWorldRuntimeDefinition.scheduledHeld(state, action));
            assertEquals(0, queries.get(), "queue eligibility cannot initialize a route search");
            var events = FrontierWorldRuntimeDefinition.planScheduled(state, action);
            assertEquals(1, events.size());
            var retry = assertInstanceOf(ScheduleEffect.Rescheduled.class, events.getFirst().payload());
            assertEquals(action.id(), retry.scheduleId());
            assertEquals(1, queries.get(), "one blocked owner cannot fan out through every waiting point");
            assertEquals(1, waits.waitingCount());
            assertTrue(state.humanPopulation().meals().isEmpty());
            waits.remove(action.id());
            var snapshots = new FrontierWorldStateCodec();
            var recovered = snapshots.decode(snapshots.encode(state));
            FrontierWorldProcessCatalog.restorePlanningWaits(FrontierWorldRuntimeDefinition.processRegistry(), recovered,
                    new FrontierExecutionView(state.bootstrap().worldId(), new Revision(0), new SimInstant(24_000L),
                            List.of(retry.replacement())));
            assertEquals(1, waits.waitingCount(), "restart must restore the calculation dependency, not absence");
        }
    }

    @Test void retainedNoPathParksTheOwnerWithoutRerunningSearch() throws Exception {
        var state = fixture();
        var resident = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        var queries = new AtomicInteger();
        var noPath = new PedestrianRouteResult(PedestrianRouteResult.Status.NO_PATH, List.of(), 1, 1, "blocked");
        try (var binding = PedestrianRoutePlanning.bind(new PedestrianRoutePlanner() {
            public PedestrianRouteResult query(PedestrianRouteGeometry g, SurfaceAnchor from, SurfaceAnchor to) {
                queries.incrementAndGet(); return noPath;
            }
            public Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry g, SurfaceAnchor from, SurfaceAnchor to) {
                return Optional.of(noPath);
            }
        })) {
            var waiting = assertInstanceOf(ResidentActivityAdmission.Waiting.class,
                    ResidentActivityProcess.admission(state, ResidentActivityProcess.review(resident, 24_000L)));
            assertEquals(ResidentActivityAdmission.Reason.MEAL_CLEARANCE, waiting.reason());
            assertEquals(0, queries.get());
        }
    }

    private static FrontierWorldState fixture() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:meal-admission-replay"), 421L));
        var settlement = initial.bootstrap().settlements().getFirst();
        var account = ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(settlement.id()));
        var resources = initial.inventory().fungibleResources().transformCold(account,
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 32), Map.of(),
                new ResourceLot(new SubjectId("lot:meal-replay-bread"), settlement.id(), ResidentMeal.BREAD_KIND,
                        32, "test", List.of()));
        return initial.withInventory(initial.inventory().withFungibleResources(resources));
    }
}
