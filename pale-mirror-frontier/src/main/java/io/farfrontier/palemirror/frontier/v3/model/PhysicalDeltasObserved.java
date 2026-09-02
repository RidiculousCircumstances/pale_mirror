package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * One durable, bounded observation of physical losses that Minecraft applies as one causal
 * change.  The full set is accepted or rejected before the initiating world mutation, so an
 * attached/dependent block can never become a silent ledger-only conflict after its support is
 * removed.
 */
public record PhysicalDeltasObserved(List<PhysicalDelta> deltas) implements FrontierPayload {
    public static final int MAX_ATOMIC_DELTAS = 16;

    public PhysicalDeltasObserved {
        deltas = List.copyOf(Objects.requireNonNull(deltas, "physical deltas"));
        if (deltas.isEmpty() || deltas.size() > MAX_ATOMIC_DELTAS) {
            throw new IllegalArgumentException("physical observation must contain 1.." + MAX_ATOMIC_DELTAS + " deltas");
        }
        HashSet<BlockPosition> positions = new HashSet<>();
        for (PhysicalDelta delta : deltas) {
            Objects.requireNonNull(delta, "physical delta");
            if (!positions.add(delta.position())) throw new IllegalArgumentException("physical observation contains a duplicate position");
        }
    }

    @Override public String type() { return "frontier.physical_deltas_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
