package io.farfrontier.palemirror.frontier.v3.model;


import java.util.Objects;

/** Actual loaded-cell result for a held foreign edit; exact block NBT remains in SavedData. */
public record ResourceFieldForeignCellObserved(ResourceFieldForeignChangeHeld hold,
                                               ResourceFieldCycle.CellState after,
                                               String soilBlockName, String cropBlockName)
        implements CellMutationReceipt {
    public ResourceFieldForeignCellObserved {
        Objects.requireNonNull(hold, "foreign field held cause");
        Objects.requireNonNull(after, "foreign field canonical postcondition");
        Objects.requireNonNull(soilBlockName, "foreign field soil block");
        Objects.requireNonNull(cropBlockName, "foreign field crop block");
        if (soilBlockName.isBlank() || cropBlockName.isBlank()
                || soilBlockName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 255
                || cropBlockName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 255
                || after.accounted() != hold.before().accounted()
                || after.yielded() != hold.before().yielded()
                || after.soil() == ResourceFieldCycle.Soil.UNKNOWN
                || after.crop() == ResourceFieldCycle.Crop.UNKNOWN)
            throw new IllegalArgumentException("foreign field result lacks an exact local postcondition");
        if (after.soil() == ResourceFieldCycle.Soil.FARMLAND && !soilBlockName.equals("minecraft:farmland")
                || after.soil() == ResourceFieldCycle.Soil.DIRT && !soilBlockName.equals("minecraft:dirt")
                || after.soil() == ResourceFieldCycle.Soil.OBSTRUCTED
                && (soilBlockName.equals("minecraft:farmland") || soilBlockName.equals("minecraft:dirt"))
                || after.crop() == ResourceFieldCycle.Crop.ABSENT && !cropBlockName.equals("minecraft:air")
                || (after.crop() == ResourceFieldCycle.Crop.GROWING || after.crop() == ResourceFieldCycle.Crop.MATURE)
                && !cropBlockName.equals("minecraft:wheat")
                || after.crop() == ResourceFieldCycle.Crop.OBSTRUCTED
                && after.soil() != ResourceFieldCycle.Soil.OBSTRUCTED
                && (cropBlockName.equals("minecraft:air")
                    || after.soil() == ResourceFieldCycle.Soil.FARMLAND
                    && cropBlockName.equals("minecraft:wheat")))
            throw new IllegalArgumentException("foreign field block names disagree with the observed cell category");
    }

    @Override public CellMutationKey mutationKey() { return ResourceSiteState.mutationKey(hold.siteId(), hold.cellId()); }
    @Override public String mutationCause() { return hold.causationId(); }
    @Override public String type() { return "frontier.resource_field_foreign_cell_observed"; }
}
