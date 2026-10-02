package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Exact candidate and validation boundary for one actor-owned field-work scene. */
public final class FrontierResourceSiteHarvestSceneSupport {
    private FrontierResourceSiteHarvestSceneSupport() { }

    /** Field owner proves its own safe stop; generic execution never reads crop stages. */
    static ActivityExecutionCheckpoint executionCheckpoint(FrontierWorldState state, HumanAssignment assignment) {
        if (assignment.kind() != HumanAssignmentKind.FIELD_HARVEST)
            throw new IllegalArgumentException("field checkpoint requires a declared field assignment");
        ResourceSiteHarvestJob job = state.resourceSites().sites().values().stream()
                .flatMap(site -> site.harvestJobs().values().stream())
                .filter(value -> value.id().equals(assignment.ownerId().orElseThrow())).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("field checkpoint lost its exact job"));
        if (!job.workerId().equals(assignment.residentId()))
            throw new IllegalArgumentException("field checkpoint names a foreign worker");
        return new ActivityExecutionCheckpoint(state, assignment,
                job.progress().hasPendingCrop() || state.resourceSites().hasPendingWorldChange(job.siteId())
                        ? ResidentWorkYield.Status.PENDING_PHYSICAL_EFFECT
                        : ResidentWorkYield.Status.READY);
    }

    static Optional<ActorCarriedResources.Presentation> carriedResources(FrontierWorldState state, HumanAssignment assignment) {
        executionCheckpoint(state, assignment); // owner validates the exact worker/job relation
        ResourceSiteHarvestJob job = state.resourceSites().sites().values().stream()
                .flatMap(site -> site.harvestJobs().values().stream())
                .filter(value -> value.id().equals(assignment.ownerId().orElseThrow())).findFirst().orElseThrow();
        if (!state.inventory().fungibleResources().accounts().containsKey(job.actorAccountId())) return Optional.empty();
        var carried = new ActorCarriedResources.Presentation(job.workerId(), job.actorAccountId(),
                new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.OFF));
        carried.requireAccount(state.inventory().fungibleResources());
        return Optional.of(carried);
    }

    /**
     * Complete, bounded and stable inventory of canonical candidates.
     *
     * <p>This boundary deliberately knows nothing about loaded chunks or players.  A physical
     * executor must evaluate demand for every member of this inventory; choosing the global
     * first candidate before doing so would let an unloaded field suppress an observed one.</p>
     */
    public static List<Candidate> candidates(FrontierWorldState state) {
        return state.resourceSites().sites().values().stream().sorted(java.util.Comparator.comparing(ResourceSiteLifecycle::siteId))
                .flatMap(site -> site.harvestJobs().values().stream())
                .map(job -> candidate(state, job)).flatMap(Optional::stream).toList();
    }

    public static Optional<Candidate> candidate(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return candidate(state, job, true);
    }
    private static Optional<Candidate> candidate(FrontierWorldState state, ResourceSiteHarvestJob job, boolean rejectExistingScene) {
        if ((rejectExistingScene && hasNonClosedScene(state, job))
                || state.humanPopulation().meals().containsKey(job.workerId())
                || state.actorMovements().containsKey(job.workerId())
                || ResourceSiteHarvestGoal.actorAtDepot(state, job)) return Optional.empty();
        // An exact physical-effect lifecycle is not a scene-readiness bit.  Before F0.2, the
        // retained traversal may use a PREPARED intent but must leave its non-replayable effect
        // unbegun; after F0.2 the process-owned effect admission supplies RUNNING instead.
        // This remains process policy, not a generic scene-family branch.
        if (!ResourceSitePhysicalIntentStateSupport.admitsTraversal(state.physicalIntents().get(job.intentId()))) return Optional.empty();
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING
                || lifecycle.harvestJob(job.id()).filter(job::equals).isEmpty()) return Optional.empty();
        ResourceSite site = state.resourceSite(job.siteId());
        ActorLocation worker = state.actorLocations().get(job.workerId());
        if (site == null || state.structureConditions().get(site.facilityId()) != StructureCondition.INTACT
                || worker == null || worker.condition().status() != ActorLifeStatus.ALIVE) return Optional.empty();
        HumanAssignment assignment = HumanAssignmentProjection.compile(state).assignment(job.workerId());
        if (assignment.kind() != HumanAssignmentKind.FIELD_HARVEST || !assignment.ownerId().equals(Optional.of(job.id()))) return Optional.empty();
        // A completed crop cursor still owns the retained delivery journey. Demand follows
        // that worker's canonical station, not the now-distant last crop. No crop work is
        // reopened when a player first reaches the worker near the depot.
        boolean workerSide = job.navigationBlock().isPresent() || job.progress().complete() || job.returningForBatch();
        int demandCropIndex = workerSide
                ? Math.max(0, job.progress().lastCompletedCropSlotIndex()) : job.progress().nextCropSlotIndex();
        BodyPosition body = worker.body();
        BlockPosition crop = workerSide
                ? new BlockPosition(body.x(), body.y(), body.z()) : site.cropSlots().get(demandCropIndex);
        BlockPosition workerSurface = worker.supportingSurface().support();
        if (!state.bootstrap().bounds().contains(workerSurface)) return Optional.empty();
        return Optional.of(new Candidate(job.id(), job.siteId(), job.workerId(), demandCropIndex, crop,
                Map.of(job.workerId(), workerSurface)));
    }

    public static ResourceSiteHarvestJob require(FrontierWorldState state, ResourceSiteHarvestSceneCause cause) {
        return activeJob(state, cause)
                .orElseThrow(() -> new IllegalArgumentException("resource-site harvest scene has no active harvest job"));
    }

    /**
     * The terminal output receipt consumes the active job before the same HOT body's DRAINING
     * scene can publish its body-exit receipt.  That is a valid one-tick ownership boundary,
     * not an invitation to invent a new farmer or a new field owner. Growth may advance
     * while body release waits; the exact predecessor relation, not growth stage zero,
     * retains authority throughout the immediately succeeding unassigned growth epoch.
     */
    public static boolean isTerminalReceiptRelease(FrontierWorldState state, ResourceSiteHarvestSceneCause cause) {
        return activeJob(state, cause).isEmpty() && terminalReceiptSite(state.bootstrap(), state.resourceSites(), cause) != null;
    }

    /** Validation overload for the scene-state constructor, before a complete world state exists. */
    public static boolean isTerminalReceiptRelease(FrontierBootstrap bootstrap, ResourceSiteState resourceSites,
                                                   ResourceSiteHarvestSceneCause cause) {
        ResourceSiteLifecycle lifecycle = resourceSites.sites().get(cause.siteId());
        return lifecycle != null && lifecycle.harvestJob(cause.jobId()).isEmpty()
                && terminalReceiptSite(bootstrap, resourceSites, cause) != null;
    }

    private static Optional<ResourceSiteHarvestJob> activeJob(FrontierWorldState state, ResourceSiteHarvestSceneCause cause) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().get(cause.siteId());
        return lifecycle == null ? Optional.empty() : lifecycle.harvestJob(cause.jobId()).filter(job -> job.siteId().equals(cause.siteId()));
    }

    public static SubjectId owner(FrontierWorldState state, ResourceSiteHarvestSceneCause cause) {
        return site(state, cause).settlementId();
    }

    /** Exact active or retained terminal owner; never reconstruct a consumed job. */
    public static ResourceSite site(FrontierWorldState state, ResourceSiteHarvestSceneCause cause) {
        ResourceSiteHarvestJob job = activeJob(state, cause).orElse(null);
        ResourceSite site = job == null ? terminalReceiptSite(state.bootstrap(), state.resourceSites(), cause)
                : state.resourceSite(job.siteId());
        if (site == null) throw new IllegalArgumentException("resource-site harvest scene has an unknown field");
        return site;
    }

    /**
     * Returns the immutable site only for the exact completed predecessor retained by the
     * current unassigned successor epoch. The lifecycle-owned lineage is the authoritative
     * relation: a stale, foreign, or later successor scene cannot borrow this terminal-release
     * exception by reconstructing a plausible identifier from a site and epoch.
     */
    static ResourceSite terminalReceiptSite(FrontierBootstrap bootstrap, ResourceSiteState resourceSites,
                                                    ResourceSiteHarvestSceneCause cause) {
        ResourceSiteLifecycle lifecycle = resourceSites.sites().get(cause.siteId());
        if (lifecycle == null || lifecycle.harvestJob(cause.jobId()).isPresent()
                || lifecycle.harvestLineages().values().stream().noneMatch(lineage ->
                        lineage.predecessorJobId().equals(cause.jobId()))) return null;
        return resourceSites.descriptor(bootstrap, cause.siteId());
    }

    /**
     * Requires the sole HOT lease whose recovery body is the canonical actor's retained body.
     * A different harvest lease, a second member or merely a similarly named HOT scene is not
     * evidence for this worker's checkpoint.
     */
    public static SceneLease requireHotLease(FrontierWorldState state, ResourceSiteHarvestJob job, SceneLeaseId leaseId) {
        SceneLease lease = state.sceneLeases().get(leaseId);
        if (lease == null || lease.status() != SceneLeaseStatus.HOT || !FrontierSceneBehaviors.isResourceSiteHarvest(lease)
                || !FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(job.siteId())
                || !FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id())
                || lease.members().size() != 1 || !lease.members().getFirst().actorId().equals(job.workerId())) {
            throw new IllegalArgumentException("resource-site HOT checkpoint has no matching exact farmer lease");
        }
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || !actor.body().equals(lease.memberPosition(job.workerId()))) {
            throw new IllegalArgumentException("resource-site HOT lease recovery body diverges from its canonical worker");
        }
        return lease;
    }

    /** Atomically retains one observed semantic arrival, actor body and HOT recovery body. */
    public static FrontierWorldState arriveGoal(FrontierWorldState state, ResourceSiteLifecycle lifecycle,
                                                 ResourceSiteHarvestJob job, ResourceSiteHarvestGoal goal,
                                                 SceneLeaseId leaseId, BodyPosition observedWorker) {
        java.util.Objects.requireNonNull(observedWorker, "observed field goal body");
        if (!goal.jobId().equals(job.id()) || !goal.siteId().equals(job.siteId())
                || !goal.workerId().equals(job.workerId())
                || goal.legalStations().stream().noneMatch(station -> station.standingBody().equals(observedWorker)))
            throw new IllegalArgumentException("field HOT arrival is not at a declared semantic station");
        SceneLease lease = requireHotLease(state, job, leaseId);
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || !actor.body().equals(lease.memberPosition(job.workerId())))
            throw new IllegalArgumentException("field HOT arrival has no retained worker body");
        if (job.arriveAtSemanticGoal(goal).equals(job) && actor.body().equals(observedWorker))
            throw new IllegalArgumentException("field HOT goal arrival is already retained");
        ResourceSiteLifecycle arrived = lifecycle.arriveHarvestGoal(job, goal);
        java.util.Map<SubjectId, ActorLocation> actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(job.workerId(), actor.withBody(observedWorker));
        java.util.Map<SceneLeaseId, SceneLease> leases = new java.util.LinkedHashMap<>(state.sceneLeases());
        leases.put(leaseId, lease.withMemberPositions(Map.of(job.workerId(), observedWorker)));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                .resourceSites(state.resourceSites().replace(arrived)).sceneLeases(leases));
    }

    /** Retains an actual support at an interrupted HOT goal without advancing CellId work. */
    public static FrontierWorldState observeTransit(FrontierWorldState state, ResourceSiteHarvestJob job,
                                                     SceneLeaseId leaseId, BodyPosition observedWorker) {
        SceneLease lease = requireHotLease(state, job, leaseId);
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.body().equals(observedWorker))
            throw new IllegalArgumentException("interrupted field worker has no new observed support");
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), observedWorker.supportingSurface().support());
        long distance = Math.abs((long) actor.body().x() - observedWorker.x())
                + Math.abs((long) actor.body().y() - observedWorker.y())
                + Math.abs((long) actor.body().z() - observedWorker.z());
        if (distance > 54L) throw new IllegalArgumentException("interrupted field worker moved beyond one bounded HOT goal");
        java.util.Map<SubjectId, ActorLocation> actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(job.workerId(), actor.withBody(observedWorker));
        java.util.Map<SceneLeaseId, SceneLease> leases = new java.util.LinkedHashMap<>(state.sceneLeases());
        leases.put(leaseId, lease.withMemberPositions(Map.of(job.workerId(), observedWorker)));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).sceneLeases(leases));
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

    public static boolean hasNonClosedScene(FrontierWorldState state, ResourceSiteHarvestJob job) {
        // A retained conflict still owns this farmer until its explicit recovery path closes
        // it; do not create a second exact-worker lease while the first one remains visible.
        return state.sceneLeases().values().stream().filter(lease -> lease.status() != SceneLeaseStatus.CLOSED)
                .filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                .anyMatch(lease -> FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(job.siteId())
                        && FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id()));
    }

    public record Candidate(SubjectId jobId, SubjectId siteId, SubjectId workerId, int cropSlotIndex, BlockPosition cropSlot,
                            Map<SubjectId, BlockPosition> memberPositions) { }
}
