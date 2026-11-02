package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** Canonical allocation publication. Each response names its exact duty/home/target. */
public record SecurityResponsesAllocated(SubjectId home,Map<SubjectId,SecurityState.Response> responses) implements FrontierPayload {
    public static final String TYPE="frontier.security.responses_allocated";
    public SecurityResponsesAllocated { Objects.requireNonNull(home); responses=Map.copyOf(responses);
        responses.forEach((id,value)->{if(!id.equals(value.duty()) || !home.equals(value.contact().home()))
            throw new IllegalArgumentException("foreign response declaration");}); }
    public String type(){return TYPE;}
    public FrontierWorldState apply(FrontierWorldState state,SubjectId subject,long tick) {
        if(!home.equals(subject) || !responses.equals(SecurityDispatch.plan(state,home,tick)))
            throw new IllegalArgumentException("stale or forged response allocation");
        return state.withChanges(FrontierWorldStateUpdate.begin().security(state.security().allocated(home,responses,tick,
                state.bootstrap().ruleset().security().investigationTicks())));
    }
}
