package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;
import java.util.UUID;

/** Durable permission to project one already-accounted COLD lot into its exact HOT farmer hand. */
record FrontierV3ResourceSiteHandProjectionWitness(SubjectId siteId, SubjectId jobId, SubjectId actorAccountId,
                                                   SubjectId lotId, SubjectId workerId, UUID entityId,
                                                   SceneLeaseId leaseId, long actorEpoch, long fieldEpoch,
                                                   int accountedPrefix, int quantity) {
    FrontierV3ResourceSiteHandProjectionWitness {
        Objects.requireNonNull(siteId, "hand projection site");
        Objects.requireNonNull(jobId, "hand projection job");
        Objects.requireNonNull(actorAccountId, "hand projection actor account");
        Objects.requireNonNull(lotId, "hand projection lot");
        Objects.requireNonNull(workerId, "hand projection worker");
        Objects.requireNonNull(entityId, "hand projection body");
        Objects.requireNonNull(leaseId, "hand projection scene");
        if (!siteId.value().startsWith("site:") || !jobId.value().startsWith("job:site-harvest-")
                || !actorAccountId.value().startsWith("custody:") || !lotId.value().startsWith("lot:")
                || !workerId.value().startsWith("resident:") || actorEpoch < 1 || fieldEpoch < 1
                || accountedPrefix < 1 || quantity < 1 || quantity > 64) {
            throw new IllegalArgumentException("field hand projection lacks its bounded declared owner or part");
        }
    }

    CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putString("site", siteId.value()); tag.putString("job", jobId.value());
        tag.putString("actorAccount", actorAccountId.value()); tag.putString("lot", lotId.value());
        tag.putString("worker", workerId.value()); tag.putUUID("entity", entityId);
        tag.putString("lease", leaseId.value()); tag.putLong("actorEpoch", actorEpoch);
        tag.putLong("fieldEpoch", fieldEpoch); tag.putInt("prefix", accountedPrefix);
        tag.putInt("quantity", quantity);
        return tag;
    }

    static FrontierV3ResourceSiteHandProjectionWitness read(CompoundTag tag) {
        for (String key : java.util.List.of("site", "job", "actorAccount", "lot", "worker", "lease")) {
            if (!tag.contains(key, Tag.TAG_STRING)) throw new IllegalStateException("incomplete field hand witness: " + key);
        }
        for (String key : java.util.List.of("actorEpoch", "fieldEpoch")) {
            if (!tag.contains(key, Tag.TAG_LONG)) throw new IllegalStateException("incomplete field hand witness: " + key);
        }
        for (String key : java.util.List.of("prefix", "quantity")) {
            if (!tag.contains(key, Tag.TAG_INT)) throw new IllegalStateException("incomplete field hand witness: " + key);
        }
        if (!tag.hasUUID("entity")) throw new IllegalStateException("incomplete field hand body identity");
        return new FrontierV3ResourceSiteHandProjectionWitness(new SubjectId(tag.getString("site")),
                new SubjectId(tag.getString("job")), new SubjectId(tag.getString("actorAccount")),
                new SubjectId(tag.getString("lot")), new SubjectId(tag.getString("worker")), tag.getUUID("entity"),
                new SceneLeaseId(tag.getString("lease")), tag.getLong("actorEpoch"), tag.getLong("fieldEpoch"),
                tag.getInt("prefix"), tag.getInt("quantity"));
    }
}
