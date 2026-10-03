package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Replays an already durable physical transfer, never discovers a new owner. */
final class FrontierV3ActorHandoffRecovery {
    private FrontierV3ActorHandoffRecovery() { }

    /** Read-only admission evidence, including when canonical replay is currently forbidden. */
    static boolean retainsRecordedBody(ServerLevel level, FrontierWorldState state, Entity body) {
        if (body == null || body.isRemoved() || body.level() != level) return false;
        var observed = FrontierV3ActorOwnerBinding.from(body).orElse(null);
        if (observed == null) return false;
        var declaration = observed.declaration();
        if (!state.actorLocations().containsKey(declaration.actorId())
                || !FrontierV3AmbientActorExecutor.entityId(state, declaration.actorId()).equals(declaration.entityId())) return false;
        Entity indexed = level.getEntity(declaration.entityId());
        if (indexed != null && indexed != body) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        return ledger.pendingHandoff(declaration.actorId()).filter(value -> value.contains(observed)).isPresent()
                || ledger.pendingAdoption(declaration.actorId()).filter(value -> value.matches(observed)).isPresent()
                || ledger.firstAdmission(declaration.actorId()).filter(value -> value.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING
                    && value.attempt().orElseThrow().equals(observed)).isPresent();
    }

    static boolean resume(ServerLevel level, FrontierWorldState state, Entity body) {
        if (body == null || body.isRemoved() || body.level() != level) return false;
        var from = FrontierV3ActorOwnerBinding.from(body).orElse(null);
        if (from == null) return false;
        var handoff = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId())
                .pendingHandoff(from.declaration().actorId()).orElse(null);
        if (handoff == null || !handoff.contains(from)) return false;
        return switch (handoff.current().owner()) {
            case AMBIENT_LEASE -> resumeAmbient(level, state, body);
            case SCENE_LEASE -> resumeScene(level, state, body, from, handoff);
        };
    }

    /** Exact lookup by retained scene identity. Recognition alone never promotes a scene to HOT. */
    static boolean currentSceneTarget(FrontierWorldState state, FrontierV3ActorOwnerBinding binding) {
        if (binding.scene().isEmpty()) return false;
        var target = binding.declaration();
        var scene = state.sceneLeases().get(binding.scene().orElseThrow());
        var actor = state.actorLocations().get(target.actorId());
        var ambient = state.ambientLeases().get(target.actorId());
        if (scene == null || scene.status() == SceneLeaseStatus.CLOSED || scene.status() == SceneLeaseStatus.CONFLICT
                || scene.revision() != target.authorityRevision() || actor == null
                || actor.condition().status() != ActorLifeStatus.ALIVE
                || ambient != null && ambient.status() != AmbientLeaseStatus.CLOSED
                || scene.members().stream().noneMatch(member -> member.actorId().equals(target.actorId())
                    && member.entityId().equals(target.entityId()))) return false;
        return target.equals(FrontierV3AmbientActorExecutor.carrierDeclaration(state, target.actorId(),
                FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE,
                FrontierV3AmbientActorExecutor.entityId(state, target.actorId()),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, scene.revision(), target.epoch()));
    }

    private static boolean resumeScene(ServerLevel level, FrontierWorldState state, Entity body,
                                      FrontierV3ActorOwnerBinding from, FrontierV3ActorHandoff handoff) {
        var binding = handoff.currentBinding(); var target = binding.declaration();
        if (!currentSceneTarget(state, binding)) return false;
        var metadata = body.getPersistentData();
        if (from.equals(binding) && target.actorId().value().equals(metadata.getString(FrontierV3SceneExecutor.ACTOR_KEY))
                && !metadata.contains(FrontierV3AmbientActorExecutor.ACTOR_KEY)
                && !metadata.contains(FrontierV3AmbientActorExecutor.KIND_KEY)
                && metadata.getLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY) == target.epoch()) return false;
        Entity indexed = level.getEntity(target.entityId());
        if (indexed != null && indexed != body) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!ledger.prepareHandoff(from, binding)) return false;
        ledger.persist(level, state.bootstrap().worldId());
        boolean transferred = FrontierV3ActorHandoffAdmission.transfer(level, state.bootstrap().worldId(), body, binding);
        if (transferred) FrontierV3ControlledMobMotion.stop((net.minecraft.world.entity.Mob) body);
        return transferred;
    }

    static boolean resumeAmbient(ServerLevel level, FrontierWorldState state, Entity body) {
        if (body == null || body.isRemoved() || body.level() != level) return false;
        var from = FrontierV3ActorOwnerBinding.from(body).orElse(null);
        if (from == null) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var handoff = ledger.pendingHandoff(from.declaration().actorId()).orElse(null);
        if (handoff == null || !handoff.contains(from)) return false;
        var target = handoff.current();
        if (target.owner() != FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE) return false;
        var ambient = state.ambientLeases().get(target.actorId());
        var actor = state.actorLocations().get(target.actorId());
        if (ambient == null || ambient.status() == AmbientLeaseStatus.CLOSED
                || ambient.revision() != target.authorityRevision() || actor == null
                || actor.condition().status() != ActorLifeStatus.ALIVE
                || state.sceneLeases().values().stream().anyMatch(scene -> scene.status() != SceneLeaseStatus.CLOSED
                    && scene.members().stream().anyMatch(member -> member.actorId().equals(target.actorId())))) return false;
        var expected = FrontierV3AmbientActorExecutor.carrierDeclaration(state, target.actorId(),
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3AmbientActorExecutor.entityId(state, target.actorId()),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, ambient.revision(), target.epoch());
        if (!expected.equals(target)) return false;
        var metadata = body.getPersistentData();
        if (from.equals(handoff.currentBinding()) && FrontierV3AmbientActorExecutor.owned(body, target.actorId(),
                target.kind() == ActorKind.BIOFORM)
                && !metadata.contains(FrontierV3SceneExecutor.LEASE_KEY)
                && !metadata.contains(FrontierV3SceneExecutor.REVISION_KEY)
                && metadata.getLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY) == target.epoch()) return false;
        Entity indexed = level.getEntity(target.entityId());
        if (indexed != null && indexed != body) return false;
        if (!ledger.prepareHandoff(from, handoff.currentBinding())) return false;
        // Also covers a prior failed persistence attempt whose in-memory target is current.
        ledger.persist(level, state.bootstrap().worldId());
        boolean transferred = FrontierV3ActorHandoffAdmission.transfer(level, state.bootstrap().worldId(), body, handoff.currentBinding());
        if (transferred) FrontierV3ControlledMobMotion.stop((net.minecraft.world.entity.Mob) body);
        return transferred;
    }
}
