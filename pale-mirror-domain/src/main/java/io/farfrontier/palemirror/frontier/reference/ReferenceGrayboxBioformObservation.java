package io.farfrontier.palemirror.frontier.reference;

/** Versioned fact that one managed graybox Zombie was killed. */
public record ReferenceGrayboxBioformObservation(
        int version,
        String eventId,
        String observedStateRevision,
        String bioformId
) {
    public static final int VERSION = 1;

    public ReferenceGrayboxBioformObservation {
        if (version != VERSION) throw new IllegalArgumentException("unsupported graybox bioform observation version: " + version);
        eventId = required(eventId, "eventId");
        observedStateRevision = required(observedStateRevision, "observedStateRevision");
        bioformId = required(bioformId, "bioformId");
        if (eventId.length() > 128) throw new IllegalArgumentException("graybox bioform observation event ID is too long");
        if (!observedStateRevision.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("graybox bioform observation revision is invalid");
        if (!bioformId.startsWith("bioform:")) throw new IllegalArgumentException("graybox bioform observation target is invalid");
    }

    public static ReferenceGrayboxBioformObservation killed(String eventId, String revision, String bioformId) {
        return new ReferenceGrayboxBioformObservation(VERSION, eventId, revision, bioformId);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
