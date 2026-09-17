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
        assertEquals(12, bootstrap.hive().organs().size());
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
        assertEquals("46d6505129f17b0f1af9853c1f5c4f7305e83e63d5a567adcf11c1194cb9acd8", first.canonicalSha256());
        assertEquals("7b7a08c95fd5b300aaddca2b8510f533d969cac94d5fc52509a8960e4f313d75", FrontierBootstrapper.create(world, 8L).canonicalSha256());
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

        int housingBeds = bootstrap.ruleset().facilityCapacity().intactHousingBeds();
        List<BlockPosition> batch = FrontierSettlementActorSlots.residentSlots(bootstrap.bounds(), bootstrap.terrain(), settlement, housingBeds, count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            assertEquals(batch.get(ordinal), FrontierSettlementActorSlots.residentSlot(bootstrap.bounds(), bootstrap.terrain(), settlement, housingBeds, ordinal));
        }
    }

    @Test
    void initialResidentHomesAreDistributedAcrossTheCanonicalApron() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:distributed-homes"), 7L);
        Settlement settlement = bootstrap.settlements().getFirst();
        int housingBeds = bootstrap.ruleset().facilityCapacity().intactHousingBeds();
        List<BlockPosition> homes = FrontierSettlementActorSlots.residentSlots(bootstrap.bounds(), bootstrap.terrain(), settlement,
                housingBeds, settlement.residents().size());

        assertTrue(homes.stream().anyMatch(home -> home.x() < settlement.anchor().x()),
                "the retained initial residents must not all originate on one east-west apron edge");
        assertTrue(homes.stream().anyMatch(home -> home.x() > settlement.anchor().x()),
                "the retained initial residents must occupy both sides of their canonical apron");
        assertTrue(homes.stream().anyMatch(home -> home.z() < settlement.anchor().z()),
                "the retained initial residents must not all originate on one north-south apron edge");
        assertTrue(homes.stream().anyMatch(home -> home.z() > settlement.anchor().z()),
                "the retained initial residents must occupy the complete public ring deterministically");
        List<BlockPosition> allHomes = FrontierSettlementActorSlots.residentSlots(bootstrap.bounds(), bootstrap.terrain(), settlement,
                housingBeds, housingBeds);
        List<BlockPosition> guards = java.util.stream.IntStream.range(ResidentRole.GUARD.ordinal(), housingBeds)
                .filter(ordinal -> ordinal % ResidentRole.values().length == ResidentRole.GUARD.ordinal())
                .mapToObj(allHomes::get).toList();
        assertEquals(guards.size(), new HashSet<>(guards).size(),
                "the retained patrol role must keep distinct canonical ingress bodies");
    }
}
