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
        assertEquals("bf9a79b56ecb1ef9b2d0be493ea37f7b10e1cd29cf086308feb5dd0323bd29d8", first.canonicalSha256());
        assertEquals("a79765351eee0fa0cfb632c911b6d95d0a3fc13bf1ee0f3e885d4bc836f39ddf", FrontierBootstrapper.create(world, 8L).canonicalSha256());
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
