package io.farfrontier.palemirror.frontier.v3.model;
import java.util.*;
/** Only holders affected by a nominal right change lose their accepted capacity facts. */
final class SiteUseEconomicInputChanges implements EconomicInputChangePort {
    public void collect(FrontierWorldState before,FrontierWorldState after,Sink sink) {
        if(before.siteUses()==after.siteUses())return;
        EconomicInputChangePort.difference(before.siteUses().grants(),after.siteUses().grants(),(old,next)->{
            if(old!=null)sink.changed(old.holder(),SettlementEconomicPlanningState.Change.CAPACITY,Set.of());
            if(next!=null)sink.changed(next.holder(),SettlementEconomicPlanningState.Change.COMMITMENT,Set.of());
        });
    }
}
