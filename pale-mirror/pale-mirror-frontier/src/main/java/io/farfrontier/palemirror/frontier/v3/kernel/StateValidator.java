package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;

import java.util.Objects;
import java.util.List;
import java.util.function.Consumer;

/**
 * Validates canonical aggregate state at the two different kernel boundaries.
 *
 * <p>The initial boundary is deliberately complete: it is used for bootstrap and decoded
 * recovery state. A transition may use an aggregate-specific dependency-aware audit, but it
 * still executes before its WAL transaction is made durable. Kernel fixtures that have no
 * aggregate audit use {@link #none()}, while existing complete validators can use
 * {@link #complete(Consumer)}.</p>
 */
public interface StateValidator<S> {
    void validateInitial(S state);

    /** Recovery has the retained schedule image as well as canonical state. */
    default void validateRecoveryInitial(S state, List<ScheduledAction> schedules) {
        validateInitial(state);
    }

    void validateTransition(S previous, S next);

    /**
     * Checks only newly retained schedule references when the aggregate is unchanged.
     * The kernel supplies the final mutation delta, excluding cancelled intermediate actions.
     * This must not require revalidating the unchanged aggregate or copying the whole queue.
     */
    default void validateScheduleChanges(S state, List<ScheduledAction> retainedChanges) { }

    /**
     * Transaction boundary with the complete committed batch and engine-owned schedule result.
     * Implementations that only validate aggregate state retain their existing transition audit.
     */
    default void validateTransaction(S previous, S next, List<FrontierEvent> events,
                                     List<ScheduledAction> schedulesBefore, List<ScheduledAction> schedulesAfter) {
        validateTransition(previous, next);
    }

    static <S> StateValidator<S> none() {
        return new StateValidator<>() {
            @Override public void validateInitial(S state) { }
            @Override public void validateTransition(S previous, S next) { }
        };
    }

    static <S> StateValidator<S> complete(Consumer<S> validator) {
        Objects.requireNonNull(validator, "state validator");
        return new StateValidator<>() {
            @Override public void validateInitial(S state) { validator.accept(state); }
            @Override public void validateTransition(S previous, S next) { validator.accept(next); }
        };
    }
}
