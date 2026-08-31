package io.farfrontier.palemirror.frontier.v3.model;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable physical identity for the one materialized carrier of a HOT cargo batch. */
public final class CargoCarrierIdentity {
    private CargoCarrierIdentity() { }

    public static UUID id(SceneLease lease) {
        return UUID.nameUUIDFromBytes(("frontier-v3:cargo-carrier:" + lease.worldId().value() + ":" + lease.id().value() + ":" + FrontierSceneBehaviors.logistics(lease).cargoId().value())
                .getBytes(StandardCharsets.UTF_8));
    }
}
