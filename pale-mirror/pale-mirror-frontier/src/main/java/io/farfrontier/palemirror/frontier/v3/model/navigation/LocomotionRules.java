package io.farfrontier.palemirror.frontier.v3.model.navigation;

/** Hashed pace policy. Modifiers request speed; they never grant a route or physical authority. */
public record LocomotionRules(int runPermille, int maneuverPermille, int carefulPermille) {
    public LocomotionRules {
        if (runPermille < 1000 || runPermille > 3000 || maneuverPermille < 1000
                || maneuverPermille > runPermille || carefulPermille < 250 || carefulPermille > 1000)
            throw new IllegalArgumentException("invalid locomotion policy");
    }
    public static LocomotionRules initial() { return new LocomotionRules(1500, 1300, 850); }
    public int multiplier(LocomotionMode mode) {
        return switch (mode) { case WALK -> 1000; case RUN -> runPermille;
            case MANEUVER -> maneuverPermille; case CAREFUL -> carefulPermille; };
    }
    public String canonicalText() { return runPermille + ":" + maneuverPermille + ":" + carefulPermille; }
}
