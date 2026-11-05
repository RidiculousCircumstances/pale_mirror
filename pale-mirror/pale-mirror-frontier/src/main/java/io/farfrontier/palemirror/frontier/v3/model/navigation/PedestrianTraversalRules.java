package io.farfrontier.palemirror.frontier.v3.model.navigation;

/** Hashed soft route preferences; collision and hard access constraints remain independent. */
public record PedestrianTraversalRules(int obstacleSupportPenalty) {
    public PedestrianTraversalRules {
        if (obstacleSupportPenalty < 0 || obstacleSupportPenalty > 1000)
            throw new IllegalArgumentException("obstacle support penalty must be 0..1000");
    }
    public static PedestrianTraversalRules initial() { return new PedestrianTraversalRules(12); }
    public String canonicalText() { return Integer.toString(obstacleSupportPenalty); }
}
