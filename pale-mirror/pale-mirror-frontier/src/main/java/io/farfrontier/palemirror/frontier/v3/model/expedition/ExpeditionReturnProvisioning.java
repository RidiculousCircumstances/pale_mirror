package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Remaining-trip demand at a supplier. Does not own stock, money or the group's movement. */
final class ExpeditionReturnProvisioning {
    private ExpeditionReturnProvisioning() { }
    static Map<SubjectId, Integer> missing(FrontierWorldState state, TransportMission mission, long now) {
        if (mission.supplies().isEmpty()) return Map.of();
        var load = mission.supplies().orElseThrow(); var rules = state.bootstrap().ruleset();
        var food = rules.residentLife().foods().foods().get(load.foodKind());
        long duration = rules.expedition().plannedDuration(0, load.journey().returning().edges());
        int shared = ExpeditionSupplyAuthority.sharedFood(state, mission, load.foodKind());
        var result = new LinkedHashMap<SubjectId, Integer>();
        for (var member : state.unitGroups().groups().get(mission.groupId()).members()) {
            var resident = state.humanPopulation().resident(member.actorId());
            if (resident == null || state.actorLocations().get(member.actorId()).condition().status() != ActorLifeStatus.ALIVE) continue;
            var nutrition = state.humanPopulation().nutrition(member.actorId());
            long evaluatedAt = Math.max(now, nutrition.lastEvaluatedTick());
            int metabolism = resident.characteristics().effectiveMetabolismPermille(evaluatedAt);
            nutrition = nutrition.accrueThrough(evaluatedAt, rules.residentLife(), metabolism);
            int required = ExpeditionProvisioning.requiredFoodItems(duration, nutrition.satietyUnits(), metabolism, food, rules.residentLife());
            int personal = UnitInventory.available(state.inventory().fungibleResources(), member.actorId(),
                    mission.organization().sponsor().id(), load.foodKind());
            int missing = Math.max(0, required - personal);
            int fromShared = Math.min(shared, missing); shared -= fromShared;
            result.put(member.actorId(), Math.max(0, missing - fromShared));
        }
        return Map.copyOf(result);
    }
}
