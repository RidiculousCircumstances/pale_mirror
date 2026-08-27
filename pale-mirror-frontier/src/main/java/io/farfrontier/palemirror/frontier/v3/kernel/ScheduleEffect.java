package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

/** Engine-owned event payloads that atomically mutate the persisted due-action index. */
public sealed interface ScheduleEffect extends FrontierPayload
        permits ScheduleEffect.Created, ScheduleEffect.Cancelled, ScheduleEffect.Rescheduled, ScheduleEffect.Consumed {
    record Created(ScheduledAction action) implements ScheduleEffect {
        @Override
        public String type() {
            return "kernel.schedule_created";
        }
    }

    record Cancelled(io.farfrontier.palemirror.frontier.v3.api.ScheduleId scheduleId) implements ScheduleEffect {
        @Override
        public String type() {
            return "kernel.schedule_cancelled";
        }
    }

    record Rescheduled(
            io.farfrontier.palemirror.frontier.v3.api.ScheduleId scheduleId,
            ScheduledAction replacement
    ) implements ScheduleEffect {
        @Override
        public String type() {
            return "kernel.schedule_rescheduled";
        }
    }

    record Consumed(io.farfrontier.palemirror.frontier.v3.api.ScheduleId scheduleId) implements ScheduleEffect {
        @Override
        public String type() {
            return "kernel.schedule_consumed";
        }
    }
}
