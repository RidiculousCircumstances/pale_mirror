package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierBootstrapperTest {
    @Test
    void createsTheExactFiniteProfileWithIndependentCanonicalSubjects() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:bootstrap"), 918_273L);

        assertEquals(1024, bootstrap.bounds().width());
        assertEquals(1024, bootstrap.bounds().depth());
        assertEquals(12, bootstrap.settlements().size());
        assertEquals(2, bootstrap.hive().seedNests().size());
        assertEquals(6, bootstrap.hive().organs().size());
        assertEquals(48, bootstrap.bioformCount());
        assertTrue(bootstrap.settlements().stream().allMatch(settlement -> settlement.residents().size() >= 20 && settlement.residents().size() <= 40));
        assertEquals(bootstrap.residentCount(), bootstrap.settlements().stream().flatMap(settlement -> settlement.residents().stream()).count());

        Set<Object> ids = new HashSet<>();
        bootstrap.settlements().forEach(settlement -> {
            assertTrue(bootstrap.bounds().contains(settlement.anchor()));
            settlement.residents().forEach(resident -> { assertEquals(settlement.id(), resident.settlementId()); assertTrue(ids.add(resident.id())); });
            settlement.structures().forEach(structure -> { assertEquals(settlement.id(), structure.settlementId()); assertTrue(ids.add(structure.id())); });
        });
        bootstrap.hive().seedNests().forEach(nest -> assertEquals(bootstrap.hive().id(), nest.hiveId()));
        bootstrap.hive().organs().forEach(organ -> {
            assertEquals(bootstrap.hive().id(), organ.hiveId());
            assertTrue(ids.add(organ.id()));
        });
        bootstrap.hive().bioforms().forEach(bioform -> assertEquals(bootstrap.hive().id(), bioform.hiveId()));
    }

    @Test
    void bootstrapIsSeedDeterministicAndLocaleIndependent() {
        WorldId world = new WorldId("frontier:bootstrap");
        FrontierBootstrap first = FrontierBootstrapper.create(world, 7L);
        assertEquals("e4259244e0e072ddd29458381ac2483479d11afc84e96c94190da8a93dfc0120", first.canonicalSha256());
        assertEquals("7adf9776c786e2e732a7a838684dc705fb5e947518bb44b6de590e3d1a58c4a9", FrontierBootstrapper.create(world, 8L).canonicalSha256());
        assertEquals(first.canonicalSha256(), FrontierBootstrapper.create(world, 7L).canonicalSha256());
        assertNotEquals(first.canonicalSha256(), FrontierBootstrapper.create(world, 8L).canonicalSha256());

        Locale prior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(first.canonicalSha256(), FrontierBootstrapper.create(world, 7L).canonicalSha256());
        } finally {
            Locale.setDefault(prior);
        }
    }
}
