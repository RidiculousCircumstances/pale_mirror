package io.farfrontier.palemirror.internal.calendar;

import net.minecraft.world.level.storage.ServerLevelData;

/** ServerLevel keeps a second alias of Level's data; both must identify the same calendar view. */
public interface CalendarServerLevelDataAccess {
    ServerLevelData calendar$getServerLevelData();
    void calendar$setServerLevelData(ServerLevelData data);
}
