package io.farfrontier.palemirror.frontier.v3.model;

/** Family-owned immediate opportunity inquiry. It must be read-only and must not call the selector. */
public interface ResidentWorkAvailabilityPort {
    ResidentWorkKind kind();
    HumanCapability capability();
    boolean available(FrontierWorldState state, ResidentProfile resident, long atTick);
}
