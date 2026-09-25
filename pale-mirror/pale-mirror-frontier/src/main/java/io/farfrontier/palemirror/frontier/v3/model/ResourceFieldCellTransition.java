package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Exact, bounded one-cell physical effect; every committed prefix is a valid cell. */
public record ResourceFieldCellTransition(SubjectId siteId, long epoch, long layoutRevision, ResourceFieldLayout.CellId cellId,
                                          ResourceFieldPhysicalSurface.Condition before,
                                          ResourceFieldPhysicalSurface.Condition after,
                                          List<Step> steps) {
    public enum Part { SOIL, CROP }

    public record Step(Part part, ResourceFieldPhysicalSurface.Condition before,
                       ResourceFieldPhysicalSurface.Condition after) {
        public Step {
            Objects.requireNonNull(part, "field projection part");
            Objects.requireNonNull(before, "field projection predecessor");
            Objects.requireNonNull(after, "field projection successor");
            if (before.equals(after) || (part == Part.SOIL
                    ? before.crop() != after.crop() || before.growthStage() != after.growthStage()
                    : before.soil() != after.soil()))
                throw new IllegalArgumentException("field projection step changes more than its declared block");
        }
    }

    public ResourceFieldCellTransition {
        Objects.requireNonNull(siteId, "field projection site");
        if (!siteId.value().startsWith("site:"))
            throw new IllegalArgumentException("field projection needs its declared site owner");
        if (epoch < 1 || layoutRevision < 1)
            throw new IllegalArgumentException("field projection needs its admitted cycle and layout revision");
        Objects.requireNonNull(cellId, "field projection cell");
        Objects.requireNonNull(before, "field projection predecessor");
        Objects.requireNonNull(after, "field projection target");
        steps = List.copyOf(Objects.requireNonNull(steps, "field projection steps"));
        if (steps.isEmpty() || steps.size() > 2 || before.equals(after))
            throw new IllegalArgumentException("field projection needs one or two changed blocks");
        ResourceFieldPhysicalSurface.Condition cursor = before;
        var changedParts = java.util.EnumSet.noneOf(Part.class);
        boolean harvestAndReplant = steps.size() == 2 && steps.get(0).part() == Part.CROP
                && steps.get(1).part() == Part.CROP
                && before.soil() == ResourceFieldCycle.Soil.FARMLAND
                && before.crop() == ResourceFieldCycle.Crop.MATURE
                && steps.get(0).after().equals(new ResourceFieldPhysicalSurface.Condition(
                        ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.ABSENT, 0))
                && after.equals(new ResourceFieldPhysicalSurface.Condition(
                        ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.GROWING, 0));
        for (Step step : steps) {
            if (!step.before().equals(cursor) || !changedParts.add(step.part()) && !harvestAndReplant)
                throw new IllegalArgumentException("field projection has a broken or repeated block prefix");
            cursor = step.after();
        }
        if (!cursor.equals(after)) throw new IllegalArgumentException("field projection has a foreign terminal condition");
    }

    public static ResourceFieldCellTransition between(SubjectId siteId, long epoch, long layoutRevision, ResourceFieldLayout.CellId cellId,
                                                      ResourceFieldPhysicalSurface.Condition before,
                                                      ResourceFieldPhysicalSurface.Condition after) {
        Objects.requireNonNull(before, "field projection predecessor");
        Objects.requireNonNull(after, "field projection target");
        if (before.equals(after)) throw new IllegalArgumentException("unchanged field cell needs no projection");
        boolean soilChanges = before.soil() != after.soil();
        boolean cropChanges = before.crop() != after.crop() || before.growthStage() != after.growthStage();
        var steps = new ArrayList<Step>(2);
        ResourceFieldPhysicalSurface.Condition cursor = before;
        if (soilChanges && cropChanges && after.crop() == ResourceFieldCycle.Crop.ABSENT) {
            // Clearing a crop before removing its support leaves a valid, inspectable prefix.
            var cleared = new ResourceFieldPhysicalSurface.Condition(before.soil(), ResourceFieldCycle.Crop.ABSENT, 0);
            steps.add(new Step(Part.CROP, cursor, cleared));
            cursor = cleared;
        }
        if (soilChanges) {
            var changedSoil = new ResourceFieldPhysicalSurface.Condition(after.soil(), cursor.crop(), cursor.growthStage());
            steps.add(new Step(Part.SOIL, cursor, changedSoil));
            cursor = changedSoil;
        }
        if (!cursor.equals(after)) steps.add(new Step(Part.CROP, cursor, after));
        return new ResourceFieldCellTransition(siteId, epoch, layoutRevision, cellId, before, after, steps);
    }

    /** A farmer's ripe-crop visit exposes the AIR prefix between actual harvest and replant. */
    public static ResourceFieldCellTransition harvestAndReplant(SubjectId siteId, long epoch, long layoutRevision, ResourceFieldLayout.CellId cellId,
                                                                 ResourceFieldPhysicalSurface.Condition before) {
        Objects.requireNonNull(before, "harvest predecessor");
        if (before.soil() != ResourceFieldCycle.Soil.FARMLAND || before.crop() != ResourceFieldCycle.Crop.MATURE)
            throw new IllegalArgumentException("harvest effect needs exact ripe farmland");
        var cut = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.ABSENT, 0);
        var replanted = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.GROWING, 0);
        return new ResourceFieldCellTransition(siteId, epoch, layoutRevision, cellId, before, replanted,
                List.of(new Step(Part.CROP, before, cut), new Step(Part.CROP, cut, replanted)));
    }

    public boolean isHarvestAndReplant() {
        return steps.size() == 2 && steps.get(0).part() == Part.CROP && steps.get(1).part() == Part.CROP;
    }

    public ResourceFieldPhysicalSurface.Condition committedPrefix(int completedSteps) {
        if (completedSteps < 0 || completedSteps > steps.size())
            throw new IllegalArgumentException("field projection cursor is outside its exact cell effect");
        return completedSteps == 0 ? before : steps.get(completedSteps - 1).after();
    }
}
