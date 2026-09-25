package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Canonical per-site hold for an already observed but not yet reconciled physical cell change. */
public record ResourceFieldWorldChangeHeld(ResourceFieldCellObserved observation) implements FrontierPayload {
    public ResourceFieldWorldChangeHeld {
        Objects.requireNonNull(observation, "held world field observation");
        if (observation.source() != ResourceFieldCellObserved.Source.WORLD
                || observation.change() == ResourceFieldCellObserved.Change.UNCHANGED)
            throw new IllegalArgumentException("field recovery hold needs one exact changed world postcondition");
    }

    @Override public String type() { return "frontier.resource_field_world_change_held"; }
}
