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
    @Test void misplacedFoodUsesOrdinaryShipmentToTheKitchenWithoutChangingOwnershipOrDuplicatingRequests() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:pantry-supply"), 139));
        var home = state.bootstrap().settlements().get(1);
        var pantry = CommunalDiningPlan.initialFoodStorage(state.bootstrap(), home.id());
        var depot = FrontierWorldState.depotId(home.id());
        var source = FungibleResourceCustodySupport.accountAtContainer(state, pantry).orElseThrow();
        var ledger = state.inventory().fungibleResources().transferToNewAccount(source.id(),
                new CustodyAccount(ReferenceContainerCustody.scopeId(depot),
                        new ResourceCustody.Container(depot, ContainerSectionId.STOCK), source.lotQuantities(), Map.of()));
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        var requests = DiningStorageSupply.requests(state, home.id());
        assertEquals(1, requests.size());
        var request = requests.getFirst();
        assertEquals(pantry, request.receiver().containerId());
        assertEquals(64, request.quantity());
        assertFalse(state.inventory().containers().get(depot).storage().orElseThrow().filter().accepts(request.kind()),
                "a destination exclusion cannot prevent pickup of already misplaced food");
        var worker = SettlementWorkforce.candidates(state, home.id(), ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS).getFirst();
        var dispatched = request.prepare(state, worker, 10).orElseThrow();
        state = InternalShipmentStateSupport.dispatch(state, home.id(), dispatched);
        assertTrue(DiningStorageSupply.requests(state, home.id()).isEmpty(), "the admitted claim prevents a second promise");
        var shipment = dispatched.shipment();
        state = state.withActorBody(worker.id(), shipment.sender().station().standingBody());
        state = ShipmentStateSupport.transferCold(state, shipment.id(), shipment.id(), 1, Shipment.Status.AWAITING_LOAD);
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        state = state.withActorBody(worker.id(), shipment.receiver().station().standingBody());
        state = ShipmentStateSupport.transferCold(state, shipment.id(), shipment.id(), 2, Shipment.Status.CARRYING);
        var delivered = state.shipments().shipments().get(shipment.id());
        state = InternalShipmentStateSupport.receive(state, delivered.id(),
                new InternalShipmentReceived(delivered.id(), delivered.revision(), delivered.reception().orElseThrow().id()));
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(home.id(), request.kind()));
        assertEquals(64, state.inventory().fungibleResources().unclaimedQuantity(
                FungibleResourceCustodySupport.accountAtContainer(state, pantry).orElseThrow().id(), home.id(), request.kind()));
        assertTrue(DiningStorageSupply.requests(state, home.id()).isEmpty());
    }

    @Test void fragmentedWarehouseStockUsesTheSameClaimedRecoverableShipmentWithoutReversingIt() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:warehouse-consolidation"), 41,
                FrontierRulesets.installed("frontier-v3-simulation-baseline-r31")));
        var home = state.bootstrap().settlements().getFirst();
        state = stock(state, home.id(), "large", 120, 10, ResourceStorageRole.WAREHOUSE);
        state = stock(state, home.id(), "small", 124, 1, ResourceStorageRole.WAREHOUSE);
        state = stock(state, home.id(), "buffer", 128, 20, ResourceStorageRole.LOCAL_BUFFER);
        var requests = StorageConsolidationSupply.requests(state, home.id());
        assertEquals(1, requests.size());
        var request = requests.getFirst();
        assertEquals(new SubjectId("container:small"), request.source().container().orElseThrow());
        assertEquals(new SubjectId("container:large"), request.receiver().containerId());
        assertEquals(1, request.quantity());
        var worker = SettlementWorkforce.candidates(state, home.id(), ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS).getFirst();
        var dispatched = request.prepare(state, worker, 10).orElseThrow();
        state = InternalShipmentStateSupport.dispatch(state, home.id(), dispatched);
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertTrue(StorageConsolidationSupply.requests(state, home.id()).isEmpty());
        var shipment = dispatched.shipment();
        state = state.withActorBody(worker.id(), shipment.sender().station().standingBody());
        state = ShipmentStateSupport.transferCold(state, shipment.id(), shipment.id(), 1, Shipment.Status.AWAITING_LOAD);
        assertTrue(StorageConsolidationSupply.requests(state, home.id()).isEmpty(), "in-transit stock cannot cause a reverse consolidation");
        state = state.withActorBody(worker.id(), shipment.receiver().station().standingBody());
        state = ShipmentStateSupport.transferCold(state, shipment.id(), shipment.id(), 2, Shipment.Status.CARRYING);
        var delivered = state.shipments().shipments().get(shipment.id());
        state = InternalShipmentStateSupport.receive(state, delivered.id(),
                new InternalShipmentReceived(delivered.id(), delivered.revision(), delivered.reception().orElseThrow().id()));
        assertEquals(31, state.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:coal"));
        assertEquals(11, state.inventory().fungibleResources().unclaimedQuantity(
                FungibleResourceCustodySupport.accountAtContainer(state, request.receiver().containerId()).orElseThrow().id(), home.id(), "minecraft:coal"));
        assertTrue(StorageConsolidationSupply.requests(state, home.id()).isEmpty());
    }
    @Test void partialRemoteBufferDoesNotStartAssetApproachSearches() throws Exception {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:partial-haul"), 41,
                FrontierRulesets.installed("frontier-v3-simulation-baseline-r31")));
        var home = state.bootstrap().settlements().getFirst();
        state = stock(state, home.id(), "partial", 120, 2, ResourceStorageRole.LOCAL_BUFFER);
        var worker = SettlementWorkforce.candidates(state, home.id(), ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS).getFirst();
        var queries = new int[1];
        var planner = new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanner() {
            public io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult query(
                    io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry geometry,
                    SurfaceAnchor start, SurfaceAnchor target) {
                queries[0]++; throw new AssertionError("partial manifest must be filtered before navigation");
            }
        };
        try (var ignored = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind(planner)) {
            assertTrue(InternalTransportPlanning.offer(state, worker, 10).isEmpty());
        }
        assertEquals(0, queries[0]);
    }
    private static FrontierWorldState stock(FrontierWorldState state, SubjectId home, String name, int offset, int count,
            ResourceStorageRole role) {
        var id = new SubjectId("container:" + name);
        var anchor = state.bootstrap().settlements().stream().filter(t -> t.id().equals(home)).findFirst().orElseThrow().anchor();
        var position = anchor.offset(offset, 1, 80); var station = new SurfaceAnchor(position.offset(1, -1, 0));
        var endpoint = new ShipmentEndpoint.Storage(home, id, station, Optional.empty(), station);
        state = new ResourceStorageDeclared(ContainerRecord.storage(id, home, 27, StorageBlockKind.CHEST,
                new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0, role)), position).apply(state, home);
        var ledger = state.inventory().fungibleResources(); var lots = new HashMap<>(ledger.lots()); var accounts = new HashMap<>(ledger.accounts());
        var lot = new SubjectId("lot:" + name); var account = new SubjectId("custody:" + name);
        lots.put(lot, new ResourceLot(lot, home, "minecraft:coal", count, "test:stock", List.of()));
        accounts.put(account, new CustodyAccount(account, endpoint.custody(), Map.of(lot, count), Map.of()));
        return state.withInventory(state.inventory().withFungibleResources(new FungibleResourceLedger(lots, ledger.claims(), accounts, ledger.bindings())));
    }

    @Test void mixedOwnedCargoUsesOneFiniteAssetAndOneGroupWithoutSaleAndFinishesTheRealQueue() {
        var bootstrap=FrontierBootstrapper.create(new WorldId("frontier:internal-manifest"),41,
                FrontierRulesets.installed("frontier-v3-simulation-baseline-r31"));
        var state=FrontierWorldState.initial(bootstrap);var home=bootstrap.settlements().getFirst();
        var id=new SubjectId("container:test-remote-buffer");
        var position=home.anchor().offset(120,0,80);
        var station=new SurfaceAnchor(position.offset(1,-1,0));
        var source=new ShipmentEndpoint.Storage(home.id(),id,station,Optional.empty(),station);
        var container=ContainerRecord.storage(id, home.id(), 27, io.farfrontier.palemirror.frontier.v3.model.StorageBlockKind.CHEST, new ResourceStoragePolicy(source,ResourceStorageFilter.all(),0,ResourceStorageRole.LOCAL_BUFFER));
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
