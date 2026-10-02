package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPlayerBreakPrepared;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Durable physical half of one player action, distinct from a farmer or projection write. */
record FrontierV3ResourceFieldPlayerBreakWitness(SubjectId siteId, long epoch, long layoutRevision,
                                                 ResourceFieldLayout.CellId cellId, UUID playerId, String actionId,
                                                 ResourceFieldPhysicalSurface.Condition before,
                                                 Optional<ResourceFieldCellObserved.Change> observedChange) {
    FrontierV3ResourceFieldPlayerBreakWitness {
        Objects.requireNonNull(siteId, "player break site");
        Objects.requireNonNull(cellId, "player break cell");
        Objects.requireNonNull(playerId, "player break player");
        Objects.requireNonNull(actionId, "player break action");
        Objects.requireNonNull(before, "player break predecessor");
        observedChange = Objects.requireNonNull(observedChange, "player break observation");
        if (!siteId.value().startsWith("site:") || epoch < 1 || layoutRevision < 1 || actionId.isBlank()
                || before.soil() != ResourceFieldCycle.Soil.FARMLAND
                || before.crop() != ResourceFieldCycle.Crop.GROWING && before.crop() != ResourceFieldCycle.Crop.MATURE
                || observedChange.filter(change -> change != ResourceFieldCellObserved.Change.CROP_REMOVED
                    && change != ResourceFieldCellObserved.Change.UNCHANGED).isPresent())
            throw new IllegalArgumentException("player crop break has an invalid exact predecessor or outcome");
    }

    static FrontierV3ResourceFieldPlayerBreakWitness prepared(ResourceFieldPlayerBreakPrepared value) {
        return new FrontierV3ResourceFieldPlayerBreakWitness(value.siteId(), value.epoch(), value.layoutRevision(),
                value.cellId(), value.playerId(), value.actionId(), value.before(), Optional.empty());
    }

    FrontierV3ResourceFieldPlayerBreakWitness observed(ResourceFieldCellObserved.Change change) {
        if (observedChange.isPresent() || change != ResourceFieldCellObserved.Change.CROP_REMOVED
                && change != ResourceFieldCellObserved.Change.UNCHANGED)
            throw new IllegalArgumentException("player crop break cannot change or overwrite its observed result");
        return new FrontierV3ResourceFieldPlayerBreakWitness(siteId, epoch, layoutRevision, cellId, playerId,
                actionId, before, Optional.of(change));
    }

    ResourceFieldPhysicalSurface.Condition after() {
        return switch (observedChange.orElseThrow()) {
            case CROP_REMOVED -> new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                    ResourceFieldCycle.Crop.ABSENT, 0);
            case UNCHANGED -> before;
            case SOIL_BECAME_DIRT, CROP_REPLANTED, CROP_GROWN -> throw new IllegalStateException("soil/replant/growth is not a crop-break outcome");
        };
    }

    boolean matches(ResourceFieldCycle cycle) {
        return siteId.equals(cycle.siteId()) && epoch == cycle.epoch()
                && layoutRevision == cycle.layout().revision()
                && cycle.layout().cells().stream().anyMatch(cell -> cell.id().equals(cellId));
    }

    CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putString("site", siteId.value()); tag.putLong("epoch", epoch); tag.putLong("layout", layoutRevision);
        tag.putLong("cell", cellId.value()); tag.putUUID("player", playerId); tag.putString("action", actionId);
        tag.putString("soil", before.soil().name()); tag.putString("crop", before.crop().name());
        tag.putInt("stage", before.growthStage());
        observedChange.ifPresent(change -> tag.putString("observed", change.name()));
        return tag;
    }

    static FrontierV3ResourceFieldPlayerBreakWitness read(CompoundTag tag) {
        if (!tag.contains("site", Tag.TAG_STRING) || !tag.contains("epoch", Tag.TAG_LONG)
                || !tag.contains("layout", Tag.TAG_LONG) || !tag.contains("cell", Tag.TAG_LONG)
                || !tag.hasUUID("player") || !tag.contains("action", Tag.TAG_STRING)
                || !tag.contains("soil", Tag.TAG_STRING) || !tag.contains("crop", Tag.TAG_STRING)
                || !tag.contains("stage", Tag.TAG_INT)
                || tag.contains("observed") && !tag.contains("observed", Tag.TAG_STRING))
            throw new IllegalStateException("incomplete player field-break witness");
        var before = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.valueOf(tag.getString("soil")),
                ResourceFieldCycle.Crop.valueOf(tag.getString("crop")), tag.getInt("stage"));
        Optional<ResourceFieldCellObserved.Change> observed = tag.contains("observed", Tag.TAG_STRING)
                ? Optional.of(ResourceFieldCellObserved.Change.valueOf(tag.getString("observed"))) : Optional.empty();
        return new FrontierV3ResourceFieldPlayerBreakWitness(new SubjectId(tag.getString("site")), tag.getLong("epoch"),
                tag.getLong("layout"), new ResourceFieldLayout.CellId(tag.getLong("cell")), tag.getUUID("player"),
                tag.getString("action"), before, observed);
    }
}
