package io.farfrontier.palemirror.frontier.v3.api;

import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.List;
import java.util.Objects;

/** Opaque immutable canonical snapshot for the future store port. */
public record CheckpointImage(
        WorldId worldId, Revision revision, SimInstant instant, byte[] canonicalState,
        List<ScheduledAction> schedules, List<CommandReceipt> receipts
) {
    public CheckpointImage {
        Objects.requireNonNull(worldId, "world id");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(instant, "instant");
        canonicalState = canonicalState.clone();
        schedules = List.copyOf(schedules);
        receipts = List.copyOf(receipts);
    }

    @Override
    public byte[] canonicalState() {
        return canonicalState.clone();
    }
}
