package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Machine-owner installation facts. Inventory contents/operation progress do not rebuild installations. */
final class InstalledStationFacts {
    record Installation(SubjectId container, SubjectId owner, ProductionStationSpec station) { }
    private record Query(SubjectId home, ProductionStationSpec.Capability capability) { }
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ContainerSurface> SURFACES =
            new ImmutableReferenceIndex.Source<>(s -> s.inventory().surfaces());
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, StructureCondition> CONDITIONS =
            new ImmutableReferenceIndex.Source<>(FrontierWorldState::structureConditions);
    private static final ImmutableReadProjection<FrontierWorldState, SubjectId, ContainerRecord, Optional<Installation>> INSTALLED =
            new ImmutableReadProjection<>(Integer.MAX_VALUE, 2, List.of(SURFACES, CONDITIONS),
                    (state, record) -> record.productionStation().map(spec -> List.of(SURFACES.at(record.id()), CONDITIONS.at(spec.facilityId())))
                            .orElse(List.of()),
                    (state, record) -> record.productionStation().filter(spec -> {
                        var status = state.inventory().surfaces().get(record.id()).status();
                        return status != ContainerSurfaceStatus.RETIRED && status != ContainerSurfaceStatus.CONFLICT
                                && state.structureConditions().get(spec.facilityId()) == StructureCondition.INTACT;
                    }).map(spec -> new Installation(record.id(), record.ownerId(), spec)));
    private static final ImmutableGroupView<SubjectId, Optional<Installation>, SubjectId> HOMES = new ImmutableGroupView<>();
    private static final ImmutableKeyedInputView<SubjectId, List<Installation>> LOCAL = new ImmutableKeyedInputView<>(SimulationCapacity.MAX_SETTLEMENTS);
    private static final ImmutableKeyedInputView<Query, List<Installation>> CAPABILITIES = new ImmutableKeyedInputView<>(512);
    static List<Installation> in(FrontierWorldState state, SubjectId home) {
        var facts = INSTALLED.project(state.bootstrap(), state.inventory().containers(), state);
        var rows = HOMES.unorderedRows(state.bootstrap(), facts, value -> value.map(i -> Set.of(i.owner())).orElse(Set.of()), home);
        return LOCAL.getPublished(state.bootstrap(), home, List.of(rows), () -> rows.stream().flatMap(Optional::stream)
                .sorted(Comparator.comparing(Installation::container)).toList());
    }
    static List<Installation> forRecipe(FrontierWorldState state, SubjectId home, ProductionRecipe recipe) {
        var local = in(state, home); var query = new Query(home, recipe.capability());
        return CAPABILITIES.getPublished(state.bootstrap(), query, List.of(local), () -> local.stream()
                .filter(i -> i.station().capability() == recipe.capability()).toList());
    }
    private InstalledStationFacts() { }
}
