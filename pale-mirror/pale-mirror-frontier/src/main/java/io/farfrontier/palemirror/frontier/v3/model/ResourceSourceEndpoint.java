package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Pickup location capability. A ground source has no fictitious container or permanent inventory slot. */
public sealed interface ResourceSourceEndpoint permits ShipmentEndpoint, ResourceSourceEndpoint.Ground {
    enum Kind { CONTAINER, GROUND }
    Kind sourceKind();
    SubjectId settlementId();
    SurfaceAnchor station();
    ResourceCustody custody();
    Optional<SubjectId> container();

    record Ground(SubjectId settlementId, ResourceCustody.Ground custody, SurfaceAnchor station, long epoch)
            implements ResourceSourceEndpoint {
        public Ground {
            Objects.requireNonNull(settlementId); Objects.requireNonNull(custody); Objects.requireNonNull(station);
            if (epoch < 1 || !io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN
                    .contains(station, custody.position()))
                throw new IllegalArgumentException("ground source requires current entity evidence and a reachable interaction station");
        }
        @Override public Kind sourceKind() { return Kind.GROUND; }
        @Override public Optional<SubjectId> container() { return Optional.empty(); }
    }
}
