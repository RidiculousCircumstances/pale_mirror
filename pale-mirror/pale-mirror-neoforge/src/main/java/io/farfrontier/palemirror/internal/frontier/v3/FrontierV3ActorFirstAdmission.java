package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

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
                                     Optional<String> absenceReceipt, long attemptGeneration) {
    private static final int FORMAT = 4;
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
        if (attemptGeneration < 0L || phase != Phase.NEVER_CREATED && attemptGeneration == 0L)
            throw new IllegalArgumentException("first admission lacks a monotonic attempt generation");
        if (phase == Phase.NEVER_CREATED && absenceReceipt.isPresent()
                || phase == Phase.PROVEN_ABSENT && absenceReceipt.isEmpty()
                || absenceReceipt.filter(value -> !value.matches("[0-9a-f]{64}")).isPresent())
            throw new IllegalArgumentException("first admission has invalid absence proof");
        if (attempt.isPresent()) {
            var declaration = attempt.orElseThrow().declaration();
            // An unstarted canonical incarnation can be cancelled without ever
            // inserting a body. First physical creation therefore need not be epoch 1;
            // the body controller separately validates the exact current incarnation.
            if (!identity.matches(declaration)
                    || declaration.representation() != Representation.LIVE_BODY)
                throw new IllegalArgumentException("first admission changes identity or is not a live body");
        }
    }
    static FrontierV3ActorFirstAdmission neverCreated(Identity identity) {
        return new FrontierV3ActorFirstAdmission(identity, Phase.NEVER_CREATED, Optional.empty(), Optional.empty(), 0L);
    }
    FrontierV3ActorFirstAdmission begin(FrontierV3ActorOwnerBinding target) {
        if (phase != Phase.NEVER_CREATED && (phase != Phase.PROVEN_ABSENT || !attempt.orElseThrow().equals(target)))
            throw new IllegalStateException("first admission lacks a fresh or exact proof-backed permit");
        return new FrontierV3ActorFirstAdmission(identity, Phase.PENDING, Optional.of(target), absenceReceipt,
                Math.addExact(attemptGeneration, 1L));
    }
    /** A proof-backed attempt never reuses an old global scan after live-world activity. */
    FrontierV3ActorFirstAdmission rejectedBeforeCreation(FrontierV3ActorOwnerBinding expected) {
        requirePending(expected);
        return absenceReceipt.isPresent() ? this : new FrontierV3ActorFirstAdmission(identity, Phase.NEVER_CREATED,
                Optional.empty(), Optional.empty(), attemptGeneration);
    }
    /** Only an offline all-region absence receipt may re-arm the exact attempted body. */
    FrontierV3ActorFirstAdmission rearmAfterProvenAbsence(FrontierV3ActorOwnerBinding expected, String receipt) {
        requirePending(expected);
        return new FrontierV3ActorFirstAdmission(identity, Phase.PROVEN_ABSENT, attempt, Optional.of(receipt), attemptGeneration);
    }
    /** Exact saved-body evidence settles creation, but never restores a fresh-creation permit. */
    FrontierV3ActorFirstAdmission saved(FrontierV3ActorOwnerBinding expected) {
        requirePending(expected);
        return new FrontierV3ActorFirstAdmission(identity, Phase.ESTABLISHED, attempt, absenceReceipt, attemptGeneration);
    }
    /** Exact inactive evidence establishes the original attempted body, not a scope transfer. */
    FrontierV3ActorFirstAdmission fencedAsInactive(Declaration inactive) {
        if (phase != Phase.PENDING || !attempt.orElseThrow().declaration().inactiveCarrier().equals(inactive))
            throw new IllegalStateException("first admission lacks its exact inactive body evidence");
        return new FrontierV3ActorFirstAdmission(identity, Phase.ESTABLISHED, attempt, absenceReceipt, attemptGeneration);
    }
    private void requirePending(FrontierV3ActorOwnerBinding expected) {
        if (phase != Phase.PENDING || !attempt.orElseThrow().equals(expected))
            throw new IllegalStateException("stale first-admission evidence");
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("format", FORMAT);
        tag.putString("actor", identity.actorId().value()); tag.putString("kind", identity.kind().name());
        tag.putUUID("uuid", identity.entityId()); tag.putString("phase", phase.wire);
        tag.putLong("attemptGeneration", attemptGeneration);
        attempt.ifPresent(binding -> tag.put("attempt", binding.save()));
        absenceReceipt.ifPresent(receipt -> tag.putString("absenceReceipt", receipt));
        return tag;
    }
    static FrontierV3ActorFirstAdmission load(CompoundTag tag) {
        if (!tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != FORMAT
                || !tag.contains("actor", Tag.TAG_STRING) || !tag.contains("kind", Tag.TAG_STRING)
                || !tag.hasUUID("uuid") || !tag.contains("phase", Tag.TAG_STRING)
                || !tag.contains("attemptGeneration", Tag.TAG_LONG)
                || tag.contains("attempt") && !tag.contains("attempt", Tag.TAG_COMPOUND)
                || tag.contains("absenceReceipt") && !tag.contains("absenceReceipt", Tag.TAG_STRING))
            throw new IllegalStateException("incomplete first-admission history");
        try {
            var identity = new Identity(new SubjectId(tag.getString("actor")), ActorKind.valueOf(tag.getString("kind")), tag.getUUID("uuid"));
            var phase = Phase.decode(tag.getString("phase"));
            return new FrontierV3ActorFirstAdmission(identity, phase,
                    tag.contains("attempt") ? Optional.of(FrontierV3ActorOwnerBinding.load(tag.getCompound("attempt"))) : Optional.empty(),
                    tag.contains("absenceReceipt") ? Optional.of(tag.getString("absenceReceipt")) : Optional.empty(),
                    tag.getLong("attemptGeneration"));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("invalid first-admission history", invalid);
        }
    }
}
