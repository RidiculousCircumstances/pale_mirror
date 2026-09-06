package io.farfrontier.palemirror.api;

import java.util.Objects;

/** Immutable fresh-world envelope which reserves one authored site's ecology and surface skyline. */
public record SiteEnvironmentPlan(String policyId, VisualPoint center, int hardRadius,
                                  int transitionRadius, int structureClearance,
                                  int minimumSurfaceY, int maximumSurfaceY) {
    public SiteEnvironmentPlan {
        if (policyId == null || policyId.isBlank()) throw new IllegalArgumentException("policyId is required");
        Objects.requireNonNull(center, "center");
        if (hardRadius < 1 || transitionRadius < hardRadius || structureClearance < 0) {
            throw new IllegalArgumentException("invalid authored-site environment radii");
        }
        if (minimumSurfaceY > maximumSurfaceY) throw new IllegalArgumentException("invalid surface range");
    }

    public boolean insideHard(int x, int z) {
        return squaredDistance(x, z) <= (long) hardRadius * hardRadius;
    }

    public boolean insideTransition(int x, int z) {
        return squaredDistance(x, z) <= (long) transitionRadius * transitionRadius;
    }

    public boolean insideStructureClearance(int x, int z) {
        int radius = hardRadius + structureClearance;
        return squaredDistance(x, z) <= (long) radius * radius;
    }

    private long squaredDistance(int x, int z) {
        long dx = (long) x - center.x();
        long dz = (long) z - center.z();
        return dx * dx + dz * dz;
    }
}
