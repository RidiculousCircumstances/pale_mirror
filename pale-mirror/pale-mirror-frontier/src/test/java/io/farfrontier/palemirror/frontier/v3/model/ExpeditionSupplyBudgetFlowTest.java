package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.expedition.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Finite pre-departure stock shortage; the registered market must admit and fund its own supplier stop. */
class ExpeditionSupplyBudgetFlowTest {
    @Test void registeredCollectionReservesFutureFoodAndReleasesUnusedBudgetAfterDeliveryAndRecovery() {
        exercise(true);
    }
    @Test void withdrawnSupplierOfferRecallsUnstartedTripAndReleasesItsSupplyBudget() {
        exercise(false);
    }
    private void exercise(boolean expectDelivery) {
        var base=FrontierV3FixtureCatalog.buyerCollectionConfiguration(new WorldId("frontier:funded-expedition"),20260918065L);
        var initial=base.initialState();var seller=new SubjectId("settlement:1");
        var buyer=initial.companies().goodsTrade().participants().participants().get(seller).known().getFirst().party().id();
        int retained=1;
        var resources=initial.inventory().fungibleResources();
        for(var account:new ArrayList<>(resources.accounts().values())) {
            if(!(account.custody() instanceof ResourceCustody.Container holder)
                    || !initial.inventory().containers().get(holder.containerId()).ownerId().equals(buyer))continue;
            for(var entry:new ArrayList<>(account.lotQuantities().entrySet())) {
                var lot=resources.lots().get(entry.getKey());if(!lot.itemKind().equals(FoodCatalog.BREAD)||!lot.economicOwnerId().equals(buyer))continue;
                int keep=Math.min(retained,entry.getValue());retained-=keep;
                if(entry.getValue()>keep)resources=resources.destroy(account.id(),Map.of(entry.getKey(),entry.getValue()-keep),Map.of());
            }
        }
        var sourceContainer=FrontierWorldState.depotId(seller);
        var sourceAccount=FungibleResourceCustodySupport.accountAtContainer(initial,sourceContainer).orElseThrow().id();
        var extra=new SubjectId("lot:funded-supplier-food");var staging=new SubjectId("custody:funded-supplier-food");
        resources=resources.issue(new ResourceLot(extra,seller,FoodCatalog.BREAD,256,"fixture:finite-supplier-food",List.of()),
                new CustodyAccount(staging,new ResourceCustody.Container(sourceContainer),Map.of(extra,256),Map.of()));
        resources=resources.transfer(staging,sourceAccount,Map.of(extra,256),Map.of());
        var people=initial.humanPopulation().residents().values().stream().filter(r -> r.settlementId().equals(buyer)).map(ResidentProfile::id).collect(java.util.stream.Collectors.toSet());
        initial=initial.withChanges(FrontierWorldStateUpdate.begin().inventory(initial.inventory().withFungibleResources(resources))
                .strategicPlans(initial.strategicPlans().withWorkPermissions(buyer,new ResidentWorkPermissions(Map.of(ResidentWorkKind.LOGISTICS,people)))));
        var config=configuration(base,initial);
        var engine=FrontierEngines.createCanonicalStateAccess(config);TransportMission admitted=null;
        for(int i=0;i<1024;i++) {
            var state=engine.canonicalState().state();
            admitted=state.shipments().missions().values().stream().filter(m -> m.supplies().flatMap(ExpeditionSupplyLoad::purchase).isPresent()).findFirst().orElse(null);
            if(admitted!=null)break;
            if(i>0&&i%128==0)engine=FrontierEngines.recoverCanonicalStateAccess(config,new RecoveryImage(config.worldId(),Optional.of(new SnapshotRecord(engine.checkpoint(),0)),List.of()));
            var next=engine.nextExecutionBoundary().orElseThrow();
            var result=engine.advanceTo(new SimInstant(Math.max(engine.executionView().instant().ticks(),next.ticks())),new WorkBudget(1,1024));
            assertEquals(EngineStatus.Kind.ACTIVE,result.status().kind(),result.status().failureDetail().orElse("active"));
        }
        assertNotNull(admitted,"the real market must publish a funded future purchase, not merely accept a test-built budget");
        assertEquals(buyer,admitted.organization().sponsor().id());assertTrue(admitted.preparingSupplies());
        var state=engine.canonicalState().state();var purchase=admitted.supplies().orElseThrow().purchase().orElseThrow();
        var budget=state.inventory().economics().budgets().get(admitted.financialBudgetId().orElseThrow());
        assertEquals(purchase.amount(),budget.remaining());assertEquals(admitted.id(),budget.ownerId());
        assertEquals(FinancialBudget.OwnerKind.TRANSPORT_MISSION,budget.ownerKind());
        var cash=state.inventory().economics().require(buyer).balance();
        assertTrue(state.inventory().economics().availableToReserve(buyer).compareTo(cash.minus(budget.remaining()))<=0);
        var snapshot=engine.checkpoint();var recovered=FrontierEngines.recoverCanonicalStateAccess(config,
                new RecoveryImage(config.worldId(),Optional.of(new SnapshotRecord(snapshot,0)),List.of()));
        assertEquals(state,recovered.canonicalState().state());
        assertEquals(budget,recovered.canonicalState().state().inventory().economics().budgets().get(budget.id()));
        engine=recovered;var missionId=admitted.id();var budgetId=budget.id();
        financialBoundaries(state,admitted);
        if(!expectDelivery) {
            var remaining=state.inventory().fungibleResources();
            for(var account:new ArrayList<>(remaining.accounts().values())) {
                if(!(account.custody() instanceof ResourceCustody.Container holder)
                        || !state.inventory().containers().get(holder.containerId()).ownerId().equals(seller))continue;
                var remove=new LinkedHashMap<SubjectId,Integer>();
                for(var entry:account.lotQuantities().entrySet()) {
                    var lot=remaining.lots().get(entry.getKey());if(!lot.itemKind().equals(FoodCatalog.BREAD))continue;
                    int claimed=account.claimQuantities().keySet().stream().mapToInt(id -> state.inventory().fungibleResources().claims().get(id).lotQuantities().getOrDefault(entry.getKey(),0)).sum();
                    if(entry.getValue()>claimed)remove.put(entry.getKey(),entry.getValue()-claimed);
                }
                if(!remove.isEmpty())remaining=remaining.destroy(account.id(),remove,Map.of());
            }
            var disrupted=state.withInventory(state.inventory().withFungibleResources(remaining));
            config=new FrontierEngineConfiguration<>(base.worldId(),disrupted,snapshot.instant(),base.commandPlanner(),base.scheduledPlanner(),base.reducer(),
                    base.stateCodec(),base.projectionMapper(),base.limits(),snapshot.schedules(),base.transactionCommitter(),base.stateValidator(),base.executionMetrics(),base.kernelQuarantineReporter());
            engine=FrontierEngines.createCanonicalStateAccess(config);
        }
        boolean bought=false,delivered=false,closed=false,recoveredPurchase=false;
        var payments=new HashSet<SubjectId>();
        long totalMoney=initial.inventory().economics().accounts().values().stream().mapToLong(a -> a.balance().raw()).sum();
        for(int i=0;i<8192;i++) {
            var current=engine.canonicalState().state();var mission=current.shipments().missions().get(missionId);
            assertEquals(totalMoney,current.inventory().economics().accounts().values().stream().mapToLong(a -> a.balance().raw()).sum(),"food purchases conserve total money");
            if(mission==null){closed=true;break;}
            if(mission.replenishmentPurchase().isPresent()) {
                var payment=mission.replenishmentPurchase().orElseThrow().payment();payments.add(payment.id());
                assertEquals(Optional.of(budgetId),payment.budgetId());
                if(!recoveredPurchase) {
                    engine=FrontierEngines.recoverCanonicalStateAccess(config,new RecoveryImage(config.worldId(),Optional.of(new SnapshotRecord(engine.checkpoint(),0)),List.of()));
                    assertEquals(current,engine.canonicalState().state());recoveredPurchase=true;
                }
            }
            if(!payments.isEmpty()&&mission.replenishment().isEmpty()
                    && payments.stream().noneMatch(current.inventory().economics().reservations()::containsKey))bought=true;
            delivered|=mission.shipmentIds().stream().allMatch(id -> current.shipments().shipments().get(id).status()==Shipment.Status.DELIVERED);
            if(mission.stage()==TransportMission.Stage.COMPLETE) {
                assertEquals(FixedScalar.ZERO,current.inventory().economics().budgets().get(budgetId).remaining(),"completion releases all unused supply money");closed=true;break;
            }
            if(i>0&&i%128==0)engine=FrontierEngines.recoverCanonicalStateAccess(config,new RecoveryImage(config.worldId(),Optional.of(new SnapshotRecord(engine.checkpoint(),0)),List.of()));
            var next=engine.nextExecutionBoundary().orElseThrow();
            var result=engine.advanceTo(new SimInstant(Math.max(engine.executionView().instant().ticks(),next.ticks())),new WorkBudget(1,1024));
            assertEquals(EngineStatus.Kind.ACTIVE,result.status().kind(),result.status().failureDetail().orElse("active"));
        }
        if(!expectDelivery) {
            assertFalse(recoveredPurchase);assertTrue(closed);
            assertEquals(FixedScalar.ZERO,engine.canonicalState().state().inventory().economics().budgets().get(budgetId).remaining());
            return;
        }
        if(!payments.isEmpty()) {
            assertTrue(recoveredPurchase,"a purchase in flight must survive recovery");
            assertTrue(bought,"actual food receipt settles the held payment");
        }
        assertTrue(delivered,"the paid stop must resume actual cargo delivery");
        assertTrue(closed,"the same registered mission must finish and release its budget");
    }
    private static void financialBoundaries(FrontierWorldState state,TransportMission mission) {
        var funding=state.inventory().economics();var budget=funding.budgets().get(mission.financialBudgetId().orElseThrow());
        assertEquals(budget,ExpeditionFundingAuthority.purchaseBudget(state,mission,new FixedScalar(1)),"a small purchase must not shrink the rest of the authorized budget");
        var payee=mission.sender().settlementId();
        var drained=funding.transfer(mission.organization().sponsor().id(),payee,funding.availableToReserve(mission.organization().sponsor().id()));
        var noFreeCash=state.withInventory(state.inventory().withEconomics(drained));
        assertThrows(IllegalArgumentException.class,() -> ExpeditionFundingAuthority.purchaseBudget(noFreeCash,mission,budget.remaining().plus(new FixedScalar(1))),"a higher price needs real additional treasury funds");
        var missing=new TransportMission(mission.id(),mission.groupId(),mission.shipmentIds(),mission.sender(),mission.receiver(),mission.organization(),
                mission.homeRendezvous(),mission.destinationRendezvous(),mission.stage(),mission.revision(),
                Optional.empty(),mission.supplies(),mission.transportAssetId(),mission.replenishment(),mission.replenishmentPurchase());
        assertThrows(IllegalArgumentException.class,() -> ExpeditionSupplyAuthority.validate(state,missing,state.unitGroups().groups().get(mission.groupId())),"a priced future stop cannot omit its held money");
        var foreign=new TransportMission(new SubjectId("transport-mission:foreign-budget"),mission.groupId(),mission.shipmentIds(),mission.sender(),mission.receiver(),mission.organization(),
                mission.homeRendezvous(),mission.destinationRendezvous(),mission.stage(),mission.revision(),
                mission.financialBudgetId(),mission.supplies(),mission.transportAssetId(),mission.replenishment(),mission.replenishmentPurchase());
        assertThrows(IllegalArgumentException.class,() -> ExpeditionFundingAuthority.availableForPlan(state,foreign),"another mission cannot spend this hold");
        var oldBytes=new FrontierWorldStateCodec().encode(state);java.nio.ByteBuffer.wrap(oldBytes).putShort(4,(short)344);
        assertThrows(IllegalArgumentException.class,() -> new FrontierWorldStateCodec().decode(oldBytes),"an old schema cannot invent missing purchase declarations");
    }
    private static FrontierEngineConfiguration<FrontierWorldState,FrontierWorldProjection> configuration(
            FrontierEngineConfiguration<FrontierWorldState,FrontierWorldProjection> base,FrontierWorldState initial) {
        var schedules=new ArrayList<>(base.initialSchedules());
        schedules.removeIf(a -> a.kind().equals(io.farfrontier.palemirror.frontier.v3.process.ResidentNeedProcess.REVIEW));
        for(var resident:initial.humanPopulation().residents().values())schedules.add(
                io.farfrontier.palemirror.frontier.v3.process.ResidentNeedProcess.review(resident.id(),
                    initial.humanPopulation().nutrition(resident.id()).nextThresholdTick(initial.bootstrap().ruleset().residentLife(),
                        resident.characteristics().effectiveMetabolismPermille(0))));
        return new FrontierEngineConfiguration<>(base.worldId(),initial,base.initialInstant(),base.commandPlanner(),base.scheduledPlanner(),base.reducer(),
                base.stateCodec(),base.projectionMapper(),base.limits(),schedules,base.transactionCommitter(),base.stateValidator(),base.executionMetrics(),base.kernelQuarantineReporter());
    }

}
