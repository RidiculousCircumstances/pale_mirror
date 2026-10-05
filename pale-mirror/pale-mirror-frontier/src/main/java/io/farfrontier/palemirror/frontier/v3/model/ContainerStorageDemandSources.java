package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.function.Function;

/** Composition root: add a process provider here, not a job switch in the storage algorithm. */
final class ContainerStorageDemandSources {
    private ContainerStorageDemandSources() { }
    private static final List<Function<FrontierWorldState, List<ContainerInboundCapacity.Demand>>> SOURCES = List.of(
            state -> ProductionOutputCapacity.pendingInbound(state.productionJobs()), GoodsTradeStorageDemand::pending);

    static List<ContainerInboundCapacity.Demand> demands(FrontierWorldState state) {
        return SOURCES.stream().flatMap(source -> source.apply(state).stream()).toList();
    }
}
