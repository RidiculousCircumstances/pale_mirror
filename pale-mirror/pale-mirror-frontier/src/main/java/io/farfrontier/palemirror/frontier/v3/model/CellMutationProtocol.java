package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

/** Family-neutral mutation sequencing. It never selects resource, crop, job or repair policy. */
public final class CellMutationProtocol {
    private CellMutationProtocol() { }
    public enum Semantics { EXTERNAL_OBSERVATION, NON_REPLAYABLE_EFFECT }
    public enum Resolution { CAPTURE, APPLY_CAPTURE, SUPERSEDE_CAPTURE, FINISH_THEN_OBSERVE_SUCCESSOR, EFFECT_AMBIGUOUS }

    /** A captured external fact is not an immutable promise that the world will stop changing. */
    public static <T> Resolution review(Optional<T> captured, T current, boolean captureCommitted, Semantics semantics) {
        Objects.requireNonNull(captured, "captured mutation fact");
        Objects.requireNonNull(current, "current physical fact");
        Objects.requireNonNull(semantics, "mutation semantics");
        if (captured.isEmpty()) {
            if (captureCommitted) throw new IllegalArgumentException("uncaptured mutation cannot be committed");
            return Resolution.CAPTURE;
        }
        if (captured.orElseThrow().equals(current)) return Resolution.APPLY_CAPTURE;
        if (semantics == Semantics.NON_REPLAYABLE_EFFECT) return Resolution.EFFECT_AMBIGUOUS;
        return captureCommitted ? Resolution.FINISH_THEN_OBSERVE_SUCCESSOR : Resolution.SUPERSEDE_CAPTURE;
    }

    /** One immutable reservation per exact cell. Other cells never become collateral locks. */
    public static <T> Map<CellMutationKey, T> reserve(Map<CellMutationKey, T> entries, CellMutationKey key, T value) {
        Objects.requireNonNull(key, "mutation address"); Objects.requireNonNull(value, "mutation obligation");
        if (entries.containsKey(key)) throw new IllegalArgumentException("cell already has a mutation obligation: " + key);
        var next = new LinkedHashMap<>(entries); next.put(key, value); return Map.copyOf(next);
    }
    public static <T> Map<CellMutationKey, T> release(Map<CellMutationKey, T> entries, CellMutationKey key, T exact) {
        if (!Objects.equals(entries.get(key), Objects.requireNonNull(exact)))
            throw new IllegalArgumentException("cell mutation release has stale or foreign evidence: " + key);
        var next = new LinkedHashMap<>(entries); next.remove(key); return Map.copyOf(next);
    }
}
