package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.util.List;
import java.util.Objects;

/** Deterministic recovery of retained transactions; malformed history never yields a partial result. */
public final class TransactionReplayer {
    private TransactionReplayer() {}

    public static <S> ReplayResult<S> replay(
            WorldId worldId, S initialState, SimInstant initialInstant, List<ScheduledAction> initialSchedules,
            List<TransactionRecord> transactions, EventReducer<S> reducer, StateCodec<S> stateCodec
    ) {
        Objects.requireNonNull(worldId, "world id");
        S state = Objects.requireNonNull(initialState, "initial state");
        SimInstant instant = Objects.requireNonNull(initialInstant, "initial instant");
        ScheduledActionQueue schedules = new ScheduledActionQueue();
        List.copyOf(initialSchedules).forEach(schedules::schedule);
        Revision revision = Revision.ZERO;
        for (TransactionRecord transaction : List.copyOf(transactions)) {
            validate(transaction, worldId, revision, instant);
            S next = state;
            ScheduledActionQueue nextSchedules = schedules.copy();
            for (FrontierEvent event : transaction.events()) {
                if (event.payload() instanceof ScheduleEffect effect) ScheduleEffectApplier.apply(nextSchedules, effect);
                else next = Objects.requireNonNull(reducer.apply(next, event), "reducer state");
            }
            if (stateCodec.encode(next) == null) throw new IllegalStateException("state codec returned null");
            state = next;
            schedules = nextSchedules;
            revision = transaction.revision();
            instant = transaction.instant();
        }
        return new ReplayResult<>(state, revision, instant, schedules.snapshot());
    }

    private static void validate(TransactionRecord record, WorldId world, Revision prior, SimInstant priorInstant) {
        if (!world.equals(record.worldId()) || !record.revision().equals(prior.next())
                || record.instant().compareTo(priorInstant) < 0) {
            throw new IllegalArgumentException("invalid transaction sequence at revision " + record.revision().value());
        }
        for (FrontierEvent event : record.events()) {
            if (!event.transactionId().equals(record.id()) || !event.worldId().equals(world)
                    || !event.revision().equals(record.revision()) || !event.instant().equals(record.instant())) {
                throw new IllegalArgumentException("transaction event envelope does not match its record");
            }
        }
    }

    public record ReplayResult<S>(S state, Revision revision, SimInstant instant, List<ScheduledAction> schedules) {
        public ReplayResult { schedules = List.copyOf(schedules); }
    }
}
