package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.internal.materialization.MaterializationJob;
import net.minecraft.core.BlockPos;

public final class TestMineRecord {
    private final WorldObjectId id;
    private final WorldObjectRegistryEntry object;
    private final StoryAudienceId primaryAudience;
    private final List<MutableCell> mutableCells;
    private UUID anchorId;
    private EncounterRecord encounter;
    private GatePresentationRecord gate;
    private MaterializationJob job;

    public TestMineRecord(WorldObjectRegistryEntry object, StoryAudienceId primaryAudience,
                          List<MutableCell> mutableCells, UUID anchorId, EncounterRecord encounter, MaterializationJob job) {
        this(object, primaryAudience, mutableCells, anchorId, encounter, GatePresentationRecord.none(), job);
    }

    public TestMineRecord(WorldObjectRegistryEntry object, StoryAudienceId primaryAudience,
                          List<MutableCell> mutableCells, UUID anchorId, EncounterRecord encounter, GatePresentationRecord gate,
                          MaterializationJob job) {
        this.id = object.id();
        this.object = object;
        this.primaryAudience = primaryAudience;
        this.mutableCells = new ArrayList<>(mutableCells);
        this.anchorId = anchorId;
        this.encounter = encounter == null ? EncounterRecord.none() : encounter;
        this.gate = gate == null ? GatePresentationRecord.none() : gate;
        this.job = job;
    }

    public WorldObjectId id() { return id; }
    public WorldObjectRegistryEntry object() { return object; }
    public String dimensionId() { return object.dimensionId(); }
    public BlockPos anchor() { return object.anchor(); }
    public String templateVersion() { return object.templateVersion(); }
    public StoryAudienceId primaryAudience() { return primaryAudience; }
    public List<MutableCell> mutableCells() { return mutableCells; }
    public List<MutableCell> nodeCells() {
        return mutableCells.stream().filter(cell -> cell.infectionStage() == InfectionBiomeStage.NODE).toList();
    }
    public List<MutableCell> biomeCells() {
        return mutableCells.stream().filter(cell -> cell.infectionStage().isBiomeCell()).toList();
    }
    public UUID anchorId() { return anchorId; }
    public EncounterRecord encounter() { return encounter; }
    public GatePresentationRecord gate() { return gate; }
    public MaterializationJob job() { return job; }
    public void setAnchorId(UUID value) { anchorId = value; }
    public void setEncounter(EncounterRecord value) { encounter = value == null ? EncounterRecord.none() : value; }
    public void setGate(GatePresentationRecord value) { gate = value == null ? GatePresentationRecord.none() : value; }
    public void setJob(MaterializationJob value) { job = value; }
    public boolean contains(BlockPos pos) { return object.contains(pos); }
    public Optional<MutableCell> mutableCell(BlockPos position) {
        return mutableCells.stream().filter(cell -> cell.position().equals(position)).findFirst();
    }
}
