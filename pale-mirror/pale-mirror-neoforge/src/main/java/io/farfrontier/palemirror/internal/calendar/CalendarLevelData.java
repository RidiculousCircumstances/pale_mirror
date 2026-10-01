package io.farfrontier.palemirror.internal.calendar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.timers.TimerQueue;

import java.util.UUID;

/** Read-only day-time view. Everything unrelated to the calendar stays with Minecraft's data owner. */
final class CalendarLevelData implements ServerLevelData {
    private final ServerLevelData delegate;
    private long projectedTime;

    CalendarLevelData(ServerLevelData delegate, long projectedTime) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "Minecraft level data");
        this.projectedTime = projectedTime;
    }

    void project(long time) { projectedTime = time; }
    @Override public long getDayTime() { return projectedTime; }
    @Override public float getDayTimeFraction() { return 0.0F; }
    @Override public float getDayTimePerTick() { return 0.0F; }
    // Sleep and vanilla /time cannot write back to an independently owned canonical calendar.
    @Override public void setDayTime(long time) { }
    @Override public void setDayTimeFraction(float fraction) { }
    @Override public void setDayTimePerTick(float speed) { }

    @Override public BlockPos getSpawnPos() { return delegate.getSpawnPos(); }
    @Override public float getSpawnAngle() { return delegate.getSpawnAngle(); }
    @Override public long getGameTime() { return delegate.getGameTime(); }
    @Override public void setGameTime(long time) { delegate.setGameTime(time); }
    @Override public String getLevelName() { return delegate.getLevelName(); }
    @Override public int getClearWeatherTime() { return delegate.getClearWeatherTime(); }
    @Override public void setClearWeatherTime(int time) { delegate.setClearWeatherTime(time); }
    @Override public boolean isThundering() { return delegate.isThundering(); }
    @Override public void setThundering(boolean value) { delegate.setThundering(value); }
    @Override public int getThunderTime() { return delegate.getThunderTime(); }
    @Override public void setThunderTime(int time) { delegate.setThunderTime(time); }
    @Override public boolean isRaining() { return delegate.isRaining(); }
    @Override public void setRaining(boolean value) { delegate.setRaining(value); }
    @Override public int getRainTime() { return delegate.getRainTime(); }
    @Override public void setRainTime(int time) { delegate.setRainTime(time); }
    @Override public GameType getGameType() { return delegate.getGameType(); }
    @Override public void setGameType(GameType type) { delegate.setGameType(type); }
    @Override public boolean isHardcore() { return delegate.isHardcore(); }
    @Override public boolean isAllowCommands() { return delegate.isAllowCommands(); }
    @Override public boolean isInitialized() { return delegate.isInitialized(); }
    @Override public void setInitialized(boolean value) { delegate.setInitialized(value); }
    @Override public GameRules getGameRules() { return delegate.getGameRules(); }
    @Override public WorldBorder.Settings getWorldBorder() { return delegate.getWorldBorder(); }
    @Override public void setWorldBorder(WorldBorder.Settings settings) { delegate.setWorldBorder(settings); }
    @Override public Difficulty getDifficulty() { return delegate.getDifficulty(); }
    @Override public boolean isDifficultyLocked() { return delegate.isDifficultyLocked(); }
    @Override public TimerQueue<MinecraftServer> getScheduledEvents() { return delegate.getScheduledEvents(); }
    @Override public int getWanderingTraderSpawnDelay() { return delegate.getWanderingTraderSpawnDelay(); }
    @Override public void setWanderingTraderSpawnDelay(int value) { delegate.setWanderingTraderSpawnDelay(value); }
    @Override public int getWanderingTraderSpawnChance() { return delegate.getWanderingTraderSpawnChance(); }
    @Override public void setWanderingTraderSpawnChance(int value) { delegate.setWanderingTraderSpawnChance(value); }
    @Override public UUID getWanderingTraderId() { return delegate.getWanderingTraderId(); }
    @Override public void setWanderingTraderId(UUID id) { delegate.setWanderingTraderId(id); }
    @Override public void setSpawn(BlockPos position, float angle) { delegate.setSpawn(position, angle); }
}
