package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;
import java.util.UUID;

/** One target-local effect witness, saved with its health; never authority to replay damage. */
record FrontierV3SceneStrikeReceipt(WorldId world, PhysicalSceneBinding scene,
        PhysicalIntentLifecycleOwner owner, long effectEpoch, UUID targetEntity, SceneStrikeObservation observation) {
    static final String KEY = "pale_mirror_frontier_v3_scene_strike_receipt";

    FrontierV3SceneStrikeReceipt {
        Objects.requireNonNull(world); Objects.requireNonNull(scene); Objects.requireNonNull(owner);
        Objects.requireNonNull(targetEntity); Objects.requireNonNull(observation);
        if (effectEpoch < 1 || !targetEntity.equals(SceneLease.deterministicEntityId(world, observation.targetId()))) {
            throw new IllegalArgumentException("invalid exact strike witness identity");
        }
    }

    boolean matches(FrontierWorldState state, SceneLease lease, PhysicalIntent intent, UUID actualEntity, FixedScalar actualHealth) {
        var fence = state.fencedRecovery().current().get(FencedRecoveryPhysicalIntentSupport.bindingId(intent));
        return world.equals(state.bootstrap().worldId()) && world.equals(lease.worldId())
                && scene.leaseId().equals(lease.id()) && scene.revision() == lease.revision()
                && SceneStrikeStateSupport.boundTo(lease, intent) && owner == intent.lifecycleOwner()
                && intent.kind() == PhysicalIntentKind.SCENE_STRIKE && observation.intentId().equals(intent.id())
                && observation.attackerId().equals(intent.roles().require(PhysicalIntentSubjectRole.ATTACKER))
                && observation.targetId().equals(intent.roles().require(PhysicalIntentSubjectRole.TARGET))
                && targetEntity.equals(actualEntity) && actualHealth.equals(observation.targetHealthAfter())
                && fence != null && fence.asset() == FencedRecoveryAsset.EFFECT
                && fence.ownerId().equals(intent.causeSubjectId()) && fence.authorityEpoch() == effectEpoch;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("format", 1); tag.putString("world", world.value());
        tag.putString("lease", scene.leaseId().value()); tag.putLong("revision", scene.revision());
        tag.putInt("ownerVersion", PhysicalIntentLifecycleOwner.CODEC_VERSION); tag.putString("owner", owner.stableId());
        tag.putLong("epoch", effectEpoch); tag.putUUID("entity", targetEntity);
        tag.putString("intent", observation.intentId().value()); tag.putString("observation", observation.id().value());
        tag.putString("attacker", observation.attackerId().value()); tag.putString("target", observation.targetId().value());
        tag.putLong("before", observation.targetHealthBefore().raw()); tag.putLong("after", observation.targetHealthAfter().raw());
        return tag;
    }

    boolean terminallyAbandoned(FrontierWorldState state) {
        var intent = state.physicalIntents().get(observation.intentId());
        if (!world.equals(state.bootstrap().worldId()) || intent == null || intent.kind() != PhysicalIntentKind.SCENE_STRIKE
                || intent.status() != PhysicalIntentStatus.CONFLICTED || owner != intent.lifecycleOwner()
                || !intent.roles().scene().filter(scene::equals).isPresent()
                || !observation.attackerId().equals(intent.roles().require(PhysicalIntentSubjectRole.ATTACKER))
                || !observation.targetId().equals(intent.roles().require(PhysicalIntentSubjectRole.TARGET))) return false;
        var tombstone = state.fencedRecovery().tombstones().get(FencedRecoveryPhysicalIntentSupport.bindingId(intent));
        return tombstone != null && tombstone.asset() == FencedRecoveryAsset.EFFECT
                && tombstone.ownerId().equals(intent.causeSubjectId()) && tombstone.retiredEpoch() == effectEpoch
                && tombstone.disposition() == FencedRecoveryDisposition.ABANDON;
    }

    static FrontierV3SceneStrikeReceipt load(CompoundTag tag) {
        for (String key : java.util.List.of("world", "lease", "owner", "intent", "observation", "attacker", "target")) {
            if (!tag.contains(key, Tag.TAG_STRING) || tag.getString(key).length() > 512) throw new IllegalArgumentException("invalid strike witness " + key);
        }
        for (String key : java.util.List.of("revision", "epoch", "before", "after")) {
            if (!tag.contains(key, Tag.TAG_LONG)) throw new IllegalArgumentException("missing strike witness " + key);
        }
        if (!tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != 1
                || !tag.contains("ownerVersion", Tag.TAG_INT) || !tag.hasUUID("entity")) {
            throw new IllegalArgumentException("unsupported or incomplete strike witness");
        }
        return new FrontierV3SceneStrikeReceipt(new WorldId(tag.getString("world")),
                new PhysicalSceneBinding(new SceneLeaseId(tag.getString("lease")), tag.getLong("revision")),
                PhysicalIntentLifecycleOwner.fromWire(tag.getInt("ownerVersion"), tag.getString("owner")),
                tag.getLong("epoch"), tag.getUUID("entity"), new SceneStrikeObservation(
                new PhysicalObservationId(tag.getString("observation")), new PhysicalIntentId(tag.getString("intent")),
                new SubjectId(tag.getString("attacker")), new SubjectId(tag.getString("target")),
                new FixedScalar(tag.getLong("before")), new FixedScalar(tag.getLong("after"))));
    }
}
