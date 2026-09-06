package io.farfrontier.palemirror.frontier.v3.model;

/** Integer Minecraft block coordinates without a dependency on Minecraft classes. */
public record BlockPosition(int x, int y, int z) {
    public BlockPosition offset(int deltaX, int deltaY, int deltaZ) {
        return new BlockPosition(Math.addExact(x, deltaX), Math.addExact(y, deltaY), Math.addExact(z, deltaZ));
    }
}
