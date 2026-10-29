package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Population-owned long-run workforce facts. Current activity, hunger and body movement are not capacity loss. */
final class ResidentWorkforceFacts {
    private record Person(ResidentProfile profile, boolean available) { }
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ActorLifeStatus> LIFE =
            new ImmutableReferenceIndex.Source<>(s -> ActorIdentityDeclarations.lifeStatuses(s.bootstrap(), s.actorLocations()));
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ResidentMigrationJourney> MIGRATIONS =
            new ImmutableReferenceIndex.Source<>(s -> s.humanPopulation().migrations(), (a, b) -> (a == null) == (b == null));
    private static final ImmutableReadProjection<FrontierWorldState, SubjectId, ResidentProfile, Person> PEOPLE =
            new ImmutableReadProjection<>(SimulationCapacity.MAX_RESIDENTS, 2, List.of(LIFE, MIGRATIONS),
                    (state, person) -> List.of(LIFE.at(person.id()), MIGRATIONS.at(person.id())),
                    (state, person) -> new Person(person, !state.humanPopulation().migrations().containsKey(person.id())
                            && state.actorLocations().get(person.id()).condition().status() == ActorLifeStatus.ALIVE));
    private static final ImmutableGroupView<SubjectId, Person, SubjectId> HOMES = new ImmutableGroupView<>();
    private static final ImmutableKeyedInputView<SubjectId, List<ResidentProfile>> AVAILABLE = new ImmutableKeyedInputView<>(SimulationCapacity.MAX_SETTLEMENTS);
    static List<ResidentProfile> available(FrontierWorldState state, SubjectId home) {
        var facts = PEOPLE.project(state.bootstrap(), state.humanPopulation().residents(), state);
        var local = HOMES.unorderedRows(state.bootstrap(), facts, person -> person.available() ? Set.of(person.profile().settlementId()) : Set.of(), home);
        return AVAILABLE.getPublished(state.bootstrap(), home, List.of(local), () -> local.stream().map(Person::profile)
                .sorted(Comparator.comparing(ResidentProfile::id)).toList());
    }
    private ResidentWorkforceFacts() { }
}
