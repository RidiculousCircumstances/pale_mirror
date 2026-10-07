package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** No-load access to the same provider used by vanilla entity saves. */
@Mixin(PersistentEntitySectionManager.class)
public interface FrontierV3EntityPermanentStorageAccessor {
    @Accessor("permanentStorage") EntityPersistentStorage<?> frontierV3$getPermanentStorage();
    /** Existing sections include hidden bodies awaiting vanilla's final entity write. */
    @Accessor("sectionStorage") EntitySectionStorage<Entity> frontierV3$getSectionStorage();
    @Accessor("chunksToUnload") it.unimi.dsi.fastutil.longs.LongSet frontierV3$getChunksToUnload();
}
