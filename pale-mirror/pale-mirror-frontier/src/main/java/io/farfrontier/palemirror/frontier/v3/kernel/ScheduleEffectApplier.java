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
            // still fences it; only explicit canonical holds may be bypassed at admission.
            ScheduledAction head = schedules.head(action -> action.id().equals(consumed.scheduleId()) || !held.test(action));
            if (head == null || !head.id().equals(consumed.scheduleId())) {
                throw new IllegalStateException("schedule consumption is not the due queue head: "
                        + consumed.scheduleId().value() + "; eligible head=" + head);
            }
            require(schedules.cancel(head.id()), head.id());
        } else {
            throw new IllegalStateException("unknown schedule effect: " + effect.type());
        }
    }

    /** Replay applies already-admitted facts, not today's process eligibility policy. */
    static void applyCommitted(ScheduledActionQueue.Mutation schedules, ScheduleEffect effect,
                               io.farfrontier.palemirror.frontier.v3.api.SimInstant instant) {
        if (effect instanceof ScheduleEffect.Consumed consumed) {
            ScheduledAction action = schedules.find(consumed.scheduleId());
            if (action == null) {
                throw new IllegalStateException("committed schedule does not exist: " + consumed.scheduleId().value());
            }
            if (action.dueAt().compareTo(instant) > 0) {
                throw new IllegalStateException("committed schedule consumed before due time: " + consumed.scheduleId().value());
            }
            require(schedules.cancel(action.id()), action.id());
        } else {
            apply(schedules, effect);
        }
    }

    private static void require(boolean changed, io.farfrontier.palemirror.frontier.v3.api.ScheduleId id) {
        if (!changed) throw new IllegalStateException("schedule does not exist: " + id.value());
    }
}
