package io.farfrontier.palemirror.frontier.v3.model;


import java.util.Objects;

/** Retires only the held foreign cause after the exact physical claim was updated. */
public record ResourceFieldForeignChangeAcknowledged(ResourceFieldForeignChangeHeld hold,
                                                     ResourceFieldCycle.CellState physical)
        implements CellMutationReceipt {
    public ResourceFieldForeignChangeAcknowledged {
        Objects.requireNonNull(hold, "acknowledged foreign field cause");
        Objects.requireNonNull(physical, "acknowledged foreign field condition");
        if (physical.soil() == ResourceFieldCycle.Soil.UNKNOWN
                || physical.crop() == ResourceFieldCycle.Crop.UNKNOWN)
            throw new IllegalArgumentException("foreign field acknowledgement needs a known physical result");
    }

    @Override public CellMutationKey mutationKey() { return ResourceSiteState.mutationKey(hold.siteId(), hold.cellId()); }
    @Override public String mutationCause() { return hold.causationId(); }
    @Override public String type() { return "frontier.resource_field_foreign_change_acknowledged"; }
}
