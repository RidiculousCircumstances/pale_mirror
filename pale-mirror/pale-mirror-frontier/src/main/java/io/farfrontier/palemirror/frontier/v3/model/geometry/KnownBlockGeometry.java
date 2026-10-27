package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.Objects;
import java.util.Optional;

/** Read-only, versioned knowledge. Absence is unknown, not traversable space or a resource. */
public interface KnownBlockGeometry<B> {
    record Sample<B>(B block, long revision, Permission permission) {
        public enum Permission { PUBLIC, PROTECTED }
        public Sample {
            Objects.requireNonNull(block); Objects.requireNonNull(permission);
            if (revision < 1) throw new IllegalArgumentException("geometry needs an explicit positive version");
        }
    }
    Optional<Sample<B>> at(BlockPosition position);
    /** Optional complete semantic version of every block and permission in these horizontal
     * regions, at every height. Omitting any dependency is forbidden. A matching version
     * permits derived read reuse only; new canonical admission still requires fresh samples. */
    default Optional<Object> regionalReadVersion(java.util.Set<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region> regions) {
        return Optional.empty();
    }
}
