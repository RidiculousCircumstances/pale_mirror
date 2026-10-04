package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.process.SupplyOperationProcess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouteSceneFixtureIdentityTest {
    @Test
    void assemblyScenarioRetainsTheExactThreePersonShipmentBeforeAnyColdStep() {
        var fixture = FrontierDevelopmentScenarios.operationAssemblyFixture(new WorldId("frontier:assembly-identity"), 41L);
        var operation = fixture.state().operations().get(fixture.operationId());
        assertEquals(new SubjectId("operation:supply-1-12"), operation.id());
        assertEquals(java.util.List.of(new SubjectId("resident:1-30"), new SubjectId("resident:1-16"),
                new SubjectId("resident:1-28")), operation.participantIds());
        assertEquals(3, operation.activeAssembly().orElseThrow().members().size());
        assertEquals(OperationStage.ASSEMBLING, operation.stage());
        assertTrue(fixture.schedules().stream().noneMatch(action -> action.subject().equals(operation.id())
                && action.kind().equals("frontier.operation.assembly")));
    }

    @Test
    void cargoTheftScenarioUsesTheActualFungibleShipment() {
        var state = FrontierV3FixtureCatalog.routeSceneReturnConfiguration(
                new WorldId("frontier:route-cargo-identity"), 41L).initialState();
        var cargoId = new SubjectId("cargo:supply-1-12");
        var cargo = state.inventory().cargo().get(cargoId);
        assertTrue(cargo.fungibleContents());
        assertTrue(cargo.itemIds().isEmpty());
        assertFalse(state.inventory().items().containsKey(new SubjectId("item:production-settlement-1-settlement_produce_bread-1-bread")));
        var account = state.inventory().fungibleResources().accounts()
                .get(new SubjectId("custody:cargo-supply-1-12"));
        assertEquals(new ResourceCustody.Cargo(cargoId), account.custody());
        assertEquals(java.util.Map.of(new SubjectId("lot:production-objective-workforce-43cecd8243a8fafa0b56ff4b804d352c-1-bread"), 64), account.lotQuantities());
        assertEquals("minecraft:bread", state.inventory().fungibleResources().lots()
                .get(new SubjectId("lot:production-objective-workforce-43cecd8243a8fafa0b56ff4b804d352c-1-bread")).itemKind());
    }

    @Test
    void routeReturnObserverPositionsRemainOutsideMaterializedStructures() {
        var state = FrontierV3FixtureCatalog.routeSceneReturnConfiguration(
                new WorldId("frontier:route-camera"), 41L).initialState();
        var plan = FrontierGrayboxPlan.compile(state);
        // Paired with the checked-in scenario positions in route-fixture-identity.test.mjs.
        for (var feet : java.util.List.of(new BlockPosition(-354, 64, -352),
                new BlockPosition(-354, 64, -314), new BlockPosition(0, 64, 0))) {
            assertEquals(feet.y() - 1, state.bootstrap().terrain().supportYAt(feet.x(), feet.z()));
            for (int y = feet.y() - 1; y <= feet.y() + 1; y++) {
                assertNull(plan.cells().get(new BlockPosition(feet.x(), y, feet.z())),
                        "observer must remain on natural ground outside planned structures: " + feet);
            }
        }
        assertNotNull(plan.cells().get(new BlockPosition(-360, 64, -340)),
                "old observer point overlapped a structure materialized during entry");
    }

    @Test
    void nativeRouteAndScoutFixturesRetainTheirActualShipmentAndFreezeOnlyThatIdentity() {
        var world = new WorldId("frontier:route-fixture-identity");
        var configurations = java.util.List.of(
                FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 41L),
                FrontierV3FixtureCatalog.hotScoutSightingConfiguration(world, 41L),
                FrontierV3FixtureCatalog.hotScoutInterceptConfiguration(world, 41L));
        for (int index = 0; index < configurations.size(); index++) {
            var configuration = configurations.get(index);
            var state = configuration.initialState();
            var operation = FrontierDevelopmentScenarios.initialNorthwatchShipment(state).orElseThrow();
            // Explicit native-scenario precondition, not an ordinal used to discover ownership.
            assertEquals(new SubjectId("operation:supply-1-12"), operation.id());
            assertEquals(new SubjectId("cargo:supply-1-12"), operation.cargoId());
            assertEquals(new SubjectId("contract:supply-1-12"), operation.contractId());
            var action = SupplyOperationProcess.operationProgress(operation, configuration.initialInstant().ticks() + 20L);
            var events = configuration.scheduledPlanner().plan(state, action);
            if (index == 0) {
                assertFalse(events.stream().anyMatch(event -> event.payload() instanceof ScheduleEffect.Cancelled));
            } else {
                assertEquals(1, events.size());
                assertEquals(new ScheduleEffect.Cancelled(action.id()), events.getFirst().payload());
            }
            // Independent scheduled work is still planned by its real production owner.
            var independent = configuration.initialSchedules().stream()
                    .filter(schedule -> !schedule.subject().equals(operation.id()))
                    .findFirst().orElseThrow();
            assertEquals(
                    io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.planScheduled(state, independent, false),
                    configuration.scheduledPlanner().plan(state, independent));
        }
    }
}
