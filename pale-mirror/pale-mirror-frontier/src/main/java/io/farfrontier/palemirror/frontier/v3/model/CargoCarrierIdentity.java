package io.farfrontier.palemirror.frontier.v3.model;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable physical identity for the one materialized carrier of a HOT cargo batch. */
public final class CargoCarrierIdentity {
    private CargoCarrierIdentity() { }

    public static UUID id(SceneLease lease) {
        return id(lease.worldId(), lease.id(), FrontierSceneBehaviors.logistics(lease).cargoId());
    }

    /** Identity validation for retained terminal declarations after scene-history compaction. */
    public static UUID id(io.farfrontier.palemirror.frontier.v3.api.WorldId world,
                          io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId scene,
                          io.farfrontier.palemirror.frontier.v3.api.SubjectId cargo) {
        return UUID.nameUUIDFromBytes(("frontier-v3:cargo-carrier:" + world.value() + ":" + scene.value() + ":" + cargo.value())
                .getBytes(StandardCharsets.UTF_8));
    }
}
