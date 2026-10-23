package io.farfrontier.palemirror.frontier.v3.model.navigation;

/** Physical following clearance, shared by formation planning and native traffic.
 * Distances are centre-to-centre; width is the declared or observed body envelope. */
public final class PedestrianHeadway {
    private PedestrianHeadway() { }
    public static double clearance(double followerWidth, double peerWidth) {
        if (!Double.isFinite(followerWidth) || !Double.isFinite(peerWidth) || followerWidth <= 0 || peerWidth <= 0)
            throw new IllegalArgumentException("headway requires positive body dimensions");
        return (followerWidth + peerWidth) / 2;
    }
    public static double freePaceDistance(double followerWidth, double peerWidth) {
        return clearance(followerWidth, peerWidth) + Math.max(followerWidth, peerWidth);
    }
    public static double paceFraction(double distance, double followerWidth, double peerWidth) {
        return paceFraction(distance, followerWidth, peerWidth, 0);
    }
    public static double paceFraction(double distance, double followerWidth, double peerWidth, double requestedSpacing) {
        if (!Double.isFinite(requestedSpacing) || requestedSpacing < 0) throw new IllegalArgumentException("invalid requested spacing");
        double clearance = clearance(followerWidth, peerWidth);
        double free = Math.max(requestedSpacing, freePaceDistance(followerWidth, peerWidth));
        return Math.clamp((distance - clearance) / (free - clearance), 0, 1);
    }
    public static int formationSpacing(int requested, double enclosingWidth) {
        return Math.max(requested, (int) Math.ceil(freePaceDistance(enclosingWidth, enclosingWidth)));
    }
}
