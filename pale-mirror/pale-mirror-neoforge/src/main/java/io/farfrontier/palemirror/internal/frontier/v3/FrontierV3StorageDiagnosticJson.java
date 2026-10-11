package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.stream.Collectors;

/** Read-only capability and terminal custody receipts, never a second stock counter. */
final class FrontierV3StorageDiagnosticJson {
    private FrontierV3StorageDiagnosticJson() { }
    static String render(FrontierWorldState state, ContainerRecord container) {
        if (container.storage().isEmpty()) return ",\"storage\":null";
        var policy = container.storage().orElseThrow();
        var deliveries = state.shipments().shipments().values().stream()
                .filter(shipment -> shipment.sender() instanceof ResourceSourceEndpoint.Ground
                        && shipment.receiver().containerId().equals(container.id()) && shipment.status() == Shipment.Status.DELIVERED)
                .sorted(java.util.Comparator.comparing(Shipment::id)).toList();
        var items = policy.filter().items().stream().sorted().map(item -> "\"" + FrontierV3DiagnosticJson.quote(item) + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        var receipts = deliveries.stream().map(shipment -> "{\"shipment\":\"" + FrontierV3DiagnosticJson.quote(shipment.id().value())
                + "\",\"sourceEntity\":\"" + ((ResourceSourceEndpoint.Ground) shipment.sender()).custody().entityId()
                + "\",\"itemKind\":\"" + FrontierV3DiagnosticJson.quote(shipment.itemKind()) + "\",\"remainingCargo\":"
                + shipment.lotQuantities().values().stream().mapToInt(Integer::intValue).sum() + "}").collect(Collectors.joining(",", "[", "]"));
        return ",\"storage\":{\"role\":\"" + policy.role() + "\",\"priority\":" + policy.priority()
                + ",\"allItems\":" + policy.filter().allItems() + ",\"items\":" + items
                + ",\"groundReceiptPresent\":" + !deliveries.isEmpty() + ",\"groundReceipts\":" + receipts + "}";
    }
}
