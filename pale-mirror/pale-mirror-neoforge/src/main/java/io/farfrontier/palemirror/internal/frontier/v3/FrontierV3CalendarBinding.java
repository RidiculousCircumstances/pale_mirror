package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.time.SimulationCalendar;
import io.farfrontier.palemirror.internal.calendar.MinecraftCalendarPresentation;
import net.minecraft.server.level.ServerLevel;

/** Composition only: the reusable presentation adapter does not know Frontier runtimes or processes. */
final class FrontierV3CalendarBinding {
    private FrontierV3CalendarBinding() { }

    static void attachActive(ServerLevel world, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        var rules = runtime.decodedState().orElseThrow().bootstrap().ruleset().residentLife();
        MinecraftCalendarPresentation.attach(world, new SimulationCalendar(rules.dayTicks()), runtime::calendarInstant);
    }
}
