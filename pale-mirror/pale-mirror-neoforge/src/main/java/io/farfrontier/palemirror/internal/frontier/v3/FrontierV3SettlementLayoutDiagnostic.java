package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;

/** Read-only bounded layout summary; neither a placement nor a bed assignment authority. */
final class FrontierV3SettlementLayoutDiagnostic {
    static String read(FrontierWorldState state, Settlement home) {
        var layout = home.layout();
        var houses =
                home.structures().stream().filter(s -> s.kind() == StructureKind.HOUSING).toList();
        var house = houses.getFirst().anchor();
        return "{\"revision\":"
                + layout.revision()
                + ",\"scheme\":\""
                + layout.scheme()
                + "\",\"plots\":"
                + layout.plots().size()
                + ",\"houses\":"
                + houses.size()
                + ",\"declaredHousingBeds\":"
                + SettlementBeds.declaredCapacity(home)
                + ",\"availableHousingBeds\":"
                + SettlementFacilityCapability.housingCapacity(state, home.id())
                + ",\"pavingCells\":"
                + layout.paving().size()
                + ",\"extensionFronts\":"
                + layout.extensionFronts().size()
                + ",\"firstHousingAnchor\":{\"x\":"
                + house.x()
                + ",\"y\":"
                + house.y()
                + ",\"z\":"
                + house.z()
                + "},\"facilities\":" + facilities(state, home) + "}";
    }

    /** Read-only authoring landmarks, never role or execution dispatch identities. */
    private static String facilities(FrontierWorldState state, Settlement home) {
        var values = new java.util.LinkedHashMap<StructureKind, SettlementStructure>();
        home.structures().forEach(building -> values.putIfAbsent(building.kind(), building));
        return values.entrySet().stream().map(entry -> {
            var building = entry.getValue();
            var port = io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSpatialPlan.of(building).primaryEntrance();
            var front = port.exteriorApproach().getFirst().support();
            var direction = building.facing();
            var view = front.offset(direction.x() * 12, 3, direction.z() * 12);
            return "\"" + entry.getKey().name() + "\":{\"id\":\"" + building.id().value()
                    + "\",\"anchor\":" + point(building.anchor()) + ",\"entrance\":" + point(port.thresholdSurface().support().offset(0, 1, 0))
                    + ",\"interior\":" + point(port.interiorConnector().support().offset(0, 1, 0))
                    + ",\"floor\":" + point(port.interiorConnector().support())
                    + ",\"integrity\":\"" + state.structureConditions().get(building.id()) + "\",\"damagedCells\":"
                    + state.structureDamage().getOrDefault(building.id(), StructureDamage.empty(building.id())).cells().size()
                    + ",\"facadeView\":" + point(view) + "}";
        }).collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
    private static String point(BlockPosition p) { return "{\"x\":" + p.x() + ",\"y\":" + p.y() + ",\"z\":" + p.z() + "}"; }

    private FrontierV3SettlementLayoutDiagnostic() {}
}
