package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;

/** Stable read-model request; later waves add typed query fields without exposing aggregates. */
public record ProjectionQuery(String view) {
    public ProjectionQuery {
        Objects.requireNonNull(view, "view");
        if (!view.matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("projection view is invalid: " + view);
        }
    }

    public static ProjectionQuery summary() {
        return new ProjectionQuery("summary");
    }
}
