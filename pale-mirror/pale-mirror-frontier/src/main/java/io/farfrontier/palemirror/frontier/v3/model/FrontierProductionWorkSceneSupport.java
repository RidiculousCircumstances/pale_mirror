package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Exact candidate and validation boundary for one actor-owned workshop-work scene. */
public final class FrontierProductionWorkSceneSupport {
    private FrontierProductionWorkSceneSupport() { }

    /** Stable canonical inventory; physical demand is selected only by the adapter. */
    public static List<Candidate> candidates(FrontierWorldState state) {
        return state.productionJobs().values().stream().sorted(java.util.Comparator.comparing(ProductionJob::id))
                .map(job -> candidate(state, job)).flatMap(Optional::stream).toList();
    }
    public static Optional<Candidate> candidate(FrontierWorldState state, ProductionJob job) {
        if (hasScene(state, job.id()) || !hasPhysicalInput(state, job)
                || job.bakeryWork().isPresent() && job.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.DELIVERED
                || job.bakeryWork().isEmpty() && job.workProgress().terminalEffectEligible()) return Optional.empty();
        SubjectId depot = FrontierWorldState.depotId(job.settlementId());
        if (ReferenceContainerCustody.hasLiveCustody(state, depot) && !ReferenceContainerCustody.hasOperationalCustody(state, depot)) return Optional.empty();
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), job.settlementId());
        SettlementStructure workshop = settlement.structures().stream().filter(value -> value.id().equals(job.facilityId())).findFirst().orElse(null);
        ActorLocation worker = state.actorLocations().get(job.workerId());
        if (workshop == null || workshop.kind() != StructureKind.WORKSHOP || state.structureConditions().get(workshop.id()) != StructureCondition.INTACT
                || worker == null || worker.condition().status() != ActorLifeStatus.ALIVE) return Optional.empty();
        if (job.bakeryWork().isPresent()) {
            SurfaceAnchor retained = worker.supportingSurface();
            BlockPosition demand = BakeryWorkGoal.current(state, job).station().support();
            return Optional.of(new Candidate(job.id(), job.settlementId(), job.workerId(), workshop.id(), demand,
                    retained.support(), Map.of(job.workerId(), retained.support())));
        }
        SurfaceAnchor retained = job.workTraversal().linearCorridorSurfaces().get(job.traversalCursor());
        if (!worker.supportingSurface().equals(retained)) return Optional.empty();
        return Optional.of(new Candidate(job.id(), job.settlementId(), job.workerId(), workshop.id(), workshop.anchor(), retained.support(), Map.of(job.workerId(), retained.support())));
    }
    public static ProductionJob require(FrontierWorldState state, ProductionWorkSceneCause cause) {
        ProductionJob job = state.productionJobs().get(cause.jobId());
        if (job == null) throw new IllegalArgumentException("production-work scene has no active job");
        return job;
    }
    /** Requires the sole live lease that may turn a Minecraft body observation into this job's fact. */
    public static SceneLease requireHotLease(FrontierWorldState state, ProductionJob job, io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId leaseId) {
        if (!hasPhysicalInput(state, job)) throw new IllegalArgumentException("production work has no current physical input custody");
        SceneLease lease = state.sceneLeases().get(leaseId);
        if (lease == null || lease.status() != SceneLeaseStatus.HOT || !FrontierSceneBehaviors.isProductionWork(lease)
                || !FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id()) || lease.members().size() != 1
                || !lease.members().getFirst().actorId().equals(job.workerId())) {
            throw new IllegalArgumentException("production work observation has no matching HOT worker lease");
        }
        if (job.bakeryWork().isPresent()) return lease;
        BodyPosition retainedStation = job.workTraversal().linearCorridorSurfaces().get(job.traversalCursor()).standingBody();
        if (!retainedStation.equals(lease.memberPosition(job.workerId()))) {
            throw new IllegalArgumentException("production work HOT lease diverges from its retained worker cursor");
        }
        return lease;
    }
    public static SubjectId owner(FrontierWorldState state, ProductionWorkSceneCause cause) { return require(state, cause).settlementId(); }
    /** A blocked worker scene keeps the exact job until its body-release receipt is durable. */
    static boolean awaitingBlockedRelease(Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases,
                                          StrategicPlanState plans, CompanyRegistry companies, ProductionJob job) {
        // A missing materialized input is lawful only while this exact job's retained task is
        // blocked and its same worker is closing.  Another bread task's failure cannot make a
        // live job's missing input valid or authorize its custody hand-off.
        boolean blocked = companies.market().workOrders().values().stream().filter(order -> order.jobId().equals(job.id()))
                .map(order -> plans.tasks().get(order.taskId())).filter(java.util.Objects::nonNull)
                .anyMatch(task -> task.status() == StrategicTaskStatus.BLOCKED);
        return blocked && leases.values().stream().anyMatch(lease -> (lease.status() == SceneLeaseStatus.DRAINING || lease.status() == SceneLeaseStatus.CLOSED)
                && FrontierSceneBehaviors.isProductionWork(lease) && FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id()));
    }
    /** Named production-state update; identity and every owner remain immutable across progress facts. */
    public static FrontierWorldState replaceJob(FrontierWorldState state, ProductionJob replacement) {
        java.util.Objects.requireNonNull(replacement, "replacement production job");
        ProductionJob current = state.productionJobs().get(replacement.id());
        if (current == null || !current.settlementId().equals(replacement.settlementId()) || !current.facilityId().equals(replacement.facilityId())
                || !current.workerId().equals(replacement.workerId()) || !current.consumedItemId().equals(replacement.consumedItemId())
                || !current.outputItemId().equals(replacement.outputItemId())) {
            throw new IllegalArgumentException("production job replacement may not change exact identity, owners or item identities");
        }
        Map<SubjectId, ProductionJob> jobs = new java.util.LinkedHashMap<>(state.productionJobs()); jobs.put(replacement.id(), replacement);
        return state.withChanges(FrontierWorldStateUpdate.begin().productionJobs(jobs));
    }
    /**
     * Commits one observed worker arrival as one causal fact: the work cursor and the
     * matching persisted HOT-body position may never diverge.  The ordinary actor location
     * remains COLD ownership and is updated only by the scene-release receipt.
     */
    public static FrontierWorldState advanceWorker(FrontierWorldState state, ProductionJob current, ProductionJob replacement,
                                                   io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId leaseId,
                                                   BodyPosition observedWorker) {
        java.util.Objects.requireNonNull(current, "current production job");
        java.util.Objects.requireNonNull(replacement, "replacement production job");
        java.util.Objects.requireNonNull(observedWorker, "observed production worker");
        if (!replacement.id().equals(current.id()) || replacement.traversalCursor() != current.traversalCursor() + 1
                || !replacement.workProgress().equals(current.workProgress())
                || !replacement.workTraversal().equals(current.workTraversal())) {
            throw new IllegalArgumentException("production worker advance must retain one job and advance exactly one cursor");
        }
        SceneLease lease = requireHotLease(state, current, leaseId);
        BodyPosition expectedCurrent = current.workTraversal().linearCorridorSurfaces().get(current.traversalCursor()).standingBody();
        BodyPosition expectedNext = replacement.workTraversal().linearCorridorSurfaces().get(replacement.traversalCursor()).standingBody();
        if (!lease.memberPosition(current.workerId()).equals(expectedCurrent) || !observedWorker.equals(expectedNext)) {
            throw new IllegalArgumentException("production worker advance must retain its prior and observed next lease positions");
        }
        Map<SubjectId, ProductionJob> jobs = new java.util.LinkedHashMap<>(state.productionJobs()); jobs.put(replacement.id(), replacement);
        Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases = new java.util.LinkedHashMap<>(state.sceneLeases());
        leases.put(leaseId, lease.withMemberPositions(Map.of(current.workerId(), observedWorker)));
        return state.withChanges(FrontierWorldStateUpdate.begin().productionJobs(jobs).sceneLeases(leases));
    }
    /** The worker may not continue a visual cycle after its named player-observable input departed. */
    public static boolean hasPhysicalInput(FrontierWorldState state, ProductionJob job) {
        if (job.bakeryWork().isPresent()) return state.productionJobs().containsKey(job.id());
        if (job.inputHold() instanceof ProductionInputHold.FungibleBound bound) {
            SubjectId depot = FrontierWorldState.depotId(job.settlementId());
            if (!ReferenceContainerCustody.hasOperationalCustody(state, depot)) return false;
            PhysicalCustodyLease custody = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(depot));
            if (custody.authorityEpoch() != bound.authorityEpoch()) return false;
            List<PhysicalStackBinding> bindings = state.inventory().fungibleResources().bindings().values().stream()
                    .filter(binding -> binding.accountId().equals(bound.accountId())).toList();
            return !bindings.isEmpty() && bindings.stream().allMatch(binding -> binding.authorityEpoch() == bound.authorityEpoch())
                    && bound.inputLots().entrySet().stream().allMatch(entry -> bindings.stream()
                            .mapToInt(binding -> binding.lotQuantities().getOrDefault(entry.getKey(), 0)).sum() >= entry.getValue());
        }
        if (!(job.inputHold() instanceof ProductionInputHold.Materialized)) return false;
        ExactItemStack input = state.inventory().items().get(job.consumedItemId());
        return input != null && input.economicOwnerId().equals(job.settlementId()) && "minecraft:wheat".equals(input.itemKind())
                && input.count() == job.outputCount() && input.custody() instanceof InventoryCustody.ContainerSlot slot
                && slot.containerId().equals(FrontierWorldState.depotId(job.settlementId()));
    }
    public static void validatePrepared(FrontierWorldState state, SceneLease lease) {
        ProductionWorkSceneCause cause = FrontierSceneBehaviors.productionWork(lease); ProductionJob job = require(state, cause);
        Candidate candidate = candidate(state, job).orElseThrow(() -> new IllegalArgumentException("production-work scene has no exact ready worker"));
        if (!lease.handoffPosition().equals(candidate.handoffPosition()) || !lease.memberPositions().equals(SceneLease.bodiesAboveSupportCells(candidate.memberPositions()))
                || !lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()).equals(Set.of(job.workerId()))) {
            throw new IllegalArgumentException("production-work scene must retain its exact worker and cursor surface");
        }
    }
    private static boolean hasScene(FrontierWorldState state, SubjectId jobId) {
        // A conflict is still a retained physical claim: its body, diagnosis and recovery
        // remain authoritative until an explicit scene owner resolves it.  Treating it as
        // absent would let the same worker enter a second lease and turn a visible conflict
        // into a whole-world invariant failure.
        return state.sceneLeases().values().stream().filter(lease -> lease.status() != SceneLeaseStatus.CLOSED)
                .filter(FrontierSceneBehaviors::isProductionWork).anyMatch(lease -> FrontierSceneBehaviors.productionWork(lease).jobId().equals(jobId));
    }
    public record Candidate(SubjectId jobId, SubjectId settlementId, SubjectId workerId, SubjectId workshopId, BlockPosition demandPosition,
                            BlockPosition handoffPosition, Map<SubjectId, BlockPosition> memberPositions) { }
}
