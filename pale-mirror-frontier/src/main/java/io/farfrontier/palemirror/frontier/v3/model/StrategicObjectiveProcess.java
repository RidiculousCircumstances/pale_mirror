package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Event-triggered deterministic utility selection and first durable task expansion. */
final class StrategicObjectiveProcess {
    private static final long REVIEW_INTERVAL = 400L;
    private static final long LOCAL_INFECTION_RADIUS_SQUARED = 25_600L;
    private StrategicObjectiveProcess() { }

    static ScheduledAction review(SubjectId owner, int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:objective-review-" + owner.value().replace(':', '-') + "-" + ordinal),
                new SimInstant(dueAt), 0, owner, "frontier.objective.review", 1);
    }

    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        SubjectId owner = action.subject(); int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        requireKnownOwner(state.bootstrap(), owner);
        ProposedEvent next = new ProposedEvent(owner, new ScheduleEffect.Created(review(owner, ordinal + 1, action.dueAt().ticks() + REVIEW_INTERVAL)));
        if (state.strategicPlans().hasActiveObjective(owner)) return List.of(next);
        Optional<Candidate> candidate = candidate(state, owner);
        if (candidate.isEmpty()) return List.of(next);
        Candidate value = candidate.orElseThrow(); StrategicObjective objective = objective(owner, value, ordinal); StrategicTask task = task(objective);
        if (task.kind() == StrategicTaskKind.SPREAD_INFECTION_CELL) {
            return List.of(new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                    new ProposedEvent(owner, new ScheduleEffect.Created(HiveInfectionProcess.task(task, 1, action.dueAt().ticks() + 100L))), next);
        }
        if (task.kind() == StrategicTaskKind.GROW_HIVE_ORGANISM) {
            return List.of(new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                    new ProposedEvent(owner, new ScheduleEffect.Created(HiveGrowthProcess.start(task, action.dueAt().ticks() + 100L))), next);
        }
        return List.of(new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)), next);
    }

    static FrontierWorldState reduceObjective(FrontierWorldState state, SubjectId subject, StrategicObjectiveSelected selected) {
        if (!subject.equals(selected.objective().ownerId())) throw new IllegalArgumentException("strategic objective has a foreign event owner");
        requireKnownOwner(state.bootstrap(), subject); return state.withStrategicPlans(state.strategicPlans().addObjective(selected.objective()));
    }

    static FrontierWorldState reduceTask(FrontierWorldState state, SubjectId subject, StrategicTaskPlanned planned) {
        StrategicTask task = planned.task(); StrategicObjective objective = state.strategicPlans().objectives().get(task.objectiveId());
        if (objective == null || !subject.equals(task.ownerId()) || !objective.ownerId().equals(subject)) throw new IllegalArgumentException("strategic task has a foreign owner or objective");
        return state.withStrategicPlans(state.strategicPlans().addTask(task));
    }

    static FrontierWorldState reduceTaskTransition(FrontierWorldState state, SubjectId subject, StrategicTaskTransition transition) {
        StrategicTask task = state.strategicPlans().tasks().get(transition.taskId());
        if (task == null || !task.ownerId().equals(subject)) throw new IllegalArgumentException("strategic task transition has a foreign owner");
        return state.withStrategicPlans(state.strategicPlans().transitionTask(task.id(), transition.status()));
    }

    private static Optional<Candidate> candidate(FrontierWorldState state, SubjectId owner) {
        return state.bootstrap().hive().id().equals(owner) ? hiveCandidate(state) : settlementCandidate(state, FrontierWorldStateSupport.settlement(state.bootstrap(), owner));
    }
    private static Optional<Candidate> settlementCandidate(FrontierWorldState state, Settlement settlement) {
        Optional<SettlementStructure> infirmary = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY)
                .filter(structure -> state.structureConditions().get(structure.id()) != StructureCondition.DESTROYED).min(Comparator.comparing(SettlementStructure::id));
        if (infirmary.isEmpty()) return Optional.empty(); SettlementStructure facility = infirmary.orElseThrow();
        return state.infection().entrySet().stream().filter(entry -> local(facility, entry.getKey())).map(entry -> new Candidate(StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION,
                Optional.of(entry.getKey()), entry.getValue().value().raw())).sorted(Candidate.HIGHEST_UTILITY).findFirst();
    }
    private static Optional<Candidate> hiveCandidate(FrontierWorldState state) {
        Optional<Candidate> growth = hiveGrowthCandidate(state); if (growth.isPresent()) return growth;
        return HiveInfectionProcess.expansionTarget(state).map(target -> new Candidate(StrategicObjectiveKind.HIVE_EXPAND_INFECTION, Optional.of(target),
                Math.subtractExact(FixedScalar.SCALE, state.infection().getOrDefault(target, new io.farfrontier.palemirror.frontier.v3.api.FixedRatio(FixedScalar.ZERO)).value().raw())));
    }
    private static Optional<Candidate> hiveGrowthCandidate(FrontierWorldState state) {
        boolean capacity = state.hiveColony().growthJobs().isEmpty() && state.hiveColony().addedOrgans().size() < HiveColony.MAX_ADDED_ORGANS
                && state.hiveColony().spawnedBioforms().size() < HiveColony.MAX_SPAWNED_BIOFORMS;
        boolean biomass = state.inventory().items().values().stream().anyMatch(item -> item.itemKind().equals("minecraft:rotten_flesh")
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && state.isHiveStore(slot.containerId()));
        return capacity && biomass ? Optional.of(new Candidate(StrategicObjectiveKind.HIVE_GROW_ORGANISM, Optional.empty(), FixedScalar.SCALE)) : Optional.empty();
    }
    private static StrategicObjective objective(SubjectId owner, Candidate candidate, int ordinal) {
        String stem = owner.value().replace(':', '-') + "-" + candidate.kind().name().toLowerCase(java.util.Locale.ROOT) + "-" + ordinal;
        return new StrategicObjective(new SubjectId("objective:" + stem), owner, candidate.kind(), candidate.target(), ordinal, StrategicObjectiveStatus.ACTIVE);
    }
    private static StrategicTask task(StrategicObjective objective) {
        List<StrategicTaskRequirement> requirements = switch (objective.kind()) {
            case SETTLEMENT_CONTAIN_LOCAL_INFECTION -> List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY, StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT);
            case HIVE_EXPAND_INFECTION -> List.of(StrategicTaskRequirement.OPERATIONAL_HEART);
            case HIVE_GROW_ORGANISM -> List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS);
        };
        StrategicTaskKind kind = switch (objective.kind()) {
            case SETTLEMENT_CONTAIN_LOCAL_INFECTION -> StrategicTaskKind.DECONTAMINATE_INFECTION_CELL;
            case HIVE_EXPAND_INFECTION -> StrategicTaskKind.SPREAD_INFECTION_CELL;
            case HIVE_GROW_ORGANISM -> StrategicTaskKind.GROW_HIVE_ORGANISM;
        };
        return new StrategicTask(new SubjectId("task:" + objective.id().value().substring("objective:".length())), objective.id(), objective.ownerId(), kind,
                objective.infectionTarget(), requirements, List.of(), StrategicTaskStatus.PENDING);
    }
    private static boolean local(SettlementStructure facility, InfectionCell cell) {
        BlockPosition position = cell.originAtY(facility.anchor().y()); long dx = position.x() - facility.anchor().x(), dz = position.z() - facility.anchor().z();
        return dx * dx + dz * dz <= LOCAL_INFECTION_RADIUS_SQUARED;
    }
    private static void requireKnownOwner(FrontierBootstrap bootstrap, SubjectId owner) {
        if (!bootstrap.hive().id().equals(owner) && bootstrap.settlements().stream().noneMatch(settlement -> settlement.id().equals(owner))) {
            throw new IllegalArgumentException("strategic review has a foreign owner");
        }
    }
    private record Candidate(StrategicObjectiveKind kind, Optional<InfectionCell> target, long utility) {
        private static final Comparator<Candidate> HIGHEST_UTILITY = Comparator.comparingLong(Candidate::utility).reversed()
                .thenComparing(Candidate::kind).thenComparing(value -> value.target().map(InfectionCell::x).orElse(Integer.MIN_VALUE))
                .thenComparing(value -> value.target().map(InfectionCell::z).orElse(Integer.MIN_VALUE));
    }
}
