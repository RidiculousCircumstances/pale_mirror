package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import java.util.function.BiFunction;

/** Composition connects declared authorization owners to independent recipient acknowledgement. */
final class ShipmentDeliveryNotifications {
    private static final Map<ResourceClaimDelegation.Kind, BiFunction<Shipment, Long, List<ProposedEvent>>> PORTS = Map.of(
            ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT,
            (shipment, tick) -> List.of(GoodsTradeReceiptProcess.wake(shipment.authorization().claimantId(), shipment.reception().orElseThrow().id(), tick)));
    static {
        if (!PORTS.keySet().equals(EnumSet.allOf(ResourceClaimDelegation.Kind.class)))
            throw new IllegalArgumentException("missing shipment receiver-notification owner");
    }
    static List<ProposedEvent> delivered(Shipment shipment, long atTick) {
        if (shipment.reception().isEmpty()) throw new IllegalArgumentException("unloaded notification has no exact delivery");
        var port = PORTS.get(shipment.authorization().kind());
        if (port == null) throw new IllegalArgumentException("unknown shipment receiver-notification owner");
        return port.apply(shipment, atTick);
    }
    private ShipmentDeliveryNotifications() { }
}
