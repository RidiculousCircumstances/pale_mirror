package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Stable exact slot inside one HIBERNACULUM organ. */
public record HiveCocoonSlot(SubjectId hibernaculumId, int index) {
    public static final int MAX_SLOTS_PER_HIBERNACULUM = 9;

    public HiveCocoonSlot {
        Objects.requireNonNull(hibernaculumId, "hibernaculum id");
        if (index < 0 || index >= MAX_SLOTS_PER_HIBERNACULUM) {
            throw new IllegalArgumentException("cocoon slot index is outside the organ capacity");
        }
    }
}
