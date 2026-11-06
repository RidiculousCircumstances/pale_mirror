package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Real station clock and shared recipe transformation; inputs are declared unit preconditions, not native setup. */
class RangedManufactureTest {
    @ParameterizedTest @ValueSource(strings={"make_crossbow","iron_arrowheads","cloth_arrows"})
    void stationManufacturesExactDeclaredOutputsAndCannotReplay(String name){
        var s=FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:ranged-manufacture"),139,FrontierRulesets.installed("frontier-v3-ranged-guards-r33")));
        var recipe=s.bootstrap().ruleset().production().require(new ProductionRecipe.Id("pale_mirror:"+name));
        var record=s.inventory().containers().values().stream().filter(c->c.productionStation().filter(p->p.capability()==recipe.capability()).isPresent()).sorted(Comparator.comparing(ContainerRecord::id)).findFirst().orElseThrow();
        var station=record.productionStation().orElseThrow();var ledger=s.inventory().fungibleResources();int slot=0;
        var materials=ProductionRecipeInputs.select(recipe,k->0L);
        var inputAccount=ContainerResourceAccounts.id(station.custody(ActorContainerItemOrder.StationPort.INPUT));
        var observations=new ArrayList<FungiblePhysicalObservation.Stack>();
        for(var input:materials.entrySet()){
            var lot=new SubjectId("lot:ranged-manufacture-"+slot);var account=slot==0?inputAccount:new SubjectId("custody:ranged-manufacture-"+slot);
            ledger=ledger.issue(new ResourceLot(lot,record.ownerId(),input.getKey(),input.getValue(),"fixture:material",List.of()),
                    new CustodyAccount(account,station.custody(ActorContainerItemOrder.StationPort.INPUT),Map.of(lot,input.getValue()),Map.of()));
            if(!account.equals(inputAccount))ledger=ledger.transfer(account,inputAccount,Map.of(lot,input.getValue()),Map.of());
            observations.add(ProductionStationOperatorTest.stack(station,station.inputSlots().get(slot++),input.getKey(),input.getValue()));
        }
        ledger=ledger.rebind(inputAccount,1,FungiblePhysicalObservation.bind(ledger,inputAccount,1,observations));
        var fuel=new SubjectId("lot:ranged-manufacture-fuel");var fuelAccount=ContainerResourceAccounts.id(station.custody(ActorContainerItemOrder.StationPort.FUEL));
        ledger=ledger.issue(new ResourceLot(fuel,record.ownerId(),"minecraft:coal",2,"fixture:fuel",List.of()),new CustodyAccount(fuelAccount,station.custody(ActorContainerItemOrder.StationPort.FUEL),Map.of(fuel,2),Map.of()));
        ledger=ledger.rebind(fuelAccount,1,FungiblePhysicalObservation.bind(ledger,fuelAccount,1,List.of(ProductionStationOperatorTest.stack(station,station.fuelSlot().orElseThrow(),"minecraft:coal",2))));
        s=ReferenceContainerCustodyFixtures.observedAndHeld(s.withInventory(s.inventory().withFungibleResources(ledger)),record.id());
        var player=new UUID(0,733);long tick=200;
        s=ProductionStationOperator.apply(s,station.id(),new ProductionStationOperatorChanged(station.id(),s.productionStations().require(station.id()).revision(),player,recipe.id(),ProductionStationOperatorChanged.Stage.START,tick));
        s=ProductionCombustionFixtures.igniteHotIfNeeded(s,station.id(),tick);
        for(int step=0;!s.productionStations().require(station.id()).operation().orElseThrow().complete() && step<100;step++){
            tick+=20;s=ProductionStationOperator.apply(s,station.id(),new ProductionStationOperatorChanged(station.id(),s.productionStations().require(station.id()).revision(),player,recipe.id(),
                    ProductionStationOperatorChanged.Stage.ATTEND,tick));
        }
        assertTrue(s.productionStations().require(station.id()).operation().orElseThrow().complete());
        var codec=new FrontierWorldStateCodec();s=codec.decode(codec.encode(s));
        var boundary=ProductionStationTransformation.next(s,station.id()).orElseThrow();
        var prepare=new ProductionStationRecipeChanged(ProductionStationRecipeChanged.Stage.HOT_PREPARED,boundary,List.of());
        s=ProductionStationTransformation.apply(s,station.id(),prepare,ProductionOperationCompletionComposition::completed);
        s=codec.decode(codec.encode(s));
        var completed=new ProductionStationRecipeChanged(ProductionStationRecipeChanged.Stage.HOT_COMPLETED,boundary,boundary.after(),boundary.exactAfter());
        s=ProductionStationTransformation.apply(s,station.id(),completed,ProductionOperationCompletionComposition::completed);
        assertTrue(s.productionStations().require(station.id()).operation().isEmpty());
        for(var input:materials.keySet())assertEquals(0,s.inventory().fungibleResources().totalQuantity(record.ownerId(),input),"declared station input consumed");
        if(recipe.outcome() instanceof ProductionRecipeOutcome.Individual){
            var output=s.inventory().items().get(boundary.exactAfter().orElseThrow().id());assertEquals("minecraft:crossbow",output.itemKind());assertEquals(new ItemCondition(465,0),output.condition());
        }else assertEquals(recipe.product().quantity(),s.inventory().fungibleResources().totalQuantity(record.ownerId(),recipe.product().itemKind()));
        var after=s;assertThrows(RuntimeException.class,()->ProductionStationTransformation.apply(after,station.id(),completed,ProductionOperationCompletionComposition::completed));
        assertEquals(s,codec.decode(codec.encode(s)));
    }
}
