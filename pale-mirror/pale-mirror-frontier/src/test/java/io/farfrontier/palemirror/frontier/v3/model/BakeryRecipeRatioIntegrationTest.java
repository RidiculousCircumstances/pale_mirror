package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Connected admission/claim/haul/machine/unload proof, not just the arithmetic helper. */
class BakeryRecipeRatioIntegrationTest {
    @Test void threeIngredientsMakeOneProductThroughRealColdJobAndWalSteps() {
        var base = FrontierRulesets.production();
        var recipe = new ProductionRecipe(BakeryRecipes.BREAD, ProductionStationSpec.Capability.BAKING,
                new ProductionRecipe.Ingredient(Set.of("minecraft:wheat"), 3),
                new ProductionRecipe.Product("minecraft:bread", 1), 0, ProductionRecipe.ProcessingMode.ATTENDED);
        var rules = new FrontierRuleset("frontier-v3-ratio-chain", 25, base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(),
                base.resourceHarvestColdTravelTicksPerEdge(), base.workCatalog(), base.goodsTrade(),
                base.initialSettlementStocks(), base.labour(), base.expedition(), base.extraction(), base.execution(),
                new ProductionCatalog(List.of(recipe), List.of(), List.of(ProductionEquipment.bench())));
        var state = BakeryPartialBatchTest.fixture(64, rules);
        var task = state.strategicPlans().tasks().values().iterator().next();
        var start = ProductionProcess.planStart(state, ProductionProcess.start(task, 200)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance).map(ProductionStarted.class::cast)
                .findFirst().orElseThrow();
        var job = start.job();
        assertEquals(63, job.inputCount()); assertEquals(21, job.outputCount());
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), start);
        var codecs = FrontierWorldPayloadCodecs.create();
        for (long due = 300, turns = 0; state.productionJobs().containsKey(job.id()) && turns < 700; due += 20, turns++) {
            due = BakeryTestClock.due(state, job, due);
            var current = state.productionJobs().get(job.id());
            var step = ProductionProcess.planCompletion(state, ProductionProcess.complete(current, due)).stream()
                    .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance).map(BakeryColdStep.class::cast)
                    .findFirst().orElseThrow();
            var replay = (BakeryColdStep) codecs.decode(step.type(), codecs.encode(step));
            assertEquals(step, replay);
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), replay);
        }
        assertFalse(state.productionJobs().containsKey(job.id()));
        assertEquals(1, state.inventory().fungibleResources().totalQuantity(task.ownerId(), "minecraft:wheat"));
        assertEquals(21, state.inventory().fungibleResources().totalQuantity(task.ownerId(), "minecraft:bread"));
        assertTrue(state.inventory().fungibleResources().claims().isEmpty());
        assertTrue(state.productionStations().machines().values().stream().allMatch(machine -> machine.operation().isEmpty()));
    }
}
