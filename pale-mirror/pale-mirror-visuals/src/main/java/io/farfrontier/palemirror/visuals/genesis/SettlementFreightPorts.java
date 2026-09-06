package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredBuildingPlan;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPortKind;

/** Resolves the physical hand-off between a depot facade and canonical transport. */
final class SettlementFreightPorts {
    private SettlementFreightPorts() { }

    static VisualPoint railhead(AuthoredBuildingPlan depot) {
        return depot.modules().stream().flatMap(module -> module.ports().stream())
                .filter(port -> port.kind() == VisualPortKind.RAIL).findFirst().orElseThrow().position();
    }

    /** Facade loading threshold beside, rather than through, the public door. */
    static VisualPoint loadingThreshold(VisualPoint entrance, VisualBounds footprint, int outwardQuarterTurns) {
        int outwardX = switch (Math.floorMod(outwardQuarterTurns, 4)) {
            case 0 -> 1;
            case 2 -> -1;
            default -> 0;
        };
        int outwardZ = switch (Math.floorMod(outwardQuarterTurns, 4)) {
            case 1 -> 1;
            case 3 -> -1;
            default -> 0;
        };
        int tangentX = -outwardZ;
        int tangentZ = outwardX;
        int x = Math.max(footprint.min().x(), Math.min(footprint.max().x(), entrance.x() + tangentX * 3));
        int z = Math.max(footprint.min().z(), Math.min(footprint.max().z(), entrance.z() + tangentZ * 3));
        return new VisualPoint(x, footprint.min().y(), z);
    }
}
