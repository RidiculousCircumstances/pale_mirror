package io.farfrontier.palemirror.internal.world;

import java.util.Objects;

import io.farfrontier.palemirror.domain.WorldObjectId;
import net.minecraft.core.BlockPos;

/** Generic durable physical identity for a PM-managed world object. */
public final class WorldObjectRegistryEntry {
    private final WorldObjectId id;
    private final String dimensionId;
    private final BlockPos anchor;
    private final BlockPos minBounds;
    private final BlockPos maxBounds;
    private final String templateId;
    private final String templateVersion;
    private WorldObjectLifecycle lifecycle;

    public WorldObjectRegistryEntry(WorldObjectId id, String dimensionId, BlockPos anchor, BlockPos minBounds,
                                    BlockPos maxBounds, String templateId, String templateVersion,
                                    WorldObjectLifecycle lifecycle) {
        this.id = Objects.requireNonNull(id, "id");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        this.minBounds = Objects.requireNonNull(minBounds, "minBounds").immutable();
        this.maxBounds = Objects.requireNonNull(maxBounds, "maxBounds").immutable();
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.templateVersion = Objects.requireNonNull(templateVersion, "templateVersion");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    public WorldObjectId id() { return id; }
    public String dimensionId() { return dimensionId; }
    public BlockPos anchor() { return anchor; }
    public BlockPos minBounds() { return minBounds; }
    public BlockPos maxBounds() { return maxBounds; }
    public String templateId() { return templateId; }
    public String templateVersion() { return templateVersion; }
    public WorldObjectLifecycle lifecycle() { return lifecycle; }
    public void setLifecycle(WorldObjectLifecycle value) { lifecycle = Objects.requireNonNull(value, "value"); }
    public boolean contains(BlockPos position) {
        return position.getX() >= minBounds.getX() && position.getX() <= maxBounds.getX()
                && position.getY() >= minBounds.getY() && position.getY() <= maxBounds.getY()
                && position.getZ() >= minBounds.getZ() && position.getZ() <= maxBounds.getZ();
    }
}
