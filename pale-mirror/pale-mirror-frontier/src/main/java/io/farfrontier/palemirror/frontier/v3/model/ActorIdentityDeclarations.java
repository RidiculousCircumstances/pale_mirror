package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Set;

/** Validates declared identity against its authoritative producer roster; supplies no missing kind. */
final class ActorIdentityDeclarations {
    private ActorIdentityDeclarations() { }
    static Set<SubjectId> validate(FrontierBootstrap bootstrap, HiveColony hive, HumanPopulation people,
                                  Map<SubjectId, ActorLocation> actors,
                                  io.farfrontier.palemirror.frontier.v3.model.expedition.TransportFleet fleet) {
        var bioforms = FrontierWorldStateSupport.bioformIds(bootstrap);
        bioforms.addAll(hive.spawnedBioforms().keySet());
        var residents = people.residentIds();
        var animals = fleet.assets().keySet();
        var expected = new java.util.HashSet<>(bioforms); expected.addAll(residents); expected.addAll(animals);
        if (!expected.equals(actors.keySet()))
            throw new IllegalArgumentException("actor location index must own every and only canonical actor");
        for (var entry : actors.entrySet()) {
            int producers = (residents.contains(entry.getKey()) ? 1 : 0) + (bioforms.contains(entry.getKey()) ? 1 : 0)
                    + (animals.contains(entry.getKey()) ? 1 : 0);
            boolean declared = switch (entry.getValue().kind()) {
                case RESIDENT -> residents.contains(entry.getKey());
                case BIOFORM -> bioforms.contains(entry.getKey());
                case PACK_ANIMAL -> animals.contains(entry.getKey());
            };
            if (producers != 1 || !declared)
                throw new IllegalArgumentException("declared actor kind mismatches its exact producer roster");
        }
        return Set.copyOf(expected);
    }
}
