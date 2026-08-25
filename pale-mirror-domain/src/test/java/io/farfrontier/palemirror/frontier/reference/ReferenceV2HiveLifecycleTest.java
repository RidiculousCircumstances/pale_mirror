package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReferenceV2HiveLifecycleTest {
    @Test
    void sourceFeralOrganFormsVisibleChrysalisThenMaturesIntoCore() {
        ReferenceWorld world = worldWithFeralMatureOrgan();
        ReferenceHiveOrgan organ = world.infection().organs().get(1);
        ReferenceV2State v2 = world.v2();

        v2.advanceHiveLifecycle(world);
        v2.observe(world);

        ReferenceNeuralChrysalis forming = v2.chrysalises().get(organ.id());
        assertNotNull(forming);
        assertEquals("3:3", forming.sectorKey());
        assertEquals(0, forming.startedDay());
        assertEquals(18, forming.daysRemaining());
        assertEquals(105.0d, forming.biomassCommitted());
        assertEquals("forming", forming.status());
        assertEquals(ReferenceHiveLifecycle.RECONSTITUTION, v2.hiveLifecycle().get("component:0"));
        assertTrue(v2.hivePerception().beliefs().get("3:3").chrysalis());
        assertEquals("D0: feral digestive_pool 1 began visible neural chrysalis", world.events().getLast());

        for (int day = 1; day <= 17; day++) {
            world.day(day);
            v2.refreshTerritory(world);
            v2.advanceHiveLifecycle(world);
        }
        assertEquals(1, v2.chrysalises().get(organ.id()).daysRemaining());
        assertEquals(125.375d, organ.biomass());
        assertEquals(ReferenceOrganKind.DIGESTIVE_POOL, organ.kind());

        world.day(18);
        v2.refreshTerritory(world);
        v2.advanceHiveLifecycle(world);
        assertNull(v2.chrysalises().get(organ.id()));
        assertEquals(ReferenceOrganKind.CORE, organ.kind());
        assertEquals("core", organ.role());
        assertEquals(122.75d, organ.biomass());
        assertEquals(100.0d, organ.vitality());
        assertFalse(organ.feral());
        assertEquals("D18: neural chrysalis matured into core 1", world.events().getLast());

        world.day(19);
        v2.refreshTerritory(world);
        v2.advanceHiveLifecycle(world);
        assertEquals(ReferenceHiveLifecycle.ROOTING, v2.hiveLifecycle().get("component:0"));
    }

    @Test
    void sourceChrysalisWithersWhenItsCommittedOrganLosesBiomass() {
        ReferenceWorld world = worldWithFeralMatureOrgan();
        ReferenceHiveOrgan organ = world.infection().organs().get(1);
        ReferenceV2State v2 = world.v2();
        v2.advanceHiveLifecycle(world);
        assertNotNull(v2.chrysalises().get(organ.id()));

        world.day(1);
        organ.biomass(0.0d);
        v2.refreshTerritory(world);
        v2.advanceHiveLifecycle(world);

        assertNull(v2.chrysalises().get(organ.id()));
        assertEquals(ReferenceOrganKind.DIGESTIVE_POOL, organ.kind());
        assertTrue(organ.feral());
        assertEquals("D1: neural chrysalis at organ 1 withered", world.events().getLast());
    }

    private static ReferenceWorld worldWithFeralMatureOrgan() {
        ReferenceWorld world = new ReferenceWorld(new ReferenceWorldConfig(
                64, 44, 12, 41L, 0, true, ReferenceSimulationProfile.SOURCE_V2));
        ReferenceHiveOrgan organ = world.infection().createOrgan(12, 12, 170.0d, null, null,
                ReferenceOrganKind.DIGESTIVE_POOL);
        organ.vitality(100.0d);
        for (int y = 10; y < 15; y++) {
            for (int x = 10; x < 15; x++) world.infection().infectionAt(x, y, 0.85d);
        }
        world.v2().refreshTerritory(world);
        return world;
    }
}
