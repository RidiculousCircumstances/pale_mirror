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
                + "}}";
    }

    private FrontierV3SettlementLayoutDiagnostic() {}
}
