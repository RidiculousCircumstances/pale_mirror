package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSite;
import java.util.*;

/** Site policy requests ordinary local stock transport; it does not own carriers, cargo or navigation. */
final class ExtractionHaulingPolicy {
    static Optional<InternalShipmentDispatched> offer(FrontierWorldState state, ExtractionSite site, ResidentProfile worker, long tick) {
        if (!site.settlementId().equals(worker.settlementId())) return Optional.empty();
        var sender = new ShipmentEndpoint.ExtractiveSite(site.settlementId(), site.id(), site.containerId(), site.layout().storagePort());
        var receiver = GoodsParticipantDeclarations.endpoint(FrontierWorldStateSupport.settlement(state.bootstrap(), site.settlementId()));
        return InternalShipmentPlanning.prepare(state, worker, sender, receiver,
                state.bootstrap().ruleset().extraction().source().coldOutput().getFirst().itemKind(),
                state.bootstrap().ruleset().extraction().haulBatch(), tick);
    }
    static boolean available(FrontierWorldState state, ResidentProfile worker, long tick) {
        return state.extractionSites().deposits().values().stream().filter(value -> value.site().settlementId().equals(worker.settlementId()))
                .anyMatch(value -> offer(state, value.site(), worker, tick).isPresent());
    }
    static List<ProposedEvent> dispatch(FrontierWorldState state, ExtractionSite site, long tick) {
        for (var worker : ResidentWorkComposition.SELECTION.eligible(state, site.settlementId(), ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS, tick)) {
            var offer = offer(state, site, worker, tick);
            if (offer.isEmpty()) continue;
            var event = offer.orElseThrow();
            InternalShipmentStateSupport.dispatch(state, site.settlementId(), event);
            return List.of(new ProposedEvent(site.settlementId(), event), new ProposedEvent(event.shipment().id(),
                    new ScheduleEffect.Created(ShipmentProcess.progress(event.shipment().id(), Math.addExact(tick, 1)))));
        }
        return List.of();
    }
    private ExtractionHaulingPolicy() { }
}
