package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InternalTransportPlanningTest {
    @Test void mixedOwnedCargoUsesOneFiniteAssetAndOneGroupWithoutSaleAndFinishesTheRealQueue() {
        var bootstrap=FrontierBootstrapper.create(new WorldId("frontier:internal-manifest"),41,
                FrontierRulesets.installed("frontier-v3-simulation-baseline-r31"));
        var state=FrontierWorldState.initial(bootstrap);var home=bootstrap.settlements().getFirst();
        var id=new SubjectId("container:test-remote-buffer");
        var position=home.anchor().offset(120,0,80);
        var station=new SurfaceAnchor(position.offset(1,-1,0));
        var source=new ShipmentEndpoint.Storage(home.id(),id,station,Optional.empty(),station);
        var container=new ContainerRecord(id,home.id(),27,ContainerPurpose.RESOURCE_STORAGE,Optional.empty(),
                Optional.of(new ResourceStoragePolicy(source,ResourceStorageFilter.all(),0,ResourceStorageRole.LOCAL_BUFFER)));
        state=new ResourceStorageDeclared(container,position).apply(state,home.id());
        var account=new SubjectId("custody:test-remote-buffer");
        var ledger=state.inventory().fungibleResources();var lots=new HashMap<>(ledger.lots());var accounts=new HashMap<>(ledger.accounts());
        var coal=new SubjectId("lot:test-coal");var iron=new SubjectId("lot:test-raw-iron");
        lots.put(coal,new ResourceLot(coal,home.id(),"minecraft:coal",64,"test:finite-output",List.of()));
        lots.put(iron,new ResourceLot(iron,home.id(),"minecraft:raw_iron",64,"test:finite-output",List.of()));
        accounts.put(account,new CustodyAccount(account,source.custody(),Map.of(coal,64,iron,64),Map.of()));
        ledger=new FungibleResourceLedger(lots,ledger.claims(),accounts,ledger.bindings());
        state=state.withInventory(state.inventory().withFungibleResources(ledger));
        var worker=SettlementWorkforce.candidates(state,home.id(),ResidentWorkKind.LOGISTICS,HumanCapability.LOGISTICS).getFirst();
        var offered=InternalTransportPlanning.offer(state,worker,10).orElseThrow();
        assertEquals(2,offered.transport().shipments().size());
        assertEquals(1,offered.transport().shipments().stream().map(Shipment::execution).distinct().count());
        assertTrue(offered.transport().shipments().stream().allMatch(parcel->parcel.execution().activityKind()==ActorActivityKind.GROUP_MEMBER));
        var coins=state.inventory().economics().require(home.id()).balance();
        state=InternalTransportPlanning.admit(state,offered.transport().mission().id(),offered,10);
        assertEquals(coins,state.inventory().economics().require(home.id()).balance());
        var codec=new FrontierWorldStateCodec();state=codec.decode(codec.encode(state));
        var base=FrontierWorldRuntimeDefinition.configuration(bootstrap);
        var initial=new ArrayList<>(TransportMissionProcess.initialContinuations(offered.transport().mission(),11));
        // Residents already own their activity review in ordinary composition; transport only reschedules it.
        initial.add(ResidentActivityProcess.review(worker.id(),100000));
        var engine=FrontierEngines.createCanonicalStateAccess(new FrontierEngineConfiguration<>(base.worldId(),state,new SimInstant(10),
                base.commandPlanner(),base.scheduledPlanner(),base.reducer(),base.stateCodec(),base.projectionMapper(),base.limits(),
                initial,base.transactionCommitter(),base.stateValidator(),
                base.executionMetrics(),base.kernelQuarantineReporter()));
        for(long tick=200;tick<=40000;tick+=200) {
            var progress=engine.advanceTo(new SimInstant(tick),new WorkBudget(1000,1000));
            assertEquals(EngineStatus.Kind.ACTIVE,progress.status().kind(),progress.status().toString());
            var current=engine.canonicalState().state();
            if(!current.shipments().missions().containsKey(offered.transport().mission().id())) {
                assertFalse(current.unitGroups().groups().containsKey(offered.transport().mission().groupId()));
                assertTrue(current.transportFleet().assets().values().stream().noneMatch(asset -> asset.missionId().equals(Optional.of(offered.transport().mission().id()))));
                assertTrue(current.actorExecutions().actors().values().stream().flatMap(actor -> java.util.stream.Stream.concat(actor.current().stream(), actor.suspended().stream()))
                        .noneMatch(execution -> execution.activityOwnerId().equals(offered.transport().mission().groupId())));
                assertFalse(current.inventory().fungibleResources().accounts().containsKey(account));
                assertEquals(64,current.inventory().fungibleResources().totalQuantity(home.id(),"minecraft:coal"));
                assertEquals(64,current.inventory().fungibleResources().totalQuantity(home.id(),"minecraft:raw_iron"));
                assertEquals(coins,current.inventory().economics().require(home.id()).balance());return;
            }
        }
        var end=engine.canonicalState().state();
        fail("mission="+end.shipments().missions()+" groups="+end.unitGroups().groups()+" movements="+end.actorMovements()+" internal manifest must deliver and release its crew/asset through the real queue; "+engine.checkpoint().schedules());
    }
}
