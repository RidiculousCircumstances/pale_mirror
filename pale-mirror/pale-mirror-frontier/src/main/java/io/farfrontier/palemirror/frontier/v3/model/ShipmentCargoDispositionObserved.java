package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;

/** Positive pre-loot hand evidence and the witnessed outcome, never inferred from an absent body. */
public record ShipmentCargoDispositionObserved(SubjectId shipmentId, long expectedRevision,
        ActorActuationId identity, Outcome outcome, Optional<UUID> worldCarrier) implements FrontierPayload {
    public enum Outcome { WORLD_DROP, MISSING_BEFORE_LOOT }
    public ShipmentCargoDispositionObserved {
        Objects.requireNonNull(shipmentId); Objects.requireNonNull(identity); Objects.requireNonNull(outcome);
        worldCarrier = Objects.requireNonNull(worldCarrier);
        if (expectedRevision < 1 || (outcome == Outcome.WORLD_DROP) != worldCarrier.isPresent())
            throw new IllegalArgumentException("shipment cargo disposition lacks its exact outcome");
    }
    @Override public String type() { return "frontier.shipment_cargo_disposition_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
