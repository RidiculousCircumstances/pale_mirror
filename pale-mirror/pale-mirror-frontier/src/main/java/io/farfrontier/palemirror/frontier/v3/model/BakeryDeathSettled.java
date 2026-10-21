package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Objects;
import java.util.Optional;

/** Terminal batch disposition; a carried portion requires its actual native ground successor. */
public record BakeryDeathSettled(ActorActuationId identity, BakeryWorkState.Phase expectedPhase,
                                long sourceEpoch, Optional<ResourceCustody.Ground> ground) implements FrontierPayload {
    public BakeryDeathSettled {
        Objects.requireNonNull(identity); Objects.requireNonNull(expectedPhase); ground = Objects.requireNonNull(ground);
        if (identity.execution().activityKind() != ActorActivityKind.PRODUCTION || sourceEpoch < 0
                || ground.isPresent() != (expectedPhase == BakeryWorkState.Phase.STATION_LOAD || expectedPhase == BakeryWorkState.Phase.DEPOT_DELIVERY)
                || ground.isEmpty() && sourceEpoch != 0)
            throw new IllegalArgumentException("invalid bakery death batch disposition");
    }
    @Override public String type() { return "frontier.bakery_death_settled"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
