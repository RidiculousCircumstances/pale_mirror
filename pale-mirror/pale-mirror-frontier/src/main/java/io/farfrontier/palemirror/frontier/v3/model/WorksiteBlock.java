package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import java.util.Objects;

/** A producer-declared physical cell. Material is data, not a new execution family. */
public record WorksiteBlock(Key key, BlockPosition position, long revision, BlockExtraction.Block block) {
    public enum Role {
        INFRASTRUCTURE(1), RESOURCE(2), CONTAINER_SOCKET(3);
        private final int tag;
        Role(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Role decode(int tag) {
            return switch (tag) { case 1 -> INFRASTRUCTURE; case 2 -> RESOURCE; case 3 -> CONTAINER_SOCKET;
                default -> throw new IllegalArgumentException("unknown worksite cell role"); };
        }
    }
    public record Key(CellMutationKey.OwnerFamily family, SubjectId owner, Role role, long cell) {
        public Key {
            Objects.requireNonNull(family); Objects.requireNonNull(owner); Objects.requireNonNull(role);
            if (cell < 1) throw new IllegalArgumentException("worksite cell needs a declared positive identity");
        }
    }
    public WorksiteBlock {
        Objects.requireNonNull(key); Objects.requireNonNull(position); Objects.requireNonNull(block);
        if (revision < 1) throw new IllegalArgumentException("worksite block needs its current generation");
    }
}
