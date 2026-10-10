package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

/** Engine-owned event payloads that atomically mutate the persisted due-action index. */
public sealed interface ScheduleEffect extends FrontierPayload
        permits ScheduleEffect.Created, ScheduleEffect.Cancelled, ScheduleEffect.Rescheduled, ScheduleEffect.Consumed,
        ScheduleEffect.ReconsiderationRequested {
    /**
     * Explicit invalidation hint, not an effect receipt or an exact continuation. The producer
     * declares the owner and review kind in the action; the kernel retains one pending review
     * for that tuple, at its earliest deadline. Every request remains a causal WAL fact.
     */
    record ReconsiderationRequested(ScheduledAction action) implements ScheduleEffect {
        public ReconsiderationRequested { java.util.Objects.requireNonNull(action, "review action"); }
        @Override public String type() { return "kernel.reconsideration_requested"; }
    }
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
