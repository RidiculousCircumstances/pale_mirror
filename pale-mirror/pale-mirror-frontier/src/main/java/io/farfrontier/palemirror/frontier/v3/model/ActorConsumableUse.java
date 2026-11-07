package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
/** One actual unclaimed carried unit. Domain authorization belongs to its declared activity. */
public final class ActorConsumableUse {
    public record Portion(SubjectId account,SubjectId lot){}
    public static Optional<Portion> select(FrontierWorldState s,SubjectId actor,SubjectId owner,String kind){
        var l=s.inventory().fungibleResources();
        for(var a:ActorCarriedResources.accounts(l,actor).stream().sorted(Comparator.comparing(CustodyAccount::id)).toList()){
            if(!a.claimQuantities().isEmpty())continue;
            for(var lot:a.lotQuantities().keySet().stream().sorted().toList()){
                var v=l.lots().get(lot);if(v.economicOwnerId().equals(owner)&&v.itemKind().equals(kind))return Optional.of(new Portion(a.id(),lot));
            }
        }return Optional.empty();
    }
    public static void require(FrontierWorldState s,SubjectId actor,SubjectId owner,String kind,Portion p){
        var l=s.inventory().fungibleResources();var a=l.accounts().get(p.account());var lot=l.lots().get(p.lot());
        if(a==null||!a.custody().equals(new ResourceCustody.Actor(actor))||!a.claimQuantities().isEmpty()
                ||a.lotQuantities().getOrDefault(p.lot(),0)<1||lot==null||!lot.economicOwnerId().equals(owner)||!lot.itemKind().equals(kind))
            throw new IllegalArgumentException("consumable has no exact unclaimed owned personal unit");
    }
    public static FungibleResourceLedger cold(FrontierWorldState s,SubjectId actor,SubjectId owner,String kind,Portion p){
        require(s,actor,owner,kind,p);
        if(!ActorExecutionCoordinator.coldAvailable(s,actor))throw new IllegalArgumentException("personal consumption conflicts with physical custody");
        return s.inventory().fungibleResources().destroy(p.account(),Map.of(p.lot(),1),Map.of());
    }
    public static FungibleResourceLedger observed(FrontierWorldState s,SubjectId actor,SubjectId owner,String kind,PhysicalStackBinding binding,SubjectId lot,List<FungiblePhysicalObservation.Stack> remaining){
        require(s,actor,owner,kind,new Portion(binding.accountId(),lot));var ledger=s.inventory().fungibleResources();
        if(!binding.equals(ledger.bindings().get(binding.id())))throw new IllegalArgumentException("consumable physical preimage changed");
        var expected=binding.quantity()==1?List.<FungiblePhysicalObservation.Stack>of():List.of(new FungiblePhysicalObservation.Stack(binding.address(),kind,binding.quantity()-1));
        if(!expected.equals(remaining))throw new IllegalArgumentException("consumable receipt must spend exactly one unit");
        return ledger.consumeUnclaimedObserved(binding.accountId(),binding.authorityEpoch(),Map.of(lot,1),remaining);
    }
    private ActorConsumableUse(){}
}
