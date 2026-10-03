package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Set;

/** Validates declared identity against its authoritative producer roster; supplies no missing kind. */
final class ActorIdentityDeclarations {
    private ActorIdentityDeclarations() { }
    static Set<SubjectId> validate(FrontierBootstrap bootstrap, HiveColony hive, HumanPopulation people,
                                  Map<SubjectId, ActorLocation> actors) {
        var bioforms = FrontierWorldStateSupport.bioformIds(bootstrap);
        bioforms.addAll(hive.spawnedBioforms().keySet());
        var residents = people.residentIds();
        var expected = new java.util.HashSet<>(bioforms); expected.addAll(residents);
        if (!expected.equals(actors.keySet()))
            throw new IllegalArgumentException("actor location index must own every and only canonical actor");
        for (var entry : actors.entrySet()) {
            boolean declaredResident = entry.getValue().kind() == ActorKind.RESIDENT;
            if (residents.contains(entry.getKey()) == bioforms.contains(entry.getKey())
                    || declaredResident != residents.contains(entry.getKey()))
                throw new IllegalArgumentException("declared actor kind mismatches its exact producer roster");
        }
        return Set.copyOf(expected);
    }
}
