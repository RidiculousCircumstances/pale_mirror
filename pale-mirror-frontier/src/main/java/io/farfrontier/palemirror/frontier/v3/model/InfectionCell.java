package io.farfrontier.palemirror.frontier.v3.model;

/** Four-by-four-block sparse surface cell; positions and territory deliberately use other indexes. */
public record InfectionCell(int x, int z) {
    public static final int BLOCKS = 4;

    public static InfectionCell at(BlockPosition position) {
        return new InfectionCell(Math.floorDiv(position.x(), BLOCKS), Math.floorDiv(position.z(), BLOCKS));
    }

    public BlockPosition originAtY(int y) {
        return new BlockPosition(Math.multiplyExact(x, BLOCKS), y, Math.multiplyExact(z, BLOCKS));
    }
}
