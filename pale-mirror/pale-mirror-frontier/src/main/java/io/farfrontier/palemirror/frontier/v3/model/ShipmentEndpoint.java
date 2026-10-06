package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Logistics retains a declared container and supported interaction station, not a hive alias. */
public record ShipmentEndpoint(Kind kind, SubjectId settlementId, SubjectId facilityId, SubjectId containerId, SurfaceAnchor station) {
    public enum Kind { SETTLEMENT_DEPOT }
    public ShipmentEndpoint {
        Objects.requireNonNull(kind); Objects.requireNonNull(settlementId); Objects.requireNonNull(facilityId);
        Objects.requireNonNull(containerId); Objects.requireNonNull(station);
    }
}
