package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One declared exact postcondition; the physical adapter must observe it after the action. */
public record ResourceFieldCellObserved(SubjectId siteId, long epoch, long layoutRevision, ResourceFieldLayout.CellId cellId,
                                        ResourceFieldPhysicalSurface.Condition before,
                                        ResourceFieldPhysicalSurface.Condition after,
                                        Change change, Source source, String causationId) implements FrontierPayload {
    public enum Change { CROP_REMOVED, SOIL_BECAME_DIRT, UNCHANGED, CROP_REPLANTED }
    public enum Source { PLAYER, WORLD }

    public ResourceFieldCellObserved {
        Objects.requireNonNull(siteId, "field observation site");
        Objects.requireNonNull(cellId, "field observation cell");
        Objects.requireNonNull(before, "field observation predecessor");
        Objects.requireNonNull(after, "field observation postcondition");
        Objects.requireNonNull(change, "field observation change");
        Objects.requireNonNull(source, "field observation source");
        Objects.requireNonNull(causationId, "field observation cause");
        if (!siteId.value().startsWith("site:") || epoch < 1 || layoutRevision < 1 || causationId.isBlank())
            throw new IllegalArgumentException("field observation has invalid owner, epoch, revision or cause");
        ResourceFieldPhysicalSurface.Condition required = switch (change) {
            case CROP_REPLANTED -> {
                if (source != Source.WORLD || before.soil() != ResourceFieldCycle.Soil.FARMLAND
                        || before.crop() != ResourceFieldCycle.Crop.GROWING && before.crop() != ResourceFieldCycle.Crop.MATURE
                        || before.growthStage() <= 0)
                    throw new IllegalArgumentException("external replant has no older owned live crop predecessor");
                yield new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                        ResourceFieldCycle.Crop.GROWING, 0);
            }
            case CROP_REMOVED -> {
                if (before.soil() != ResourceFieldCycle.Soil.FARMLAND
                        || before.crop() != ResourceFieldCycle.Crop.GROWING
                        && before.crop() != ResourceFieldCycle.Crop.MATURE)
                    throw new IllegalArgumentException("crop loss has no owned live crop predecessor");
                yield new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                        ResourceFieldCycle.Crop.ABSENT, 0);
            }
            case SOIL_BECAME_DIRT -> {
                if (before.soil() != ResourceFieldCycle.Soil.FARMLAND)
                    throw new IllegalArgumentException("soil damage has no owned farmland predecessor");
                yield new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.DIRT,
                        ResourceFieldCycle.Crop.ABSENT, 0);
            }
            case UNCHANGED -> {
                if (source != Source.PLAYER)
                    throw new IllegalArgumentException("unchanged field observation needs a pending player action");
                yield before;
            }
        };
        if (!after.equals(required)) throw new IllegalArgumentException("field observation does not prove its exact changed postcondition");
    }

    @Override public String type() { return "frontier.resource_field_cell_observed"; }
}
