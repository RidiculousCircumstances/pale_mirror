package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;

/** Immutable provider-neutral site facts. The provider declares the outdoor ground layer;
 * collision remains three-dimensional, so roofs, caves and overhangs are not a heightmap guess. */
public interface BuildingSiteSurvey {
    Optional<SurfaceAnchor> groundAt(int x, int z);
    Optional<NavigationCell> cellAt(BlockPosition position);
    /** Positive certificate: one dry solid natural plane and clear air above it throughout this volume. */
    default OptionalInt uniformClearSite(BuildingVolume volume) { return OptionalInt.empty(); }
    /** This current genesis provider describes its actual natural solid terrain/air. */
    static BuildingSiteSurvey terrain(TerrainSurfacePlan source) {
        Objects.requireNonNull(source);
        return new BuildingSiteSurvey() {
            public Optional<SurfaceAnchor> groundAt(int x, int z) { return Optional.of(SurfaceAnchor.at(x,source.supportYAt(x,z),z)); }
            public OptionalInt uniformClearSite(BuildingVolume volume) {
                if (source.surveyedSupportY().entrySet().stream().anyMatch(e -> e.getKey().x() >= volume.min().x()
                        && e.getKey().x() < volume.max().x() && e.getKey().z() >= volume.min().z() && e.getKey().z() < volume.max().z()))
                    return OptionalInt.empty();
                return OptionalInt.of(source.baselineSupportY());
            }
            public Optional<NavigationCell> cellAt(BlockPosition p) {
                return Optional.of(p.y()<=source.supportYAt(p.x(),p.z()) ? NavigationCell.SOLID : NavigationCell.AIR);
            }
        };
    }
}
