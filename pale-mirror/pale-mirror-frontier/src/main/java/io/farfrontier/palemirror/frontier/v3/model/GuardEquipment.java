package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import java.util.*;

/** Guard capability contract; neither a Minecraft sword name nor an imaginary work/wear operation. */
public final class GuardEquipment {
    public static final String MELEE_WEAPON = "pale_mirror:melee_weapon";
    public static final EquipmentRequirement.Capability CAPABILITY = new EquipmentRequirement.Capability(MELEE_WEAPON, 0);
    public static final EquipmentRequirements REQUIREMENTS = EquipmentRequirements.all(Set.of(CAPABILITY));
    public static boolean held(FrontierWorldState state, GuardDuty duty) {
        var item = state.inventory().items().get(duty.weapon());
        return item != null && item.economicOwnerId().equals(duty.home())
                && item.custody().equals(new InventoryCustody.Actor(duty.execution().actorId()))
                && REQUIREMENTS.serviceable(state.bootstrap().ruleset().tools(), item);
    }
    public static int target(FrontierWorldState state) {
        var entry = state.bootstrap().ruleset().labour().entries().get(ResidentWorkKind.GUARD);
        return entry == null ? 0 : entry.targetWorkers();
    }
    public static List<GuardDuty> in(FrontierWorldState state, SubjectId home) {
        return state.strategicPlans().guards().values().stream().filter(duty -> duty.home().equals(home))
                .sorted(Comparator.comparing(GuardDuty::id)).toList();
    }
    private GuardEquipment() { }
}
