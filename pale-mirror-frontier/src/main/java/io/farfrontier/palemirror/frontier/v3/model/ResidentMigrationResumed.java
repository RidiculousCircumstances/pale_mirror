package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable reopening of the same blocked journey after its exact preconditions return. */
public record ResidentMigrationResumed(SubjectId residentId) implements FrontierPayload {
    public ResidentMigrationResumed { Objects.requireNonNull(residentId, "migration resident"); }
    @Override public String type() { return "frontier.resident_migration_resumed"; }
}
