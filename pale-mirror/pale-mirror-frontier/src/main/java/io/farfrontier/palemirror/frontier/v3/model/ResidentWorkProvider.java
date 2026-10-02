package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Optional;

/** Family discovery port. Current target/resource/station validation belongs to the family's atomic start reducer. */
public interface ResidentWorkProvider<T> {
    ResidentWorkKind kind();
    HumanCapability capability();
    Optional<ResidentWorkOffer<T>> discover(FrontierWorldState state, ResidentProfile resident, long atTick);
}
