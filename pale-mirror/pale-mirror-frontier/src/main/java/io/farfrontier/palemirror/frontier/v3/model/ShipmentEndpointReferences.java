package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableReferenceIndex.Source;
import java.util.List;

/** Nominal endpoint read sources. Registered ports alone declare their dependencies. */
final class ShipmentEndpointReferences {
    static final Source<FrontierWorldState, SubjectId, ContainerRecord> CONTAINERS =
            new Source<>(state -> state.inventory().containers());
    static final Source<FrontierWorldState, SubjectId, ContainerSurface> SURFACES =
            new Source<>(state -> state.inventory().surfaces());
    static final Source<FrontierWorldState, SubjectId, ExtractionDeposit> DEPOSITS =
            new Source<>(state -> state.extractionSites().deposits());
    static final List<Source<FrontierWorldState, ?, ?>> SOURCES = List.of(CONTAINERS, SURFACES, DEPOSITS);
    private ShipmentEndpointReferences() { }
}
