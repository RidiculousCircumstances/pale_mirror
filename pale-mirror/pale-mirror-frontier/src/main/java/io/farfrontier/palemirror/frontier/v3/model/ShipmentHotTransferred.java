package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
public record ShipmentHotTransferred(SubjectId shipmentId, ShipmentPhysicalStep step,
        List<FungiblePhysicalObservation.Stack> remainingSource, List<FungiblePhysicalObservation.Stack> destination) implements FrontierPayload {
    public ShipmentHotTransferred {
        Objects.requireNonNull(shipmentId); Objects.requireNonNull(step);
        remainingSource = List.copyOf(remainingSource); destination = List.copyOf(destination);
        if (remainingSource.size() > 54 || destination.size() > 54)
            throw new IllegalArgumentException("shipment transfer witness exceeds its bounded endpoints");
    }
    @Override public String type() { return "frontier.shipment_hot_transferred"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
