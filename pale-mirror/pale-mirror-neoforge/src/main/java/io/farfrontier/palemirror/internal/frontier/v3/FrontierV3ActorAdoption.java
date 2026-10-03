package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;

/**
 * Recovery evidence for an inactive-carrier to live-body transfer awaiting entity
 * persistence. Neither endpoint is permission to recreate a body: reconciliation
 * still needs the actual physical evidence. This receipt owns no actor state.
 */
record FrontierV3ActorAdoption(FrontierV3AmbientCarrierLedger.Carrier predecessor,
                             FrontierV3ActorOwnerBinding admittedBinding) {
    private static final int FORMAT = 2;

    FrontierV3ActorAdoption {
        Objects.requireNonNull(predecessor, "predecessor");
        Objects.requireNonNull(admittedBinding, "admitted binding");
        var admitted = admittedBinding.declaration();
        var old = predecessor.identity();
        long priorRevision = admitted.owner() == FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE
                ? predecessor.physicalRevision() : predecessor.ambientRevision();
        if (!old.actorId().equals(admitted.actorId()) || !old.entityId().equals(admitted.entityId())
                || old.kind() != admitted.kind()
                || admitted.representation() != FrontierV3ActorCarrierComposition.Representation.LIVE_BODY
                || old.epoch() == Long.MAX_VALUE || admitted.epoch() != old.epoch() + 1L
                || admitted.authorityRevision() <= priorRevision) {
            throw new IllegalArgumentException("invalid actor adoption endpoints");
        }
    }

    /** Exact generation comparison, not an acknowledgement that a save occurred. */
    boolean matches(FrontierV3ActorOwnerBinding observed) {
        return admittedBinding.equals(observed);
    }

    FrontierV3ActorCarrierComposition.Declaration admitted() { return admittedBinding.declaration(); }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("format", FORMAT);
        // Keep the declaration's authority revision independent of the two owner
        // clocks. Never reconstruct its owner/revision from the recipient tuple.
        tag.put("predecessor", saveDeclaration(predecessor.identity()));
        tag.putLong("physicalRevision", predecessor.physicalRevision());
        tag.putLong("ambientRevision", predecessor.ambientRevision());
        tag.put("admitted", admittedBinding.save());
        return tag;
    }

    static FrontierV3ActorAdoption load(CompoundTag tag) {
        if (!tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != FORMAT
                || !tag.contains("predecessor", Tag.TAG_COMPOUND) || !tag.contains("admitted", Tag.TAG_COMPOUND)
                || !tag.contains("physicalRevision", Tag.TAG_LONG) || !tag.contains("ambientRevision", Tag.TAG_LONG)) {
            throw new IllegalStateException("incomplete or incompatible actor adoption");
        }
        try {
            return new FrontierV3ActorAdoption(new FrontierV3AmbientCarrierLedger.Carrier(
                    loadDeclaration(tag.getCompound("predecessor")), tag.getLong("physicalRevision"),
                    tag.getLong("ambientRevision")), FrontierV3ActorOwnerBinding.load(tag.getCompound("admitted")));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("invalid actor adoption", invalid);
        }
    }

    static CompoundTag saveDeclaration(FrontierV3ActorCarrierComposition.Declaration declaration) {
        var tag = new CompoundTag();
        tag.putString("actor", declaration.actorId().value());
        tag.putUUID("uuid", declaration.entityId());
        tag.putString("kind", declaration.kind().name());
        tag.putString("owner", declaration.owner().name());
        tag.putString("representation", declaration.representation().name());
        tag.putLong("revision", declaration.authorityRevision());
        tag.putLong("epoch", declaration.epoch());
        return tag;
    }

    static FrontierV3ActorCarrierComposition.Declaration loadDeclaration(CompoundTag tag) {
        if (!tag.contains("actor", Tag.TAG_STRING) || !tag.hasUUID("uuid")
                || !tag.contains("kind", Tag.TAG_STRING) || !tag.contains("owner", Tag.TAG_STRING)
                || !tag.contains("representation", Tag.TAG_STRING) || !tag.contains("revision", Tag.TAG_LONG)
                || !tag.contains("epoch", Tag.TAG_LONG)) {
            throw new IllegalStateException("incomplete actor adoption declaration");
        }
        return new FrontierV3ActorCarrierComposition.Declaration(new SubjectId(tag.getString("actor")),
                ActorKind.valueOf(tag.getString("kind")),
                FrontierV3ActorCarrierComposition.Owner.valueOf(tag.getString("owner")), tag.getUUID("uuid"),
                FrontierV3ActorCarrierComposition.Representation.valueOf(tag.getString("representation")),
                tag.getLong("revision"), tag.getLong("epoch"));
    }
}
