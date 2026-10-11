package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Logistics retains a declared container and supported interaction station, not a hive alias. */
public sealed interface ShipmentEndpoint extends ResourceSourceEndpoint permits ShipmentEndpoint.Depot, ShipmentEndpoint.ExtractiveSite, ShipmentEndpoint.Storage {
    enum Kind { SETTLEMENT_DEPOT, EXTRACTIVE_SITE, DECLARED_STORAGE }
    Kind kind();
    SubjectId settlementId();
    SubjectId containerId();
    SurfaceAnchor station();
    @Override default ResourceSourceEndpoint.Kind sourceKind() { return ResourceSourceEndpoint.Kind.CONTAINER; }
    @Override default ResourceCustody custody() { return new ResourceCustody.Container(containerId()); }
    @Override default java.util.Optional<SubjectId> container() { return java.util.Optional.of(containerId()); }
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
    /** User/planner declared storage. Access geometry is explicit, not inferred from container purpose. */
    record Storage(SubjectId settlementId, SubjectId containerId, SurfaceAnchor station,
                   java.util.Optional<SubjectId> facilityId, SurfaceAnchor exteriorApproach) implements ShipmentEndpoint {
        public Storage {
            Objects.requireNonNull(settlementId); Objects.requireNonNull(containerId); Objects.requireNonNull(station);
            facilityId = Objects.requireNonNull(facilityId); Objects.requireNonNull(exteriorApproach);
        }
        @Override public Kind kind() { return Kind.DECLARED_STORAGE; }
    }
}
