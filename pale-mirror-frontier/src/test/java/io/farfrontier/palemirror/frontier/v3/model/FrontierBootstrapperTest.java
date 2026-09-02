package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
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
        assertEquals("1eafc566435e3cd72a5f69bbc88d92ff5c6bf458dea98ff344d535c790145115", first.canonicalSha256());
        assertEquals("56d6398d1143604d7617a6858621b4752894e98c86b93a7780ff6e4b77e43392", FrontierBootstrapper.create(world, 8L).canonicalSha256());
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

    @Test
    void batchPlacementUsesTheSameImmutableGeometryAsAnIndividualFutureBirthSlot() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:slots"), 7L);
        Settlement settlement = bootstrap.settlements().getFirst();
        int count = settlement.residents().size() + 1;

        List<BlockPosition> batch = FrontierSettlementActorSlots.slots(bootstrap.bounds(), bootstrap.terrain(), settlement.anchor(), settlement.structures(), count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            assertEquals(batch.get(ordinal), FrontierSettlementActorSlots.slot(bootstrap.bounds(), bootstrap.terrain(), settlement, ordinal));
        }
    }
}
