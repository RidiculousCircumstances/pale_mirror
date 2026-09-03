package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Exact candidate and validation boundary for one actor-owned field-work scene. */
public final class FrontierResourceSiteHarvestSceneSupport {
    private FrontierResourceSiteHarvestSceneSupport() { }

    public static Optional<Candidate> nextCandidate(FrontierWorldState state) {
        return state.resourceSites().sites().values().stream().sorted(java.util.Comparator.comparing(ResourceSiteLifecycle::siteId))
                .map(ResourceSiteLifecycle::activeWork).flatMap(Optional::stream)
                .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .map(job -> candidate(state, job)).flatMap(Optional::stream).findFirst();
    }

    public static Optional<Candidate> candidate(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return candidate(state, job, true);
    }
    private static Optional<Candidate> candidate(FrontierWorldState state, ResourceSiteHarvestJob job, boolean rejectExistingScene) {
        if ((rejectExistingScene && hasScene(state, job.id())) || job.progress().complete()) return Optional.empty();
        // PREPARED means the canonical job exists but its loaded field/depot baseline has not
        // yet been observed ready by the sole physical harvest owner.  RUNNING is that durable
        // readiness hand-off; only then may the actor leave ambient custody for field work.
        if (state.physicalIntents().get(job.intentId()) == null
                || state.physicalIntents().get(job.intentId()).status() != PhysicalIntentStatus.RUNNING) return Optional.empty();
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING
                || lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .filter(job::equals).isEmpty()) return Optional.empty();
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(job.siteId());
        ActorLocation worker = state.actorLocations().get(job.workerId());
        if (site == null || state.structureConditions().get(site.facilityId()) != StructureCondition.INTACT
                || worker == null || worker.condition().status() != ActorLifeStatus.ALIVE) return Optional.empty();
        HumanAssignment assignment = HumanAssignmentProjection.compile(state).assignment(job.workerId());
        if (assignment.kind() != HumanAssignmentKind.FIELD_HARVEST || !assignment.ownerId().equals(Optional.of(job.id()))) return Optional.empty();
        BlockPosition crop = site.cropSlots().get(job.progress().nextCropSlotIndex());
        BlockPosition workerSurface = worker.supportingSurface().support();
        if (!workerSurface.equals(job.traversal().linearCorridorSurfaces().get(job.traversalCursor()).support())) return Optional.empty();
        return Optional.of(new Candidate(job.id(), job.siteId(), job.workerId(), job.progress().nextCropSlotIndex(), crop,
                Map.of(job.workerId(), workerSurface)));
    }

    public static ResourceSiteHarvestJob require(FrontierWorldState state, ResourceSiteHarvestSceneCause cause) {
        return state.resourceSites().sites().values().stream().map(ResourceSiteLifecycle::activeWork).flatMap(Optional::stream)
                .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .filter(job -> job.id().equals(cause.jobId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("resource-site harvest scene has no active harvest job"));
    }

    public static SubjectId owner(FrontierWorldState state, ResourceSiteHarvestSceneCause cause) {
        ResourceSiteHarvestJob job = require(state, cause);
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(job.siteId());
        if (site == null) throw new IllegalArgumentException("resource-site harvest scene has an unknown field");
        return site.settlementId();
    }

    public static void validatePrepared(FrontierWorldState state, SceneLease lease) {
        ResourceSiteHarvestSceneCause cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
        ResourceSiteHarvestJob job = require(state, cause);
        Candidate candidate = candidate(state, job, false).orElseThrow(() -> new IllegalArgumentException("resource-site harvest scene has no exact ready worker/crop"));
        if (!lease.handoffPosition().equals(candidate.cropSlot())
                || !lease.memberPositions().equals(SceneLease.bodiesAboveSupportCells(candidate.memberPositions()))
                || !lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()).equals(Set.of(job.workerId()))) {
            throw new IllegalArgumentException("resource-site harvest scene must retain its exact worker and current crop slot");
        }
    }

    private static boolean hasScene(FrontierWorldState state, SubjectId jobId) {
        // A retained conflict still owns this farmer until its explicit recovery path closes
        // it; do not create a second exact-worker lease while the first one remains visible.
        return state.sceneLeases().values().stream().filter(lease -> lease.status() != SceneLeaseStatus.CLOSED)
                .filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                .anyMatch(lease -> FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(jobId));
    }

    public record Candidate(SubjectId jobId, SubjectId siteId, SubjectId workerId, int cropSlotIndex, BlockPosition cropSlot,
                            Map<SubjectId, BlockPosition> memberPositions) { }
}
