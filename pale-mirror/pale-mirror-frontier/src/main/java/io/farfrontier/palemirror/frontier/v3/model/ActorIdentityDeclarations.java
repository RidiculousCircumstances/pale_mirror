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
    private static final ImmutableRegistryView<SubjectId, ActorLocation, ActorKind> KINDS =
            new ImmutableRegistryView<>(SimulationCapacity.MAX_ACTORS);
    static Map<SubjectId, ActorKind> declaredKinds(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actors) {
        if (ImmutableInvariantCheck.fullAudit()) return KINDS.project(bootstrap, actors, (before, after) -> false, (id, actor) -> actor.kind());
        return KINDS.project(bootstrap, actors, (id, actor) -> actor.kind());
    }
    private static final ImmutableInvariantCheck<Set<SubjectId>> CHECK = new ImmutableInvariantCheck<>();
    private ActorIdentityDeclarations() { }
    static Set<SubjectId> validate(FrontierBootstrap bootstrap, HiveColony hive, HumanPopulation people,
                                  Map<SubjectId, ActorLocation> actors,
                                  io.farfrontier.palemirror.frontier.v3.model.expedition.TransportFleet fleet) {
        var kinds = declaredKinds(bootstrap, actors);
        return CHECK.value(() -> validateCurrent(bootstrap, hive, people, kinds, fleet),
                bootstrap, hive.spawnedBioforms(), people.residents(), kinds, fleet.assets());
    }
    private static Set<SubjectId> validateCurrent(FrontierBootstrap bootstrap, HiveColony hive, HumanPopulation people,
                                  Map<SubjectId, ActorKind> kinds,
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
                    ImmutableLookupSet.copyOf(bioforms), ImmutableLookupSet.copyOf(residents), ImmutableLookupSet.copyOf(animals), ImmutableLookupSet.copyOf(expected));
            cached = roster;
        }
        var bioforms = roster.bioforms(); var residents = roster.residents(); var animals = roster.animals();
        var expected = roster.expected();
        if ((long) residents.size() + bioforms.size() + animals.size() != expected.size())
            throw new IllegalArgumentException("actor identity has competing producer declarations");
        if (!expected.equals(kinds.keySet()))
            throw new IllegalArgumentException("actor location index must own every and only canonical actor");
        for (var entry : kinds.entrySet()) {
            boolean declared = switch (entry.getValue()) {
                case RESIDENT -> residents.contains(entry.getKey());
                case BIOFORM -> bioforms.contains(entry.getKey());
                case PACK_ANIMAL -> animals.contains(entry.getKey());
            };
            if (!declared)
                throw new IllegalArgumentException("declared actor kind mismatches its exact producer roster");
        }
        return expected;
    }
}
