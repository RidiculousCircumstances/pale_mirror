package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class FrontierDomainRelationshipsTest {
    @Test
    void dependencyAwareValidationRejectsRetiredTargetsOfUnchangedJobsLikeTheFullAudit() {
        var before = FrontierV3FixtureCatalog.productionWorkConfiguration(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:dependency-validation"), 91L).initialState();
        assertTrue(!before.productionJobs().isEmpty());
        assertDoesNotThrow(() -> FrontierDomainRelationships.validate(before));
        assertDoesNotThrow(() -> FrontierDomainRelationships.validateTransition(before, before));
        var after = FrontierWorldState.duringReducerTransition(() ->
                before.withChanges(FrontierWorldStateUpdate.begin().strategicPlans(StrategicPlanState.empty())));
        assertTrue(before.productionJobs() == after.productionJobs(), "the owning jobs did not change");
        var full = assertThrows(IllegalArgumentException.class, () -> FrontierDomainRelationships.validate(after));
        var incremental = assertThrows(IllegalArgumentException.class,
                () -> FrontierDomainRelationships.validateTransition(before, after));
        assertEquals(full.getMessage(), incremental.getMessage(), "changed targets must invalidate unchanged owners");
    }

    @Test
    void everyStableKindHasAnExecutableClosedDeclarationAndRejectsWrongTypedEndpoints() {
        assertEquals(Set.of(FrontierDomainRelationships.Kind.values()), FrontierDomainRelationships.declarations().keySet());
        FrontierDomainRelationships.Endpoint order = new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.MARKET_ORDER, new SubjectId("order:relationship-test"));
        FrontierDomainRelationships.Edge wrong = new FrontierDomainRelationships.Edge(FrontierDomainRelationships.Kind.ORDER_JOB, order, order,
                new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESIDENT, new SubjectId("resident:1-1")),
                FrontierDomainRelationships.Lifecycle.ACTIVE, "relationship:test");
        assertThrows(IllegalArgumentException.class, () -> FrontierDomainRelationships.declaration(wrong.kind()).validate(wrong));
        FrontierDomainRelationships.Endpoint resident = new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESIDENT, new SubjectId("resident:1-1"));
        FrontierDomainRelationships.Edge staleCarrier = new FrontierDomainRelationships.Edge(FrontierDomainRelationships.Kind.ACTOR_CARRIER_EVIDENCE, resident, resident,
                new FrontierDomainRelationships.CarrierEvidenceEndpoint(new SubjectId("resident:1-1"), "carrier:stale"), FrontierDomainRelationships.Lifecycle.ACTIVE, "relationship:stale");
        assertThrows(IllegalArgumentException.class, () -> FrontierDomainRelationships.declaration(staleCarrier.kind()).validate(staleCarrier));
    }
    @Test
    void inventoryMakesTheCurrentVerticalExplicitWithoutClaimingASecondGraphAuthority() {
        assertTrue(FrontierDomainRelationships.inventory().stream()
                .filter(family -> family.disposition() == FrontierDomainRelationships.FamilyDisposition.RELATION_LAYER_CURRENT)
                .map(FrontierDomainRelationships.Family::tag)
                .collect(java.util.stream.Collectors.toSet())
                .containsAll(java.util.Set.of("strategic-objective-task", "market-demand-order-production",
                        "resource-harvest-successor", "provision-allocation-recipient", "actor-physical-carrier")));
    }

    @Test
    void currentFamilyOwnerSurfaceManifestRejectsAnUnregisteredDurableIdentityComponent() {
        assertDoesNotThrow(FrontierDomainRelationships::verifyCurrentOwnerSurfaces);
        assertThrows(IllegalArgumentException.class, () -> FrontierDomainRelationships.validateOwnerSurface(
                UnregisteredCurrentOwnerSurface.class, Set.of("ownerId")),
                "a new durable identity must be classified before it can enter a migrated owner surface");
    }

    @Test
    void successorLineageRejectsAReplacementFarmer() {
        ResourceSiteHarvestLineage lineage = new ResourceSiteHarvestLineage(new SubjectId("job:site-harvest-1-wheat-field-1"),
                new SubjectId("task:site-harvest-1"), new SubjectId("resident:1-1"),
                new SubjectId("custody:field-actor-site-harvest-1-wheat-field-1"), new SubjectId("custody:container-settlement-1-depot"),
                new SubjectId("item:site-harvest-1-wheat-field-1"),
                1L, new BodyPosition(1, 65, 1), new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-harvest-1-wheat-field-1"),
                new InventoryCustody.ContainerSlot(new SubjectId("container:settlement-1-depot"), 0), true, Optional.empty(), Optional.empty());
        ResourceSiteHarvestJob replacement = new ResourceSiteHarvestJob(new SubjectId("job:site-harvest-1-wheat-field-2"),
                new SubjectId("task:site-harvest-2"), new SubjectId("site:1-wheat-field"), new SubjectId("resident:1-2"),
                new SubjectId("custody:field-actor-site-harvest-1-wheat-field-2"),
                new SubjectId("custody:container-settlement-1-depot"),
                new SubjectId("item:site-harvest-1-wheat-field-2"), new InventoryCustody.ContainerSlot(new SubjectId("container:settlement-1-depot"), 0),
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-harvest-1-wheat-field-2"),
                ResourceSiteHarvestProgress.notStarted(ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS));

        assertThrows(IllegalArgumentException.class, () -> lineage.bindSuccessor(replacement),
                "another eligible farmer may not replace the exact retained successor");
    }

    private record UnregisteredCurrentOwnerSurface(SubjectId ownerId, SubjectId retainedForeignIdentity) { }
}
