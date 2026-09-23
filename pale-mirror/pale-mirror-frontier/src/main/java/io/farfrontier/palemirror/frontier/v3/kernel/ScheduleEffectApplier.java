package io.farfrontier.palemirror.frontier.v3.kernel;

/** Shared fail-closed interpreter for persisted schedule-effect events. */
final class ScheduleEffectApplier {
    private ScheduleEffectApplier() {}

    static void apply(ScheduledActionQueue.Mutation schedules, ScheduleEffect effect) {
        apply(schedules, effect, ignored -> false);
    }

    static void apply(ScheduledActionQueue.Mutation schedules, ScheduleEffect effect,
                      java.util.function.Predicate<ScheduledAction> held) {
        if (effect instanceof ScheduleEffect.Created created) {
            schedules.schedule(created.action());
        } else if (effect instanceof ScheduleEffect.Cancelled cancelled) {
            require(schedules.cancel(cancelled.scheduleId()), cancelled.scheduleId());
        } else if (effect instanceof ScheduleEffect.Rescheduled rescheduled) {
            require(schedules.cancel(rescheduled.scheduleId()), rescheduled.scheduleId());
            schedules.schedule(rescheduled.replacement());
        } else if (effect instanceof ScheduleEffect.Consumed consumed) {
            // A HOT command may consume its own held continuation. Earlier runnable work
            // still fences it; only explicit canonical holds may be bypassed in live/replay.
            ScheduledAction head = schedules.head(action -> action.id().equals(consumed.scheduleId()) || !held.test(action));
            if (head == null || !head.id().equals(consumed.scheduleId())) {
                throw new IllegalStateException("schedule consumption is not the due queue head: " + consumed.scheduleId().value());
            }
            require(schedules.cancel(head.id()), head.id());
        } else {
            throw new IllegalStateException("unknown schedule effect: " + effect.type());
        }
    }

    private static void require(boolean changed, io.farfrontier.palemirror.frontier.v3.api.ScheduleId id) {
        if (!changed) throw new IllegalStateException("schedule does not exist: " + id.value());
    }
}
