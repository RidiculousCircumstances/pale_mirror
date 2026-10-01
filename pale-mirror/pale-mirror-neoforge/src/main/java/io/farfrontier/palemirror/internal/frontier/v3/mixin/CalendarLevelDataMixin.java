package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.calendar.CalendarLevelDataAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Level.class)
public interface CalendarLevelDataMixin extends CalendarLevelDataAccess {
    @Override @Accessor("levelData") WritableLevelData calendar$getLevelData();
    @Override @Mutable @Accessor("levelData") void calendar$setLevelData(WritableLevelData data);
}
