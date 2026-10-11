package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.UUID;

/** Stable resource-account location; a physical slot is deliberately not canonical custody. */
public sealed interface ResourceCustody permits ResourceCustody.Container, ResourceCustody.Player,
        ResourceCustody.Cargo, ResourceCustody.WorldCarrier, ResourceCustody.Actor, ResourceCustody.Ground {
    record Container(SubjectId containerId) implements ResourceCustody {
        public Container { Objects.requireNonNull(containerId, "resource container"); }
    }
    record Player(UUID playerId) implements ResourceCustody {
        public Player { Objects.requireNonNull(playerId, "resource player"); }
    }
    record Cargo(SubjectId cargoId) implements ResourceCustody {
        public Cargo { Objects.requireNonNull(cargoId, "resource cargo"); }
    }
    record WorldCarrier(UUID carrierId) implements ResourceCustody {
        public WorldCarrier { Objects.requireNonNull(carrierId, "resource world carrier"); }
    }
    record Actor(SubjectId actorId) implements ResourceCustody {
        public Actor { Objects.requireNonNull(actorId, "resource actor"); }
    }
    /** One observed item entity. Position is retained evidence, not permission to recreate a missing drop. */
    record Ground(UUID entityId, BlockPosition position) implements ResourceCustody {
        public Ground { Objects.requireNonNull(entityId, "ground resource entity"); Objects.requireNonNull(position, "observed drop position"); }
    }
}
