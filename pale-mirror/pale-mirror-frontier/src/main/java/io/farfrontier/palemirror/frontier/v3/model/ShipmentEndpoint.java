package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Logistics retains a declared container and supported interaction station, not a hive alias. */
public sealed interface ShipmentEndpoint extends ResourceSourceEndpoint permits ShipmentEndpoint.Depot, ShipmentEndpoint.ExtractiveSite, ShipmentEndpoint.Storage, ShipmentEndpoint.ProductionPort {
    enum Kind { SETTLEMENT_DEPOT, EXTRACTIVE_SITE, DECLARED_STORAGE, PRODUCTION_PORT }
    Kind kind();
    SubjectId settlementId();
    SubjectId containerId();
    SurfaceAnchor station();
    default ActorContainerItemOrder.ContainerEndpoint receivingPort() {
        return new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(containerId());
    }
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
    /** Producer-declared machine port, not an ordinary free-slot warehouse. */
    record ProductionPort(SubjectId settlementId, ProductionStationSpec spec,
                          ActorContainerItemOrder.StationPort port, SurfaceAnchor exteriorApproach) implements ShipmentEndpoint {
        public ProductionPort {
            Objects.requireNonNull(settlementId); Objects.requireNonNull(spec); Objects.requireNonNull(port);
            Objects.requireNonNull(exteriorApproach);
            if (port == ActorContainerItemOrder.StationPort.OUTPUT)
                throw new IllegalArgumentException("logistics cannot supply a machine output port");
        }
        @Override public Kind kind() { return Kind.PRODUCTION_PORT; }
        @Override public SubjectId containerId() { return spec.containerId(); }
        @Override public SurfaceAnchor station() { return spec.workerStation(); }
        @Override public ActorContainerItemOrder.ContainerEndpoint receivingPort() {
            return new ActorContainerItemOrder.ContainerEndpoint.FungibleStation(spec, port);
        }
    }
}
