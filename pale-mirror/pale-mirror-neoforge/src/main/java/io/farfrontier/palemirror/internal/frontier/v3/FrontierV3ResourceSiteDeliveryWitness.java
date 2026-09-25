package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;
import java.util.UUID;

/** Durable before-effect owner of one bounded farmer-hand to depot-slot transfer. */
record FrontierV3ResourceSiteDeliveryWitness(SubjectId siteId, SubjectId jobId, PhysicalIntentId intentId,
                                             SubjectId workerId, UUID entityId, SceneLeaseId leaseId,
                                             long actorEpoch, SubjectId containerId, int slot, int quantity,
                                             long depotEpoch, String beforeFingerprint, String afterFingerprint,
                                             String witnessId, int deliveredYieldBefore, boolean intermediate,
                                             int successorSlot) {
    FrontierV3ResourceSiteDeliveryWitness {
        Objects.requireNonNull(siteId, "delivery site"); Objects.requireNonNull(jobId, "delivery job");
        Objects.requireNonNull(intentId, "delivery intent"); Objects.requireNonNull(workerId, "delivery worker");
        Objects.requireNonNull(entityId, "delivery body"); Objects.requireNonNull(leaseId, "delivery scene");
        Objects.requireNonNull(containerId, "delivery chest"); Objects.requireNonNull(beforeFingerprint, "delivery predecessor");
        Objects.requireNonNull(afterFingerprint, "delivery successor"); Objects.requireNonNull(witnessId, "delivery witness identity");
        if (!siteId.value().startsWith("site:") || !jobId.value().startsWith("job:site-harvest-")
                || !intentId.value().startsWith("intent:site-harvest-") || !workerId.value().startsWith("resident:")
                || !containerId.value().startsWith("container:") || actorEpoch < 1 || depotEpoch < 1
                || slot < 0 || quantity < 1 || quantity > 64 || beforeFingerprint.equals(afterFingerprint)
                || !beforeFingerprint.matches("sha256:[0-9a-f]{64}")
                || !afterFingerprint.matches("sha256:[0-9a-f]{64}")
                || witnessId.isBlank() || witnessId.length() > 256
                || deliveredYieldBefore < 0 || deliveredYieldBefore % 64 != 0
                || intermediate && (quantity != 64 || successorSlot < 0 || successorSlot == slot)
                || !intermediate && successorSlot != -1) {
            throw new IllegalArgumentException("field delivery witness has invalid owner or physical boundary");
        }
    }

    FrontierV3ResourceSiteDeliveryWitness(SubjectId siteId, SubjectId jobId, PhysicalIntentId intentId,
                                          SubjectId workerId, UUID entityId, SceneLeaseId leaseId,
                                          long actorEpoch, SubjectId containerId, int slot, int quantity,
                                          long depotEpoch, String beforeFingerprint, String afterFingerprint,
                                          String witnessId) {
        this(siteId, jobId, intentId, workerId, entityId, leaseId, actorEpoch, containerId, slot, quantity,
                depotEpoch, beforeFingerprint, afterFingerprint, witnessId, 0, false, -1);
    }

    CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putString("site", siteId.value()); tag.putString("job", jobId.value()); tag.putString("intent", intentId.value());
        tag.putString("worker", workerId.value()); tag.putUUID("entity", entityId); tag.putString("lease", leaseId.value());
        tag.putLong("actorEpoch", actorEpoch); tag.putString("container", containerId.value()); tag.putInt("slot", slot);
        tag.putInt("quantity", quantity); tag.putLong("depotEpoch", depotEpoch);
        tag.putString("before", beforeFingerprint); tag.putString("after", afterFingerprint);
        tag.putString("witness", witnessId); tag.putInt("deliveredBefore", deliveredYieldBefore);
        tag.putBoolean("intermediate", intermediate); tag.putInt("successorSlot", successorSlot);
        return tag;
    }

    static FrontierV3ResourceSiteDeliveryWitness read(CompoundTag tag) {
        for (String key : java.util.List.of("site", "job", "intent", "worker", "lease", "container", "before", "after", "witness")) {
            if (!tag.contains(key, Tag.TAG_STRING)) throw new IllegalStateException("incomplete field delivery witness: " + key);
        }
        for (String key : java.util.List.of("actorEpoch", "depotEpoch")) {
            if (!tag.contains(key, Tag.TAG_LONG)) throw new IllegalStateException("incomplete field delivery witness: " + key);
        }
        for (String key : java.util.List.of("slot", "quantity", "deliveredBefore", "successorSlot")) {
            if (!tag.contains(key, Tag.TAG_INT)) throw new IllegalStateException("incomplete field delivery witness: " + key);
        }
        if (!tag.contains("intermediate", Tag.TAG_BYTE)) throw new IllegalStateException("incomplete field delivery witness: intermediate");
        if (!tag.hasUUID("entity")) throw new IllegalStateException("incomplete field delivery body identity");
        return new FrontierV3ResourceSiteDeliveryWitness(new SubjectId(tag.getString("site")), new SubjectId(tag.getString("job")),
                new PhysicalIntentId(tag.getString("intent")), new SubjectId(tag.getString("worker")), tag.getUUID("entity"),
                new SceneLeaseId(tag.getString("lease")), tag.getLong("actorEpoch"), new SubjectId(tag.getString("container")),
                tag.getInt("slot"), tag.getInt("quantity"), tag.getLong("depotEpoch"), tag.getString("before"),
                tag.getString("after"), tag.getString("witness"), tag.getInt("deliveredBefore"),
                tag.getBoolean("intermediate"), tag.getInt("successorSlot"));
    }
}
