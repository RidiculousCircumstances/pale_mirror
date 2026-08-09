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
    private final String dimensionId;
    private final BlockPos anchor;
    private final String templateVersion;
    private final StoryAudienceId primaryAudience;
    private final List<MutableCell> mutableCells;
    private UUID controllerId;
    private MaterializationJob job;

    public TestMineRecord(WorldObjectId id, String dimensionId, BlockPos anchor, String templateVersion, StoryAudienceId primaryAudience,
                          List<MutableCell> mutableCells, UUID controllerId, MaterializationJob job) {
        this.id = id;
        this.dimensionId = dimensionId;
        this.anchor = anchor.immutable();
        this.templateVersion = templateVersion;
        this.primaryAudience = primaryAudience;
        this.mutableCells = new ArrayList<>(mutableCells);
        this.controllerId = controllerId;
        this.job = job;
    }

    public WorldObjectId id() { return id; }
    public String dimensionId() { return dimensionId; }
    public BlockPos anchor() { return anchor; }
    public String templateVersion() { return templateVersion; }
    public StoryAudienceId primaryAudience() { return primaryAudience; }
    public List<MutableCell> mutableCells() { return mutableCells; }
    public UUID controllerId() { return controllerId; }
    public MaterializationJob job() { return job; }
    public void setControllerId(UUID value) { controllerId = value; }
    public void setJob(MaterializationJob value) { job = value; }
    public boolean contains(BlockPos pos) {
        return Math.abs(pos.getX() - anchor.getX()) <= 4
                && pos.getY() >= anchor.getY() && pos.getY() <= anchor.getY() + 4
                && Math.abs(pos.getZ() - anchor.getZ()) <= 4;
    }
}
