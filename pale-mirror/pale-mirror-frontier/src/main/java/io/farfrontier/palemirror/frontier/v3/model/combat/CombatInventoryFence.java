package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.*;
/** Common inventory boundary receives the combat owner's exact pending identity. */
public final class CombatInventoryFence {
    public static Optional<SubjectId> pendingOwner(FrontierWorldState state,SubjectId actor){
        var pending=state.combat().ranged().pending().values().stream().filter(s->s.attacker().execution().actorId().equals(actor)).map(RangedShot::owner).findFirst();
        if(pending.isPresent())return pending;
        pending=state.combat().ranged().supplies().values().stream().filter(s->s.pending().isPresent()&&s.execution().actorId().equals(actor)).map(RangedSupply::owner).findFirst();
        if(pending.isPresent())return pending;
        return state.combat().pending().values().stream().filter(strike->strike.attacker().body().actorId().equals(actor))
                .map(MeleeStrike::owner).findFirst();
    }
    private CombatInventoryFence(){}
}
