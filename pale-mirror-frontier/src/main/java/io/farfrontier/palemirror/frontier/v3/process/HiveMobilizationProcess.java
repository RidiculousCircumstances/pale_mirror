package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.ActorLocation;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.BioformLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.BioformLifecyclePhase;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.HiveCocoonPlan;
import io.farfrontier.palemirror.frontier.v3.model.HiveCocoonSlot;
import io.farfrontier.palemirror.frontier.v3.model.HiveAssemblyCorridor;
import io.farfrontier.palemirror.frontier.v3.model.HiveAssemblyPortPlan;
import io.farfrontier.palemirror.frontier.v3.model.HiveCommandCapacity;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilization;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationCocoonReleased;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflictReason;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflicted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationReleaseStarted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStarted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStatus;
import io.farfrontier.palemirror.frontier.v3.model.HiveTaskAssembly;
import io.farfrontier.palemirror.frontier.v3.model.HiveNest;
import io.farfrontier.palemirror.frontier.v3.model.HiveOrgan;
import io.farfrontier.palemirror.frontier.v3.model.HiveSettlementKnowledge;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTask;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskKind;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskStatus;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/** Exact task-to-cocoon release boundary; it never creates or teleports a Minecraft body. */
public final class HiveMobilizationProcess {
    private static final SubjectId SYSTEM = new SubjectId("system:hive-mobilization");
    private HiveMobilizationProcess() { }

    /**
     * Selects one nest-local dormant assault group. The caller must retain the current Scout
     * sighting independently; this method does not make a hidden knowledge query.
     */
    public static Optional<HiveMobilization> forSettlementAssault(FrontierWorldState state, StrategicTask task,
                                                                    HiveSettlementKnowledge.Sighting sighting, long now) {
        Objects.requireNonNull(state, "hive mobilization state");
        if (task.kind() != StrategicTaskKind.ASSAULT_SETTLEMENT || task.status() != StrategicTaskStatus.PENDING
                || !task.ownerId().equals(state.bootstrap().hive().id()) || now < 0L) return Optional.empty();
        return state.bootstrap().hive().seedNests().stream().sorted(Comparator.comparing(HiveNest::id))
                .map(nest -> selectNest(state, task, sighting, nest, now)).flatMap(Optional::stream)
                .min(Comparator.comparing(mobilization -> mobilization.id()));
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, HiveMobilizationStarted started) {
        HiveMobilization mobilization = started.mobilization();
        requireTask(state, subject, mobilization);
        if (mobilization.status() != HiveMobilizationStatus.WAKING || !mobilization.releasedMemberIds().isEmpty()
                || mobilization.releasingMemberId().isPresent() || mobilization.conflictReason().isPresent()) {
            throw new IllegalArgumentException("hive mobilization must begin before any physical cocoon release");
        }
        requireCommandCapacity(state, mobilization);
        Map<SubjectId, BioformLifecycle> lifecycles = new LinkedHashMap<>(state.hiveColony().bioformLifecycles());
        for (SubjectId member : mobilization.memberIds()) {
            BioformLifecycle lifecycle = lifecycles.get(member);
            if (lifecycle == null || lifecycle.phase() != BioformLifecyclePhase.DORMANT || lifecycle.homeSlot().isEmpty()) {
                throw new IllegalArgumentException("hive mobilization must select an exact dormant cocoon occupant");
            }
            lifecycles.put(member, lifecycle.waking());
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().hiveColony(
                state.hiveColony().withBioformLifecycles(lifecycles).startMobilization(mobilization)));
    }

    public static FrontierWorldState reduceReleaseStarted(FrontierWorldState state, SubjectId subject, HiveMobilizationReleaseStarted started) {
        HiveMobilization mobilization = requireMobilization(state, subject, started.mobilizationId());
        if (mobilization.status() != HiveMobilizationStatus.WAKING) {
            throw new IllegalArgumentException("only a selected waking group may begin cocoon release");
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().hiveColony(state.hiveColony().startMobilizationRelease(mobilization.id())));
    }

    public static FrontierWorldState reduceCocoonReleased(FrontierWorldState state, SubjectId subject, HiveMobilizationCocoonReleased released) {
        HiveMobilization mobilization = requireMobilization(state, subject, released.mobilizationId());
        if (!mobilization.releasingMemberId().equals(Optional.of(released.bioformId()))) {
            throw new IllegalArgumentException("cocoon release does not match the one durable in-flight bioform");
        }
        BioformLifecycle lifecycle = state.hiveColony().bioformLifecycles().get(released.bioformId());
        if (lifecycle == null || lifecycle.phase() != BioformLifecyclePhase.WAKING) {
            throw new IllegalArgumentException("cocoon release requires the same waking bioform lifecycle");
        }
        HiveOrgan hibernaculum = hibernaculum(state, lifecycle.homeSlot().orElseThrow());
        ActorLocation actor = state.actorLocations().get(released.bioformId());
        BodyPosition expected = BodyPosition.above(new io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor(
                HiveCocoonPlan.cocoonCell(hibernaculum, lifecycle.homeSlot().orElseThrow())));
        if (actor == null || !actor.body().equals(expected)) {
            throw new IllegalArgumentException("cocoon release may not move an already relocated exact body");
        }
        Optional<HiveTaskAssembly> completedAssembly = mobilization.releasedMemberIds().size() + 1 == mobilization.memberIds().size()
                ? Optional.of(HiveAssemblyCorridor.compile(state, mobilization,
                HiveAssemblyPortPlan.compile(state.bootstrap(), state.hiveColony(), mobilization))) : Optional.empty();
        Map<SubjectId, BioformLifecycle> lifecycles = new LinkedHashMap<>(state.hiveColony().bioformLifecycles());
        lifecycles.put(released.bioformId(), lifecycle.assembling());
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(released.bioformId(), actor.withBody(BodyPosition.above(HiveCocoonPlan.wakingSurface(hibernaculum, lifecycle.homeSlot().orElseThrow()))));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).hiveColony(
                state.hiveColony().withBioformLifecycles(lifecycles).confirmMobilizationRelease(mobilization.id(), released.bioformId(), completedAssembly)));
    }

    /** One durable COLD clock for a retained assembly; it is not a new strategic decision. */
    public static ScheduledAction assemblyProgress(SubjectId mobilizationId, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:hive-mobilization-assembly-" + mobilizationId.value().replace(':', '-')),
                new SimInstant(dueAt), 0, mobilizationId, "frontier.hive.mobilization.assembly_progress", 1);
    }

    /** Advances only one stored edge after every exact member has returned from HOT custody. */
    public static List<ProposedEvent> planAssemblyProgress(FrontierWorldState state, ScheduledAction action) {
        HiveMobilization mobilization = state.hiveColony().mobilizations().get(action.subject());
        if (mobilization == null || mobilization.status() != HiveMobilizationStatus.ASSEMBLING
                || !assemblyProgress(mobilization.id(), action.dueAt().ticks()).id().equals(action.id())) return List.of();
        HiveTaskAssembly assembly = mobilization.assembly().orElseThrow();
        if (assembly.complete()) return List.of();
        long nextDue = Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().migrationStepInterval());
        ProposedEvent retry = new ProposedEvent(SYSTEM, new ScheduleEffect.Created(assemblyProgress(mobilization.id(), nextDue)));
        if (mobilization.memberIds().stream().map(state.ambientLeases()::get)
                .anyMatch(lease -> lease != null && lease.status() != io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED)) return List.of(retry);
        SubjectId advancing = assembly.safeAdvances().stream().findFirst().orElse(null);
        if (advancing == null) return List.of(retry);
        HiveTaskAssembly next = assembly.advance(advancing);
        ProposedEvent advanced = new ProposedEvent(mobilization.hiveId(), new HiveMobilizationAssemblyAdvanced(mobilization.id(), advancing,
                assembly.members().get(advancing).cursor()));
        return next.complete() ? List.of(advanced) : List.of(advanced, retry);
    }

    /** Reducer validation preserves the same topology, exact body and one-step cursor relation. */
    public static FrontierWorldState reduceAssemblyAdvanced(FrontierWorldState state, SubjectId subject, HiveMobilizationAssemblyAdvanced advanced) {
        HiveMobilization mobilization = requireMobilization(state, subject, advanced.mobilizationId());
        if (mobilization.status() != HiveMobilizationStatus.ASSEMBLING || mobilization.memberIds().stream().map(state.ambientLeases()::get)
                .anyMatch(lease -> lease != null && lease.status() != io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED)) {
            throw new IllegalArgumentException("hive assembly cursor may not advance while its exact group is HOT or inactive");
        }
        HiveTaskAssembly assembly = mobilization.assembly().orElseThrow();
        HiveTaskAssembly.Member member = assembly.members().get(advanced.bioformId());
        ActorLocation actor = state.actorLocations().get(advanced.bioformId());
        if (member == null || member.cursor() != advanced.expectedCursor() || actor == null || !actor.supportingSurface().equals(member.currentSurface())) {
            throw new IllegalArgumentException("hive assembly body no longer matches its retained cursor");
        }
        HiveTaskAssembly next = assembly.advance(advanced.bioformId());
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(advanced.bioformId(), actor.withBody(BodyPosition.above(next.members().get(advanced.bioformId()).currentSurface())));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).hiveColony(
                state.hiveColony().advanceMobilizationAssembly(mobilization.id(), advanced.bioformId())));
    }

    public static FrontierWorldState reduceConflicted(FrontierWorldState state, SubjectId subject, HiveMobilizationConflicted conflicted) {
        HiveMobilization mobilization = requireMobilization(state, subject, conflicted.mobilizationId());
        if (mobilization.status() != HiveMobilizationStatus.WAKING && mobilization.status() != HiveMobilizationStatus.RELEASING) {
            throw new IllegalArgumentException("only an unconfirmed cocoon release may become a mobilization conflict");
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().hiveColony(
                state.hiveColony().conflictMobilization(mobilization.id(), conflicted.reason())));
    }

    private static Optional<HiveMobilization> selectNest(FrontierWorldState state, StrategicTask task,
                                                           HiveSettlementKnowledge.Sighting sighting, HiveNest nest, long now) {
        List<Bioform> dormant = allBioforms(state).filter(bioform -> bioform.nestId().equals(nest.id()))
                .filter(bioform -> state.hiveColony().bioformLifecycles().get(bioform.id()).phase() == BioformLifecyclePhase.DORMANT)
                .sorted(Comparator.comparingLong((Bioform bioform) -> distanceSquared(state.actorLocations().get(bioform.id()).supportingSurface().support(),
                        sighting.settlementAnchor())).thenComparing(Bioform::id)).toList();
        Bioform bomber = dormant.stream().filter(Bioform::isExplosiveAssaulter).findFirst().orElse(null);
        if (bomber == null) return Optional.empty();
        Bioform overseer = dormant.stream().filter(Bioform::isOverseer).findFirst().orElse(null);
        if (overseer == null) return Optional.empty();
        List<Bioform> defenders = dormant.stream().filter(Bioform::isDefender).filter(value -> !value.id().equals(bomber.id())).limit(2).toList();
        if (defenders.size() != 2) return Optional.empty();
        // Preserve the established visible breach order. Controller ownership is explicit,
        // not an implication of list position; its cocoon still opens before the group is HOT.
        List<SubjectId> members = new ArrayList<>(); members.add(bomber.id()); defenders.forEach(value -> members.add(value.id())); members.add(overseer.id());
        if (!HiveCommandCapacity.admits(state.bootstrap().ruleset(), overseer.id(), members,
                allBioforms(state).collect(Collectors.toUnmodifiableMap(Bioform::id, value -> value)))) return Optional.empty();
        String suffix = task.id().value().substring("task:".length());
        return Optional.of(new HiveMobilization(new SubjectId("mobilization:" + suffix), state.bootstrap().hive().id(), nest.id(), task.id(),
                sighting.settlementId(), overseer.id(), members, HiveMobilizationStatus.WAKING, now));
    }

    private static Stream<Bioform> allBioforms(FrontierWorldState state) {
        return Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream());
    }

    private static void requireCommandCapacity(FrontierWorldState state, HiveMobilization mobilization) {
        Map<SubjectId, Bioform> bioforms = allBioforms(state).collect(Collectors.toUnmodifiableMap(Bioform::id, value -> value));
        if (!HiveCommandCapacity.admits(state.bootstrap().ruleset(), mobilization.overseerId(), mobilization.memberIds(), bioforms)) {
            throw new IllegalArgumentException("hive mobilization exceeds its exact Overseer command capacity");
        }
    }

    private static void requireTask(FrontierWorldState state, SubjectId subject, HiveMobilization mobilization) {
        StrategicTask task = state.strategicPlans().tasks().get(mobilization.taskId());
        if (!subject.equals(state.bootstrap().hive().id()) || !mobilization.hiveId().equals(subject) || task == null
                || task.kind() != StrategicTaskKind.ASSAULT_SETTLEMENT || task.status() != StrategicTaskStatus.ACTIVE
                || !task.ownerId().equals(subject) || state.hiveColony().mobilizations().containsKey(mobilization.id())) {
            throw new IllegalArgumentException("hive mobilization must retain one active exact hive assault task");
        }
    }

    private static HiveMobilization requireMobilization(FrontierWorldState state, SubjectId subject, SubjectId id) {
        HiveMobilization mobilization = state.hiveColony().mobilizations().get(id);
        if (mobilization == null || !subject.equals(state.bootstrap().hive().id()) || !mobilization.hiveId().equals(subject)) {
            throw new IllegalArgumentException("unknown or foreign hive mobilization");
        }
        return mobilization;
    }

    private static HiveOrgan hibernaculum(FrontierWorldState state, HiveCocoonSlot slot) {
        return Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .filter(organ -> organ.id().equals(slot.hibernaculumId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("hive mobilization references an absent hibernaculum"));
    }

    private static long distanceSquared(io.farfrontier.palemirror.frontier.v3.model.BlockPosition from,
                                        io.farfrontier.palemirror.frontier.v3.model.BlockPosition to) {
        long dx = Math.subtractExact(from.x(), to.x()), dy = Math.subtractExact(from.y(), to.y()), dz = Math.subtractExact(from.z(), to.z());
        return Math.addExact(Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dy, dy)), Math.multiplyExact(dz, dz));
    }
}
