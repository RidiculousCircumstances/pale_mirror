package io.farfrontier.palemirror.visuals.threat;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.VisualStateProjection;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Reconciles one animated visual carrier with one canonical primary facility. */
public final class ThreatHeartRuntime {
    public void reconcile(ServerLevel level, VisualStateProjection projection, Collection<AuthoredRegionSeed> regions) {
        AuthoredRegionSeed region = regions.stream().filter(seed -> projection.objectId().equals(seed.planId() + "_mine"))
                .findFirst().orElse(null);
        if (region == null) return;
        BlockPos column = new BlockPos(region.primaryMine().x(), 0, region.primaryMine().z());
        if (!level.hasChunkAt(column)) return;
        ThreatHeartEntity existing = level.getEntities(VisualEntityTypes.THREAT_HEART.get(),
                entity -> projection.objectId().equals(entity.facilityId())).stream().findFirst().orElse(null);
        if (!projection.operation().equals("INFECTED")) {
            if (existing != null) existing.discard();
            return;
        }
        int stage = switch (projection.threatStage()) {
            case "FOOTHOLD" -> 1; case "INFESTED" -> 2; case "SIEGE" -> 3; case "APEX" -> 4; default -> 1;
        };
        if (existing != null) { existing.setStage(stage); return; }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        BlockPos chamber = new BlockPos(column.getX(), surface - 16, column.getZ() + 72);
        if (!level.hasChunkAt(chamber)) return;
        ThreatHeartEntity heart = VisualEntityTypes.THREAT_HEART.get().create(level);
        if (heart == null) throw new IllegalStateException("Threat Heart entity factory returned null");
        heart.setPos(chamber.getX() + 0.5, chamber.getY(), chamber.getZ() + 0.5);
        heart.facilityId(projection.objectId()); heart.setStage(stage);
        level.addFreshEntity(heart);
    }
}
