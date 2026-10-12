package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Canonical admission owner for reusable resident service work.
 *
 * <p>Decontamination is the first form. It may reserve neither a physical effect nor a worker
 * independently: this one owner retains the exact task, medic, depot slot, two immutable legs,
 * field station and endpoint intent atomically.</p>
 */
public final class SettlementServiceWorkProcess {
    private static final SubjectId SYSTEM = new SubjectId("system:decontamination");
    private SettlementServiceWorkProcess() { }

    public static ScheduledAction scan(int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:decontamination-" + ordinal), new SimInstant(dueAt), 0,
                SYSTEM, "frontier.decontamination.scan", 1);
    }

    public static List<ProposedEvent> planDecontamination(FrontierWorldState state, ScheduledAction action) {
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        ProposedEvent next = new ProposedEvent(SYSTEM, new ScheduleEffect.Created(scan(ordinal + 1,
                action.dueAt().ticks() + state.bootstrap().ruleset().cadence().decontaminationScanInterval())));
        if (state.serviceWorks().values().stream().anyMatch(work -> work.kind() == SettlementServiceWorkKind.DECONTAMINATION && work.phase().active())) {
            return List.of(next);
        }
        Optional<StrategicTask> task = pendingTask(state);
        if (task.isEmpty()) return List.of(next);
        Optional<Candidate> candidate;
        try { candidate = candidate(state, task.orElseThrow(), ordinal); }
        catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable pending) {
            if (pending.status() == io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH)
                throw pending;
            return List.of(new ProposedEvent(SYSTEM, new ScheduleEffect.Created(scan(ordinal,
                    Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().decontaminationScanInterval())))));
        }
        if (candidate.isEmpty()) {
            return List.of(new ProposedEvent(task.orElseThrow().ownerId(), new StrategicTaskTransition(task.orElseThrow().id(), StrategicTaskStatus.BLOCKED)), next);
        }
        Candidate value = candidate.orElseThrow();
        return List.of(new ProposedEvent(value.settlement().id(), new StrategicTaskTransition(value.task().id(), StrategicTaskStatus.ACTIVE)),
                new ProposedEvent(value.settlement().id(), new SettlementServiceWorkStarted(value.task().id(), value.work(), value.inputIssue(), value.endpoint(),
                        io.farfrontier.palemirror.frontier.v3.model.SettlementServiceExecutionAuthority.admission(state, value.work()))), next);
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, SettlementServiceWorkStarted started) {
        SettlementServiceWork work = started.work();
        if (!subject.equals(work.settlementId())) throw new IllegalArgumentException("service work start has a foreign settlement owner");
        StrategicTask task = state.strategicPlans().tasks().get(started.taskId());
        if (work.kind() != SettlementServiceWorkKind.DECONTAMINATION
                || !(work.target() instanceof SettlementServiceTarget.Infection infection)) {
            throw new IllegalArgumentException("service-work start has an invalid decontamination shape");
        }
        if (task == null || task.kind() != StrategicTaskKind.DECONTAMINATE_INFECTION_CELL || task.status() != StrategicTaskStatus.ACTIVE
                || !task.ownerId().equals(work.settlementId()) || !task.infectionTarget().equals(Optional.of(infection.cell()))) {
            throw new IllegalArgumentException("service-work start has no exact active decontamination task");
        }
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), work.settlementId());
        SettlementStructure facility = settlement.structures().stream().filter(value -> value.id().equals(work.facilityId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("service work lost its declared facility"));
        ResidentProfile worker = state.humanPopulation().resident(work.workerId());
        ExactItemStack material = state.inventory().items().get(work.inputItemId());
        SettlementWorkforce.requireAvailable(state, work.settlementId(), List.of(work.workerId()));
        if (facility.kind() != StructureKind.INFIRMARY || state.structureConditions().get(facility.id()) != StructureCondition.INTACT
                || worker.capability(HumanCapability.MEDICINE) <= 0 || !state.infection().containsKey(infection.cell())
                || !nearby(state.bootstrap().ruleset(), facility, infection.cell()) || material == null
                || !material.itemKind().equals(DecontaminationPolicy.REAGENT) || !material.custody().equals(work.inputSource())
                || !activeWarehouseMaterial(state, settlement, material)
                || work.inputTraversalCursor() != 0 || work.workTraversalCursor() != 0 || work.completedWorkTicks() != 0
                || !work.spatial().equals(StationApproachState.initial())
                || work.phase() != (work.inputTraversal().linearCorridorSurfaces().size() == 1
                    ? SettlementServiceWorkPhase.INPUT_ISSUE_PENDING : SettlementServiceWorkPhase.PREPARED)
                || !inputIssue(work).equals(started.inputIssueIntent()) || !endpoint(work, infection.cell()).equals(started.endpointIntent()))
            throw new IllegalArgumentException("service-work admission lost its declared worker, facility, source or exact initial boundaries");
        SettlementServiceWorkTraversal.requireSelected(state, work);
        if (state.serviceWorks().containsKey(work.id()) || state.physicalIntents().containsKey(work.inputIssueIntentId())
                || state.physicalIntents().containsKey(work.endpointIntentId())) {
            throw new IllegalArgumentException("service-work start reuses a durable identity");
        }
        LinkedHashMap<SubjectId, SettlementServiceWork> works = new LinkedHashMap<>(state.serviceWorks());
        works.put(work.id(), work);
        LinkedHashMap<PhysicalIntentId, PhysicalIntent> intents = new LinkedHashMap<>(state.physicalIntents());
        intents.put(work.inputIssueIntentId(), started.inputIssueIntent());
        intents.put(work.endpointIntentId(), started.endpointIntent());
        var recovery = FencedRecoveryPhysicalIntentSupport.prepared(state.fencedRecovery(),
                started.inputIssueIntent(), FencedRecoveryAsset.EFFECT);
        recovery = FencedRecoveryPhysicalIntentSupport.prepared(recovery, started.endpointIntent(), FencedRecoveryAsset.EFFECT);
        return io.farfrontier.palemirror.frontier.v3.model.ActorExecutionComposition.LIFECYCLE.prepareVacant(state, started.execution())
                .commit(state, FrontierWorldStateUpdate.begin().serviceWorks(works).physicalIntents(intents).fencedRecovery(recovery));
    }

    /** Commits only a loaded observation of the next edge on the current retained service leg. */
    public static FrontierWorldState reduceHotTraversalAdvanced(FrontierWorldState state, SubjectId subject,
                                                                 SettlementServiceWorkTraversalAdvanced advanced) {
        SettlementServiceWork work = requireOwned(state, subject, advanced.workId());
        io.farfrontier.palemirror.frontier.v3.model.SettlementServiceExecutionAuthority.requireCurrent(state, work, advanced.execution());
        SettlementServiceWork replacement = switch (work.phase()) {
            case PREPARED, APPROACH_INPUT -> work.withInputTraversalCursor(advanced.nextCursor());
            case APPROACH_WORK -> work.withWorkTraversalCursor(advanced.nextCursor());
            default -> throw new IllegalArgumentException("service-work traversal is not currently eligible to advance");
        };
        return FrontierSettlementServiceWorkSceneSupport.advanceWorker(state, work, replacement, advanced.leaseId(), advanced.observedWorker(), advanced.observation());
    }

    /** Progress is legal only at the retained work station and only by one durable bounded tick. */
    public static FrontierWorldState reduceHotProgressed(FrontierWorldState state, SubjectId subject,
                                                          SettlementServiceWorkProgressed progressed) {
        SettlementServiceWork work = requireOwned(state, subject, progressed.workId());
        io.farfrontier.palemirror.frontier.v3.model.SettlementServiceExecutionAuthority.requireCurrent(state, work, progressed.execution());
        if (work.phase() != SettlementServiceWorkPhase.WORKING || !progressed.observedWorker().equals(work.workStation().standingBody())) {
            throw new IllegalArgumentException("service-work progress must be observed at its retained work station");
        }
        SceneLease lease = FrontierSettlementServiceWorkSceneSupport.requireHotLease(state, work, progressed.leaseId());
        progressed.observation().require(state, work, lease, progressed.observedWorker());
        boolean legal = progressed.nextPhase() == SettlementServiceWorkPhase.WORKING
                && progressed.completedWorkTicks() == work.completedWorkTicks() + 1
                || progressed.nextPhase() == SettlementServiceWorkPhase.EFFECT_READY
                && work.completedWorkTicks() == SettlementServiceWork.REQUIRED_WORK_TICKS - 1
                && progressed.completedWorkTicks() == 0;
        if (!legal) throw new IllegalArgumentException("service-work progress is not its retained next bounded step");
        return replace(state, work, work.withPhase(progressed.nextPhase(), progressed.completedWorkTicks()));
    }

    /** A loaded full next body is canonical BLOCKED evidence, never authority for a detour. */
    public static List<ProposedEvent> planHotTraversalBlocked(FrontierWorldState state, SubjectId subject,
                                                               SettlementServiceWorkTraversalBlocked blocked) {
        reduceHotTraversalBlocked(state, subject, blocked);
        SettlementServiceWork work = state.serviceWorks().get(blocked.workId());
        return List.of(new ProposedEvent(work.settlementId(), blocked),
                new ProposedEvent(work.settlementId(), new StrategicTaskTransition(work.taskId(), StrategicTaskStatus.BLOCKED)),
                new ProposedEvent(work.settlementId(), new io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition(blocked.leaseId(),
                        io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.DRAINING)));
    }

    /** Validates obstruction evidence; the following same-transaction event makes the work BLOCKED. */
    public static FrontierWorldState reduceHotTraversalBlocked(FrontierWorldState state, SubjectId subject,
                                                                SettlementServiceWorkTraversalBlocked blocked) {
        SettlementServiceWork work = requireOwned(state, subject, blocked.workId());
        io.farfrontier.palemirror.frontier.v3.model.SettlementServiceExecutionAuthority.requireCurrent(state, work, blocked.execution());
        int current = switch (work.phase()) {
            case PREPARED, APPROACH_INPUT -> work.inputTraversalCursor();
            case APPROACH_WORK -> work.workTraversalCursor();
            default -> throw new IllegalArgumentException("service-work block has no active traversal leg");
        };
        int size = switch (work.phase()) {
            case PREPARED, APPROACH_INPUT -> work.inputTraversal().linearCorridorSurfaces().size();
            case APPROACH_WORK -> work.workTraversal().linearCorridorSurfaces().size();
            default -> throw new IllegalStateException("unreachable service-work traversal phase");
        };
        if (blocked.blockedNextCursor() != current + 1 || blocked.blockedNextCursor() >= size
                || !blocked.observedWorker().equals(FrontierSettlementServiceWorkSceneSupport.currentSurface(work).standingBody())) {
            throw new IllegalArgumentException("service-work block does not name one retained next edge");
        }
        SceneLease lease = FrontierSettlementServiceWorkSceneSupport.requireHotLease(state, work, blocked.leaseId());
        blocked.observation().require(state, work, lease, blocked.observedWorker());
        return replace(state, work, work.withPhase(SettlementServiceWorkPhase.BLOCKED, 0));
    }

    private static SettlementServiceWork requireOwned(FrontierWorldState state, SubjectId subject, SubjectId workId) {
        SettlementServiceWork work = state.serviceWorks().get(workId);
        if (work == null || !subject.equals(work.settlementId())) {
            throw new IllegalArgumentException("service-work observation has no owned retained aggregate");
        }
        return work;
    }

    private static FrontierWorldState replace(FrontierWorldState state, SettlementServiceWork current, SettlementServiceWork replacement) {
        if (!replacement.id().equals(current.id()) || !replacement.taskId().equals(current.taskId())
                || !replacement.workerId().equals(current.workerId()) || !replacement.settlementId().equals(current.settlementId())
                || !replacement.facilityId().equals(current.facilityId()) || !replacement.inputItemId().equals(current.inputItemId())) {
            throw new IllegalArgumentException("service-work replacement may not change durable identities or owners");
        }
        LinkedHashMap<SubjectId, SettlementServiceWork> works = new LinkedHashMap<>(state.serviceWorks());
        works.put(replacement.id(), replacement);
        return state.withChanges(FrontierWorldStateUpdate.begin().serviceWorks(works).actorExecutions(replacement.phase().active()
                ? state.actorExecutions() : io.farfrontier.palemirror.frontier.v3.model.SettlementServiceExecutionAuthority.retired(state, current)));
    }

    private static Optional<StrategicTask> pendingTask(FrontierWorldState state) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.kind() == StrategicTaskKind.DECONTAMINATE_INFECTION_CELL
                && task.status() == StrategicTaskStatus.PENDING).sorted(Comparator.comparing(StrategicTask::id)).findFirst();
    }

    private static Optional<Candidate> candidate(FrontierWorldState state, StrategicTask task, int ordinal) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), task.ownerId());
        InfectionCell cell = task.infectionTarget().orElseThrow();
        Optional<SettlementStructure> facility = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY)
                .filter(structure -> state.structureConditions().get(structure.id()) == StructureCondition.INTACT).min(Comparator.comparing(SettlementStructure::id));
        Optional<ResidentProfile> worker = FrontierWorldStateSupport.availableFieldResident(state, settlement.id(), HumanCapability.MEDICINE);
        if (facility.isEmpty() || worker.isEmpty() || !state.infection().containsKey(cell)
                || !nearby(state.bootstrap().ruleset(), facility.orElseThrow(), cell)) return Optional.empty();
        SubjectId workId = new SubjectId("service:decontamination-" + ordinal);
        var actor = state.actorLocations().get(worker.orElseThrow().id());
        for (var material : ResourceStorageSelection.exactSources(state, settlement.id(), DecontaminationPolicy.REAGENT,
                1, actor.supportingSurface(), java.util.Set.of(ResourceStorageRole.WAREHOUSE))) {
            InventoryCustody.ContainerSlot source = (InventoryCustody.ContainerSlot) material.custody();
            SettlementServiceWorkTraversal.Plan traversal;
            try {
                traversal = SettlementServiceWorkTraversal.compileDecontamination(state, settlement,
                        worker.orElseThrow().id(), actor, cell, workId, ResourceStorageAccess.at(state, source.containerId()));
            } catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH)
                    throw unavailable;
                continue;
            }
            PhysicalIntentId inputIssueId = new PhysicalIntentId("intent:service-input-issue-" + ordinal);
            PhysicalIntentId endpointId = new PhysicalIntentId("intent:service-decontamination-" + ordinal);
            SettlementServiceWork work = new SettlementServiceWork(workId, task.id(), SettlementServiceWorkKind.DECONTAMINATION, settlement.id(), worker.orElseThrow().id(),
                    facility.orElseThrow().id(), source, traversal.inputStation(), traversal.workStation(), material.id(),
                    new SettlementServiceTarget.Infection(cell), inputIssueId, endpointId, traversal.inputTraversal(), 0, traversal.workTraversal(), 0,
                    traversal.inputTraversal().linearCorridorSurfaces().size() == 1
                            ? SettlementServiceWorkPhase.INPUT_ISSUE_PENDING : SettlementServiceWorkPhase.PREPARED, 0);
            return Optional.of(new Candidate(task, settlement, work, inputIssue(work), endpoint(work, cell)));
        }
        return Optional.empty();
    }

    private static PhysicalIntent inputIssue(SettlementServiceWork work) {
        return new PhysicalIntent(work.inputIssueIntentId(), PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE, PhysicalIntentStatus.PREPARED,
                work.id(), PhysicalIntentRoleBinding.serviceInputIssue(work.id(), work.workerId(), work.inputItemId()), fixed(work.inputStation().support()), 0,
                PhysicalPostcondition.SETTLEMENT_SERVICE_INPUT_ISSUED_OBSERVED, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_SERVICE_WORK);
    }

    private static PhysicalIntent endpoint(SettlementServiceWork work, InfectionCell cell) {
        return new PhysicalIntent(work.endpointIntentId(), PhysicalIntentKind.DECONTAMINATION, PhysicalIntentStatus.PREPARED,
                work.id(), PhysicalIntentRoleBinding.serviceDecontamination(work.id(), work.workerId(), work.inputItemId()), fixed(cell.originAtY(0)), 0,
                PhysicalPostcondition.DECONTAMINATION_OBSERVED, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_SERVICE_DECONTAMINATION);
    }

    private static boolean activeWarehouseMaterial(FrontierWorldState state, Settlement settlement, ExactItemStack item) {
        if (!item.economicOwnerId().equals(settlement.id()) || !(item.custody() instanceof InventoryCustody.ContainerSlot slot)
                || !ResourceStorageSelection.permits(state.inventory(), slot.containerId(), settlement.id(), item.itemKind(),
                    java.util.Set.of(ResourceStorageRole.WAREHOUSE))
                || ReferenceContainerCustody.blocksCanonicalUse(state, slot.containerId())) return false;
        ContainerSurface surface = state.inventory().surfaces().get(slot.containerId());
        return surface != null && surface.status() == ContainerSurfaceStatus.ACTIVE;
    }

    private static boolean nearby(FrontierRuleset ruleset, SettlementStructure facility, InfectionCell cell) {
        BlockPosition position = cell.originAtY(facility.anchor().y()); long dx = position.x() - facility.anchor().x(), dz = position.z() - facility.anchor().z();
        int radius = ruleset.spatial().decontaminationResponseRadius();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static FixedPosition fixed(BlockPosition position) {
        return new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z()));
    }

    private record Candidate(StrategicTask task, Settlement settlement, SettlementServiceWork work,
                             PhysicalIntent inputIssue, PhysicalIntent endpoint) { }
}
