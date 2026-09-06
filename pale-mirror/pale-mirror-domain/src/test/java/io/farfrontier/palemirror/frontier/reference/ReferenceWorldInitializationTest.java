package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceWorldInitializationTest {
    @Test
    void sourceSeedBuildsTheExactSettlementSiteRouteAndInfectionFoundation() {
        ReferenceWorld world = new ReferenceWorld(sourceConfig());

        assertEquals(List.of(
                "1:Ironreach-01:43:10", "2:Ashwatch-02:48:37", "3:Ashhill-03:9:25", "4:Blackwatch-04:5:17",
                "5:Blackfield-05:37:18", "6:Pinereach-06:16:34", "7:Lakestead-07:8:6", "8:Oldgate-08:51:20",
                "9:Greenwatch-09:56:35", "10:Highford-10:18:6", "11:Stonehaven-11:28:26", "12:Lakestead-12:58:24"),
                world.settlements().values().stream().map(item -> item.id() + ":" + item.name() + ":" + item.x() + ":" + item.y()).toList());
        assertEquals(1225.1945643094746d, world.settlements().get(1).population());
        assertEquals(1118.0564714556826d, world.settlements().get(1).cash());
        assertEquals(762.4650714162713d, world.settlements().get(12).population());
        assertEquals(1150.99082284131d, world.settlements().get(12).cash());
        assertEquals(180.0d, world.settlements().get(1).amount(ReferenceResource.SEEDS));

        assertEquals(List.of(
                "1:FOREST:43:5", "2:MINE:43:7", "3:POWER:41:7", "4:MINE:49:31", "5:FARM:53:38", "6:MINE:12:25",
                "7:POWER:6:25", "8:POWER:5:14", "9:FOREST:10:20", "10:MINE:2:17", "11:FARM:9:17", "12:FOREST:43:19",
                "13:FARM:41:16", "14:POWER:33:19", "15:MINE:16:31", "16:FARM:22:31", "17:MINE:8:9", "18:FARM:7:13",
                "19:POWER:15:6", "20:MINE:51:17", "21:FARM:56:19", "22:POWER:48:19", "23:FOREST:48:22", "24:POWER:56:32",
                "25:FARM:62:34", "26:FOREST:60:41", "27:POWER:18:9", "28:FARM:20:10", "29:POWER:27:23", "30:FARM:23:20",
                "31:FOREST:24:29", "32:MINE:25:26", "33:MINE:55:24", "34:POWER:53:23"),
                world.resourceSites().values().stream().map(item -> item.id() + ":" + item.kind() + ":" + item.x() + ":" + item.y()).toList());
        ReferenceResourceSite firstSite = world.resourceSites().get(1);
        assertEquals(1.431117837141642d, firstSite.quality());
        assertEquals(1.3832606795678806d, firstSite.capacity());
        assertEquals(0.9510046820939952d, firstSite.condition());
        assertEquals(471.734140456997d, firstSite.haulCapacity());
        assertEquals(1, firstSite.operatorCompanyId());

        assertEquals(List.of(
                "4:7", "7:3", "3:6", "6:10", "10:11", "11:5", "5:1", "1:2", "2:8", "8:9", "9:12",
                "1:8", "2:9", "2:12", "3:4", "6:11", "7:10", "8:12", "10:4"),
                world.trade().routes().stream().map(item -> item.a() + ":" + item.b()).toList());
        ReferenceRoute firstRoute = world.trade().routes().getFirst();
        assertEquals(11.40175425099138d, firstRoute.distance());
        assertEquals(124.91614064781896d, firstRoute.capacity());
        assertEquals(0.0543805697933266d, firstRoute.risk());
        assertEquals(1.2003707181525691d, firstRoute.quality());

        assertEquals(2, world.infection().organs().size());
        assertEquals(new ReferenceGridPosition(17, 26), new ReferenceGridPosition(world.infection().organs().get(1).x(), world.infection().organs().get(1).y()));
        assertEquals(new ReferenceGridPosition(62, 5), new ReferenceGridPosition(world.infection().organs().get(2).x(), world.infection().organs().get(2).y()));
        assertEquals(273971.84076615033d, world.infection().ecosystem().totalOrganic());
        assertEquals(0.013849431818181818d, world.infection().infectedFraction());

        assertEquals(70, world.microeconomy().companies().size());
        assertEquals(12, world.microeconomy().households().size());
        assertEquals(34, world.microeconomy().licences().size());
        ReferenceCompany firstCompany = world.microeconomy().companies().get(1);
        assertEquals("Ironreach-01 forest-1 Co.", firstCompany.name());
        assertEquals(138.57263223623843d, firstCompany.assets());
        assertEquals(540.3939612035798d, firstCompany.cash());
        assertEquals(82.0d, firstCompany.amount(ReferenceResource.TIMBER));
    }

    @Test
    void worldInitializationIsRepeatableWithoutSharingMutableState() {
        ReferenceWorld left = new ReferenceWorld(sourceConfig());
        ReferenceWorld right = new ReferenceWorld(sourceConfig());

        left.resourceSites().get(1).condition(0.0d);

        assertEquals(0.9510046820939952d, right.resourceSites().get(1).condition());
        assertEquals(left.settlements().keySet(), right.settlements().keySet());
        assertEquals(left.trade().routes().stream().map(ReferenceRoute::key).toList(),
                right.trade().routes().stream().map(ReferenceRoute::key).toList());
    }

    @Test
    void compactWorldRecoversFromPreferredSeedPlacementWithTheSourceFallback() {
        ReferenceWorld world = new ReferenceWorld(new ReferenceWorldConfig(
                14, 14, 1, 42L, 8, false, ReferenceSimulationProfile.SOURCE_V2));

        // Python deliberately starts the best viable outbreak on a compact map
        // instead of silently creating an infection-enabled world with no nests.
        assertEquals(4, world.infection().organs().size());
        assertEquals(List.of(
                new ReferenceGridPosition(12, 12), new ReferenceGridPosition(6, 12),
                new ReferenceGridPosition(12, 2), new ReferenceGridPosition(2, 5)),
                world.infection().organs().values().stream()
                        .map(item -> new ReferenceGridPosition(item.x(), item.y())).toList());
    }

    private static ReferenceWorldConfig sourceConfig() {
        return new ReferenceWorldConfig(64, 44, 12, 42L, 2, false, ReferenceSimulationProfile.SOURCE_V2);
    }
}
