package io.farfrontier.palemirror.visuals.threat;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.VisualStateProjection;
import io.farfrontier.palemirror.api.ThreatControllerProjection;
import io.farfrontier.palemirror.api.ThreatControllerResult;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Executes controller capability requests and applies read-only visual stage updates. */
public final class ThreatHeartRuntime {
    private final java.util.Map<String, java.util.UUID> identities = new java.util.HashMap<>();
    public void reconcile(ServerLevel level, VisualStateProjection projection, Collection<AuthoredRegionSeed> regions) {
        AuthoredRegionSeed region = regions.stream().filter(seed -> projection.objectId().equals(seed.planId() + "_mine"))
                .findFirst().orElse(null);
        if (region == null) return;
        BlockPos column = new BlockPos(region.primaryMine().x(), 0, region.primaryMine().z());
        if (!level.hasChunkAt(column)) return;
        ThreatHeartEntity existing = level.getEntities(VisualEntityTypes.THREAT_HEART.get(),
                entity -> projection.objectId().equals(entity.facilityId())).stream().findFirst().orElse(null);
        if (!projection.operation().equals("INFECTED")) return;
        int stage = switch (projection.threatStage()) {
            case "FOOTHOLD" -> 1; case "INFESTED" -> 2; case "SIEGE" -> 3; case "APEX" -> 4; default -> 1;
        };
        if (existing != null) existing.setStage(stage);
    }

    public ThreatControllerResult ensure(ServerLevel level, ThreatControllerProjection projection) {
        ThreatHeartEntity existing = find(level, projection.objectId());
        if (existing != null) { existing.setStage(projection.stage()); identities.put(projection.objectId(), existing.getUUID());
            return ThreatControllerResult.materialized(existing.getUUID()); }
        BlockPos chamber = new BlockPos(projection.anchor().x(), projection.anchor().y(), projection.anchor().z());
        if (!level.hasChunkAt(chamber)) return ThreatControllerResult.blocked("Threat Heart chunk is not loaded");
        ThreatHeartEntity heart = VisualEntityTypes.THREAT_HEART.get().create(level);
        if (heart == null) return ThreatControllerResult.blocked("Threat Heart entity factory returned null");
        heart.setPos(chamber.getX() + 0.5, chamber.getY() + 1.0, chamber.getZ() + 0.5);
        heart.facilityId(projection.objectId()); heart.jobId(projection.jobId()); heart.setStage(projection.stage());
        if (!level.addFreshEntity(heart)) return ThreatControllerResult.blocked("Threat Heart entity could not be added");
        identities.put(projection.objectId(), heart.getUUID());
        return ThreatControllerResult.materialized(heart.getUUID());
    }

    public ThreatControllerResult remove(ServerLevel level, String objectId) {
        ThreatHeartEntity existing = find(level, objectId);
        if (existing == null) return ThreatControllerResult.absent();
        java.util.UUID id = existing.getUUID(); existing.discard(); identities.remove(objectId);
        return ThreatControllerResult.materialized(id);
    }

    public ThreatHeartEntity find(ServerLevel level, String objectId) {
        java.util.UUID known = identities.get(objectId);
        if (known != null) {
            var entity = level.getEntity(known);
            if (entity instanceof ThreatHeartEntity heart && objectId.equals(heart.facilityId())) return heart;
            identities.remove(objectId);
        }
        ThreatHeartEntity found = level.getEntities(VisualEntityTypes.THREAT_HEART.get(),
                entity -> objectId.equals(entity.facilityId())).stream().findFirst().orElse(null);
        if (found != null) identities.put(objectId, found.getUUID());
        return found;
    }

    public void clear() { identities.clear(); }
}
