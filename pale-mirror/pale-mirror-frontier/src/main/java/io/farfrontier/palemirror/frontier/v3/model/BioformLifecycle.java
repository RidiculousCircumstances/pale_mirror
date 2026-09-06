package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Exact physical-custody state for one bioform.
 *
 * <p>A mature dormant/recovering organism has one occupied cocoon slot and no Minecraft mob.
 * Active organisms may retain a reserved return slot, but no second body or health ledger is
 * created here.</p>
 */
public record BioformLifecycle(BioformLifecyclePhase phase, Optional<HiveCocoonSlot> homeSlot) {
    public BioformLifecycle {
        Objects.requireNonNull(phase, "bioform lifecycle phase");
        homeSlot = Objects.requireNonNull(homeSlot, "bioform cocoon home");
        if (phase.occupiesCocoon() && homeSlot.isEmpty()) {
            throw new IllegalArgumentException("dormant or recovering bioform requires one exact cocoon slot");
        }
    }

    public static BioformLifecycle dormant(HiveCocoonSlot slot) {
        return new BioformLifecycle(BioformLifecyclePhase.DORMANT, Optional.of(slot));
    }

    public static BioformLifecycle active(HiveCocoonSlot slot) {
        return new BioformLifecycle(BioformLifecyclePhase.ACTIVE, Optional.of(slot));
    }

    /** A newly matured active body may have no return organ until a later growth task builds it. */
    public static BioformLifecycle activeWithoutHome() {
        return new BioformLifecycle(BioformLifecyclePhase.ACTIVE, Optional.empty());
    }

    public BioformLifecycle waking() {
        if (homeSlot.isEmpty()) throw new IllegalStateException("waking bioform has no cocoon origin");
        return new BioformLifecycle(BioformLifecyclePhase.WAKING, homeSlot);
    }

    public BioformLifecycle active() {
        if (!phase.permitsAmbientBody()) throw new IllegalStateException("only a waking/active bioform may retain an ambient body");
        return new BioformLifecycle(BioformLifecyclePhase.ACTIVE, homeSlot);
    }

    public BioformLifecycle assembling() {
        if (phase != BioformLifecyclePhase.WAKING) {
            throw new IllegalStateException("only an observed released bioform may enter assembly");
        }
        return new BioformLifecycle(BioformLifecyclePhase.ASSEMBLING, homeSlot);
    }
}
