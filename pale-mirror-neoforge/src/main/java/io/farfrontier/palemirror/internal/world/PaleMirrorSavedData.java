package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.DomainEventType;
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.domain.ScenarioInstance;
import io.farfrontier.palemirror.domain.ScenarioStatus;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.WorldState;
import io.farfrontier.palemirror.internal.materialization.JobState;
import io.farfrontier.palemirror.internal.materialization.MaterializationJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** One global server-world store, physically hosted in the Overworld data storage. */
public final class PaleMirrorSavedData extends SavedData {
    public static final String DATA_NAME = "pale_mirror";
    private static final int CURRENT_SCHEMA = 1;

    private final WorldState worldState;
    private final Map<WorldObjectId, TestMineRecord> testMines;
    private final Map<String, StoryAudienceId> audienceMappings;

    public PaleMirrorSavedData() {
        this(new WorldState(), new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    private PaleMirrorSavedData(WorldState worldState, Map<WorldObjectId, TestMineRecord> testMines, Map<String, StoryAudienceId> audienceMappings) {
        this.worldState = worldState;
        this.testMines = testMines;
        this.audienceMappings = audienceMappings;
    }

    public static PaleMirrorSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PaleMirrorSavedData::new, PaleMirrorSavedData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), DATA_NAME);
    }

    public WorldState worldState() { return worldState; }
    public Map<WorldObjectId, TestMineRecord> testMines() { return testMines; }
    public Map<String, StoryAudienceId> audienceMappings() { return audienceMappings; }

    public static PaleMirrorSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        int version = tag.contains("schemaVersion", Tag.TAG_INT) ? tag.getInt("schemaVersion") : 0;
        if (version > CURRENT_SCHEMA) {
            throw new IllegalStateException("Pale Mirror data schema " + version + " is newer than this mod supports");
        }
        CompoundTag snapshot = tag.getCompound("snapshot");
        WorldState state = readState(snapshot);
        state.setSchemaVersion(CURRENT_SCHEMA);
        Map<WorldObjectId, TestMineRecord> mines = new LinkedHashMap<>();
        for (Tag element : tag.getList("testMines", Tag.TAG_COMPOUND)) {
            TestMineRecord mine = readMine((CompoundTag) element);
            mines.put(mine.id(), mine);
        }
        Map<String, StoryAudienceId> audiences = new LinkedHashMap<>();
        for (Tag element : tag.getList("audiences", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            audiences.put(value.getString("key"), new StoryAudienceId(value.getString("id")));
        }
        return new PaleMirrorSavedData(state, mines, audiences);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schemaVersion", CURRENT_SCHEMA);
        tag.put("snapshot", writeState(worldState));
        ListTag mines = new ListTag();
        testMines.values().forEach(mine -> mines.add(writeMine(mine)));
        tag.put("testMines", mines);
        ListTag audiences = new ListTag();
        audienceMappings.forEach((key, value) -> {
            CompoundTag audience = new CompoundTag();
            audience.putString("key", key);
            audience.putString("id", value.value());
            audiences.add(audience);
        });
        tag.put("audiences", audiences);
        return tag;
    }

    private static CompoundTag writeState(WorldState state) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("simulationStep", state.simulationStep());
        ListTag facilities = new ListTag();
        state.facilities().forEach(value -> {
            CompoundTag facility = new CompoundTag();
            facility.putString("id", value.id().value());
            facility.putInt("normalProduction", value.normalProduction());
            facility.putInt("threshold", value.infectionThreshold());
            facility.putInt("pressure", value.infectionPressure());
            facility.putInt("currentProduction", value.currentProduction());
            facility.putInt("recoverySteps", value.recoveryStepsRemaining());
            facility.putLong("desiredRevision", value.desiredRevision());
            facility.putLong("observedRevision", value.observedRevision());
            facility.putString("status", value.status().name());
            facilities.add(facility);
        });
        tag.put("facilities", facilities);
        ListTag scenarios = new ListTag();
        state.scenarios().forEach(value -> {
            CompoundTag scenario = new CompoundTag();
            scenario.putString("id", value.id());
            scenario.putString("source", value.sourceEventId());
            scenario.putString("target", value.target().value());
            scenario.putString("audience", value.audience().value());
            scenario.putString("definition", value.definitionId());
            scenario.putString("definitionVersion", value.definitionVersion());
            scenario.putString("status", value.status().name());
            scenarios.add(scenario);
        });
        tag.put("scenarios", scenarios);
        ListTag events = new ListTag();
        state.history().forEach(value -> {
            CompoundTag event = new CompoundTag();
            event.putString("id", value.eventId());
            event.putString("type", value.type().name());
            event.putString("subject", value.subject().value());
            event.putLong("step", value.simulationStep());
            event.putString("causation", value.causationId() == null ? "" : value.causationId());
            event.putString("correlation", value.correlationId() == null ? "" : value.correlationId());
            events.add(event);
        });
        tag.put("events", events);
        return tag;
    }

    private static WorldState readState(CompoundTag tag) {
        WorldState state = new WorldState();
        state.setSimulationStep(tag.getLong("simulationStep"));
        long eventSequence = 0;
        for (Tag element : tag.getList("facilities", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            state.putFacility(new FacilityState(new WorldObjectId(value.getString("id")), value.getInt("normalProduction"),
                    value.getInt("threshold"), value.getInt("pressure"), value.getInt("currentProduction"),
                    value.getInt("recoverySteps"), value.getLong("desiredRevision"), value.getLong("observedRevision"),
                    FacilityStatus.valueOf(value.getString("status"))));
        }
        for (Tag element : tag.getList("scenarios", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            state.putScenario(new ScenarioInstance(value.getString("id"), value.getString("source"),
                    new WorldObjectId(value.getString("target")), new StoryAudienceId(value.getString("audience")),
                    value.getString("definition"), value.getString("definitionVersion"), ScenarioStatus.valueOf(value.getString("status"))));
        }
        for (Tag element : tag.getList("events", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            String id = value.getString("id");
            state.addEvent(new DomainEvent(id, DomainEventType.valueOf(value.getString("type")),
                    new WorldObjectId(value.getString("subject")), value.getLong("step"),
                    value.getString("causation"), value.getString("correlation")));
            int suffix = id.lastIndexOf(':');
            if (suffix >= 0) {
                try { eventSequence = Math.max(eventSequence, Long.parseLong(id.substring(suffix + 1))); } catch (NumberFormatException ignored) { }
            }
        }
        state.setEventSequence(eventSequence);
        return state;
    }

    private static CompoundTag writeMine(TestMineRecord mine) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", mine.id().value());
        tag.putString("dimension", mine.dimensionId());
        tag.putLong("anchor", mine.anchor().asLong());
        tag.putString("templateVersion", mine.templateVersion());
        tag.putString("audience", mine.primaryAudience().value());
        if (mine.controllerId() != null) tag.putUUID("controller", mine.controllerId());
        ListTag cells = new ListTag();
        mine.mutableCells().forEach(cell -> {
            CompoundTag value = new CompoundTag();
            value.putLong("pos", cell.position().asLong());
            value.putString("baseline", cell.baselineBlock());
            value.putString("lastApplied", cell.lastAppliedBlock());
            value.putBoolean("conflicted", cell.conflicted());
            cells.add(value);
        });
        tag.put("cells", cells);
        if (mine.job() != null) {
            MaterializationJob job = mine.job();
            CompoundTag value = new CompoundTag();
            value.putString("id", job.jobId());
            value.putLong("desiredRevision", job.desiredRevision());
            value.putString("policy", job.policyId());
            value.putString("policyVersion", job.policyVersion());
            value.putString("state", job.state().name());
            value.putInt("attempts", job.attemptCount());
            value.putString("error", job.lastError());
            tag.put("job", value);
        }
        return tag;
    }

    private static TestMineRecord readMine(CompoundTag tag) {
        List<MutableCell> cells = new ArrayList<>();
        for (Tag element : tag.getList("cells", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            cells.add(new MutableCell(BlockPos.of(value.getLong("pos")), value.getString("baseline"),
                    value.getString("lastApplied"), value.getBoolean("conflicted")));
        }
        MaterializationJob job = null;
        if (tag.contains("job", Tag.TAG_COMPOUND)) {
            CompoundTag value = tag.getCompound("job");
            job = new MaterializationJob(value.getString("id"), value.getLong("desiredRevision"), value.getString("policy"),
                    value.getString("policyVersion"), JobState.valueOf(value.getString("state")), value.getInt("attempts"), value.getString("error"));
        }
        UUID controller = tag.hasUUID("controller") ? tag.getUUID("controller") : null;
        StoryAudienceId audience = tag.contains("audience", Tag.TAG_STRING)
                ? new StoryAudienceId(tag.getString("audience")) : StoryAudienceId.globalTestAudience();
        return new TestMineRecord(new WorldObjectId(tag.getString("id")), tag.getString("dimension"),
                BlockPos.of(tag.getLong("anchor")), tag.getString("templateVersion"), audience, cells, controller, job);
    }
}
