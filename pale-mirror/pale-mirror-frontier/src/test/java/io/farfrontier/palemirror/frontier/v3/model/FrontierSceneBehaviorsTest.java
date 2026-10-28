package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierSceneBehaviorsTest {
    @Test void harvestSceneCannotDiscoverItsJobUnderAnotherDeclaredSite() {
        var fixture = FrontierResourceSiteHarvestFixture.create(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:scene-site-reference"), 125L);
        var state = fixture.state();
        var job = state.resourceSites().site(fixture.siteId()).harvestJob(fixture.jobId()).orElseThrow();
        var world = state.bootstrap().worldId();
        var member = new SceneMember(job.workerId(), SceneLease.deterministicEntityId(world, job.workerId()));
        var position = state.actorLocations().get(job.workerId()).supportingSurface().support();
        var lease = SceneLease.forCause(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:exact-site-reference"), world,
                new ResourceSiteHarvestSceneCause(fixture.siteId(), job.id()), position, fixture.instant(), 1,
                SceneLeaseStatus.PREPARED, List.of(member), java.util.Set.of(), java.util.Optional.empty());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(job.workerId()), members(state, lease));
        var foreign = state.resourceSites().sites().keySet().stream().filter(id -> !id.equals(fixture.siteId())).findFirst().orElseThrow();
        var forged = SceneLease.forCause(lease.id(), world, new ResourceSiteHarvestSceneCause(foreign, job.id()), position,
                fixture.instant(), 1, SceneLeaseStatus.PREPARED, List.of(member), java.util.Set.of(), java.util.Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> members(state, forged));
    }
    private static java.util.Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId> members(FrontierWorldState state, SceneLease lease) {
        return FrontierSceneBehaviors.expectedMembers(state.bootstrap(), state.humanPopulation(), state.actorLocations(),
                state.structureConditions(), state.routeConstructions(), state.routeMaintenances(), state.strategicPlans(),
                state.resourceSites(), state.productionJobs(), state.serviceWorks(), lease);
    }

    @Test
    void registryRefusesMissingAndDuplicateSceneFamiliesBeforeEngineStart() {
        assertDoesNotThrow(() -> FrontierSceneBehaviors.requireCompleteKindsForTest(List.of(
                SceneCauseKind.SETTLEMENT_ASSAULT, SceneCauseKind.ENGINEERING_WORKSITE,
                SceneCauseKind.MEDICAL_TREATMENT, SceneCauseKind.RESOURCE_SITE_HARVEST, SceneCauseKind.PRODUCTION_WORK,
                SceneCauseKind.SERVICE_WORK, SceneCauseKind.ROUTE_PATROL)));
        assertThrows(IllegalArgumentException.class, () -> FrontierSceneBehaviors.requireCompleteKindsForTest(List.of(SceneCauseKind.SETTLEMENT_ASSAULT)));
        assertThrows(IllegalArgumentException.class, () -> FrontierSceneBehaviors.requireCompleteKindsForTest(List.of(
                SceneCauseKind.SETTLEMENT_ASSAULT, SceneCauseKind.SETTLEMENT_ASSAULT,
                SceneCauseKind.ENGINEERING_WORKSITE, SceneCauseKind.MEDICAL_TREATMENT,
                SceneCauseKind.RESOURCE_SITE_HARVEST, SceneCauseKind.PRODUCTION_WORK, SceneCauseKind.SERVICE_WORK,
                SceneCauseKind.ROUTE_PATROL)));
    }
}
