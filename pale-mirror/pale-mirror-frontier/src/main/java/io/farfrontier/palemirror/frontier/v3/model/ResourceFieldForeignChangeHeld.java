package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One site-local world edit held before its physical postcondition is known. */
public record ResourceFieldForeignChangeHeld(SubjectId siteId, long epoch, long layoutRevision,
                                             ResourceFieldLayout.CellId cellId,
                                             ResourceFieldCycle.CellState before,
                                             String causationId) implements FrontierPayload {
    public ResourceFieldForeignChangeHeld {
        Objects.requireNonNull(siteId, "foreign field site");
        Objects.requireNonNull(cellId, "foreign field cell");
        Objects.requireNonNull(before, "foreign field canonical predecessor");
        Objects.requireNonNull(causationId, "foreign field cause");
        if (!siteId.value().startsWith("site:") || epoch < 1 || layoutRevision < 1
                || causationId.isBlank() || before.soil() == ResourceFieldCycle.Soil.UNKNOWN
                || before.crop() == ResourceFieldCycle.Crop.UNKNOWN)
            throw new IllegalArgumentException("foreign field hold lacks an exact known predecessor");
    }

    @Override public String type() { return "frontier.resource_field_foreign_change_held"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
