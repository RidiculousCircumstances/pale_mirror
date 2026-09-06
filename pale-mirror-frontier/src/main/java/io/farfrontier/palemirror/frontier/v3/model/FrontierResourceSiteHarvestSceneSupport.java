package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Exact candidate and validation boundary for one actor-owned field-work scene. */
public final class FrontierResourceSiteHarvestSceneSupport {
    private FrontierResourceSiteHarvestSceneSupport() { }

    /**
     * Complete, bounded and stable inventory of canonical candidates.
     *
     * <p>This boundary deliberately knows nothing about loaded chunks or players.  A physical
     * executor must evaluate demand for every member of this inventory; choosing the global
     * first candidate before doing so would let an unloaded field suppress an observed one.</p>
     */
    public static List<Candidate> candidates(FrontierWorldState state) {
        return state.resourceSites().sites().values().stream().sorted(java.util.Comparator.comparing(ResourceSiteLifecycle::siteId))
                .map(ResourceSiteLifecycle::activeWork).flatMap(Optional::stream)
                .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .map(job -> candidate(state, job)).flatMap(Optional::stream).toList();
    }

    public static Optional<Candidate> candidate(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return candidate(state, job, true);
    }
    private static Optional<Candidate> candidate(FrontierWorldState state, ResourceSiteHarvestJob job, boolean rejectExistingScene) {
        if ((rejectExistingScene && hasNonClosedScene(state, job.id())) || job.progress().complete()) return Optional.empty();
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

    /**
     * Requires the sole HOT lease whose recovery body is the job's current retained cursor.
     * A different harvest lease, a second member or merely a similarly named HOT scene is not
     * evidence for this worker's checkpoint.
     */
    public static SceneLease requireHotLease(FrontierWorldState state, ResourceSiteHarvestJob job, SceneLeaseId leaseId) {
        SceneLease lease = state.sceneLeases().get(leaseId);
        if (lease == null || lease.status() != SceneLeaseStatus.HOT || !FrontierSceneBehaviors.isResourceSiteHarvest(lease)
                || !FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id())
                || lease.members().size() != 1 || !lease.members().getFirst().actorId().equals(job.workerId())) {
            throw new IllegalArgumentException("resource-site HOT checkpoint has no matching exact farmer lease");
        }
        BodyPosition retained = job.traversal().linearCorridorSurfaces().get(job.traversalCursor()).standingBody();
        if (!retained.equals(lease.memberPosition(job.workerId()))) {
            throw new IllegalArgumentException("resource-site HOT lease recovery body diverges from its retained cursor");
        }
        return lease;
    }

    /**
     * Atomically installs one observed HOT step of this exact harvest job.  The process cursor,
     * lease recovery body and canonical actor body all move together or none does.
     */
    public static FrontierWorldState advanceWorker(FrontierWorldState state, ResourceSiteLifecycle lifecycle,
                                                   ResourceSiteHarvestJob current, ResourceSiteHarvestJob replacement,
                                                   SceneLeaseId leaseId, BodyPosition observedWorker) {
        java.util.Objects.requireNonNull(lifecycle, "resource-site harvest lifecycle");
        java.util.Objects.requireNonNull(current, "current resource-site harvest job");
        java.util.Objects.requireNonNull(replacement, "replacement resource-site harvest job");
        java.util.Objects.requireNonNull(observedWorker, "observed resource-site harvest worker");
        if (!replacement.id().equals(current.id()) || !replacement.workerId().equals(current.workerId())
                || replacement.traversalCursor() != current.traversalCursor() + 1
                || !replacement.traversal().equals(current.traversal()) || !replacement.progress().equals(current.progress())) {
            throw new IllegalArgumentException("resource-site HOT checkpoint may advance only one retained job cursor");
        }
        SceneLease lease = requireHotLease(state, current, leaseId);
        BodyPosition currentBody = current.traversal().linearCorridorSurfaces().get(current.traversalCursor()).standingBody();
        BodyPosition expectedNext = replacement.traversal().linearCorridorSurfaces().get(replacement.traversalCursor()).standingBody();
        ActorLocation actor = state.actorLocations().get(current.workerId());
        if (actor == null || !actor.body().equals(currentBody) || !lease.memberPosition(current.workerId()).equals(currentBody)
                || !observedWorker.equals(expectedNext)) {
            throw new IllegalArgumentException("resource-site HOT checkpoint body does not match its exact retained edge");
        }
        java.util.Map<SubjectId, ActorLocation> actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(current.workerId(), actor.withBody(observedWorker));
        java.util.Map<SceneLeaseId, SceneLease> leases = new java.util.LinkedHashMap<>(state.sceneLeases());
        leases.put(leaseId, lease.withMemberPositions(Map.of(current.workerId(), observedWorker)));
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(actors)
                .resourceSites(state.resourceSites().replace(lifecycle.advanceHarvestTraversal(current, replacement.traversalCursor())))
                .sceneLeases(leases));
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

    public static boolean hasNonClosedScene(FrontierWorldState state, SubjectId jobId) {
        // A retained conflict still owns this farmer until its explicit recovery path closes
        // it; do not create a second exact-worker lease while the first one remains visible.
        return state.sceneLeases().values().stream().filter(lease -> lease.status() != SceneLeaseStatus.CLOSED)
                .filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                .anyMatch(lease -> FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(jobId));
    }

    public record Candidate(SubjectId jobId, SubjectId siteId, SubjectId workerId, int cropSlotIndex, BlockPosition cropSlot,
                            Map<SubjectId, BlockPosition> memberPositions) { }
}
