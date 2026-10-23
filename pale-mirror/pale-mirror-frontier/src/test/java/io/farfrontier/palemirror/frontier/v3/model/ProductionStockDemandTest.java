package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Policy tests use canonical fixtures; they make no native movement/container evidence claim. */
class ProductionStockDemandTest {
    @Test void targetBalancesCommittedSupplyWithoutReplacingCapacityOrAcceptingInvalidValues() {
        assertFalse(new StockTargetDemand(100, 60, 40, 0).requested());
        assertEquals(20, new StockTargetDemand(100, 60, 40, 20).missing());
        assertEquals(100, new StockTargetDemand(100, 0, 0, 20).missing());
        assertThrows(IllegalArgumentException.class, () -> new StockTargetDemand(-1, 0, 0, 0));
        assertThrows(ArithmeticException.class, () -> new StockTargetDemand(1, Long.MAX_VALUE, 1, 0).covered());
        assertThrows(IllegalArgumentException.class, () -> StockSupplyComposition.register(List.of()));
        var bakery = new StationWorkStockSupply();
        assertThrows(IllegalArgumentException.class, () -> StockSupplyComposition.register(List.of(bakery, bakery)));
    }

    @Test void enoughBreadRefusesNewBatchesAndPlayerWithdrawalReopensTheSamePolicy() {
        var state = BakeryPartialBatchTest.fixture(64);
        var owner = state.bootstrap().settlements().getFirst().id();
        var demand = ProductionStockDemand.read(state, owner, SettlementFoodPolicy.BREAD, 0L);
        state = supply(state, owner, SettlementFoodPolicy.BREAD, (int) (demand.target() + demand.outgoing()), "bread-reserve");
        assertFalse(ProductionStockDemand.read(state, owner, SettlementFoodPolicy.BREAD, 0L).requested());
        assertTrue(BakeryBatchSelection.admissible(state, owner, 0L).isEmpty());
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(ProductionStockDemand.read(state, owner, SettlementFoodPolicy.BREAD, 0L),
                ProductionStockDemand.read(recovered, owner, SettlementFoodPolicy.BREAD, 0L));
        var ledger = state.inventory().fungibleResources();
        var account = ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(owner));
        var lot = new SubjectId("lot:stock-test-bread-reserve");
        var count = ledger.accounts().get(account).lotQuantities().get(lot);
        // This models the confirmed resource transfer, not a guessed physical/player observation.
        ledger = ledger.transferToNewAccount(account, new CustodyAccount(new SubjectId("custody:stock-test-player"),
                new ResourceCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000099")),
                Map.of(lot, count), Map.of()));
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        assertTrue(ProductionStockDemand.read(state, owner, SettlementFoodPolicy.BREAD, 0L).requested());
        assertTrue(BakeryBatchSelection.admissible(state, owner, 0L).isPresent());
    }

    @Test void activeBakeryOutputCountsBeforeRecipeAndOnlyOnceAfterRecipeThenFinishesDespiteFullReserve() {
        var state = BakeryPartialBatchTest.fixture(64);
        var task = state.strategicPlans().tasks().values().iterator().next();
        var started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        assertEquals(64, ProductionStockDemand.read(state, task.ownerId(), SettlementFoodPolicy.BREAD, 0L).incoming());
        var needed = ProductionStockDemand.read(state, task.ownerId(), SettlementFoodPolicy.BREAD, 0L);
        state = supply(state, task.ownerId(), SettlementFoodPolicy.BREAD,
                (int) (needed.target() + needed.outgoing()), "late-bread");
        assertTrue(BakeryBatchSelection.admissible(state, task.ownerId(), 0L).isEmpty());
        boolean recipe = false;
        var job = started.job();
        for (long due = 300, steps = 0; state.productionJobs().containsKey(job.id()) && steps < 700; due += 20, steps++) {
            due = BakeryTestClock.due(state, job, due);
            var step = ProductionProcess.planCompletion(state, ProductionProcess.complete(state.productionJobs().get(job.id()), due))
                    .stream().map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                    .map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), step);
            if (step.action() == BakeryColdStep.Action.RECIPE) {
                recipe = true;
                assertEquals(0, ProductionStockDemand.read(state, task.ownerId(), SettlementFoodPolicy.BREAD, 0L).incoming());
                state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
            }
        }
        assertTrue(recipe); assertFalse(state.productionJobs().containsKey(job.id()));
        assertEquals(64, state.inventory().fungibleResources().lots().get(job.outputItemId()).quantity());
    }

    @Test void miningStockAtSiteInHandAndAtDepotCountsOnceWhileOtherOwnersStockDoesNot() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:stock-quarry"), 41,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r4")));
        var site = state.extractionSites().deposits().values().stream().sorted(Comparator.comparing(value -> value.site().id()))
                .findFirst().orElseThrow().site();
        var owner = site.settlementId();
        var demand = ProductionStockDemand.read(state, owner, "minecraft:cobblestone", 0L);
        assertTrue(demand.requested());
        assertTrue(demand.outgoing() > 0, "known funded import demand must permit a bounded export surplus");
        int count = (int) (demand.target() + demand.outgoing());
        var ledger = state.inventory().fungibleResources();
        var lot = new SubjectId("lot:stock-test-quarry"); var account = new SubjectId("custody:stock-test-quarry");
        ledger = ledger.issue(new ResourceLot(lot, owner, "minecraft:cobblestone", count, "test:stock", List.of()),
                new CustodyAccount(account, new ResourceCustody.Container(QuarryStorageFixture.container(state, site)), Map.of(lot, count), Map.of()));
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        var filled = ProductionStockDemand.read(state, owner, "minecraft:cobblestone", 0L);
        assertFalse(filled.requested()); assertEquals(count, filled.held());
        var actor = state.bootstrap().settlements().stream().filter(value -> value.id().equals(owner)).findFirst().orElseThrow().residents().getFirst().id();
        var take = new ActorContainerItemOrder(site.id(), actor, ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(account, new ResourceCustody.Container(QuarryStorageFixture.container(state, site)),
                        new SubjectId("custody:stock-test-hauler"), new ResourceCustody.Actor(actor), Optional.empty(),
                        "minecraft:cobblestone", Map.of(lot, 64)),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(QuarryStorageFixture.container(state, site)), site.layout().storagePort(),
                ActorContainerItemOrder.Hand.OFF, 1, 1);
        ledger = ledger.transferActorOrderCold(take);
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        assertEquals(filled, ProductionStockDemand.read(state, owner, "minecraft:cobblestone", 0L));
        var depot = FrontierWorldState.depotId(owner);
        var place = new ActorContainerItemOrder(site.id(), actor, ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Fungible(new SubjectId("custody:stock-test-hauler"), new ResourceCustody.Actor(actor),
                        ReferenceContainerCustody.scopeId(depot), new ResourceCustody.Container(depot), Optional.empty(),
                        "minecraft:cobblestone", Map.of(lot, 64)),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(depot), GoodsParticipantDeclarations.endpoint(
                        FrontierWorldStateSupport.settlement(state.bootstrap(), owner)).station(), ActorContainerItemOrder.Hand.OFF, 1, 1);
        ledger = ledger.transferActorOrderCold(place);
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        assertEquals(filled, ProductionStockDemand.read(state, owner, "minecraft:cobblestone", 0L));
        var foreign = state.bootstrap().settlements().stream().filter(value -> !value.id().equals(owner)).findFirst().orElseThrow().id();
        state = supply(state, foreign, "minecraft:cobblestone", 64, "foreign-stone");
        assertEquals(count, ProductionStockDemand.read(state, owner, "minecraft:cobblestone", 0L).held());
        var current = state;
        assertThrows(IllegalArgumentException.class, () -> ProductionStockDemand.read(current, owner, "minecraft:diamond", 0L));
    }

    static FrontierWorldState supply(FrontierWorldState state, SubjectId owner, String kind, int quantity, String suffix) {
        var lot = new SubjectId("lot:stock-test-" + suffix); var temporary = new SubjectId("custody:stock-test-" + suffix);
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot, owner, kind, quantity, "test:stock", List.of()),
                new CustodyAccount(temporary, new ResourceCustody.WorldCarrier(UUID.nameUUIDFromBytes(suffix.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                        Map.of(lot, quantity), Map.of()));
        ledger = ledger.transfer(temporary, ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(owner)), Map.of(lot, quantity), Map.of());
        return state.withInventory(state.inventory().withFungibleResources(ledger));
    }
}
