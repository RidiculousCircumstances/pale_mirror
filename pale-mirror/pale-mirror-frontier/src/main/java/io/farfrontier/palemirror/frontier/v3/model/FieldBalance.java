package io.farfrontier.palemirror.frontier.v3.model;

/** Fresh authored footprints; ongoing field work still consumes arbitrary explicit cell sets. */
public record FieldBalance(int ordinarySide, int extractiveSide) {
    public FieldBalance {
        if ((ordinarySide != 8 && ordinarySide != 16) || (extractiveSide != 8 && extractiveSide != 16))
            throw new IllegalArgumentException("unsupported authored irrigation footprint");
    }
    public static FieldBalance uniform() { return new FieldBalance(16, 16); }
    public String canonicalText() { return ordinarySide + ":" + extractiveSide; }
}
