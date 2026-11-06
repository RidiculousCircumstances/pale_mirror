package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import java.util.*;

/** Guard capability contract; neither a Minecraft sword name nor an imaginary work/wear operation. */
public final class GuardEquipment {
    public static final String RANGED_WEAPON = "pale_mirror:ranged_weapon";
    public static final String MELEE_WEAPON = "pale_mirror:melee_weapon";
    public static final EquipmentRequirement.Capability CAPABILITY = new EquipmentRequirement.Capability(MELEE_WEAPON, 0);
    public static final EquipmentRequirements REQUIREMENTS = EquipmentRequirements.all(Set.of(CAPABILITY));
    public static io.farfrontier.palemirror.frontier.v3.model.tooling.EquipmentRequirement.Capability capability(
            io.farfrontier.palemirror.frontier.v3.model.combat.CombatMethod method) {
        return switch (java.util.Objects.requireNonNull(method)) {
            case MELEE -> CAPABILITY;
            case CROSSBOW -> new io.farfrontier.palemirror.frontier.v3.model.tooling.EquipmentRequirement.Capability(RANGED_WEAPON, 0);
        };
    }
    public static io.farfrontier.palemirror.frontier.v3.model.tooling.EquipmentRequirements requirements(
            io.farfrontier.palemirror.frontier.v3.model.combat.CombatMethod method) {
        return io.farfrontier.palemirror.frontier.v3.model.tooling.EquipmentRequirements.all(java.util.Set.of(capability(method)));
    }
    public static int rangedTarget(FrontierWorldState state, SubjectId home, int total) {
        int percent = state.bootstrap().ruleset().security().rangedGuardPercent();
        return percent == 0 || total < 2 ? 0 : Math.min(total - 1, Math.max(1, total * percent / 100));
    }
    public static java.util.List<io.farfrontier.palemirror.frontier.v3.model.combat.CombatMethod> vacancies(FrontierWorldState state, SubjectId home) {
        int total = target(state, home), ranged = rangedTarget(state, home, total);
        var current = in(state, home).stream().filter(d -> state.actorLocations().get(d.execution().actorId()).condition().status() == ActorLifeStatus.ALIVE).toList();
        var result = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.model.combat.CombatMethod>();
        for (var method : java.util.List.of(io.farfrontier.palemirror.frontier.v3.model.combat.CombatMethod.MELEE,
                io.farfrontier.palemirror.frontier.v3.model.combat.CombatMethod.CROSSBOW)) {
            int wanted = method == io.farfrontier.palemirror.frontier.v3.model.combat.CombatMethod.CROSSBOW ? ranged : total-ranged;
            if (current.stream().filter(d -> d.method() == method).count() < wanted) result.add(method);
        }
        return java.util.List.copyOf(result);
    }
    public static boolean carried(FrontierWorldState state, GuardDuty duty) {
        var item = state.inventory().items().get(duty.weapon());
        return item != null && item.custody().equals(new InventoryCustody.Actor(duty.execution().actorId()));
    }
    public static boolean held(FrontierWorldState state, GuardDuty duty) {
        var item = state.inventory().items().get(duty.weapon());
        return item != null && item.economicOwnerId().equals(duty.home())
                && carried(state, duty)
                && requirements(duty.method()).serviceable(state.bootstrap().ruleset().tools(), item);
    }
    public static int target(FrontierWorldState state, SubjectId home) {
        return io.farfrontier.palemirror.frontier.v3.model.security.SecurityCoverage.required(state, home);
    }
    public static List<GuardDuty> in(FrontierWorldState state, SubjectId home) {
        return state.strategicPlans().guards().values().stream().filter(duty -> duty.home().equals(home))
                .sorted(Comparator.comparing(GuardDuty::id)).toList();
    }
    private GuardEquipment() { }
}
