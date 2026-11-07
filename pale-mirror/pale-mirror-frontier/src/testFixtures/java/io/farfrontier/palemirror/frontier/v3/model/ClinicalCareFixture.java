package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;

/** Medicine is an explicit scenario prerequisite. No wound, task, dressing, healing or occupied bed is seeded. */
final class ClinicalCareFixture {
    static FrontierEngineConfiguration<FrontierWorldState,FrontierWorldProjection> configuration(WorldId world,long seed){
        var base=FrontierWorldRuntimeDefinition.configuration(world,seed,FrontierRulesets.installed("frontier-v3-clinical-care-r35"));
        var s=base.initialState();var home=s.bootstrap().settlements().getFirst().id();
        var container=s.inventory().containers().get(FrontierWorldState.depotId(home));
        var ledger=s.inventory().fungibleResources();var lot=new SubjectId("lot:clinical-fixture-medicine");var staging=new SubjectId("custody:clinical-fixture-medicine");
        ledger=ledger.issue(new ResourceLot(lot,home,s.bootstrap().ruleset().clinical().bandage(),16,"fixture:initial-medicine",List.of()),
                new CustodyAccount(staging,new ResourceCustody.Container(container.id()),Map.of(lot,16),Map.of()));
        var existing=FungibleResourceCustodySupport.accountAtContainer(s,container.id());
        if(existing.isPresent())ledger=ledger.transfer(staging,existing.orElseThrow().id(),Map.of(lot,16),Map.of());
        s=s.withInventory(s.inventory().withFungibleResources(ledger));
        return new FrontierEngineConfiguration<>(base.worldId(),s,base.initialInstant(),base.commandPlanner(),base.scheduledPlanner(),base.reducer(),base.stateCodec(),
                base.projectionMapper(),base.limits(),base.initialSchedules(),base.transactionCommitter(),base.stateValidator(),base.executionMetrics(),base.kernelQuarantineReporter());
    }
    private ClinicalCareFixture(){}
}
