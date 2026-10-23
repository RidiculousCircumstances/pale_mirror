package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** One durable before-effect declaration over the exact physical body and resource preimage. */
public record ShipmentPhysicalStep(Shipment.Status status, long shipmentRevision, ActorHotObservation observation,
        List<MaterialSourceSelection.Slice> source, int destinationSlot, long destinationEpoch,
        Map<SubjectId, Integer> lotQuantities, int destinationBefore, java.util.Optional<IndividualItemTransferWitness> individual) {
    public ShipmentPhysicalStep(Shipment.Status status, long revision, ActorHotObservation observation,
            List<MaterialSourceSelection.Slice> source, int slot, long epoch, Map<SubjectId, Integer> lots, int before) {
        this(status, revision, observation, source, slot, epoch, lots, before, java.util.Optional.empty());
    }
    public ShipmentPhysicalStep {
        Objects.requireNonNull(status); Objects.requireNonNull(observation); source = List.copyOf(source);
        lotQuantities = Map.copyOf(lotQuantities); Objects.requireNonNull(individual);
        boolean exact = individual.isPresent();
        int moved = exact ? 1 : source.stream().mapToInt(MaterialSourceSelection.Slice::moved).sum();
        if (exact && (!source.isEmpty() || !lotQuantities.equals(Map.of(individual.orElseThrow().item().id(), 1)) || destinationBefore != 0))
            throw new IllegalArgumentException("individual transfer cannot retain fungible layout evidence");
        if (status != Shipment.Status.AWAITING_LOAD && status != Shipment.Status.CARRYING && status != Shipment.Status.RETURNING
                || shipmentRevision < 1 || !exact && source.isEmpty() || source.size() > 54 || destinationEpoch < 1
                || source.stream().map(MaterialSourceSelection.Slice::address).distinct().count() != source.size()
                || !exact && source.stream().map(MaterialSourceSelection.Slice::epoch).distinct().count() != 1
                || source.stream().mapToInt(MaterialSourceSelection.Slice::moved).sum() > 64
                || lotQuantities.isEmpty() || lotQuantities.size() > 64
                || lotQuantities.values().stream().anyMatch(q -> q == null || q < 1 || q > 64)
                || moved != lotQuantities.values().stream().mapToInt(Integer::intValue).sum()
                || destinationBefore < 0 || destinationBefore + moved > 64
                || destinationSlot < -1 || destinationSlot > 53
                || destinationSlot == -1 && destinationBefore != 0
                || (status == Shipment.Status.CARRYING || status == Shipment.Status.RETURNING) && (destinationSlot < 0 || destinationSlot > 53))
            throw new IllegalArgumentException("shipment physical step lacks bounded exact pre-effect evidence");
    }
    public long sourceEpoch() { return individual.map(IndividualItemTransferWitness::sourceEpoch).orElseGet(() -> source.getFirst().epoch()); }
}
