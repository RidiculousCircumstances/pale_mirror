package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;

/** Final observed body departure, not a periodic sample and not itself a COLD authority grant. */
record FrontierV3SceneDeparture(FrontierV3AmbientCarrierLedger.Carrier carrier, SceneLeaseId leaseId, long sceneRevision,
                                SceneMemberPosition observed, FixedScalar canonicalHealthAtCapture) {
    FrontierV3SceneDeparture {
        Objects.requireNonNull(carrier);
        Objects.requireNonNull(leaseId);
        Objects.requireNonNull(observed);
        Objects.requireNonNull(canonicalHealthAtCapture);
        if (carrier.identity().owner() != FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE
                || sceneRevision < 0 || carrier.physicalRevision() != Math.max(1L, sceneRevision)
                || !carrier.identity().actorId().equals(observed.actorId())
                || canonicalHealthAtCapture.compareTo(FixedScalar.ZERO) <= 0) {
            throw new IllegalArgumentException("invalid scene departure ownership or survivor observation");
        }
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("carrier", carrier.save());
        tag.putString("lease", leaseId.value());
        tag.putLong("sceneRevision", sceneRevision);
        tag.putInt("x", observed.body().x());
        tag.putInt("y", observed.body().y());
        tag.putInt("z", observed.body().z());
        tag.putLong("health", observed.health().raw());
        tag.putLong("canonicalHealth", canonicalHealthAtCapture.raw());
        return tag;
    }

    static FrontierV3SceneDeparture load(CompoundTag tag) {
        if (!tag.contains("carrier", Tag.TAG_COMPOUND) || !tag.contains("lease", Tag.TAG_STRING)
                || !tag.contains("sceneRevision", Tag.TAG_LONG)
                || !tag.contains("x", Tag.TAG_INT) || !tag.contains("y", Tag.TAG_INT) || !tag.contains("z", Tag.TAG_INT)
                || !tag.contains("health", Tag.TAG_LONG) || !tag.contains("canonicalHealth", Tag.TAG_LONG)) {
            throw new IllegalStateException("incomplete scene departure evidence");
        }
        var carrier = FrontierV3AmbientCarrierLedger.Carrier.load(tag.getCompound("carrier"));
        return new FrontierV3SceneDeparture(carrier, new SceneLeaseId(tag.getString("lease")), tag.getLong("sceneRevision"),
                new SceneMemberPosition(carrier.identity().actorId(), new BodyPosition(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                        new FixedScalar(tag.getLong("health"))), new FixedScalar(tag.getLong("canonicalHealth")));
    }
}
