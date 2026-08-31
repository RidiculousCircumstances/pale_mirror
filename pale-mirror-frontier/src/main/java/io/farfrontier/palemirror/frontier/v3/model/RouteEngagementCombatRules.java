package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.List;

/** Pure, fixed-point rules for the first deterministic COLD engagement slice. */
public final class RouteEngagementCombatRules {
    private RouteEngagementCombatRules() { }

    public static FixedScalar damage(FrontierWorldState state, SubjectId actor) {
        FrontierRuleset.Combat combat = state.bootstrap().ruleset().combat();
        Bioform bioform = bioform(state, actor);
        if (bioform != null) return switch (bioform.role()) {
            case GUARD -> combat.hiveGuardDamage();
            case WORKER, SCOUT -> combat.hiveWorkerScoutDamage();
            case BOMBER -> combat.hiveBomberDamage();
        };
        ResidentProfile resident = resident(state, actor);
        if (resident == null) throw new IllegalArgumentException("COLD combat actor is neither resident nor bioform");
        return switch (resident.role()) {
            case GUARD -> combat.residentGuardDamage();
            case HAULER, BUILDER, FARMER, CRAFTER, MEDIC -> combat.residentWorkerDamage();
        };
    }

    public static List<SubjectId> livingAttackers(FrontierWorldState state, RouteEngagement engagement) {
        return engagement.attackerIds().stream().filter(id -> alive(state, id)).sorted().toList();
    }

    public static List<SubjectId> livingDefenders(FrontierWorldState state, RouteEngagement engagement) {
        return state.operations().get(engagement.operationId()).participantIds().stream().filter(id -> alive(state, id)).sorted().toList();
    }

    static RouteEngagementOutcome outcome(FrontierWorldState state, RouteEngagement engagement) {
        boolean attackers = !livingAttackers(state, engagement).isEmpty();
        boolean defenders = !livingDefenders(state, engagement).isEmpty();
        if (!defenders && attackers) return RouteEngagementOutcome.HIVE_VICTORY;
        if (!attackers && defenders) return RouteEngagementOutcome.SETTLEMENT_VICTORY;
        if (!attackers) return RouteEngagementOutcome.ABORTED;
        throw new IllegalArgumentException("route engagement still has living combatants");
    }

    public static SubjectId choose(List<SubjectId> candidates, int epoch) {
        if (candidates.isEmpty()) throw new IllegalArgumentException("COLD combat has no living candidate");
        return candidates.get(Math.floorMod(epoch, candidates.size()));
    }

    public static boolean alive(FrontierWorldState state, SubjectId actor) {
        ActorLocation location = state.actorLocations().get(actor);
        return location != null && location.condition().status() == ActorLifeStatus.ALIVE;
    }

    private static Bioform bioform(FrontierWorldState state, SubjectId actor) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(value -> value.id().equals(actor)).findFirst().orElse(null);
    }
    private static ResidentProfile resident(FrontierWorldState state, SubjectId actor) {
        return state.humanPopulation().resident(actor);
    }
}
