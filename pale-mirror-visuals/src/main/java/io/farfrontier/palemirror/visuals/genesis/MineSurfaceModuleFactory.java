package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredMineRole;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPort;
import io.farfrontier.palemirror.api.VisualPortKind;
import java.util.List;

/** Compiles a surveyed mine pad into a facade-aware reusable module placement. */
final class MineSurfaceModuleFactory {
    private MineSurfaceModuleFactory() { }

    static VisualModulePlacement place(String family, String template, String padId,
                                       String moduleRole, AuthoredMineRole mineRole,
                                       MountainMineAnchor anchor) {
        VisualPoint origin = anchor.surfaceCenter(mineRole, padId);
        String relative = template.startsWith("../") ? template.substring(3) : "mine/" + template;
        String path = family + "/" + relative;
        FrontierModuleCatalog.Definition definition = FrontierModuleCatalog.require(relative);
        int direction = anchor.inwardQuarterTurns();
        boolean swap = Math.floorMod(direction, 2) == 1;
        int width = swap ? definition.sizeZ() : definition.sizeX();
        int depth = swap ? definition.sizeX() : definition.sizeZ();
        VisualBounds footprint = new VisualBounds(new VisualPoint(origin.x() - width / 2, origin.y() + 1,
                origin.z() - depth / 2), new VisualPoint(origin.x() + (width - 1) / 2,
                origin.y() + definition.sizeY(),
                origin.z() + (depth - 1) / 2));
        int outward = Math.floorMod(definition.entranceOutward() + direction, 4);
        VisualPoint entrance = SettlementLayoutGeometry.authoredEntrance(definition, footprint, direction);
        return new VisualModulePlacement(path.replace('/', '_') + "_" + origin.x() + "_" + origin.z(),
                "pale_mirror_visuals:" + path, family, moduleRole, origin, direction, footprint, padId,
                definition.stateProfile(), List.of(new VisualPort(
                        "public", VisualPortKind.PUBLIC_ENTRANCE, entrance, outward)));
    }
}
