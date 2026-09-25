package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Final site-local release after the exact world cell and physical claim have been inspected. */
public record ResourceFieldWorldChangeAcknowledged(ResourceFieldCellObserved observation,
                                                   ResourceFieldPhysicalSurface.Condition physical) implements FrontierPayload {
    public ResourceFieldWorldChangeAcknowledged {
        Objects.requireNonNull(observation, "acknowledged world field cause");
        Objects.requireNonNull(physical, "acknowledged world field condition");
        if (observation.source() != ResourceFieldCellObserved.Source.WORLD
                || observation.change() == ResourceFieldCellObserved.Change.UNCHANGED
                || !physical.equals(observation.before()) && !physical.equals(observation.after()))
            throw new IllegalArgumentException("field world acknowledgement has a foreign cause or postcondition");
    }

    @Override public String type() { return "frontier.resource_field_world_change_acknowledged"; }
}
