package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded unresolved obligations, not an evictable history or a second inventory ledger. */
public record CargoProjectionRetirements(Map<UUID, CargoProjectionRetirement> pending) {
    public static final int MAX_PENDING = 4_096;

    public CargoProjectionRetirements {
        pending = Map.copyOf(Objects.requireNonNull(pending, "pending cargo retirements"));
        if (pending.size() > MAX_PENDING) throw new IllegalArgumentException("cargo cleanup obligation limit exceeded");
        pending.forEach((entity, obligation) -> {
            if (!entity.equals(obligation.entityId())) throw new IllegalArgumentException("cargo cleanup entity key mismatch");
        });
    }

    public static CargoProjectionRetirements empty() { return new CargoProjectionRetirements(Map.of()); }

    /** Historical endpoints may be compacted, but any retained endpoint must agree exactly. */
    public void validateContext(WorldId world, Map<SceneLeaseId, SceneLease> scenes) {
        Objects.requireNonNull(world, "retirement world");
        Objects.requireNonNull(scenes, "retirement scene inventory");
        for (var obligation : pending.values()) {
            if (!obligation.worldId().equals(world)) throw new IllegalArgumentException("cargo retirement belongs to another world");
            var scene = scenes.get(obligation.leaseId());
            if (scene != null && (scene.status() != SceneLeaseStatus.CLOSED
                    || !FrontierSceneBehaviors.isLogistics(scene)
                    || !scene.worldId().equals(world)
                    || scene.revision() != obligation.authorization().ownerRevision()
                    || !FrontierSceneBehaviors.logistics(scene).cargoId().equals(obligation.cargoId()))) {
                throw new IllegalArgumentException("cargo retirement conflicts with retained scene endpoint");
            }
        }
    }

    /** Every admitted live projection reserves one future terminal slot before it materializes. */
    public boolean canAdmit(int reservedLiveProjections) {
        if (reservedLiveProjections < 0) throw new IllegalArgumentException("negative live cargo reservation count");
        return reservedLiveProjections < MAX_PENDING - pending.size();
    }

    public CargoProjectionRetirements retain(CargoProjectionRetirement obligation) {
        Objects.requireNonNull(obligation, "cargo retirement obligation");
        CargoProjectionRetirement previous = pending.get(obligation.entityId());
        if (previous != null) {
            if (!previous.equals(obligation)) throw new IllegalArgumentException("conflicting cargo retirement authorization");
            return this;
        }
        if (!canAdmit(0)) throw new IllegalArgumentException("unreserved cargo cleanup obligation");
        Map<UUID, CargoProjectionRetirement> next = new LinkedHashMap<>(pending);
        next.put(obligation.entityId(), obligation);
        return new CargoProjectionRetirements(next);
    }

    /** Called only for the provider's durable-save acknowledgement, not physical discard. */
    CargoProjectionRetirements acknowledgeSaved(CargoProjectionRetirement expected) {
        Objects.requireNonNull(expected, "saved cargo retirement");
        if (!expected.equals(pending.get(expected.entityId()))) {
            throw new IllegalArgumentException("cargo cleanup acknowledgement does not match pending obligation");
        }
        Map<UUID, CargoProjectionRetirement> next = new LinkedHashMap<>(pending);
        next.remove(expected.entityId());
        return new CargoProjectionRetirements(next);
    }
}
