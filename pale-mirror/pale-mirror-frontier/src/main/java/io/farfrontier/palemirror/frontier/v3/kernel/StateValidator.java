package io.farfrontier.palemirror.frontier.v3.kernel;

import java.util.Objects;
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

    void validateTransition(S previous, S next);

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
