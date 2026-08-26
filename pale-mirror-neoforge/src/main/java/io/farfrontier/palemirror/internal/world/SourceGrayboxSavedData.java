package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxBioformObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxResidentObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxStructureObservation;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable canonical owner for one source-parity graybox world. */
final class SourceGrayboxSavedData extends SavedData {
    static final String DATA_NAME = "pale_mirror_frontier";
    private static final int SCHEMA = 16;
    private static final int MAX_PROCESSED_OBSERVATIONS = 4_096;
    private final ReferenceGrayboxSimulation simulation;
    private final LinkedHashSet<String> processedObservationIds;
    private boolean activated;
    private long lastClockGameTime;

    private SourceGrayboxSavedData(ReferenceGrayboxSimulation simulation, boolean activated, long lastClockGameTime,
                                   LinkedHashSet<String> processedObservationIds) {
        this.simulation = simulation;
        this.activated = activated;
        this.lastClockGameTime = lastClockGameTime;
        this.processedObservationIds = processedObservationIds;
    }

    static SourceGrayboxSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                () -> fresh(overworld.getSeed()),
                SourceGrayboxSavedData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), DATA_NAME);
    }

    static SourceGrayboxSavedData fresh(long seed) {
        return new SourceGrayboxSavedData(ReferenceGrayboxSimulation.create(seed), false, 0L, new LinkedHashSet<>());
    }

    /**
     * DimensionDataStorage turns a loader failure into a fresh record.  The
     * source graybox must instead stop before storage sees an unhydratable
     * canonical document, otherwise the old clock could silently disappear.
     */
    static void assertCompatibleData(Path worldRoot) {
        PaleMirrorSavedDataCompatibility.assertHydratable(worldRoot, DATA_NAME,
                SourceGrayboxSavedData::assertHydratable, "source graybox canonical state");
    }

    /** Package-visible only so the Minecraft GameTest boundary can prove the preflight rejection. */
    static void assertHydratable(CompoundTag tag) {
        load(tag, null);
    }

    ReferenceGrayboxSnapshot snapshot() { return simulation.snapshot(); }
    boolean activated() { return activated; }

    void activate(long gameTime) {
        if (activated) return;
        activated = true;
        lastClockGameTime = gameTime;
        setDirty();
    }

    int advanceDueDays(long gameTime, long intervalTicks, int maximumDays) {
        if (!activated || intervalTicks < 1 || maximumDays < 1) return 0;
        if (gameTime < lastClockGameTime) {
            lastClockGameTime = gameTime;
            setDirty();
            return 0;
        }
        long due = Math.min(maximumDays, (gameTime - lastClockGameTime) / intervalTicks);
        for (long index = 0; index < due; index++) simulation.tick();
        if (due > 0) {
            lastClockGameTime += due * intervalTicks;
            setDirty();
        }
        return Math.toIntExact(due);
    }

    void advance(int days) {
        if (!activated) throw new IllegalStateException("source graybox is not activated");
        if (days < 1 || days > 365) throw new IllegalArgumentException("source graybox advance must be between one and 365 days");
        for (int index = 0; index < days; index++) simulation.tick();
        setDirty();
    }

    ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxResidentObservation observation) {
        return observe(observation.eventId(), () -> simulation.observe(observation));
    }

    ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxBioformObservation observation) {
        return observe(observation.eventId(), () -> simulation.observe(observation));
    }

    ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxStructureObservation observation) {
        return observe(observation.eventId(), () -> simulation.observe(observation));
    }

    private ReferenceGrayboxObservationOutcome observe(String eventId, java.util.function.Supplier<ReferenceGrayboxObservationOutcome> apply) {
        if (processedObservationIds.contains(eventId)) {
            return new ReferenceGrayboxObservationOutcome(eventId, ReferenceGrayboxObservationOutcome.Status.REJECTED_CONFLICT,
                    "observation event was already processed", simulation.snapshot().stateRevision());
        }
        ReferenceGrayboxObservationOutcome outcome = apply.get();
        if (outcome.applied()) {
            processedObservationIds.add(eventId);
            while (processedObservationIds.size() > MAX_PROCESSED_OBSERVATIONS) processedObservationIds.removeFirst();
            setDirty();
        }
        return outcome;
    }

    static SourceGrayboxSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("schemaVersion") != SCHEMA || !tag.contains("sourceState", Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("incompatible Frontier SavedData; reset the disposable graybox world");
        }
        LinkedHashSet<String> processed = readProcessed(tag.getList("processedObservationIds", Tag.TAG_STRING));
        return new SourceGrayboxSavedData(SourceGrayboxStateNbt.read(tag.getCompound("sourceState")), tag.getBoolean("activated"),
                tag.getLong("lastClockGameTime"), processed);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schemaVersion", SCHEMA);
        tag.putBoolean("activated", activated);
        tag.putLong("lastClockGameTime", lastClockGameTime);
        tag.put("sourceState", SourceGrayboxStateNbt.write(simulation));
        ListTag processed = new ListTag();
        processedObservationIds.forEach(id -> processed.add(StringTag.valueOf(id)));
        tag.put("processedObservationIds", processed);
        return tag;
    }

    private static LinkedHashSet<String> readProcessed(ListTag encoded) {
        if (encoded.size() > MAX_PROCESSED_OBSERVATIONS) throw new IllegalStateException("source graybox observation history exceeds its bound");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Tag item : encoded) {
            String id = item.getAsString();
            if (id.isBlank() || id.length() > 128 || !result.add(id)) throw new IllegalStateException("source graybox observation history is invalid");
        }
        return result;
    }
}
