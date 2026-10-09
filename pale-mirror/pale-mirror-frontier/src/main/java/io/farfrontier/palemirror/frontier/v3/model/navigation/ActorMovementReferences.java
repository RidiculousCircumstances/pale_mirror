package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState;
import java.util.Objects;

/** Immutable aggregate inputs during hydration; never an alternative movement authority. */
public record ActorMovementReferences(HumanPopulation people, ExactInventory inventory, ShipmentState shipments,
                                     UnitGroupState groups, ExtractionSiteState extractionSites) {
    public ActorMovementReferences {
        Objects.requireNonNull(people); Objects.requireNonNull(inventory); Objects.requireNonNull(shipments);
        Objects.requireNonNull(groups); Objects.requireNonNull(extractionSites);
    }
}
