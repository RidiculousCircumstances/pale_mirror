package io.farfrontier.palemirror.internal.calendar;

import net.minecraft.world.level.storage.WritableLevelData;

/** Narrow attachment seam; only the calendar presentation owner replaces this reference. */
public interface CalendarLevelDataAccess {
    WritableLevelData calendar$getLevelData();
    void calendar$setLevelData(WritableLevelData data);
}
