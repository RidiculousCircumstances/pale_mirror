package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.function.BiFunction;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Composition root: add a process provider here, not a job switch in the storage algorithm. */
final class ContainerStorageDemandSources {
    private ContainerStorageDemandSources() { }
    private static final List<BiFunction<FrontierWorldState, SubjectId, List<ContainerInboundCapacity.Demand>>> SOURCES = List.of(
            (state, container) -> ProductionOutputCapacity.pendingInbound(state.productionJobs(), container), GoodsTradeStorageDemand::pending,
            InternalShipmentStorageDemand::pending);
    private static final ImmutableKeyedInputView<SubjectId, List<ContainerInboundCapacity.Demand>> VIEW = new ImmutableKeyedInputView<>(512);

    static List<ContainerInboundCapacity.Demand> demands(FrontierWorldState state, SubjectId container) {
        var contributions = SOURCES.stream().map(source -> source.apply(state, container)).toList();
        return VIEW.get(state.bootstrap(), container, List.copyOf(contributions), () -> {
            var result = contributions.stream().flatMap(List::stream).toList();
            if (result.stream().anyMatch(demand -> !demand.container().equals(container)))
                throw new IllegalArgumentException("storage demand provider returned a foreign receiver");
            return result;
        });
    }
}
