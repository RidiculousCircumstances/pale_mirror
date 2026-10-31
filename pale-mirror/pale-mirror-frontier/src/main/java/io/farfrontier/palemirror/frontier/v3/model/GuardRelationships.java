package io.farfrontier.palemirror.frontier.v3.model;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships.*;
/** Read-only active appointment relations. Lost equipment/retired storage remain historical goals until reselected. */
public final class GuardRelationships {
    public static List<Edge> edges(FrontierWorldState state, GuardDuty duty) {
        var result = new ArrayList<Edge>(); var owner = new SubjectEndpoint(EntityKind.GUARD_DUTY, duty.id());
        result.add(declaredEdge(Kind.GUARD_HOME, owner, owner, new SubjectEndpoint(EntityKind.SETTLEMENT, duty.home()), Lifecycle.ACTIVE, "guard"));
        result.add(declaredEdge(Kind.GUARD_RESIDENT, owner, owner, new SubjectEndpoint(EntityKind.RESIDENT, duty.execution().actorId()), Lifecycle.ACTIVE, "guard"));
        if (state.inventory().items().containsKey(duty.weapon())) result.add(declaredEdge(Kind.GUARD_WEAPON, owner, owner,
                new SubjectEndpoint(EntityKind.EXACT_ITEM, duty.weapon()), Lifecycle.ACTIVE, "guard"));
        if (state.inventory().containers().containsKey(duty.source().containerId())) result.add(declaredEdge(Kind.GUARD_STORAGE, owner, owner,
                new SubjectEndpoint(EntityKind.CONTAINER, duty.source().containerId()), Lifecycle.ACTIVE, "guard"));
        return List.copyOf(result);
    }
    static void collect(FrontierWorldState state, List<Edge> target) { state.strategicPlans().guards().values().forEach(duty -> target.addAll(edges(state, duty))); }
    private GuardRelationships() { }
}
