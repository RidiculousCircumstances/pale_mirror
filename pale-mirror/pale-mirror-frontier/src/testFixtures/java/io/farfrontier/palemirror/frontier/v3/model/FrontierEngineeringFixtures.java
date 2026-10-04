package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.farfrontier.palemirror.frontier.v3.model.FrontierDevelopmentScenarios.RouteConstructionFixture;

/** Disposable engineering setup; never selected by production bootstrap. */
final class FrontierEngineeringFixtures {
    private FrontierEngineeringFixtures() { }

    static RouteConstructionFixture engineeringEquipmentFixture(WorldId worldId, long seed) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(worldId, seed));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        RouteConstruction project = fixtureDetourProject(state, settlement);
        state = RouteConstructionStateSupport.reduceStarted(state, FrontierRouteNetwork.OWNER, io.farfrontier.palemirror.frontier.v3.model.EngineeringExecutionEvents.constructionStarted(state, project));
        return new RouteConstructionFixture(state, new SimInstant(200L), List.of(RouteConstructionProcess.scan(1, 200L)), project.id());
    }

    private static RouteConstruction fixtureDetourProject(FrontierWorldState state, Settlement settlement) {
        List<BlockPosition> baseline = state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id());
        BlockPosition origin = baseline.getFirst(), destination = baseline.getLast();
        BlockPosition egress = origin.offset(-36, 0, 0), lane = egress.offset(0, 0, 60);
        List<BlockPosition> detour = List.of(origin, egress, lane, new BlockPosition(-300, lane.y(), lane.z()),
                new BlockPosition(-300, destination.y(), destination.z()), destination);
        // This identity is the fixture's stable subject referenced by checked-in scenario and
        // GameTest evidence. It no longer encodes a physical-loss cause.
        SubjectId projectId = new SubjectId("construction:route-reroute-" + settlement.id().value().replace(':', '-') + "--366-64--304");
        List<SubjectId> crew = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlement.id()))
                .filter(resident -> resident.capability(HumanCapability.ENGINEERING) > 0)
                .sorted(Comparator.comparing(ResidentProfile::id)).limit(EngineeringRecoveryTeam.MIN_MEMBERS).map(ResidentProfile::id).toList();
        if (crew.isEmpty()) throw new IllegalStateException("engineering fixture needs one exact engineer");
        List<BlockPosition> workCells = FrontierRouteNetwork.constructionCells(state.bootstrap(), state.routeTopology(), settlement.id(), detour);
        if (workCells.isEmpty()) throw new IllegalStateException("engineering fixture needs a non-empty immutable detour work plan");
        return new RouteConstruction(projectId, settlement.id(), detour, workCells, 0, RouteConstructionStatus.BUILDING,
                Optional.empty(), Optional.of(EngineeringRecoveryTeam.forWorkOrder(projectId, settlement.id(), crew)), Optional.empty());
    }

    /**
     * Test-only postcondition fixture for the physical work boundary. It retains the ordinary
     * project/team identities, moves only their already-issued exact tools and creates the same
     * one-unit cargo split used by the production receipt path. Minecraft remains untouched
     * until an ordinary player visit admits the HOT work-site scene.
     */
    static RouteConstructionFixture engineeringWorksiteFixture(WorldId worldId, long seed) {
        RouteConstructionFixture base = engineeringEquipmentFixture(worldId, seed);
        FrontierWorldState state = base.state();
        RouteConstruction project = state.routeConstructions().get(base.projectId());
        EngineeringRecoveryTeam team = project.team().orElseThrow();
        ExactInventory inventory = state.inventory();
        for (SubjectId member : team.memberIds()) {
            ExactItemStack tool = inventory.items().values().stream().filter(item -> item.economicOwnerId().equals(project.settlementId()))
                    .filter(item -> EngineeringToolCustody.isTool(item.itemKind())).filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot)
                    .findFirst().orElseThrow(() -> new IllegalStateException("engineering worksite fixture needs one exact depot tool per member"));
            inventory = inventory.moveObservedItem(tool.id(), tool.custody(), new InventoryCustody.Actor(member));
        }
        SubjectId maintenance = FrontierRouteNetwork.MAINTENANCE_CONTAINER;
        SubjectId source = new SubjectId("item:fixture-engineering-worksite-concrete");
        inventory = inventory.withSurfaceStatus(maintenance, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(maintenance, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(source, FrontierRouteNetwork.OWNER, "minecraft:gray_concrete", 1,
                        new InventoryCustody.ContainerSlot(maintenance, inventory.firstFreeSlot(maintenance).orElseThrow())));
        CargoBatch cargo = new CargoBatch(project.plannedCargoId(), FrontierRouteNetwork.OWNER, List.of(project.plannedCargoItemId()));
        inventory = inventory.extractOneToCargo(source, cargo, project.plannedCargoItemId());
        EngineeringWorkAssembly compiled = EngineeringWorksite.compile(state, project);
        java.util.Map<SubjectId, EngineeringWorkAssembly.Member> completed = new java.util.LinkedHashMap<>();
        java.util.Map<SubjectId, ActorLocation> locations = new java.util.LinkedHashMap<>(state.actorLocations());
        compiled.members().forEach((member, approach) -> {
            EngineeringWorkAssembly.Member arrived = new EngineeringWorkAssembly.Member(approach.corridor(), approach.corridor().size() - 1);
            completed.put(member, arrived);
            locations.put(member, new ActorLocation(BodyPosition.above(new SurfaceAnchor(arrived.currentPosition())), locations.get(member).condition(), locations.get(member).kind()));
        });
        RouteConstruction ready = new RouteConstruction(project.id(), project.settlementId(), project.waypoints(), project.workCells(), project.confirmedCells(),
                project.status(), java.util.Optional.of(cargo.id()), project.team(), java.util.Optional.of(new EngineeringWorkAssembly(EngineeringJourneyPurpose.WORKSITE, completed)));
        java.util.Map<SubjectId, RouteConstruction> projects = new java.util.LinkedHashMap<>(state.routeConstructions()); projects.put(ready.id(), ready);
        state = EngineeringExecutionAuthority.assembled(state, project, ready.assembly().orElseThrow(),
                EngineeringExecutionAuthority.assemblyCurrent(state, project),
                EngineeringExecutionAuthority.workAdmission(state, project, ready.assembly().orElseThrow()),
                FrontierWorldStateUpdate.begin().inventory(inventory).actorLocations(locations).routeConstructions(projects));
        return new RouteConstructionFixture(state, base.instant(), List.of(), ready.id());
    }

}
