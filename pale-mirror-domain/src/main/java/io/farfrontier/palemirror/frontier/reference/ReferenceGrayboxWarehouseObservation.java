package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/**
 * Versioned physical movement observed in one registered settlement warehouse.
 *
 * <p>One Minecraft item is exactly one sixty-fourth of a canonical source
 * unit. A positive delta is a deposit; a negative delta is a withdrawal. The
 * physical adapter has no authority to choose another settlement, resource or
 * conversion scale.</p>
 */
public record ReferenceGrayboxWarehouseObservation(
        int version,
        String eventId,
        String observedStateRevision,
        int settlementId,
        ReferenceResource resource,
        int itemDelta
) {
    public static final int VERSION = 1;
    public static final int ITEMS_PER_SOURCE_UNIT = 64;

    public ReferenceGrayboxWarehouseObservation {
        if (version != VERSION) throw new IllegalArgumentException("unsupported graybox warehouse observation version: " + version);
        eventId = required(eventId, "eventId", 128);
        observedStateRevision = required(observedStateRevision, "observedStateRevision", 64);
        if (!observedStateRevision.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("graybox warehouse observation revision is invalid");
        }
        if (settlementId < 1) throw new IllegalArgumentException("graybox warehouse settlement ID is invalid");
        resource = Objects.requireNonNull(resource, "resource");
        if (itemDelta == 0 || Math.abs((long) itemDelta) > 65_536L) {
            throw new IllegalArgumentException("graybox warehouse item delta is invalid");
        }
    }

    public double sourceQuantity() {
        return (double) itemDelta / ITEMS_PER_SOURCE_UNIT;
    }

    private static String required(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
