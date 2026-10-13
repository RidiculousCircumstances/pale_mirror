package io.farfrontier.palemirror.frontier.v3.model.navigation;

/** Producer-declared physical envelope, independent of profession, activity and movement owner.
 * Dimensions use millionths of a block; the entire value is canonical, not a derived kind lookup. */
public record UnitNavigationProfile(int widthMicros, int heightMicros, int stepUpBlocks, int dropBlocks,
                                    java.util.Set<io.farfrontier.palemirror.frontier.v3.model.TraversalCapability> capabilities) {
    public static final UnitNavigationProfile VILLAGER = new UnitNavigationProfile(600_000, 1_950_000, 1, 1,
            java.util.Set.of(io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN));
    public static final UnitNavigationProfile ZOMBIE = new UnitNavigationProfile(600_000, 1_950_000, 1, 1,
            java.util.Set.of(io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN,
                    io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.GROUND_BIOFORM));
    public static final UnitNavigationProfile DONKEY = new UnitNavigationProfile(1_396_485, 1_500_000, 1, 1,
            java.util.Set.of(io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN));

    public UnitNavigationProfile {
        capabilities = java.util.Set.copyOf(capabilities);
        if (widthMicros < 100_000 || widthMicros > 4_000_000 || heightMicros < 100_000 || heightMicros > 8_000_000
                || stepUpBlocks < 0 || stepUpBlocks > 2 || dropBlocks < 0 || dropBlocks > 3 || capabilities.isEmpty())
            throw new IllegalArgumentException("invalid declared unit navigation envelope");
    }
    public double width() { return widthMicros / 1_000_000.0; }
    public double height() { return heightMicros / 1_000_000.0; }
    public int footprintRadius() { return (int) Math.ceil((width() - 1.0) / 2.0); }
    public int bodyLayers() { return (heightMicros + 999_999) / 1_000_000; }
    public boolean permitsGrade(int fromFeetY, int toFeetY) {
        long rise = (long) toFeetY - fromFeetY;
        return rise <= stepUpBlocks && -rise <= dropBlocks;
    }
    public boolean matchesDimensions(double observedWidth, double observedHeight) {
        return Math.abs(width() - observedWidth) <= 0.00001 && Math.abs(height() - observedHeight) <= 0.00001;
    }
    /** Group route feasibility is the intersection of its explicitly declared member envelopes. */
    public static UnitNavigationProfile enclosing(java.util.Collection<UnitNavigationProfile> members) {
        if (members.isEmpty()) throw new IllegalArgumentException("navigation envelope needs actual group members");
        var shared = java.util.EnumSet.allOf(io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.class);
        members.forEach(member -> shared.retainAll(member.capabilities()));
        return new UnitNavigationProfile(members.stream().mapToInt(UnitNavigationProfile::widthMicros).max().orElseThrow(),
                members.stream().mapToInt(UnitNavigationProfile::heightMicros).max().orElseThrow(),
                members.stream().mapToInt(UnitNavigationProfile::stepUpBlocks).min().orElseThrow(),
                members.stream().mapToInt(UnitNavigationProfile::dropBlocks).min().orElseThrow(), shared);
    }
}
