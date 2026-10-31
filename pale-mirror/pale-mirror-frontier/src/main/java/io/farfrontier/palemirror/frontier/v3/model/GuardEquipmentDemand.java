package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import java.util.*;

/** Unfilled staffing places create finite equipment demand before anybody is armed or appointed. */
final class GuardEquipmentDemand implements EquipmentDemandPort {
    public Key key() { return Key.GUARD; }
    private final ImmutableKeyedInputView<SubjectId, List<EquipmentDemand>> views = new ImmutableKeyedInputView<>(256);
    public List<EquipmentDemand> read(FrontierWorldState state, SubjectId home) {
        int target = GuardEquipment.target(state);
        if (target == 0) return List.of();
        return views.get(state.bootstrap(), home, List.of(state.bootstrap().ruleset().labour()), () -> List.of(
                new EquipmentDemand("guard", List.of(GuardEquipment.CAPABILITY), target, Optional.empty(),
                        EquipmentDemand.Acquisition.PRODUCE_LOCAL)));
    }
}
