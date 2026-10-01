package io.farfrontier.palemirror.internal.calendar;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.time.SimulationCalendar;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

/** Native attachment/data-alias regression, not full farmer or graphical acceptance. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CalendarPresentationGameTests {
    private CalendarPresentationGameTests() { }

    @GameTest(batch = "pm-frontier-v3-calendar", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void calendarIsDimensionScopedReadOnlyAndRestoredOnDetach(GameTestHelper helper) {
        var level = helper.getLevel();
        var data = (CalendarLevelDataAccess) level;
        var serverData = (CalendarServerLevelDataAccess) level;
        var original = data.calendar$getLevelData();
        long gameTime = level.getGameTime();
        var other = level.getServer().getLevel(Level.NETHER);
        long otherDay = other.getDayTime();
        var instant = new AtomicLong(11_999);
        var available = new java.util.concurrent.atomic.AtomicBoolean(true);
        try {
            MinecraftCalendarPresentation.attach(level, new SimulationCalendar(24_000),
                    () -> available.get() ? OptionalLong.of(instant.get()) : OptionalLong.empty());
            helper.assertValueEqual(data.calendar$getLevelData(), serverData.calendar$getServerLevelData(),
                    "Level and ServerLevel read the same calendar view");
            helper.assertValueEqual(level.getDayTime(), 11_999L, "initial calendar follows the committed instant");
            level.setDayTime(18_000); level.setDayTimeFraction(0.5F); level.setDayTimePerTick(2.0F);
            helper.assertValueEqual(level.getDayTime(), 11_999L, "external day-time writes cannot rewrite the calendar");
            helper.assertValueEqual(level.getDayTimePerTick(), 0.0F, "no independent interpolation clock");
            instant.set(12_137); MinecraftCalendarPresentation.synchronize(level.getServer());
            helper.assertValueEqual(level.getDayTime(), 12_137L, "only actually reached fast-forward progress is projected");
            MinecraftCalendarPresentation.synchronize(level.getServer());
            helper.assertValueEqual(level.getDayTime(), 12_137L, "held time does not acquire native ticking");
            available.set(false); instant.set(18_000); MinecraftCalendarPresentation.synchronize(level.getServer());
            helper.assertValueEqual(level.getDayTime(), 12_137L, "unavailable authority freezes its last valid frame");
            helper.assertValueEqual(level.getGameTime(), gameTime, "presentation must not advance physics time");
            helper.assertValueEqual(other.getDayTime(), otherDay, "unattached dimensions are untouched");
            MinecraftCalendarPresentation.detach(level);
            helper.assertValueEqual(data.calendar$getLevelData(), original, "detach restores original world data");
            available.set(true); instant.set(12_137);
            MinecraftCalendarPresentation.attach(level, new SimulationCalendar(24_000), () -> OptionalLong.of(instant.get()));
            helper.assertValueEqual(level.getDayTime(), 12_137L, "reattachment derives exactly the recovered phase");
        } finally {
            MinecraftCalendarPresentation.detach(level);
        }
        helper.succeed();
    }
}
