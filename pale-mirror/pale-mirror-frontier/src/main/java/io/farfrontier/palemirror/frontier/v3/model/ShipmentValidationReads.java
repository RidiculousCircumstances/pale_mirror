package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.expedition.TransportAsset;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableReferenceIndex.Source;
import java.util.ArrayList;
import java.util.List;

/** Nominal immutable read sources, not shipment or commercial permission. Each
 * registered port declares the exact addresses its own validation consumes. */
final class ShipmentValidationReads {
    static final Source<FrontierWorldState, SubjectId, CustodyAccount> ACCOUNTS =
            new Source<>(state -> state.inventory().fungibleResources().accounts());
    static final Source<FrontierWorldState, SubjectId, ExactItemStack> ITEMS =
            new Source<>(state -> state.inventory().items());
    static final Source<FrontierWorldState, SubjectId, ClaimAllocation> CLAIMS =
            new Source<>(state -> state.inventory().fungibleResources().claims());
    static final Source<FrontierWorldState, SubjectId, GoodsTradeContract> CONTRACTS =
            new Source<>(state -> state.companies().goodsTrade().contracts());
    static final Source<FrontierWorldState, SubjectId, GoodsParticipant> PARTICIPANTS =
            new Source<>(state -> state.companies().goodsTrade().participants().participants());
    static final Source<FrontierWorldState, SubjectId, TransportMission> MISSIONS =
            new Source<>(state -> state.shipments().missions());
    static final Source<FrontierWorldState, SubjectId, TransportAsset> ASSETS =
            new Source<>(state -> state.transportFleet().assets());
    static final List<Source<FrontierWorldState, ?, ?>> SOURCES;
    static {
        var sources = new ArrayList<Source<FrontierWorldState, ?, ?>>(ShipmentEndpointReferences.SOURCES);
        sources.addAll(List.of(ACCOUNTS, ITEMS, CLAIMS, CONTRACTS, PARTICIPANTS, MISSIONS, ASSETS));
        SOURCES = List.copyOf(sources);
    }
    private ShipmentValidationReads() { }
}
