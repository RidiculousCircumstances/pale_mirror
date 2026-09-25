package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Objects;

/** Final unload observation bound to one ambient authority; never a periodic pose cache. */
record FrontierV3AmbientDeparture(FrontierV3AmbientCarrierLedger.Carrier carrier,
                                  SceneMemberPosition observed, BodyPosition canonicalBodyAtCapture,
                                  FixedScalar canonicalHealthAtCapture) {
    FrontierV3AmbientDeparture {
        Objects.requireNonNull(carrier); Objects.requireNonNull(observed);
        Objects.requireNonNull(canonicalBodyAtCapture); Objects.requireNonNull(canonicalHealthAtCapture);
        var identity = carrier.identity();
        if (identity.owner() != FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE
                || !identity.actorId().equals(observed.actorId())
                || identity.authorityRevision() != carrier.ambientRevision()
                || carrier.physicalRevision() != carrier.ambientRevision()
                || observed.health().compareTo(FixedScalar.ZERO) <= 0
                || canonicalHealthAtCapture.compareTo(FixedScalar.ZERO) <= 0)
            throw new IllegalArgumentException("invalid ambient departure ownership or survivor observation");
    }

    boolean current(FrontierWorldState state) {
        var identity = carrier.identity();
        var actor = state.actorLocations().get(identity.actorId());
        var lease = state.ambientLeases().get(identity.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE || lease == null
                || (lease.status() != AmbientLeaseStatus.HOT && lease.status() != AmbientLeaseStatus.DRAINING
                    && lease.status() != AmbientLeaseStatus.UNKNOWN_AFTER_RESTART)
                || lease.revision() != carrier.ambientRevision()
                || !actor.body().equals(canonicalBodyAtCapture)
                || !actor.condition().health().equals(canonicalHealthAtCapture)) return false;
        if (state.sceneLeases().values().stream().anyMatch(scene -> scene.status() != SceneLeaseStatus.CLOSED
                && scene.members().stream().anyMatch(member -> member.actorId().equals(identity.actorId())))) return false;
        try {
            return identity.equals(FrontierV3ActorCarrierComposition.fromCanonical(state, identity.actorId(), identity.kind(),
                    identity.owner(), identity.entityId(), identity.representation(), identity.authorityRevision(), identity.epoch()));
        } catch (IllegalArgumentException invalid) { return false; }
    }

    CompoundTag save() {
        var tag = new CompoundTag(); tag.put("carrier", carrier.save());
        position(tag, "observed", observed.body()); position(tag, "canonical", canonicalBodyAtCapture);
        tag.putLong("health", observed.health().raw()); tag.putLong("canonicalHealth", canonicalHealthAtCapture.raw());
        return tag;
    }

    static FrontierV3AmbientDeparture load(CompoundTag tag) {
        if (!tag.contains("carrier", Tag.TAG_COMPOUND) || !tag.contains("health", Tag.TAG_LONG)
                || !tag.contains("canonicalHealth", Tag.TAG_LONG)) throw new IllegalStateException("incomplete ambient departure evidence");
        var carrier = FrontierV3AmbientCarrierLedger.Carrier.load(tag.getCompound("carrier"));
        return new FrontierV3AmbientDeparture(carrier,
                new SceneMemberPosition(carrier.identity().actorId(), position(tag, "observed"), new FixedScalar(tag.getLong("health"))),
                position(tag, "canonical"), new FixedScalar(tag.getLong("canonicalHealth")));
    }

    private static void position(CompoundTag tag, String prefix, BodyPosition position) {
        tag.putInt(prefix + "X", position.x()); tag.putInt(prefix + "Y", position.y()); tag.putInt(prefix + "Z", position.z());
    }
    private static BodyPosition position(CompoundTag tag, String prefix) {
        for (String suffix : new String[]{"X", "Y", "Z"})
            if (!tag.contains(prefix + suffix, Tag.TAG_INT)) throw new IllegalStateException("incomplete ambient departure position");
        return new BodyPosition(tag.getInt(prefix + "X"), tag.getInt(prefix + "Y"), tag.getInt(prefix + "Z"));
    }
}
