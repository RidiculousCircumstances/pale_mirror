package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Collection;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/** Ephemeral service order, never canonical progress or authority. One retained key per queue. */
final class FrontierV3FairTurn<Key extends Comparable<? super Key>> {
    private Key previous;
    private boolean admissionNext;

    /** Advances on an attempt, including a waiting/unknown item, not only on successful work. */
    <Value> Optional<Value> next(Collection<Value> candidates, Function<Value, Key> identity) {
        Value first = null;
        Value after = null;
        Key firstKey = null;
        Key afterKey = null;
        for (Value candidate : candidates) {
            Key key = java.util.Objects.requireNonNull(identity.apply(candidate));
            if (firstKey == null || key.compareTo(firstKey) < 0) {
                first = candidate;
                firstKey = key;
            }
            if ((previous == null || key.compareTo(previous) > 0)
                    && (afterKey == null || key.compareTo(afterKey) < 0)) {
                after = candidate;
                afterKey = key;
            }
        }
        Value selected = after != null ? after : first;
        if (selected == null) return Optional.empty();
        previous = after != null ? afterKey : firstKey;
        return Optional.of(selected);
    }

    /**
     * Alternate active work and admission attempts. A false admission result must mean no
     * attempt/mutation, so falling through may execute one active item within the same budget.
     */
    <Value> boolean run(Collection<Value> active, Function<Value, Key> identity,
                        Consumer<Value> execute, BooleanSupplier admit) {
        if (admissionNext || active.isEmpty()) {
            admissionNext = false;
            if (admit.getAsBoolean()) return true;
        }
        Optional<Value> selected = next(active, identity);
        if (selected.isEmpty()) return false;
        admissionNext = true;
        execute.accept(selected.orElseThrow());
        return true;
    }
}
