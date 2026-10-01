package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.calendar.CalendarServerLevelDataAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerLevel.class)
public interface CalendarServerLevelDataMixin extends CalendarServerLevelDataAccess {
    @Override @Accessor("serverLevelData") ServerLevelData calendar$getServerLevelData();
    @Override @Mutable @Accessor("serverLevelData") void calendar$setServerLevelData(ServerLevelData data);
}
