package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable refusal to advance a journey; it never rewrites the resident's current location. */
public record ResidentMigrationBlocked(SubjectId residentId, ResidentMigrationBlockReason reason) implements FrontierPayload {
    public ResidentMigrationBlocked { Objects.requireNonNull(residentId, "migration resident"); Objects.requireNonNull(reason, "migration block reason"); }
    @Override public String type() { return "frontier.resident_migration_blocked"; }
}
