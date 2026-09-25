package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes only the existing entity manager to the bounded stored-body inspector. */
@Mixin(ServerLevel.class)
public interface FrontierV3ServerEntityManagerAccessor {
    @Accessor("entityManager") PersistentEntitySectionManager<Entity> frontierV3$getEntityManager();
}
