package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;

/** Stable typed target for an exact physical container slot; it is not a generic subject ID. */
public record PhysicalContainerSlot(SubjectId containerId, int slot) {
    public PhysicalContainerSlot {
        Objects.requireNonNull(containerId, "physical container slot container");
        if (slot < 0 || slot > 255) throw new IllegalArgumentException("physical container slot is out of byte range");
    }
}
