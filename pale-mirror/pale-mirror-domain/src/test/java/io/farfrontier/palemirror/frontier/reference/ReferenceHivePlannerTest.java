package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceHivePlannerTest {
    private final ReferenceHivePlanner planner = new ReferenceHivePlanner();

    @Test
    void purePlannerMatchesSourceMorphHarvestAssaultSporeAndFrontierOrders() {
        ReferenceHiveOrder core = only(planner.plan(view(6, 16, 12,
                List.of(nest(1, 5, 5, ReferenceOrganKind.CORE, 200.0d, false)), List.of(), List.of(), List.of(), 9, 5, 0.50d, 100.0d, 0.5d)));
        assertEquals(ReferenceHiveOrderKind.MORPH_ORGAN, core.kind());
        assertEquals(ReferenceOrganKind.SYNAPSE, core.organKind());
        assertEquals(9, core.targetX());
        assertEquals(5, core.targetY());
        assertEquals("complete local biological complex", core.reason());

        ReferenceHiveOrder harvest = only(planner.plan(view(6, 16, 12,
                List.of(nest(2, 5, 5, ReferenceOrganKind.BROOD_SAC, 70.0d, false)), List.of(), List.of(), List.of(), 10, 5, 0.0d, 100.0d, 0.5d)));
        assertEquals(ReferenceBioformKind.HARVESTER, harvest.bioformKind());
        assertEquals(Map.of(), harvest.composition());
        assertEquals("forage organic frontier", harvest.reason());

        ReferenceHiveOrder armoured = only(planner.plan(view(6, 16, 12,
                List.of(nest(3, 5, 5, ReferenceOrganKind.BROOD_SAC, 120.0d, false)),
                List.of(new ReferenceHiveWorldView.Settlement(9, 8, 5, true, 100.0d, 100.0d, 150.0d)), List.of(), List.of(), -1, -1, 0.0d, 0.0d, 0.5d)));
        assertEquals(ReferenceBioformKind.RAIDER, armoured.bioformKind());
        assertEquals(9, armoured.targetId());
        assertEquals(Map.of(ReferenceBioformKind.RAIDER, 1.0d, ReferenceBioformKind.BREAKER, 1.0d), armoured.composition());
        assertEquals("screen and breach an armoured human settlement", armoured.reason());

        ReferenceHiveOrder spore = only(planner.plan(view(6, 50, 12,
                List.of(nest(4, 5, 5, ReferenceOrganKind.SPORULATOR, 100.0d, false)),
                List.of(new ReferenceHiveWorldView.Settlement(10, 33, 5, true, 100.0d, 100.0d, 10.0d)), List.of(), List.of(), 30, 5, 0.0d, 100.0d, 0.8d)));
        assertEquals(ReferenceBioformKind.SPORE_CARRIER, spore.bioformKind());
        assertEquals(30, spore.targetX());
        assertEquals(5, spore.targetY());
        assertEquals("seed distant organic foothold", spore.reason());

        ReferenceHiveOrder frontier = only(planner.plan(view(6, 20, 12,
                List.of(nest(5, 5, 5, ReferenceOrganKind.BROOD_SAC, 100.0d, false)), List.of(), List.of(),
                List.of(new ReferenceHiveWorldView.Sector(2, 1, "human", 0.20d, 10.0d, 3.0d, true)), -1, -1, 0.0d, 0.0d, 0.5d)));
        assertEquals(ReferenceBioformKind.RAIDER, frontier.bioformKind());
        assertEquals(10, frontier.targetX());
        assertEquals(6, frontier.targetY());
        assertEquals(Map.of(ReferenceBioformKind.RAIDER, 5.0d, ReferenceBioformKind.BREAKER, 2.0d), frontier.composition());
        assertEquals("break a biologically observed human cordon and sever its supply", frontier.reason());
    }

    @Test
    void plannerRespectsItsSourceScheduleAndFeralStrategicBlindness() {
        assertTrue(planner.plan(view(5, 16, 12, List.of(nest(7, 5, 5, ReferenceOrganKind.CORE, 200.0d, false)),
                List.of(), List.of(), List.of(), 9, 5, 0.50d, 100.0d, 0.5d)).isEmpty());
        assertTrue(planner.plan(view(6, 16, 12, List.of(nest(6, 5, 5, ReferenceOrganKind.CORE, 200.0d, true)),
                List.of(), List.of(), List.of(), 9, 5, 0.50d, 100.0d, 0.5d)).isEmpty());
    }

    @Test
    void plannerCarriesEveryRemainingOrganRoleAndTheFeralBroodReflex() {
        ReferenceHiveOrder coreDigestive = only(planner.plan(view(6, 20, 12,
                List.of(nest(11, 5, 5, ReferenceOrganKind.CORE, 200.0d, false), nest(12, 9, 5, ReferenceOrganKind.SYNAPSE, 55.0d, false, 4)),
                List.of(), List.of(), List.of(), 13, 5, 0.50d, 100.0d, 0.5d)));
        assertEquals(ReferenceOrganKind.DIGESTIVE_POOL, coreDigestive.organKind());
        assertEquals("complete local biological complex", coreDigestive.reason());

        ReferenceHiveOrder coreSporulator = only(planner.plan(view(6, 26, 12,
                List.of(nest(13, 5, 5, ReferenceOrganKind.CORE, 200.0d, false), nest(14, 9, 5, ReferenceOrganKind.SYNAPSE, 55.0d, false, 4),
                        nest(15, 13, 5, ReferenceOrganKind.DIGESTIVE_POOL, 55.0d, false, 4)),
                List.of(), List.of(), List.of(), 17, 5, 0.50d, 100.0d, 0.5d)));
        assertEquals(ReferenceOrganKind.SPORULATOR, coreSporulator.organKind());

        ReferenceHiveOrder digestive = only(planner.plan(view(6, 16, 12,
                List.of(nest(16, 5, 5, ReferenceOrganKind.DIGESTIVE_POOL, 100.0d, false)), List.of(), List.of(), List.of(),
                9, 5, 0.50d, 100.0d, 0.5d)));
        assertEquals(ReferenceOrganKind.BROOD_SAC, digestive.organKind());
        assertEquals("feed a brood organ", digestive.reason());

        ReferenceHiveOrder synapse = only(planner.plan(view(6, 16, 12,
                List.of(nest(17, 5, 5, ReferenceOrganKind.SYNAPSE, 100.0d, false)), List.of(), List.of(), List.of(),
                9, 5, 0.50d, 100.0d, 0.5d)));
        assertEquals(ReferenceOrganKind.DIGESTIVE_POOL, synapse.organKind());
        assertEquals("extend nutrient routing", synapse.reason());

        ReferenceHiveOrder soft = only(planner.plan(view(6, 16, 12,
                List.of(nest(18, 5, 5, ReferenceOrganKind.BROOD_SAC, 120.0d, false)),
                List.of(new ReferenceHiveWorldView.Settlement(19, 8, 5, true, 100.0d, 100.0d, 10.0d)), List.of(), List.of(),
                -1, -1, 0.0d, 0.0d, 0.5d)));
        assertEquals(Map.of(ReferenceBioformKind.RAIDER, 1.0d), soft.composition());
        assertEquals("screen and consume vulnerable human settlement", soft.reason());

        ReferenceHiveOrder feralBrood = only(planner.plan(view(6, 16, 12,
                List.of(nest(20, 5, 5, ReferenceOrganKind.BROOD_SAC, 120.0d, true)),
                List.of(new ReferenceHiveWorldView.Settlement(21, 8, 5, true, 100.0d, 100.0d, 10.0d)), List.of(), List.of(),
                -1, -1, 0.0d, 0.0d, 0.5d)));
        assertEquals(21, feralBrood.targetId());
        assertEquals(ReferenceBioformKind.RAIDER, feralBrood.bioformKind());
    }

    @Test
    void executorIsTheOnlyMutationBoundaryForAPlannerOrder() {
        ReferenceInfectionModel model = new ReferenceInfectionModel(7, 5, 17L, 1.0d, false);
        model.seedInfection(2, 2, 0.85d, 2, true);
        model.organs().get(1).biomass(200.0d);
        ReferenceHiveOrder order = ReferenceHiveOrder.morph(1, 4, 2, ReferenceOrganKind.SYNAPSE, "complete local biological complex");

        assertTrue(new ReferenceHiveOrderExecutor().execute(model, order, 9));
        assertEquals(145.0d, model.organs().get(1).biomass());
        assertEquals(9, model.organs().get(1).lastProjectDay());
        ReferenceNestProject project = model.nestProjects().getFirst();
        assertEquals(1, project.sourceOrganId());
        assertEquals(4, project.x());
        assertEquals(2, project.y());
        assertEquals(4, project.daysRemaining());
        assertEquals(39.6d, project.committedBiomass());
        assertFalse(new ReferenceHiveOrderExecutor().execute(model,
                ReferenceHiveOrder.morph(99, 4, 2, ReferenceOrganKind.SYNAPSE, "missing source"), 9));
    }

    private static ReferenceHiveOrder only(List<ReferenceHiveOrder> orders) {
        assertEquals(1, orders.size());
        return orders.getFirst();
    }

    private static ReferenceHiveWorldView.Nest nest(int id, int x, int y, ReferenceOrganKind kind, double biomass, boolean feral) {
        return nest(id, x, y, kind, biomass, feral, -10_000);
    }

    private static ReferenceHiveWorldView.Nest nest(int id, int x, int y, ReferenceOrganKind kind, double biomass, boolean feral, int lastProjectDay) {
        return new ReferenceHiveWorldView.Nest(id, x, y, kind, biomass, 100.0d, feral, lastProjectDay);
    }

    private static ReferenceHiveWorldView view(int day, int width, int height, List<ReferenceHiveWorldView.Nest> nests,
                                               List<ReferenceHiveWorldView.Settlement> settlements, List<ReferenceHiveWorldView.Swarm> swarms,
                                               List<ReferenceHiveWorldView.Sector> sectors, int targetX, int targetY,
                                               double infection, double organic, double moisture) {
        List<ReferenceHiveWorldView.Cell> cells = new ArrayList<>(width * height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            boolean target = x == targetX && y == targetY;
            cells.add(new ReferenceHiveWorldView.Cell(x, y, target ? infection : 0.0d, target ? organic : 0.0d, target ? moisture : 0.5d, 0.0d));
        }
        return new ReferenceHiveWorldView(day, width, height, settlements, nests, swarms, cells, sectors, 7);
    }
}
