package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** One durable before-effect declaration over the exact physical body and resource preimage. */
public record ShipmentPhysicalStep(Shipment.Status status, long shipmentRevision, ActorHotObservation observation,
        List<MaterialSourceSelection.Slice> source, int destinationSlot, long destinationEpoch,
        Map<SubjectId, Integer> lotQuantities, int destinationBefore) {
    public ShipmentPhysicalStep {
        Objects.requireNonNull(status); Objects.requireNonNull(observation); source = List.copyOf(source);
        lotQuantities = Map.copyOf(lotQuantities);
        if (status != Shipment.Status.AWAITING_LOAD && status != Shipment.Status.CARRYING
                || shipmentRevision < 1 || source.isEmpty() || source.size() > 27 || destinationEpoch < 1
                || source.stream().map(MaterialSourceSelection.Slice::address).distinct().count() != source.size()
                || source.stream().map(MaterialSourceSelection.Slice::epoch).distinct().count() != 1
                || source.stream().mapToInt(MaterialSourceSelection.Slice::moved).sum() > 64
                || lotQuantities.isEmpty() || lotQuantities.size() > 64
                || lotQuantities.values().stream().anyMatch(q -> q == null || q < 1 || q > 64)
                || source.stream().mapToInt(MaterialSourceSelection.Slice::moved).sum() != lotQuantities.values().stream().mapToInt(Integer::intValue).sum()
                || destinationBefore < 0 || destinationBefore + source.stream().mapToInt(MaterialSourceSelection.Slice::moved).sum() > 64
                || destinationSlot < -1 || destinationSlot > 26
                || destinationSlot == -1 && destinationBefore != 0
                || status == Shipment.Status.CARRYING && (destinationSlot < 0 || destinationSlot > 26))
            throw new IllegalArgumentException("shipment physical step lacks bounded exact pre-effect evidence");
    }
    public long sourceEpoch() { return source.getFirst().epoch(); }
}
