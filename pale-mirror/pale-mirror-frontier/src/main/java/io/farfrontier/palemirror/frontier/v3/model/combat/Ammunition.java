package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** Shared personal stock queries. No ammunition counter is kept beside the resource ledger. */
public final class Ammunition {
    public static int available(FrontierWorldState state,SubjectId actor,SubjectId title,String kind) { return ActorConsumables.available(state,actor,title,kind); }
    public static Optional<ActorItemSlot.Pocket> freePocket(FrontierWorldState state,SubjectId actor) { return ActorConsumables.freePocket(state,actor); }
    public static Optional<PhysicalStackBinding> spendable(FrontierWorldState state,SubjectId actor,SubjectId title,String kind) { return ActorConsumables.spendable(state,actor,title,kind); }
    public static boolean pendingForContainer(FrontierWorldState state,SubjectId container) {
        return state.combat().ranged().supplies().values().stream().anyMatch(s->s.pending().isPresent() && s.order().containerEndpoint().containerId().equals(container));
    }
    private Ammunition(){}
}
