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
    private SiegeRecord siege;
    private MaterializationJob job;

    public TestMineRecord(WorldObjectRegistryEntry object, StoryAudienceId primaryAudience,
                          List<MutableCell> mutableCells, UUID anchorId, EncounterRecord encounter, MaterializationJob job) {
        this(object, primaryAudience, mutableCells, anchorId, encounter, SiegeRecord.none(), job);
    }

    public TestMineRecord(WorldObjectRegistryEntry object, StoryAudienceId primaryAudience,
                          List<MutableCell> mutableCells, UUID anchorId, EncounterRecord encounter, SiegeRecord siege,
                          MaterializationJob job) {
        this.id = object.id();
        this.object = object;
        this.primaryAudience = primaryAudience;
        this.mutableCells = new ArrayList<>(mutableCells);
        this.anchorId = anchorId;
        this.encounter = encounter == null ? EncounterRecord.none() : encounter;
        this.siege = siege == null ? SiegeRecord.none() : siege;
        this.job = job;
    }

    public WorldObjectId id() { return id; }
    public WorldObjectRegistryEntry object() { return object; }
    public String dimensionId() { return object.dimensionId(); }
    public BlockPos anchor() { return object.anchor(); }
    public String templateVersion() { return object.templateVersion(); }
    public StoryAudienceId primaryAudience() { return primaryAudience; }
    public List<MutableCell> mutableCells() { return mutableCells; }
    public UUID anchorId() { return anchorId; }
    public EncounterRecord encounter() { return encounter; }
    public SiegeRecord siege() { return siege; }
    public MaterializationJob job() { return job; }
    public void setAnchorId(UUID value) { anchorId = value; }
    public void setEncounter(EncounterRecord value) { encounter = value == null ? EncounterRecord.none() : value; }
    public void setSiege(SiegeRecord value) { siege = value == null ? SiegeRecord.none() : value; }
    public void setJob(MaterializationJob value) { job = value; }
    public boolean contains(BlockPos pos) { return object.contains(pos); }
    public Optional<MutableCell> mutableCell(BlockPos position) {
        return mutableCells.stream().filter(cell -> cell.position().equals(position)).findFirst();
    }
}
