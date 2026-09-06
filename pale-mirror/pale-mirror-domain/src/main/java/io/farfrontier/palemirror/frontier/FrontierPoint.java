package io.farfrontier.palemirror.frontier;

/** Integer cell coordinate; Minecraft conversion belongs to the NeoForge projection layer. */
public record FrontierPoint(int x, int z) {
    public long squaredDistanceTo(FrontierPoint other) {
        long dx = (long) x - other.x;
        long dz = (long) z - other.z;
        return dx * dx + dz * dz;
    }
}
