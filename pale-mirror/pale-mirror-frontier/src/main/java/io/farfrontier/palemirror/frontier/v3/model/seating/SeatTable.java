package io.farfrontier.palemirror.frontier.v3.model.seating;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.Objects;

/** An explicitly declared table contact. A rest seat may declare no table. */
public record SeatTable(String block, BlockPosition position) {
    public SeatTable {
        if (block == null || !block.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("invalid seat table block");
        Objects.requireNonNull(position);
    }
}
