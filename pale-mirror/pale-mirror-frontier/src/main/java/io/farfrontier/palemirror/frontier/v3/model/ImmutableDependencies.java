package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Owner-declared immutable read set. Identity, not deep hashing, establishes
 * unchanged inputs; absent optional records are explicit null inputs. No
 * discovery/reflection or mutable canonical state is permitted here. */
public final class ImmutableDependencies {
    private final Object[] values;
    private ImmutableDependencies(Object[] values) { this.values = values.clone(); }
    public static ImmutableDependencies of(Object... values) {
        return new ImmutableDependencies(Objects.requireNonNull(values, "read dependencies"));
    }
    public boolean matches(Object... current) {
        Objects.requireNonNull(current, "current read dependencies");
        if (values.length != current.length) return false;
        for (int index = 0; index < values.length; index++) if (values[index] != current[index]) return false;
        return true;
    }
}
