package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.internal.materialization.MaterializationJob;
import net.minecraft.core.BlockPos;

public final class TestMineRecord {
    private final WorldObjectId id;
    private final WorldObjectRegistryEntry object;
    private final StoryAudienceId primaryAudience;
    private final List<MutableCell> mutableCells;
    private UUID controllerId;
    private MaterializationJob job;

    public TestMineRecord(WorldObjectRegistryEntry object, StoryAudienceId primaryAudience,
                          List<MutableCell> mutableCells, UUID controllerId, MaterializationJob job) {
        this.id = object.id();
        this.object = object;
        this.primaryAudience = primaryAudience;
        this.mutableCells = new ArrayList<>(mutableCells);
        this.controllerId = controllerId;
        this.job = job;
    }

    public WorldObjectId id() { return id; }
    public WorldObjectRegistryEntry object() { return object; }
    public String dimensionId() { return object.dimensionId(); }
    public BlockPos anchor() { return object.anchor(); }
    public String templateVersion() { return object.templateVersion(); }
    public StoryAudienceId primaryAudience() { return primaryAudience; }
    public List<MutableCell> mutableCells() { return mutableCells; }
    public UUID controllerId() { return controllerId; }
    public MaterializationJob job() { return job; }
    public void setControllerId(UUID value) { controllerId = value; }
    public void setJob(MaterializationJob value) { job = value; }
    public boolean contains(BlockPos pos) { return object.contains(pos); }
}
