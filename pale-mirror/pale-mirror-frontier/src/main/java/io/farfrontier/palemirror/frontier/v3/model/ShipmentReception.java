package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Objects;
import java.nio.charset.StandardCharsets;

/** Immutable evidence of one unloaded portion, not another stock account or a sale. */
public record ShipmentReception(SubjectId id, SubjectId claimId, Map<SubjectId, Integer> lotQuantities) {
    public ShipmentReception {
        Objects.requireNonNull(id); Objects.requireNonNull(claimId); lotQuantities = Map.copyOf(lotQuantities);
        if (lotQuantities.isEmpty() || lotQuantities.size() > 64
                || lotQuantities.values().stream().anyMatch(q -> q == null || q < 1 || q > 64)
                || lotQuantities.values().stream().mapToInt(Integer::intValue).sum() > 64)
            throw new IllegalArgumentException("reception needs one exact bounded unloaded portion");
    }
    public static ShipmentReception unloaded(Shipment shipment, Map<SubjectId, Integer> lots) {
        var token = java.util.UUID.nameUUIDFromBytes((shipment.id().value() + "\n" + shipment.revision()).getBytes(StandardCharsets.UTF_8));
        return new ShipmentReception(new SubjectId("receipt:shipment-" + token),
                lots.equals(shipment.lotQuantities()) ? shipment.authorization().claimId() : new SubjectId("claim:shipment-" + token), lots);
    }
    public int quantity() { return lotQuantities.values().stream().mapToInt(Integer::intValue).sum(); }
}
