package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/**
 * Versioned physical item movement in one exact operation or field-post cargo
 * container.
 *
 * <p>One Minecraft item is one sixty-fourth of a canonical source unit. The
 * cargo ID is taken from the immutable snapshot; this adapter event never
 * chooses an owner, resource, or conversion independently.</p>
 */
public record ReferenceGrayboxCargoObservation(
        int version,
        String eventId,
        String observedStateRevision,
        String cargoId,
        ReferenceResource resource,
        int itemDelta
) {
    public static final int VERSION = 1;
    public static final int ITEMS_PER_SOURCE_UNIT = 64;

    public ReferenceGrayboxCargoObservation {
        if (version != VERSION) throw new IllegalArgumentException("unsupported graybox cargo observation version: " + version);
        eventId = required(eventId, "eventId", 160);
        observedStateRevision = required(observedStateRevision, "observedStateRevision", 64);
        if (!observedStateRevision.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("graybox cargo observation revision is invalid");
        }
        cargoId = required(cargoId, "cargoId", 160);
        resource = Objects.requireNonNull(resource, "resource");
        if (itemDelta == 0 || Math.abs((long) itemDelta) > 65_536L) {
            throw new IllegalArgumentException("graybox cargo item delta is invalid");
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
