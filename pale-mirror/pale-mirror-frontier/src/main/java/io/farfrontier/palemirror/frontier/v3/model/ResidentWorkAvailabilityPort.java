package io.farfrontier.palemirror.frontier.v3.model;

/** Family-owned immediate opportunity inquiry. It must be read-only and must not call the selector. */
public interface ResidentWorkAvailabilityPort {
    ResidentWorkKind kind();
    HumanCapability capability();
    boolean available(FrontierWorldState state, ResidentProfile resident, long atTick);

    /** Selection inspects retained readiness; only a selected owner requests calculation.
     * UNRESOLVED is not a reservation and cannot claim or hold a resident. */
    default io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.Readiness readiness(
            FrontierWorldState state, ResidentProfile resident, long atTick) {
        return io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.inspectAvailability(
                () -> available(state, resident, atTick));
    }
}
