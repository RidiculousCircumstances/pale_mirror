package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** Shared personal stock queries. No ammunition counter is kept beside the resource ledger. */
public final class Ammunition {
    public static int available(FrontierWorldState state,SubjectId actor,SubjectId title,String kind) {
        var ledger=state.inventory().fungibleResources();
        return ActorCarriedResources.accounts(ledger,actor).stream().mapToInt(a->ledger.unclaimedQuantity(a.id(),title,kind)).sum();
    }
    public static Optional<ActorItemSlot.Pocket> freePocket(FrontierWorldState state,SubjectId actor) {
        var occupied=ActorCarriedResources.accounts(state.inventory().fungibleResources(),actor).stream().flatMap(a->a.actorPresentation().stream()).collect(java.util.stream.Collectors.toSet());
        for(int i=0;i<8;i++){var slot=new ActorItemSlot.Pocket(i);if(!occupied.contains(slot))return Optional.of(slot);}return Optional.empty();
    }
    public static Optional<PhysicalStackBinding> spendable(FrontierWorldState state,SubjectId actor,SubjectId title,String kind) {
        var ledger=state.inventory().fungibleResources();
        return ActorCarriedResources.accounts(ledger,actor).stream().sorted(Comparator.comparing(CustodyAccount::id))
                .filter(a->a.claimQuantities().isEmpty() && ledger.unclaimedQuantity(a.id(),title,kind)>0)
                .flatMap(a->FungibleBindingReadIndex.forAccount(ledger,a.id()).stream())
                .filter(b->b.itemKind().equals(kind) && b.claimQuantities().isEmpty()).findFirst();
    }
    public static boolean pendingForContainer(FrontierWorldState state,SubjectId container) {
        return state.combat().ranged().supplies().values().stream().anyMatch(s->s.pending().isPresent() && s.order().containerEndpoint().containerId().equals(container));
    }
    private Ammunition(){}
}
