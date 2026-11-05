package io.farfrontier.palemirror.frontier.v3.model.navigation;

/** Hashed soft route preferences; collision and hard access constraints remain independent. */
public record PedestrianTraversalRules(int obstacleSupportPenalty, int manualInteractionReachMicros) {
    public PedestrianTraversalRules(int obstacleSupportPenalty) { this(obstacleSupportPenalty, 1_500_000); }
    public PedestrianTraversalRules {
        if (manualInteractionReachMicros < 500_000 || manualInteractionReachMicros > 2_000_000)
            throw new IllegalArgumentException("manual passage reach must be .5..2 blocks");
        if (obstacleSupportPenalty < 0 || obstacleSupportPenalty > 1000)
            throw new IllegalArgumentException("obstacle support penalty must be 0..1000");
    }
    public static PedestrianTraversalRules initial() { return new PedestrianTraversalRules(12); }
    public double manualInteractionReach() { return manualInteractionReachMicros / 1_000_000.0; }
    public String canonicalText() { return obstacleSupportPenalty + ":" + manualInteractionReachMicros; }
}
