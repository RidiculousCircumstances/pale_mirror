package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;

/** Durable exact physical postcondition between a world block change and its canonical WAL receipt. */
record FrontierV3ResourceFieldWorldChangeWitness(ResourceFieldCellObserved observation) {
    FrontierV3ResourceFieldWorldChangeWitness {
        Objects.requireNonNull(observation, "world field observation");
        if (observation.source() != ResourceFieldCellObserved.Source.WORLD)
            throw new IllegalArgumentException("world field witness has a foreign source");
    }

    SubjectId siteId() { return observation.siteId(); }
    ResourceFieldLayout.CellId cellId() { return observation.cellId(); }
    ResourceFieldPhysicalSurface.Condition before() { return observation.before(); }
    ResourceFieldPhysicalSurface.Condition after() { return observation.after(); }

    boolean matches(ResourceFieldCycle cycle) {
        return observation.siteId().equals(cycle.siteId()) && observation.epoch() == cycle.epoch()
                && observation.layoutRevision() == cycle.layout().revision()
                && cycle.layout().cells().stream().anyMatch(cell -> cell.id().equals(observation.cellId()));
    }

    CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putString("site", siteId().value());
        tag.putLong("epoch", observation.epoch());
        tag.putLong("layout", observation.layoutRevision());
        tag.putLong("cell", cellId().value());
        tag.putString("beforeSoil", before().soil().name());
        tag.putString("beforeCrop", before().crop().name());
        tag.putInt("beforeStage", before().growthStage());
        tag.putString("change", observation.change().name());
        tag.putString("cause", observation.causationId());
        return tag;
    }

    static FrontierV3ResourceFieldWorldChangeWitness read(CompoundTag tag) {
        if (!tag.contains("site", Tag.TAG_STRING) || !tag.contains("epoch", Tag.TAG_LONG)
                || !tag.contains("layout", Tag.TAG_LONG) || !tag.contains("cell", Tag.TAG_LONG)
                || !tag.contains("beforeSoil", Tag.TAG_STRING) || !tag.contains("beforeCrop", Tag.TAG_STRING)
                || !tag.contains("beforeStage", Tag.TAG_INT) || !tag.contains("change", Tag.TAG_STRING)
                || !tag.contains("cause", Tag.TAG_STRING))
            throw new IllegalStateException("incomplete world field-change witness");
        var before = new ResourceFieldPhysicalSurface.Condition(
                ResourceFieldCycle.Soil.valueOf(tag.getString("beforeSoil")),
                ResourceFieldCycle.Crop.valueOf(tag.getString("beforeCrop")), tag.getInt("beforeStage"));
        var change = ResourceFieldCellObserved.Change.valueOf(tag.getString("change"));
        var after = switch (change) {
            case CROP_REMOVED -> new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                    ResourceFieldCycle.Crop.ABSENT, 0);
            case SOIL_BECAME_DIRT -> new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.DIRT,
                    ResourceFieldCycle.Crop.ABSENT, 0);
            case UNCHANGED -> throw new IllegalStateException("world change witness cannot be unchanged");
        };
        return new FrontierV3ResourceFieldWorldChangeWitness(new ResourceFieldCellObserved(
                new SubjectId(tag.getString("site")), tag.getLong("epoch"), tag.getLong("layout"),
                new ResourceFieldLayout.CellId(tag.getLong("cell")), before, after, change,
                ResourceFieldCellObserved.Source.WORLD, tag.getString("cause")));
    }
}
