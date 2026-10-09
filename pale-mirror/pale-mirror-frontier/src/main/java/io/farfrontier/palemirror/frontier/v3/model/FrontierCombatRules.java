package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;

/** Shared fixed-point damage and actor-liveness rules for retained combat processes. */
public final class FrontierCombatRules {
    private FrontierCombatRules() { }

    public static FixedScalar damage(FrontierWorldState state, SubjectId actor) {
        FrontierRuleset.Combat combat = state.bootstrap().ruleset().combat();
        Bioform bioform = bioform(state, actor);
        if (bioform != null) {
            if (bioform.isExplosiveAssaulter()) return combat.hiveBomberDamage();
            if (bioform.isDefender()) return combat.hiveGuardDamage();
            return combat.hiveWorkerScoutDamage();
        }
        ResidentProfile resident = resident(state, actor);
        if (resident == null) throw new IllegalArgumentException("COLD combat actor is neither resident nor bioform");
        var defenderAssault = SettlementDefenderReadinessProjection.owningActiveAssault(state, resident.id());
        if (defenderAssault.isPresent()) {
            SettlementDefenderReadinessProjection readiness = SettlementDefenderReadinessProjection.derive(state, defenderAssault.orElseThrow());
            if (!readiness.livingAssignedMembers().contains(resident.id())) {
                throw new IllegalArgumentException("unavailable defender cannot deal COLD combat damage");
            }
            return readiness.status() == SettlementDefenderReadinessStatus.READY
                    && readiness.armedLivingAssignedMembers().contains(resident.id())
                    ? combat.residentGuardDamage() : combat.residentWorkerDamage();
        }
        return HumanTacticalFunctionProjection.hasWeapon(state, resident.id())
                ? combat.residentGuardDamage() : combat.residentWorkerDamage();
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
