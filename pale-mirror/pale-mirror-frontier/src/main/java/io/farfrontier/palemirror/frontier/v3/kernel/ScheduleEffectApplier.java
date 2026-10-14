package io.farfrontier.palemirror.frontier.v3.kernel;

/** Shared fail-closed interpreter for persisted schedule-effect events. */
final class ScheduleEffectApplier {
    private ScheduleEffectApplier() {}

    static void apply(ScheduledActionQueue.Mutation schedules, ScheduleEffect effect) {
        if (effect instanceof ScheduleEffect.Created created) {
            schedules.schedule(created.action());
        } else if (effect instanceof ScheduleEffect.ReconsiderationRequested requested) {
            schedules.requestReconsideration(requested.action());
        } else if (effect instanceof ScheduleEffect.Cancelled cancelled) {
            require(schedules.cancel(cancelled.scheduleId()), cancelled.scheduleId());
        } else if (effect instanceof ScheduleEffect.Rescheduled rescheduled) {
            require(schedules.cancel(rescheduled.scheduleId()), rescheduled.scheduleId());
            schedules.schedule(rescheduled.replacement());
        } else if (effect instanceof ScheduleEffect.Consumed consumed) {
            throw new IllegalStateException("schedule consumption requires explicit admitted time: " + consumed.scheduleId().value());
        } else {
            throw new IllegalStateException("unknown schedule effect: " + effect.type());
        }
    }
    /** Live selection already checked this owner/generation. Waiting strangers are not a consumption fence. */
    static void apply(ScheduledActionQueue.Mutation schedules, ScheduleEffect effect,
                      io.farfrontier.palemirror.frontier.v3.api.SimInstant instant,
                      io.farfrontier.palemirror.frontier.v3.api.SubjectId subject) {
        if (effect instanceof ScheduleEffect.Consumed consumed) consumeExactDue(schedules, consumed, instant, subject);
        else apply(schedules, effect);
    }

    /** Replay applies already-admitted facts, not today's process eligibility policy. */
    static void applyCommitted(ScheduledActionQueue.Mutation schedules, ScheduleEffect effect,
                               io.farfrontier.palemirror.frontier.v3.api.SimInstant instant,
                               io.farfrontier.palemirror.frontier.v3.api.SubjectId subject) {
        apply(schedules, effect, instant, subject);
    }
    private static void consumeExactDue(ScheduledActionQueue.Mutation schedules, ScheduleEffect.Consumed consumed,
                                        io.farfrontier.palemirror.frontier.v3.api.SimInstant instant,
                                        io.farfrontier.palemirror.frontier.v3.api.SubjectId subject) {
            ScheduledAction action = schedules.find(consumed.scheduleId());
            if (action == null) {
                throw new IllegalStateException("committed schedule does not exist: " + consumed.scheduleId().value());
            }
            if (!action.subject().equals(subject)) throw new IllegalArgumentException("consumed schedule belongs to a different declared subject");
            if (action.dueAt().compareTo(instant) > 0) {
                throw new IllegalStateException("committed schedule consumed before due time: " + consumed.scheduleId().value());
            }
            require(schedules.cancel(action.id()), action.id());
    }

    private static void require(boolean changed, io.farfrontier.palemirror.frontier.v3.api.ScheduleId id) {
        if (!changed) throw new IllegalStateException("schedule does not exist: " + id.value());
    }
}
