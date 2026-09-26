package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.process.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read-only retained-loss fairness fixture; isolated from general development scenarios. */
final class RouteMaintenanceFairnessScenario {
    private RouteMaintenanceFairnessScenario() { }

    static FrontierDevelopmentScenarios.RouteMaintenanceFairnessFixture routeMaintenanceColdSourceFairnessFixture(WorldId worldId, long seed) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(worldId, seed));
        BlockPosition firstLoss = new BlockPosition(-380, 64, -304);
        BlockPosition secondLoss = new BlockPosition(-140, 64, -304);
        state = state.recordPhysicalDelta(new PhysicalDelta(firstLoss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK, FrontierRouteNetwork.OWNER)), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "fixture:cold-source"));
        state = state.recordPhysicalDelta(new PhysicalDelta(secondLoss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK, FrontierRouteNetwork.OWNER)), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "fixture:loaded-repair"));

        RouteMaintenance first = maintenanceStarted(state, 1, 100L, firstLoss);
        state = RouteMaintenanceStateSupport.reduceStarted(state, FrontierRouteNetwork.OWNER, new RouteMaintenanceStarted(first));
        RouteMaintenance second = maintenanceStarted(state, 2, 200L, secondLoss);
        state = RouteMaintenanceStateSupport.reduceStarted(state, FrontierRouteNetwork.OWNER, new RouteMaintenanceStarted(second));

        ExactInventory inventory = state.inventory().withSurfaceStatus(FrontierRouteNetwork.MAINTENANCE_CONTAINER, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(FrontierRouteNetwork.MAINTENANCE_CONTAINER, ContainerSurfaceStatus.ACTIVE);
        SubjectId coldSource = new SubjectId("item:fixture-maintenance-cold-source");
        inventory = inventory.store(new ExactItemStack(coldSource, FrontierRouteNetwork.OWNER, "minecraft:gray_concrete", 1,
                new InventoryCustody.ContainerSlot(FrontierRouteNetwork.MAINTENANCE_CONTAINER,
                        inventory.firstFreeSlot(FrontierRouteNetwork.MAINTENANCE_CONTAINER).orElseThrow())));
        state = state.withInventory(inventory);
        state = RouteMaintenanceProcess.reducePrepared(state, FrontierRouteNetwork.OWNER,
                RouteMaintenanceProcess.materialLoadingIntent(state, first, state.inventory().items().get(coldSource)));

        // This cargo models an already-confirmed earlier physical pickup, not an alternate
        // source. It lets the player load only the later worksite while the first exact source
        // remains naturally COLD at the real shared maintenance chest.
        SubjectId priorSource = new SubjectId("item:fixture-maintenance-loaded-source");
        inventory = state.inventory().store(new ExactItemStack(priorSource, FrontierRouteNetwork.OWNER, "minecraft:gray_concrete", 1,
                new InventoryCustody.ContainerSlot(FrontierRouteNetwork.MAINTENANCE_CONTAINER,
                        state.inventory().firstFreeSlot(FrontierRouteNetwork.MAINTENANCE_CONTAINER).orElseThrow())));
        CargoBatch secondCargo = new CargoBatch(second.plannedCargoId(), FrontierRouteNetwork.OWNER, List.of(second.plannedCargoItemId()));
        inventory = inventory.extractOneToCargo(priorSource, secondCargo, second.plannedCargoItemId());
        for (SubjectId member : second.team().memberIds()) {
            ExactItemStack tool = inventory.items().values().stream().filter(item -> item.economicOwnerId().equals(second.settlementId()))
                    .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot)
                    .filter(item -> EngineeringToolCustody.isTool(item.itemKind())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("fairness fixture needs one exact local engineering tool per member"));
            inventory = inventory.moveObservedItem(tool.id(), tool.custody(), new InventoryCustody.Actor(member));
        }
        state = state.withInventory(inventory);
        EngineeringWorkAssembly initialAssembly = EngineeringWorksite.compile(state, second);
        state = RouteMaintenanceStateSupport.reduceAssemblyStarted(state, FrontierRouteNetwork.OWNER,
                new RouteMaintenanceAssemblyStarted(second.id(), initialAssembly));
        while (!state.routeMaintenances().get(second.id()).assembly().orElseThrow().complete()) {
            EngineeringWorkAssembly current = state.routeMaintenances().get(second.id()).assembly().orElseThrow();
            SubjectId advancing = current.safeAdvances().getFirst();
            state = RouteMaintenanceStateSupport.reduceAssemblyAdvanced(state, FrontierRouteNetwork.OWNER,
                    new RouteMaintenanceAssemblyAdvanced(second.id(), current.advance(advancing)));
        }
        Map<SubjectId, RouteMaintenance> maintenances = new LinkedHashMap<>(state.routeMaintenances());
        maintenances.put(second.id(), maintenances.get(second.id()).withCargo(secondCargo.id()));
        state = state.withChanges(FrontierWorldStateUpdate.begin().routeMaintenances(maintenances));
        // Do not pre-prepare the physical repair. A work intent is authority granted only by
        // the actual HOT worksite lease; the normal loaded scene executor must request it after
        // the player visit. Retaining it here would let a direct physical executor race the
        // scene transition and would make this fixture prove an invalid lifecycle.
        return new FrontierDevelopmentScenarios.RouteMaintenanceFairnessFixture(state, new SimInstant(300L), List.of(), first.id(), second.id());
    }

    private static RouteMaintenance maintenanceStarted(FrontierWorldState state, int scanOrdinal, long dueAt, BlockPosition expectedLoss) {
        return RouteMaintenanceProcess.plan(state, RouteMaintenanceProcess.scan(scanOrdinal, dueAt)).stream().map(ProposedEvent::payload)
                .filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .map(RouteMaintenanceStarted::maintenance).filter(maintenance -> maintenance.repairCell().equals(expectedLoss)).findFirst()
                .orElseThrow(() -> new IllegalStateException("fairness fixture did not admit its exact retained loss " + expectedLoss));
    }
}
