package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import java.util.*;

/** Unfilled staffing places create finite equipment demand before anybody is armed or appointed. */
final class GuardEquipmentDemand implements EquipmentDemandPort {
    public Key key() { return Key.GUARD; }
    private final ImmutableKeyedInputView<SubjectId, List<EquipmentDemand>> views = new ImmutableKeyedInputView<>(256);
    public List<EquipmentDemand> read(FrontierWorldState state, SubjectId home) {
        int target = SettlementWorkPolicy.permissions(state, home).workers(ResidentWorkKind.GUARD).size();
        if (target == 0) return List.of();
        int ranged = GuardEquipment.rangedTarget(state, home, target);
        return views.get(state.bootstrap(), home, List.of(state.bootstrap().ruleset().labour(), target, ranged), () -> {
            var demands = new ArrayList<EquipmentDemand>();
            for (var method : io.farfrontier.palemirror.frontier.v3.model.combat.CombatMethod.values()) {
                int count = method == io.farfrontier.palemirror.frontier.v3.model.combat.CombatMethod.CROSSBOW ? ranged : target-ranged;
                if (count > 0) demands.add(new EquipmentDemand("guard-" + method.name().toLowerCase(java.util.Locale.ROOT),
                        List.of(GuardEquipment.capability(method)), count, Optional.empty(), EquipmentDemand.Acquisition.PRODUCE_LOCAL));
            }
            return List.copyOf(demands);
        });
    }
}
