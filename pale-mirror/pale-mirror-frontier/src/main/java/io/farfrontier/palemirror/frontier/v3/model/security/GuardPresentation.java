package io.farfrontier.palemirror.frontier.v3.model.security;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Objects;

/** Guard-owned read-only labels. Presentation never grants allocation or movement authority. */
public final class GuardPresentation {
    public static String task(FrontierWorldState state,SubjectId owner) {
        var duty=Objects.requireNonNull(state.strategicPlans().guards().get(owner),"declared guard appointment");
        return switch(duty.phase()) {
            case EQUIPPING -> "Collect guard equipment";
            case RETURNING -> "Return guard equipment";
            case ON_DUTY -> duty.mission().map(mission -> switch(mission.stage()) {
                case PATROL -> "Patrol protected sites";
                case WATCH -> "Watch post";
                case INTERCEPT -> "Intercept threat";
                case SEARCH -> "Search last known area";
                case RETURN -> "Return to post";
            }).orElse("Guard duty");
        };
    }
    private GuardPresentation() { }
}
