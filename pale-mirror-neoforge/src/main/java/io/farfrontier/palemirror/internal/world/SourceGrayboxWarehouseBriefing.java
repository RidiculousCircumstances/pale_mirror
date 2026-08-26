package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.Locale;
import java.util.Optional;

/** Read-only player wording for the physical settlement warehouse boundary. */
final class SourceGrayboxWarehouseBriefing {
    private SourceGrayboxWarehouseBriefing() { }

    static String label(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.Warehouse warehouse) {
        return "[WAREHOUSE] " + settlementName(snapshot, warehouse.settlementId())
                + "\n9 resource shelves — 64 items = 1 unit"
                + "\nOpen barrels to supply or withdraw.";
    }

    static Optional<String> at(ReferenceGrayboxSnapshot snapshot, int x, int z) {
        return snapshot.warehouses().stream().filter(value -> contains(value.rectangle(), x, z))
                .findFirst().map(value -> brief(snapshot, value));
    }

    static Optional<String> forIdentity(ReferenceGrayboxSnapshot snapshot, String id) {
        if (!id.startsWith("warehouse:")) return Optional.empty();
        String subject = id.substring("warehouse:".length());
        return snapshot.warehouses().stream().filter(value -> value.id().equals(subject)).findFirst().map(value -> brief(snapshot, value));
    }

    private static String brief(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.Warehouse warehouse) {
        String stock = warehouse.stockpiles().stream().map(value -> words(value.resource()) + '=' + number(value.quantity()))
                .collect(java.util.stream.Collectors.joining(" | "));
        return "Warehouse of " + settlementName(snapshot, warehouse.settlementId())
                + "\nState: " + stock + "."
                + "\nRule: every matching Minecraft item is 1/64 source unit; deposits and withdrawals update this settlement immediately."
                + "\nNext: use only the labelled resource barrels; a blocked or destroyed barrel stays a visible conflict.";
    }

    private static String settlementName(ReferenceGrayboxSnapshot snapshot, int id) {
        return snapshot.settlements().stream().filter(value -> value.id() == id).map(ReferenceGrayboxSnapshot.Settlement::name)
                .findFirst().orElse("settlement #" + id);
    }

    private static boolean contains(ReferenceGrayboxLayout.Rectangle rectangle, int x, int z) {
        return x >= rectangle.x() && x < rectangle.x() + rectangle.width() && z >= rectangle.z() && z < rectangle.z() + rectangle.depth();
    }

    private static String words(String value) { return value.replace('_', ' '); }
    private static String number(double value) { return String.format(Locale.ROOT, "%.2f", value); }
}
