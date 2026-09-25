package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import java.util.Objects;
import java.util.Optional;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;

/** Complete owner address for a recorded physical handoff. No registry search fills missing identity. */
record FrontierV3ActorOwnerBinding(Declaration declaration, Optional<SceneLeaseId> scene) {
    FrontierV3ActorOwnerBinding {
        Objects.requireNonNull(declaration); Objects.requireNonNull(scene);
        if ((declaration.owner() == Owner.SCENE_LEASE) != scene.isPresent())
            throw new IllegalArgumentException("actor owner requires its exact scene identity");
    }
    static FrontierV3ActorOwnerBinding ambient(Declaration declaration) {
        return new FrontierV3ActorOwnerBinding(declaration, Optional.empty());
    }
    static FrontierV3ActorOwnerBinding scene(Declaration declaration, SceneLeaseId scene) {
        return new FrontierV3ActorOwnerBinding(declaration, Optional.of(scene));
    }
    static Optional<FrontierV3ActorOwnerBinding> from(Entity entity) {
        return declaredBy(entity).flatMap(declaration -> {
            try {
                if (declaration.owner() == Owner.AMBIENT_LEASE) return Optional.of(ambient(declaration));
                var tag = entity.getPersistentData();
                if (!tag.contains(FrontierV3SceneExecutor.LEASE_KEY, Tag.TAG_STRING)
                        || !tag.contains(FrontierV3SceneExecutor.REVISION_KEY, Tag.TAG_LONG)
                        || tag.getLong(FrontierV3SceneExecutor.REVISION_KEY) != declaration.authorityRevision()) return Optional.empty();
                return Optional.of(scene(declaration, new SceneLeaseId(tag.getString(FrontierV3SceneExecutor.LEASE_KEY))));
            } catch (IllegalArgumentException invalid) { return Optional.empty(); }
        });
    }
    /** One writer for the complete owner metadata; callers cannot omit a legacy fence. */
    void stamp(Entity entity) {
        var tag = entity.getPersistentData();
        tag.putLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY, declaration.epoch());
        switch (declaration.owner()) {
            case AMBIENT_LEASE -> {
                tag.remove(FrontierV3SceneExecutor.ACTOR_KEY);
                tag.remove(FrontierV3SceneExecutor.LEASE_KEY);
                tag.remove(FrontierV3SceneExecutor.REVISION_KEY);
                tag.putString(FrontierV3AmbientActorExecutor.ACTOR_KEY, declaration.actorId().value());
                tag.putString(FrontierV3AmbientActorExecutor.KIND_KEY, declaration.kind().name());
            }
            case SCENE_LEASE -> {
                tag.remove(FrontierV3AmbientActorExecutor.ACTOR_KEY);
                tag.remove(FrontierV3AmbientActorExecutor.KIND_KEY);
                tag.putString(FrontierV3SceneExecutor.ACTOR_KEY, declaration.actorId().value());
                tag.putString(FrontierV3SceneExecutor.LEASE_KEY, scene.orElseThrow().value());
                tag.putLong(FrontierV3SceneExecutor.REVISION_KEY, declaration.authorityRevision());
            }
        }
        FrontierV3ActorCarrierComposition.stamp(entity, declaration);
    }
    boolean matchesSaved(CompoundTag entity) {
        if (!FrontierV3ActorAdoptionPersistence.matchesSaved(declaration, entity)) return false;
        return scene.isEmpty() || scene.orElseThrow().value().equals(entity.getCompound("NeoForgeData")
                .getString(FrontierV3SceneExecutor.LEASE_KEY))
                && entity.getCompound("NeoForgeData").contains(FrontierV3SceneExecutor.REVISION_KEY, Tag.TAG_LONG)
                && entity.getCompound("NeoForgeData").getLong(FrontierV3SceneExecutor.REVISION_KEY) == declaration.authorityRevision();
    }
    CompoundTag save() {
        var tag = FrontierV3ActorAdoption.saveDeclaration(declaration);
        scene.ifPresent(id -> tag.putString("sceneLease", id.value()));
        return tag;
    }
    static FrontierV3ActorOwnerBinding load(CompoundTag tag) {
        var declaration = FrontierV3ActorAdoption.loadDeclaration(tag);
        if (tag.contains("sceneLease") && !tag.contains("sceneLease", Tag.TAG_STRING))
            throw new IllegalArgumentException("invalid actor scene identity");
        return new FrontierV3ActorOwnerBinding(declaration, tag.contains("sceneLease", Tag.TAG_STRING)
                ? Optional.of(new SceneLeaseId(tag.getString("sceneLease"))) : Optional.empty());
    }
}
