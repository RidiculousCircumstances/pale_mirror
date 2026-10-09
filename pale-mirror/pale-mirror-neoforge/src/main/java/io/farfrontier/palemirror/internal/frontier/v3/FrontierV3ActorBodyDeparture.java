package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Objects;
import java.util.Optional;

/** Exact physical unload evidence. Neither a scene identity nor an activity owns the body. */
record FrontierV3ActorBodyDeparture(FrontierV3ActorCarrierComposition.Declaration identity, long residenceGeneration,
        SceneMemberPosition observed, BodyPosition canonicalBody, FixedScalar canonicalHealth,
        Optional<ActorExecutionId> executionAtCapture,
        Optional<FrontierV3ActorBodyDeparture.HandStack> offhand,
        Optional<FrontierV3ActorBodyDeparture.HandStack> mainhand, Optional<FrontierV3StoredAttachedStorage> attachedStorage) {
    FrontierV3ActorBodyDeparture(FrontierV3ActorCarrierComposition.Declaration identity, long residenceGeneration,
            SceneMemberPosition observed, BodyPosition canonicalBody, FixedScalar canonicalHealth, Optional<ActorExecutionId> execution,
            Optional<HandStack> offhand, Optional<HandStack> mainhand) {
        this(identity, residenceGeneration, observed, canonicalBody, canonicalHealth, execution, offhand, mainhand, Optional.empty());
    }
    record HandStack(String itemKind, int quantity, Optional<SubjectId> exactItemId) {
        HandStack(String itemKind, int quantity) { this(itemKind, quantity, Optional.empty()); }
        HandStack {
            Objects.requireNonNull(exactItemId);
            if (itemKind == null || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")
                    || quantity < 1 || quantity > 64) throw new IllegalArgumentException("invalid saved actor hand");
        }
        void writeIdentity(CompoundTag tag) { exactItemId.ifPresent(id -> tag.putString("exactItemId", id.value())); }
        static Optional<SubjectId> readIdentity(CompoundTag tag) {
            if (!tag.contains("exactItemId")) return Optional.empty();
            if (!tag.contains("exactItemId", Tag.TAG_STRING)) throw new IllegalStateException("invalid exact hand identity");
            return Optional.of(new SubjectId(tag.getString("exactItemId")));
        }
    }
    FrontierV3ActorBodyDeparture {
        Objects.requireNonNull(identity); Objects.requireNonNull(observed);
        Objects.requireNonNull(canonicalBody); Objects.requireNonNull(canonicalHealth);
        Objects.requireNonNull(executionAtCapture); Objects.requireNonNull(offhand); Objects.requireNonNull(mainhand);
        Objects.requireNonNull(attachedStorage);
        if (identity.representation() != FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER
                || residenceGeneration < 1L || !identity.actorId().equals(observed.actorId())
                || canonicalHealth.compareTo(FixedScalar.ZERO) <= 0
                || observed.health().compareTo(FixedScalar.ZERO) <= 0
                || executionAtCapture.filter(id -> !id.actorId().equals(identity.actorId())).isPresent())
            throw new IllegalArgumentException("invalid actor body departure");
    }

    static Optional<ActorExecutionId> execution(FrontierWorldState state, SubjectId actor) {
        var retained = state.actorExecutions().actors().get(actor);
        return retained == null ? Optional.empty() : retained.current();
    }

    boolean current(FrontierWorldState state) {
        var actor = state.actorLocations().get(identity.actorId());
        return actor != null && (actor.body().equals(canonicalBody) && actor.condition().health().equals(canonicalHealth)
                    || actor.body().equals(observed.body()) && actor.condition().health().equals(observed.health()))
                && FrontierV3ActorBodyController.recognizesDeclaration(state,
                    identity.liveBody(identity.owner(), 0L, identity.epoch()));
    }

    boolean matches(FrontierV3SceneDeparture receipt) {
        return residenceGeneration == receipt.residenceGeneration()
                && identity.equals(receipt.carrier().identity()) && observed.equals(receipt.observed())
                && canonicalHealth.equals(receipt.canonicalHealthAtCapture())
                && offhand.equals(receipt.offhand()) && mainhand.equals(receipt.mainhand());
    }

    boolean matches(FrontierV3AmbientDeparture receipt) {
        return residenceGeneration == receipt.residenceGeneration()
                && identity.equals(receipt.carrier().identity()) && observed.equals(receipt.observed())
                && canonicalBody.equals(receipt.canonicalBodyAtCapture())
                && canonicalHealth.equals(receipt.canonicalHealthAtCapture());
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.put("identity", FrontierV3ActorAdoption.saveDeclaration(identity));
        tag.putLong("residenceGeneration", residenceGeneration);
        position(tag, "observed", observed.body()); position(tag, "canonical", canonicalBody);
        tag.putLong("health", observed.health().raw()); tag.putLong("canonicalHealth", canonicalHealth.raw());
        tag.putBoolean("hasExecution", executionAtCapture.isPresent());
        executionAtCapture.ifPresent(id -> {
            var execution = new CompoundTag(); execution.putString("actor", id.actorId().value());
            execution.putString("kind", id.activityKind().name()); execution.putString("owner", id.activityOwnerId().value());
            execution.putLong("generation", id.generation()); tag.put("execution", execution);
        });
        hand(tag, "offhand", offhand); hand(tag, "mainhand", mainhand);
        attachedStorage.ifPresent(storage -> tag.put("attachedStorage", storage.save()));
        return tag;
    }

    static FrontierV3ActorBodyDeparture load(CompoundTag tag) {
        if (!tag.contains("identity", Tag.TAG_COMPOUND) || !tag.contains("residenceGeneration", Tag.TAG_LONG)
                || !tag.contains("health", Tag.TAG_LONG)
                || !tag.contains("canonicalHealth", Tag.TAG_LONG) || !tag.contains("hasExecution", Tag.TAG_BYTE))
            throw new IllegalStateException("incomplete actor body departure");
        var identity = FrontierV3ActorAdoption.loadDeclaration(tag.getCompound("identity"));
        Optional<ActorExecutionId> execution = Optional.empty();
        if (tag.getBoolean("hasExecution")) {
            if (!tag.contains("execution", Tag.TAG_COMPOUND)) throw new IllegalStateException("missing captured execution");
            var value = tag.getCompound("execution");
            if (!value.contains("actor", Tag.TAG_STRING) || !value.contains("kind", Tag.TAG_STRING)
                    || !value.contains("owner", Tag.TAG_STRING) || !value.contains("generation", Tag.TAG_LONG))
                throw new IllegalStateException("incomplete captured execution");
            execution = Optional.of(new ActorExecutionId(new SubjectId(value.getString("actor")),
                    ActorActivityKind.valueOf(value.getString("kind")), new SubjectId(value.getString("owner")),
                    value.getLong("generation")));
        } else if (tag.contains("execution")) throw new IllegalStateException("unexpected captured execution");
        return new FrontierV3ActorBodyDeparture(identity, tag.getLong("residenceGeneration"),
                new SceneMemberPosition(identity.actorId(), position(tag, "observed"), new FixedScalar(tag.getLong("health"))),
                position(tag, "canonical"), new FixedScalar(tag.getLong("canonicalHealth")), execution,
                hand(tag, "offhand"), hand(tag, "mainhand"), tag.contains("attachedStorage", Tag.TAG_COMPOUND)
                    ? Optional.of(FrontierV3StoredAttachedStorage.load(tag.getCompound("attachedStorage"))) : Optional.empty());
    }

    private static void position(CompoundTag tag, String key, BodyPosition position) {
        tag.putInt(key + "X", position.x()); tag.putInt(key + "Y", position.y()); tag.putInt(key + "Z", position.z());
    }
    private static BodyPosition position(CompoundTag tag, String key) {
        if (!tag.contains(key + "X", Tag.TAG_INT) || !tag.contains(key + "Y", Tag.TAG_INT)
                || !tag.contains(key + "Z", Tag.TAG_INT)) throw new IllegalStateException("incomplete captured body position");
        return new BodyPosition(tag.getInt(key + "X"), tag.getInt(key + "Y"), tag.getInt(key + "Z"));
    }
    private static void hand(CompoundTag tag, String key, Optional<FrontierV3ActorBodyDeparture.HandStack> hand) {
        tag.putBoolean(key + "Present", hand.isPresent());
        hand.ifPresent(stack -> {
            var value = new CompoundTag(); value.putString("item", stack.itemKind()); value.putInt("count", stack.quantity());
            stack.writeIdentity(value);
            tag.put(key, value);
        });
    }
    private static Optional<FrontierV3ActorBodyDeparture.HandStack> hand(CompoundTag tag, String key) {
        if (!tag.contains(key + "Present", Tag.TAG_BYTE)) throw new IllegalStateException("missing hand evidence declaration");
        if (!tag.getBoolean(key + "Present")) {
            if (tag.contains(key)) throw new IllegalStateException("unexpected hand evidence");
            return Optional.empty();
        }
        if (!tag.contains(key, Tag.TAG_COMPOUND)) throw new IllegalStateException("missing hand evidence");
        var value = tag.getCompound(key);
        if (!value.contains("item", Tag.TAG_STRING) || !value.contains("count", Tag.TAG_INT))
            throw new IllegalStateException("incomplete hand evidence");
        return Optional.of(new FrontierV3ActorBodyDeparture.HandStack(value.getString("item"), value.getInt("count"), HandStack.readIdentity(value)));
    }
}
