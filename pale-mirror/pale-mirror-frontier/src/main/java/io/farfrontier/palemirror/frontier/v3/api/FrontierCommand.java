package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;
import java.util.Optional;

/** Immutable intent validated against exactly one canonical revision. */
public record FrontierCommand(
        int schemaVersion,
        CommandId id,
        WorldId worldId,
        Revision expectedRevision,
        SimInstant submittedAt,
        SubjectId actor,
        CauseChain causes,
        FrontierPayload payload,
        Optional<EngineScheduleBinding> scheduleBinding
) {
    public static final int LEGACY_SCHEMA_VERSION = 1;
    public static final int SCHEMA_VERSION = 2;

    /** Compatibility constructor for persisted v1 command envelopes without a schedule binding. */
    public FrontierCommand(int schemaVersion, CommandId id, WorldId worldId, Revision expectedRevision,
                           SimInstant submittedAt, SubjectId actor, CauseChain causes, FrontierPayload payload) {
        this(schemaVersion, id, worldId, expectedRevision, submittedAt, actor, causes, payload, Optional.empty());
    }

    public FrontierCommand {
        if (schemaVersion != LEGACY_SCHEMA_VERSION && schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported command schema version: " + schemaVersion);
        }
        Objects.requireNonNull(id, "command id");
        Objects.requireNonNull(worldId, "world id");
        Objects.requireNonNull(expectedRevision, "expected revision");
        Objects.requireNonNull(submittedAt, "submitted at");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(causes, "causes");
        Objects.requireNonNull(payload, "payload");
        scheduleBinding = Optional.ofNullable(scheduleBinding).orElseThrow(() -> new NullPointerException("schedule binding"));
        if (schemaVersion == LEGACY_SCHEMA_VERSION && scheduleBinding.isPresent()) {
            throw new IllegalArgumentException("legacy command cannot carry a schedule binding");
        }
    }
}
