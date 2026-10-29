package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import java.util.*;

/** Transport-owned retained lengths for cost advice, never a current route or arrival proof. */
final class TransportJourneyForecastFacts {
    record Pair(SubjectId source, SubjectId receiver) { }
    record Journey(SubjectId id, Pair endpoints, TransportOrganization.Itinerary itinerary, long outward, long returning) { }
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, UnitGroup> GROUPS =
            new ImmutableReferenceIndex.Source<>(s -> s.unitGroups().groups(),
                    (before, after) -> (before == null) == (after == null));
    private static final ImmutableReadProjection<FrontierWorldState, SubjectId, TransportMission, Optional<Journey>> JOURNEYS =
            new ImmutableReadProjection<>(Integer.MAX_VALUE, 1, List.of(GROUPS),
                    (state, mission) -> List.of(GROUPS.at(mission.groupId())), (state, mission) -> {
                        if (mission.supplies().isEmpty() || mission.supplies().orElseThrow().needsReplan()
                                || !state.unitGroups().groups().containsKey(mission.groupId())) return Optional.empty();
                        var proof = mission.supplies().orElseThrow().journey();
                        return Optional.of(new Journey(mission.id(), new Pair(mission.sender().containerId(), mission.receiver().containerId()),
                                mission.organization().itinerary(), proof.outward().edges(), proof.returning().edges()));
                    });
    private static final ImmutableGroupView<SubjectId, Optional<Journey>, Pair> PAIRS = new ImmutableGroupView<>();
    private TransportJourneyForecastFacts() { }
    static Optional<Journey> between(FrontierWorldState state, SubjectId source, SubjectId receiver) {
        var facts = JOURNEYS.project(state.bootstrap(), state.shipments().missions(), state);
        return PAIRS.unorderedRows(state.bootstrap(), facts,
                        row -> row.map(journey -> Set.of(journey.endpoints())).orElse(Set.of()), new Pair(source, receiver))
                .stream().flatMap(Optional::stream).min(Comparator.comparing(Journey::id));
    }
}
