package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Logistics retains a declared container and supported interaction station, not a hive alias. */
public sealed interface ShipmentEndpoint permits ShipmentEndpoint.Depot, ShipmentEndpoint.ExtractiveSite {
    enum Kind { SETTLEMENT_DEPOT, EXTRACTIVE_SITE }
    Kind kind();
    SubjectId settlementId();
    SubjectId containerId();
    SurfaceAnchor station();
    record Depot(SubjectId settlementId, SubjectId facilityId, SubjectId containerId, SurfaceAnchor station) implements ShipmentEndpoint {
        public Depot {
            Objects.requireNonNull(settlementId); Objects.requireNonNull(facilityId);
            Objects.requireNonNull(containerId); Objects.requireNonNull(station);
        }
        @Override public Kind kind() { return Kind.SETTLEMENT_DEPOT; }
    }
    record ExtractiveSite(SubjectId settlementId, SubjectId siteId, SubjectId containerId, SurfaceAnchor station) implements ShipmentEndpoint {
        public ExtractiveSite {
            Objects.requireNonNull(settlementId); Objects.requireNonNull(siteId);
            Objects.requireNonNull(containerId); Objects.requireNonNull(station);
        }
        @Override public Kind kind() { return Kind.EXTRACTIVE_SITE; }
    }
}
