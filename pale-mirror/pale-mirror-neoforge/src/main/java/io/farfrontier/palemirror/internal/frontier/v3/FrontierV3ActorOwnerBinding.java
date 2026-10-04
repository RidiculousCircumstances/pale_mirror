package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import java.util.Objects;
import java.util.Optional;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;

/** Complete activity-independent body address. No scene or assignment can own this binding. */
record FrontierV3ActorOwnerBinding(Declaration declaration) {
    FrontierV3ActorOwnerBinding {
        Objects.requireNonNull(declaration);
    }
    static FrontierV3ActorOwnerBinding body(Declaration declaration) {
        return new FrontierV3ActorOwnerBinding(declaration);
    }
    static Optional<FrontierV3ActorOwnerBinding> from(Entity entity) {
        return declaredBy(entity).map(FrontierV3ActorOwnerBinding::body);
    }
    /** Only initial physical admission writes body authority; changing an activity never does. */
    void stamp(Entity entity) {
        FrontierV3ActorCarrierComposition.stamp(entity, declaration);
    }
    boolean matchesSaved(CompoundTag entity) {
        return FrontierV3ActorAdoptionPersistence.matchesSaved(declaration, entity);
    }
    CompoundTag save() {
        return FrontierV3ActorAdoption.saveDeclaration(declaration);
    }
    static FrontierV3ActorOwnerBinding load(CompoundTag tag) {
        if (tag.contains("sceneLease")) throw new IllegalArgumentException("scene-owned body schema is unsupported");
        return body(FrontierV3ActorAdoption.loadDeclaration(tag));
    }
}
