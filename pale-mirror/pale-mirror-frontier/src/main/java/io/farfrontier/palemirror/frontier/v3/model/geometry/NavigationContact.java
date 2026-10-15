package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.Objects;

/** Derived physical feet pose on a named semantic support, not another actor position authority. */
public record NavigationContact(SurfaceAnchor support, double x, double feetY, double z) {
    public NavigationContact {
        Objects.requireNonNull(support);
        if (!Double.isFinite(x) || !Double.isFinite(feetY) || !Double.isFinite(z)
                || x < support.x() || x >= support.x() + 1 || z < support.z() || z >= support.z() + 1
                || feetY <= support.y() - 1 || feetY > support.y() + 2)
            throw new IllegalArgumentException("contact is outside its declared support");
    }
}
