package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.*;
/** Common inventory boundary receives the combat owner's exact pending identity. */
public final class CombatInventoryFence {
    public static Optional<SubjectId> pendingOwner(FrontierWorldState state,SubjectId actor){
        return state.security().pending().values().stream().filter(strike->strike.attacker().body().actorId().equals(actor))
                .map(SecurityState.Strike::duty).findFirst();
    }
    private CombatInventoryFence(){}
}
