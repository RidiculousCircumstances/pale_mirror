package io.farfrontier.palemirror.frontier.v3.model;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded HOT-only latitude around one retained movement checkpoint pair.
 *
 * <p>The envelope is derived from the exact canonical formation and never selects a new
 * route, port or checkpoint. Collision and local avoidance may vary inside it; only the
 * retained next checkpoint can commit the parent cursor.</p>
 */
public record LocalNavigationEnvelope(Set<BlockPosition> permittedSupports) {
    private static final int MAX_SUPPORTS = 54;

    public LocalNavigationEnvelope {
        permittedSupports = Set.copyOf(Objects.requireNonNull(permittedSupports, "navigation envelope supports"));
        if (permittedSupports.isEmpty() || permittedSupports.size() > MAX_SUPPORTS) {
            throw new IllegalArgumentException("navigation envelope support bound is invalid");
        }
    }

    public boolean contains(BlockPosition observedSupport) {
        return permittedSupports.contains(Objects.requireNonNull(observedSupport, "observed support"));
    }

    static LocalNavigationEnvelope around(BodyPosition current, BodyPosition next) {
        LinkedHashSet<BlockPosition> supports = new LinkedHashSet<>();
        addNeighborhood(supports, current); addNeighborhood(supports, next);
        return new LocalNavigationEnvelope(supports);
    }

    private static void addNeighborhood(Set<BlockPosition> supports, BodyPosition body) {
        for (int y = body.y() - 1; y <= body.y() + 1; y++) {
            for (int x = body.x() - 1; x <= body.x() + 1; x++) {
                for (int z = body.z() - 1; z <= body.z() + 1; z++) supports.add(new BlockPosition(x, y, z));
            }
        }
    }
}
