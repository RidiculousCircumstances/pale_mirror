package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Off-thread analytical projection from one immutable cut. No planner/cache/physical-world access. */
final class FrontierV3EconomyProjection {
    private FrontierV3EconomyProjection() { }
    static Map<String,Object> row(Object... pairs) {
        var result = new LinkedHashMap<String,Object>();
        for (int i=0;i<pairs.length;i+=2) result.put((String)pairs[i], pairs[i+1] instanceof SubjectId id ? id.value() : pairs[i+1]);
        return result;
    }
    static Map<String,Object> snapshot(FrontierWorldState s, long tick, long revision, String status) {
        var towns = new ArrayList<Map<String,Object>>();
        for (var town : s.bootstrap().settlements()) {
            int residents=0, hungry=0;
            for (var resident : s.humanPopulation().residents().values()) {
                var location=s.actorLocations().get(resident.id());
                if (!resident.settlementId().equals(town.id()) || location==null || location.condition().health().raw()==0) continue;
                residents++;
                var nutrition=s.humanPopulation().nutrition(resident.id()).accrueThrough(tick,
                        s.bootstrap().ruleset().residentLife(), resident.characteristics().effectiveMetabolismPermille(tick));
                if (nutrition.wantsFood(s.bootstrap().ruleset().residentLife())) hungry++;
            }
            var finance=FinancialAccountReport.read(s.inventory().economics(),town.id());
            towns.add(row("id",town.id(),"name",town.displayName(),"x",town.anchor().x(),"y",town.anchor().y(),"z",town.anchor().z(),"residents",residents,"hungry",hungry,
                    "balanceRaw",finance.balance().raw(),"reservedRaw",finance.reservedPayments().raw(),
                    "budgetsRaw",finance.unusedBudgets().raw(),"availableRaw",finance.availableToCommit().raw(),
                    "receivedRaw",finance.turnover().received().raw(),"paidRaw",finance.turnover().paid().raw()));
        }
        var stocks=stocks(s); var orders=new ArrayList<Map<String,Object>>(); var contracts=new ArrayList<Map<String,Object>>();
        var trade=s.companies().goodsTrade();
        for (var order:trade.orders().values()) if(order.availableQuantity()>0 && order.expiresAtTick()>tick)
            orders.add(row("id",order.id(),"town",order.party().id(),"counterparty",order.counterparty().id(),"side",order.side().name(),
                    "item",order.itemKind(),"quantity",order.availableQuantity(),"priceRaw",order.unitPriceLimit().raw(),"expires",order.expiresAtTick()));
        for (var contract:trade.contracts().values()) if(!contract.terminal())
            contracts.add(row("id",contract.id(),"seller",contract.seller().id(),"buyer",contract.buyer().id(),"item",contract.itemKind(),
                    "quantity",contract.quantity(),"accepted",contract.acceptedQuantity(),"disposed",contract.disposedQuantity()));
        var missions=new ArrayList<Map<String,Object>>();
        for(var mission:s.shipments().missions().values()) {
            var group=s.unitGroups().groups().get(mission.groupId());
            missions.add(row("id",mission.id(),"town",mission.organization().home().settlementId(),
                    "source",mission.sender().settlementId(),"destination",mission.receiver().settlementId(),
                    "stage",mission.stage().name(),"members",group==null?0:group.members().size(),"shipments",mission.shipmentIds().size(),
                    "items",mission.shipmentIds().stream().map(id->s.shipments().shipments().get(id)).filter(Objects::nonNull).map(Shipment::itemKind).distinct().toList(),
                    "asset",mission.transportAssetId().map(SubjectId::value).orElse("")));
        }
        var jobs=new ArrayList<Map<String,Object>>();
        for(var work:s.extractionSites().work().values()) jobs.add(row("id",work.id(),"town",work.storageTarget().settlementId(),
                "kind","EXTRACTION","item",work.outputKind(),"phase",work.phase().name(),"actor",work.execution().actorId()));
        for(var job:s.productionJobs().values()) jobs.add(row("id",job.id(),"town",job.settlementId(),"kind","PRODUCTION","item",job.outputItemKind(),
                "phase",job.stationWork().map(w->w.phase().name()).orElse("WAITING"),"actor",job.workerId()));
        for(var site:s.resourceSites().sites().values()) for(var work:site.harvestJobs().values())
            jobs.add(row("id",work.id(),"town",s.humanPopulation().resident(work.workerId()).settlementId(),
                    "kind","HARVEST","item","minecraft:wheat","phase",work.progress().completedCropSlots()+"/"+work.progress().totalCropSlots(),"actor",work.workerId()));
        return row("id","snapshot:"+revision,"kind","snapshot","tick",tick,"revision",revision,"status",status,
                "map",FrontierV3EconomyMapProjection.snapshot(s,tick),"settlements",towns,"stocks",stocks,"orders",orders,"contracts",contracts,"missions",missions,"jobs",jobs);
    }
    record StockKey(SubjectId owner,String item) { }
    private record AccountBalance(SubjectId account,StockKey stock) { }
    static List<Map<String,Object>> stocks(FrontierWorldState state) {
        var totals=new HashMap<StockKey,long[]>(); var ledger=state.inventory().fungibleResources();
        var measured=new HashSet<AccountBalance>();
        var eligible=new HashMap<StockKey,Set<SubjectId>>();
        for(var account:ledger.accounts().values()) for(var entry:account.lotQuantities().entrySet()) {
            var lot=ledger.lots().get(entry.getKey()); var key=new StockKey(lot.economicOwnerId(),lot.itemKind());
            var value=totals.computeIfAbsent(key,ignored->new long[6]); long quantity=entry.getValue(); value[0]+=quantity;
            int bucket=account.custody() instanceof ResourceCustody.Container ? 1
                    : account.custody() instanceof ResourceCustody.Actor || account.custody() instanceof ResourceCustody.Cargo ? 2 : 3;
            value[bucket]+=quantity;
            if(account.custody() instanceof ResourceCustody.Container container && eligible.computeIfAbsent(key,
                    ignored->ResourceStorageSelection.containers(state,key.owner(),key.item(),Set.of(ResourceStorageRole.WAREHOUSE))).contains(container.containerId())
                    && measured.add(new AccountBalance(account.id(),key)))
                value[5]+=ledger.unclaimedQuantity(account.id(),key.owner(),key.item());
        }
        for(var claim:ledger.claims().values()) totals.computeIfAbsent(new StockKey(claim.economicOwnerId(),claim.itemKind()),ignored->new long[6])[4]+=claim.quantity();
        for(var item:state.inventory().items().values()) {
            var value=totals.computeIfAbsent(new StockKey(item.economicOwnerId(),item.itemKind()),ignored->new long[6]);value[0]+=item.count();
            value[item.custody() instanceof InventoryCustody.ContainerSlot ? 1 : item.custody() instanceof InventoryCustody.Actor ? 2 : 3]+=item.count();
        }
        return totals.entrySet().stream().sorted(Comparator.comparing((Map.Entry<StockKey,long[]> e)->e.getKey().owner().value()).thenComparing(e->e.getKey().item()))
                .map(e->row("town",e.getKey().owner(),"item",e.getKey().item(),"total",e.getValue()[0],"stored",e.getValue()[1],
                        "carried",e.getValue()[2],"other",e.getValue()[3],"claimed",e.getValue()[4],"unclaimedWarehouse",e.getValue()[5])).toList();
    }
}
