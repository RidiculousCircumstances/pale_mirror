package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.Set;

/**
 * Provider-neutral contract for observing one retained traversal support.
 *
 * <p>The canonical route owns the exact support, declared capability and required clearance.
 * A physical provider supplies an exact support/medium/clearance observation; it never rounds a
 * pose, selects a neighbouring support, or turns a local detour into a new cursor.  This keeps
 * HOT and COLD on the same retained edge while allowing a Minecraft, rail, or future provider
 * to implement its own local trajectory.</p>
 */
public final class SemanticTraversalArrival {
    public enum Medium { AIR, WATER, LAVA, OTHER }

    public enum Disposition {
        ARRIVED,
        IN_PROGRESS,
        BLOCKED_SUPPORT,
        BLOCKED_CLEARANCE,
        BLOCKED_MEDIUM,
        OFF_CONTRACT
    }

    /** Exact read-only physical evidence for one body at one provider boundary. */
    public record Observation(SurfaceAnchor support, Medium medium, boolean grounded, boolean clearance) {
        public Observation {
            support = Objects.requireNonNull(support, "observed support");
            medium = Objects.requireNonNull(medium, "observed medium");
        }
    }

    /** Immutable destination supplied by a retained topology edge or typed facility port. */
    public record Contract(SurfaceAnchor support, Set<TraversalCapability> capabilities, int clearance) {
        public Contract {
            support = Objects.requireNonNull(support, "arrival support");
            capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "arrival capabilities"));
            if (capabilities.isEmpty() || clearance < 2) throw new IllegalArgumentException("arrival contract is invalid");
        }
    }

    private SemanticTraversalArrival() { }

    /**
     * Evaluates an exact reported support.  Ground traversal does not silently acquire swimming
     * or flying capability: any non-air medium is an owner-local blocked disposition.
     */
    public static Disposition evaluate(Observation observation, Contract contract) {
        Objects.requireNonNull(observation, "arrival observation");
        Objects.requireNonNull(contract, "arrival contract");
        if (!observation.support().equals(contract.support())) return Disposition.OFF_CONTRACT;
        if (observation.medium() != Medium.AIR) return Disposition.BLOCKED_MEDIUM;
        if (!observation.grounded()) return Disposition.IN_PROGRESS;
        return observation.clearance() ? Disposition.ARRIVED : Disposition.BLOCKED_CLEARANCE;
    }

    /** Classifies a named target before a provider starts its bounded local trajectory. */
    public static Disposition target(Observation target, Contract contract) {
        Objects.requireNonNull(target, "target observation");
        Objects.requireNonNull(contract, "arrival contract");
        if (!target.support().equals(contract.support())) return Disposition.OFF_CONTRACT;
        if (target.medium() != Medium.AIR) return Disposition.BLOCKED_MEDIUM;
        if (!target.grounded()) return Disposition.BLOCKED_SUPPORT;
        return target.clearance() ? Disposition.IN_PROGRESS : Disposition.BLOCKED_CLEARANCE;
    }
}
