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
        if (after.soil().cultivable() && !soilBlockName.equals(after.soil().blockKind())
                || after.soil() == ResourceFieldCycle.Soil.OBSTRUCTED && ResourceFieldCycle.Soil.fromBlock(soilBlockName) != null
                || after.crop() == ResourceFieldCycle.Crop.ABSENT && !cropBlockName.equals("minecraft:air")
                || (after.crop() == ResourceFieldCycle.Crop.GROWING || after.crop() == ResourceFieldCycle.Crop.MATURE)
                && ResourceSiteKind.forCropBlock(cropBlockName).isEmpty()
                || after.crop() == ResourceFieldCycle.Crop.OBSTRUCTED
                && after.soil() != ResourceFieldCycle.Soil.OBSTRUCTED
                && cropBlockName.equals("minecraft:air"))
            throw new IllegalArgumentException("foreign field block names disagree with the observed cell category");
    }

    public void requireCrop(ResourceSiteKind kind) {
        boolean expected = cropBlockName.equals(kind.crop().blockKind());
        if ((after.crop() == ResourceFieldCycle.Crop.GROWING || after.crop() == ResourceFieldCycle.Crop.MATURE) && !expected
                || after.crop() == ResourceFieldCycle.Crop.OBSTRUCTED && after.soil() == ResourceFieldCycle.Soil.FARMLAND && expected)
            throw new IllegalArgumentException("observed plant disagrees with the exact field crop");
    }

    @Override public CellMutationKey mutationKey() { return ResourceSiteState.mutationKey(hold.siteId(), hold.cellId()); }
    @Override public String mutationCause() { return hold.causationId(); }
    @Override public String type() { return "frontier.resource_field_foreign_cell_observed"; }
}
