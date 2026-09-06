package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable admission of one exact resident's background journey. */
public record ResidentMigrationStarted(ResidentMigrationJourney journey) implements FrontierPayload {
    public ResidentMigrationStarted { Objects.requireNonNull(journey, "migration journey"); }
    @Override public String type() { return "frontier.resident_migration_started"; }
}
