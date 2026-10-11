package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Complete producer declaration; a socket must not discover its physical provenance owner. */
public sealed interface ContainerSocketSupport permits ContainerSocketSupport.Graybox, ContainerSocketSupport.Worksite, ContainerSocketSupport.Terrain {
    enum Kind { GRAYBOX, WORKSITE, TERRAIN }
    Kind kind();
    BlockPosition position();
    /** Ordinary declared storage needs load-bearing terrain, not invented ownership of the ground. */
    record Terrain(BlockPosition position) implements ContainerSocketSupport {
        public Terrain { Objects.requireNonNull(position); }
        @Override public Kind kind() { return Kind.TERRAIN; }
    }
    record Graybox(GrayboxCell cell) implements ContainerSocketSupport {
        public Graybox { Objects.requireNonNull(cell); }
        @Override public Kind kind() { return Kind.GRAYBOX; }
        @Override public BlockPosition position() { return cell.position(); }
    }
    record Worksite(WorksiteBlock cell, WorksiteBlock socket) implements ContainerSocketSupport {
        public Worksite {
            Objects.requireNonNull(cell); Objects.requireNonNull(socket);
            if (!socket.key().owner().equals(cell.key().owner()) || socket.key().family() != cell.key().family()
                    || socket.key().role() != WorksiteBlock.Role.CONTAINER_SOCKET
                    || !socket.position().equals(cell.position().offset(0, 1, 0)))
                throw new IllegalArgumentException("worksite socket requires its exact declared support and opening");
        }
        @Override public Kind kind() { return Kind.WORKSITE; }
        @Override public BlockPosition position() { return cell.position(); }
    }
}
