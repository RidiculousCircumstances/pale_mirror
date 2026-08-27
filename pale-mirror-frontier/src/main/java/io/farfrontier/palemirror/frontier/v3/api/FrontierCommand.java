package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;

/** Immutable intent validated against exactly one canonical revision. */
public record FrontierCommand(
        int schemaVersion,
        CommandId id,
        WorldId worldId,
        Revision expectedRevision,
        SimInstant submittedAt,
        SubjectId actor,
        CauseChain causes,
        FrontierPayload payload
) {
    public static final int SCHEMA_VERSION = 1;

    public FrontierCommand {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported command schema version: " + schemaVersion);
        }
        Objects.requireNonNull(id, "command id");
        Objects.requireNonNull(worldId, "world id");
        Objects.requireNonNull(expectedRevision, "expected revision");
        Objects.requireNonNull(submittedAt, "submitted at");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(causes, "causes");
        Objects.requireNonNull(payload, "payload");
    }
}
