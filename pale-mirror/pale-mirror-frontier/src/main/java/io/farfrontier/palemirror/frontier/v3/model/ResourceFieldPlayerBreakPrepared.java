package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.UUID;

/** WAL-backed permission for one imminent Vanilla crop removal, not a claim that it happened. */
public record ResourceFieldPlayerBreakPrepared(SubjectId siteId, long epoch, long layoutRevision, ResourceFieldLayout.CellId cellId,
                                               ResourceFieldPhysicalSurface.Condition before, UUID playerId,
                                               String actionId) implements FrontierPayload {
    public ResourceFieldPlayerBreakPrepared {
        Objects.requireNonNull(siteId, "prepared field break site");
        Objects.requireNonNull(cellId, "prepared field break cell");
        Objects.requireNonNull(before, "prepared field break predecessor");
        Objects.requireNonNull(playerId, "prepared field break player");
        Objects.requireNonNull(actionId, "prepared field break action");
        if (!siteId.value().startsWith("site:") || epoch < 1 || layoutRevision < 1 || actionId.isBlank()
                || before.soil() != ResourceFieldCycle.Soil.FARMLAND
                || before.crop() != ResourceFieldCycle.Crop.GROWING
                && before.crop() != ResourceFieldCycle.Crop.MATURE)
            throw new IllegalArgumentException("prepared field break lacks an exact owned live crop");
    }

    @Override public String type() { return "frontier.resource_field_player_break_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
