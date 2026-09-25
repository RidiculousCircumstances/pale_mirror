package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldForeignChangeHeld;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;
import java.util.Optional;

/** Physical-before/WAL fence and exact post-write NBT for one foreign-cell cause. */
record FrontierV3ResourceFieldForeignChangeWitness(ResourceFieldForeignChangeHeld hold,
                                                    Optional<Blocks> observed) {
    record Blocks(CompoundTag soil, CompoundTag crop) {
        Blocks {
            soil = Objects.requireNonNull(soil, "observed field soil NBT").copy();
            crop = Objects.requireNonNull(crop, "observed field crop NBT").copy();
            if (!soil.contains("Name", Tag.TAG_STRING) || soil.getString("Name").isBlank()
                    || !crop.contains("Name", Tag.TAG_STRING) || crop.getString("Name").isBlank())
                throw new IllegalArgumentException("field observation lacks exact block names");
        }
        @Override public CompoundTag soil() { return soil.copy(); }
        @Override public CompoundTag crop() { return crop.copy(); }
    }

    FrontierV3ResourceFieldForeignChangeWitness {
        Objects.requireNonNull(hold, "foreign field held cause");
        observed = Objects.requireNonNull(observed, "foreign field postcondition");
    }

    SubjectId siteId() { return hold.siteId(); }
    ResourceFieldLayout.CellId cellId() { return hold.cellId(); }
    boolean matches(ResourceFieldCycle cycle) {
        return siteId().equals(cycle.siteId()) && hold.epoch() == cycle.epoch()
                && hold.layoutRevision() == cycle.layout().revision()
                && cycle.layout().cell(cellId()).isPresent();
    }
    FrontierV3ResourceFieldForeignChangeWitness observe(Blocks after) {
        if (observed.isPresent()) throw new IllegalStateException("foreign field cause already has a postcondition");
        return new FrontierV3ResourceFieldForeignChangeWitness(hold, Optional.of(after));
    }

    CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putString("site", siteId().value());
        tag.putLong("epoch", hold.epoch()); tag.putLong("layout", hold.layoutRevision());
        tag.putLong("cell", cellId().value());
        var before = hold.before();
        tag.putString("beforeSoil", before.soil().name()); tag.putString("beforeCrop", before.crop().name());
        tag.putInt("beforeStage", before.growthStage());
        tag.putBoolean("beforeAccounted", before.accounted()); tag.putBoolean("beforeYielded", before.yielded());
        tag.putString("cause", hold.causationId());
        observed.ifPresent(blocks -> { tag.put("observedSoil", blocks.soil()); tag.put("observedCrop", blocks.crop()); });
        return tag;
    }

    static FrontierV3ResourceFieldForeignChangeWitness read(CompoundTag tag) {
        if (!tag.contains("site", Tag.TAG_STRING) || !tag.contains("epoch", Tag.TAG_LONG)
                || !tag.contains("layout", Tag.TAG_LONG) || !tag.contains("cell", Tag.TAG_LONG)
                || !tag.contains("beforeSoil", Tag.TAG_STRING) || !tag.contains("beforeCrop", Tag.TAG_STRING)
                || !tag.contains("beforeStage", Tag.TAG_INT) || !tag.contains("beforeAccounted", Tag.TAG_BYTE)
                || !tag.contains("beforeYielded", Tag.TAG_BYTE) || !tag.contains("cause", Tag.TAG_STRING)
                || tag.contains("observedSoil") != tag.contains("observedCrop"))
            throw new IllegalStateException("incomplete foreign field-change witness");
        var before = new ResourceFieldCycle.CellState(
                ResourceFieldCycle.Soil.valueOf(tag.getString("beforeSoil")),
                ResourceFieldCycle.Crop.valueOf(tag.getString("beforeCrop")), tag.getInt("beforeStage"),
                tag.getBoolean("beforeAccounted"), tag.getBoolean("beforeYielded"));
        var hold = new ResourceFieldForeignChangeHeld(new SubjectId(tag.getString("site")),
                tag.getLong("epoch"), tag.getLong("layout"), new ResourceFieldLayout.CellId(tag.getLong("cell")),
                before, tag.getString("cause"));
        Optional<Blocks> observed = tag.contains("observedSoil", Tag.TAG_COMPOUND)
                && tag.contains("observedCrop", Tag.TAG_COMPOUND)
                ? Optional.of(new Blocks(tag.getCompound("observedSoil"), tag.getCompound("observedCrop")))
                : Optional.empty();
        if (tag.contains("observedSoil") && observed.isEmpty())
            throw new IllegalStateException("foreign field postcondition has invalid NBT types");
        return new FrontierV3ResourceFieldForeignChangeWitness(hold, observed);
    }
}
