package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Set;

/** Validates declared identity against its authoritative producer roster; supplies no missing kind. */
final class ActorIdentityDeclarations {
    private record ProducerRoster(FrontierBootstrap bootstrap, Object spawned, Object people, Object assets,
                                  Set<SubjectId> bioforms, Set<SubjectId> residents, Set<SubjectId> animals,
                                  Set<SubjectId> expected) { }
    // One bounded immutable derived view. Position, nutrition and activity changes are not births.
    private static volatile ProducerRoster cached;
    private ActorIdentityDeclarations() { }
    static Set<SubjectId> validate(FrontierBootstrap bootstrap, HiveColony hive, HumanPopulation people,
                                  Map<SubjectId, ActorLocation> actors,
                                  io.farfrontier.palemirror.frontier.v3.model.expedition.TransportFleet fleet) {
        var roster = cached;
        if (roster == null || roster.bootstrap() != bootstrap || roster.spawned() != hive.spawnedBioforms()
                || roster.people() != people.residents() || roster.assets() != fleet.assets()) {
            var bioforms = FrontierWorldStateSupport.bioformIds(bootstrap);
            bioforms.addAll(hive.spawnedBioforms().keySet());
            var residents = people.residentIds();
            var animals = fleet.assets().keySet();
            var expected = new java.util.HashSet<>(bioforms); expected.addAll(residents); expected.addAll(animals);
            roster = new ProducerRoster(bootstrap, hive.spawnedBioforms(), people.residents(), fleet.assets(),
                    Set.copyOf(bioforms), Set.copyOf(residents), Set.copyOf(animals), Set.copyOf(expected));
            cached = roster;
        }
        var bioforms = roster.bioforms(); var residents = roster.residents(); var animals = roster.animals();
        var expected = roster.expected();
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
        return expected;
    }
}
