package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;

/**
 * Physical first-admission history, not a roster or a live execution owner.
 * Only an authoritative actor-initialization boundary may issue neverCreated().
 * In particular a missing recovered record or an empty chunk cannot issue it.
 */
record FrontierV3ActorFirstAdmission(Identity identity, Phase phase, Optional<FrontierV3ActorOwnerBinding> attempt,
                                     Optional<String> absenceReceipt) {
    private static final int FORMAT = 2;
    record Identity(SubjectId actorId, ActorKind kind, UUID entityId) {
        Identity { Objects.requireNonNull(actorId); Objects.requireNonNull(kind); Objects.requireNonNull(entityId); }
        boolean matches(Declaration declaration) {
            return actorId.equals(declaration.actorId()) && kind == declaration.kind() && entityId.equals(declaration.entityId());
        }
    }
    enum Phase {
        NEVER_CREATED("never_created"), PROVEN_ABSENT("absence_proven"),
        PENDING("creation_pending"), ESTABLISHED("physical_history");
        private final String wire;
        Phase(String wire) { this.wire = wire; }
        static Phase decode(String wire) {
            return switch (wire) {
                case "never_created" -> NEVER_CREATED;
                case "absence_proven" -> PROVEN_ABSENT;
                case "creation_pending" -> PENDING;
                case "physical_history" -> ESTABLISHED;
                default -> throw new IllegalArgumentException("unknown first-admission phase");
            };
        }
    }
    FrontierV3ActorFirstAdmission {
        Objects.requireNonNull(identity); Objects.requireNonNull(phase); Objects.requireNonNull(attempt);
        Objects.requireNonNull(absenceReceipt);
        if ((phase == Phase.NEVER_CREATED) != attempt.isEmpty())
            throw new IllegalArgumentException("first-admission phase lacks its exact attempted owner");
        if (phase == Phase.NEVER_CREATED && absenceReceipt.isPresent()
                || phase == Phase.PROVEN_ABSENT && absenceReceipt.isEmpty()
                || absenceReceipt.filter(value -> !value.matches("[0-9a-f]{64}")).isPresent())
            throw new IllegalArgumentException("first admission has invalid absence proof");
        if (attempt.isPresent()) {
            var declaration = attempt.orElseThrow().declaration();
            if (!identity.matches(declaration) || declaration.epoch() != 1L
                    || declaration.representation() != Representation.LIVE_BODY)
                throw new IllegalArgumentException("first admission changes identity or is not the first physical generation");
            if (absenceReceipt.isPresent() && declaration.owner() != Owner.AMBIENT_LEASE)
                throw new IllegalArgumentException("offline first-body re-arm only covers ambient ownership");
        }
    }
    static FrontierV3ActorFirstAdmission neverCreated(Identity identity) {
        return new FrontierV3ActorFirstAdmission(identity, Phase.NEVER_CREATED, Optional.empty(), Optional.empty());
    }
    FrontierV3ActorFirstAdmission begin(FrontierV3ActorOwnerBinding target) {
        if (phase != Phase.NEVER_CREATED && (phase != Phase.PROVEN_ABSENT || !attempt.orElseThrow().equals(target)))
            throw new IllegalStateException("first admission lacks a fresh or exact proof-backed permit");
        return new FrontierV3ActorFirstAdmission(identity, Phase.PENDING, Optional.of(target), absenceReceipt);
    }
    /** A proof-backed attempt never reuses an old global scan after live-world activity. */
    FrontierV3ActorFirstAdmission rejectedBeforeCreation(FrontierV3ActorOwnerBinding expected) {
        requirePending(expected);
        return absenceReceipt.isPresent() ? this : neverCreated(identity);
    }
    /** Only an offline all-region absence receipt may re-arm the exact attempted body. */
    FrontierV3ActorFirstAdmission rearmAfterProvenAbsence(FrontierV3ActorOwnerBinding expected, String receipt) {
        requirePending(expected);
        return new FrontierV3ActorFirstAdmission(identity, Phase.PROVEN_ABSENT, attempt, Optional.of(receipt));
    }
    /** Exact saved-body evidence settles creation, but never restores a fresh-creation permit. */
    FrontierV3ActorFirstAdmission saved(FrontierV3ActorOwnerBinding expected) {
        requirePending(expected);
        return new FrontierV3ActorFirstAdmission(identity, Phase.ESTABLISHED, attempt, absenceReceipt);
    }
    /** Caller has retained the exact same-body handoff/fence proving this first body existed. */
    FrontierV3ActorFirstAdmission transferred(Declaration successor) {
        if (phase != Phase.PENDING || !identity.matches(successor) || successor.epoch() != 1L)
            throw new IllegalStateException("first admission lacks exact same-generation successor evidence");
        return new FrontierV3ActorFirstAdmission(identity, Phase.ESTABLISHED, attempt, absenceReceipt);
    }
    private void requirePending(FrontierV3ActorOwnerBinding expected) {
        if (phase != Phase.PENDING || !attempt.orElseThrow().equals(expected))
            throw new IllegalStateException("stale first-admission evidence");
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("format", FORMAT);
        tag.putString("actor", identity.actorId().value()); tag.putString("kind", identity.kind().name());
        tag.putUUID("uuid", identity.entityId()); tag.putString("phase", phase.wire);
        attempt.ifPresent(binding -> tag.put("attempt", binding.save()));
        absenceReceipt.ifPresent(receipt -> tag.putString("absenceReceipt", receipt));
        return tag;
    }
    static FrontierV3ActorFirstAdmission load(CompoundTag tag) {
        if (!tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != 1 && tag.getInt("format") != FORMAT
                || !tag.contains("actor", Tag.TAG_STRING) || !tag.contains("kind", Tag.TAG_STRING)
                || !tag.hasUUID("uuid") || !tag.contains("phase", Tag.TAG_STRING)
                || tag.contains("attempt") && !tag.contains("attempt", Tag.TAG_COMPOUND)
                || tag.contains("absenceReceipt") && !tag.contains("absenceReceipt", Tag.TAG_STRING)
                || tag.getInt("format") == 1 && tag.contains("absenceReceipt"))
            throw new IllegalStateException("incomplete first-admission history");
        try {
            var identity = new Identity(new SubjectId(tag.getString("actor")), ActorKind.valueOf(tag.getString("kind")), tag.getUUID("uuid"));
            var phase = Phase.decode(tag.getString("phase"));
            if (tag.getInt("format") == 1 && phase == Phase.PROVEN_ABSENT)
                throw new IllegalArgumentException("old format cannot retain absence proof");
            return new FrontierV3ActorFirstAdmission(identity, phase,
                    tag.contains("attempt") ? Optional.of(FrontierV3ActorOwnerBinding.load(tag.getCompound("attempt"))) : Optional.empty(),
                    tag.contains("absenceReceipt") ? Optional.of(tag.getString("absenceReceipt")) : Optional.empty());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("invalid first-admission history", invalid);
        }
    }
}
