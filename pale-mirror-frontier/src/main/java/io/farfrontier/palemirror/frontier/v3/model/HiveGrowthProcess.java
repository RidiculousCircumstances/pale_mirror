package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Executes exact-biomass growth only as the durable task of the one hive economy. */
final class HiveGrowthProcess {
    private static final String BIOMASS = "minecraft:rotten_flesh";
    private HiveGrowthProcess() { }

    static ScheduledAction start(StrategicTask task, long dueAt) {
        if (task.kind() != StrategicTaskKind.GROW_HIVE_ORGANISM) throw new IllegalArgumentException("invalid hive growth task schedule");
        return new ScheduledAction(new ScheduleId("schedule:hive-growth-task-start-" + task.id().value().replace(':', '-')), new SimInstant(dueAt), 0,
                task.id(), "frontier.hive.growth.task.start", 1);
    }

    static List<ProposedEvent> planStart(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = task(state, action.subject(), StrategicTaskStatus.PENDING);
        SubjectId hive = state.bootstrap().hive().id(); HiveNest nest = state.bootstrap().hive().seedNests().getFirst();
        if (!hive.equals(task.ownerId())) throw new IllegalStateException("hive growth task has a foreign owner");
        boolean capacity = state.hiveColony().growthJobs().isEmpty() && state.hiveColony().addedOrgans().size() < HiveColony.MAX_ADDED_ORGANS
                && state.hiveColony().spawnedBioforms().size() < HiveColony.MAX_SPAWNED_BIOFORMS;
        Optional<ExactItemStack> biomass = state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id))
                .filter(item -> BIOMASS.equals(item.itemKind()) && item.custody() instanceof InventoryCustody.ContainerSlot slot && state.isHiveStore(slot.containerId())).findFirst();
        if (!capacity || biomass.isEmpty()) return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        int ordinal = state.strategicPlans().objectives().get(task.objectiveId()).decisionOrdinal();
        HiveGrowthJob job = growthJob(hive, nest, biomass.orElseThrow(), ordinal);
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(hive, new HiveGrowthStarted(job)),
                schedule(complete(job, action.dueAt().ticks() + 200L)));
    }

    static List<ProposedEvent> planCompletion(FrontierWorldState state, ScheduledAction action) {
        HiveGrowthJob job = state.hiveColony().growthJobs().get(action.subject());
        if (job == null) throw new IllegalStateException("hive growth completion has no active job: " + action.subject().value());
        StrategicTask task = activeTask(state, job.hiveId());
        return List.of(new ProposedEvent(job.hiveId(), new HiveGrowthCompleted(job.id())), transition(task, StrategicTaskStatus.COMPLETED));
    }

    static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, HiveGrowthStarted started) {
        HiveGrowthJob job = started.job(); ExactItemStack input = state.inventory().items().get(job.consumedItemId());
        if (!subject.equals(job.hiveId()) || input == null || !BIOMASS.equals(input.itemKind()) || input.count() != 64) throw new IllegalArgumentException("hive growth start lacks exact biomass");
        activeTask(state, job.hiveId()); return state.startHiveGrowth(job);
    }

    static FrontierWorldState reduceCompleted(FrontierWorldState state, SubjectId subject, HiveGrowthCompleted completed) {
        HiveGrowthJob job = state.hiveColony().growthJobs().get(completed.jobId());
        if (job == null || !subject.equals(job.hiveId())) throw new IllegalArgumentException("hive growth completion lacks its owning hive");
        activeTask(state, job.hiveId()); return state.completeHiveGrowth(completed.jobId());
    }

    static FrontierWorldState reduceBlocked(FrontierWorldState state, SubjectId subject, HiveGrowthBlocked blocked) {
        if (!subject.equals(blocked.hiveId()) || !blocked.hiveId().equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive growth block lacks its owning colony");
        return state;
    }

    private static StrategicTask task(FrontierWorldState state, SubjectId id, StrategicTaskStatus status) {
        StrategicTask task = state.strategicPlans().tasks().get(id);
        if (task == null || task.kind() != StrategicTaskKind.GROW_HIVE_ORGANISM || task.status() != status) {
            throw new IllegalStateException("hive growth schedule has no matching strategic task");
        }
        return task;
    }

    private static StrategicTask activeTask(FrontierWorldState state, SubjectId hive) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.ownerId().equals(hive)
                && task.kind() == StrategicTaskKind.GROW_HIVE_ORGANISM && task.status() == StrategicTaskStatus.ACTIVE)
                .reduce((left, right) -> { throw new IllegalArgumentException("hive growth task binding is ambiguous"); })
                .orElseThrow(() -> new IllegalArgumentException("hive growth has no active strategic task"));
    }

    private static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) { return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status)); }
    private static ScheduledAction complete(HiveGrowthJob job, long due) {
        return new ScheduledAction(new ScheduleId("schedule:hive-growth-task-complete-" + job.id().value().substring("job:".length())), new SimInstant(due), 0,
                job.id(), "frontier.hive.growth.task.complete", 1);
    }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    private static HiveGrowthJob growthJob(SubjectId hive, HiveNest nest, ExactItemStack input, int ordinal) {
        int column = (ordinal - 1) % 8; int row = (ordinal - 1) / 8; int x = nest.anchor().x() + 12 + column * 8; int z = nest.anchor().z() + 12 + row * 8;
        return new HiveGrowthJob(new SubjectId("job:hive-growth-" + ordinal), hive, nest.id(), input.id(),
                new HiveOrgan(new SubjectId("organ:west-grown-heart-" + ordinal), hive, nest.id(), HiveOrganKind.HEART, new BlockPosition(x, nest.anchor().y(), z), Optional.empty()),
                new Bioform(new SubjectId("bioform:west-grown-" + ordinal), hive, nest.id(), BioformRole.GUARD, new BlockPosition(x + 4, nest.anchor().y(), z)));
    }
}
