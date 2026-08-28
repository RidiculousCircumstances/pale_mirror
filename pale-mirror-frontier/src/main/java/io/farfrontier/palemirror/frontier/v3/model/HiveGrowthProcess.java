package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Event-driven exact-resource growth for the two-nest shared hive economy. */
final class HiveGrowthProcess {
    private static final String BIOMASS = "minecraft:rotten_flesh";
    private HiveGrowthProcess() { }

    static List<ProposedEvent> planStart(FrontierWorldState state, ScheduledAction action) {
        int ordinal = ordinal(action.id().value()); SubjectId hive = state.bootstrap().hive().id(); HiveNest nest = state.bootstrap().hive().seedNests().getFirst();
        if (!hive.equals(action.subject())) throw new IllegalStateException("hive growth start has a foreign subject");
        HiveGrowthBlockReason blocked = state.hiveColony().growthJobs().isEmpty() && state.hiveColony().addedOrgans().size() < HiveColony.MAX_ADDED_ORGANS
                && state.hiveColony().spawnedBioforms().size() < HiveColony.MAX_SPAWNED_BIOFORMS ? null : HiveGrowthBlockReason.GROWTH_CAPACITY_UNAVAILABLE;
        Optional<ExactItemStack> biomass = state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id))
                .filter(item -> BIOMASS.equals(item.itemKind()) && item.custody() instanceof InventoryCustody.ContainerSlot slot && state.isHiveStore(slot.containerId())).findFirst();
        if (blocked != null || biomass.isEmpty()) {
            HiveGrowthBlockReason reason = blocked == null ? HiveGrowthBlockReason.BIOMASS_UNAVAILABLE : blocked;
            return List.of(new ProposedEvent(hive, new HiveGrowthBlocked(hive, nest.id(), work(ordinal), reason)), schedule(start(ordinal + 1, action.dueAt().ticks() + 200L)));
        }
        HiveGrowthJob job = growthJob(hive, nest, biomass.orElseThrow(), ordinal);
        return List.of(new ProposedEvent(hive, new HiveGrowthStarted(job)), schedule(complete(job, action.dueAt().ticks() + 200L)));
    }

    static List<ProposedEvent> planCompletion(FrontierWorldState state, ScheduledAction action) {
        HiveGrowthJob job = state.hiveColony().growthJobs().get(action.subject());
        if (job == null) throw new IllegalStateException("hive growth completion has no active job: " + action.subject().value());
        return List.of(new ProposedEvent(job.hiveId(), new HiveGrowthCompleted(job.id())), schedule(start(ordinal(job.id().value()) + 1, action.dueAt().ticks() + 200L)));
    }

    static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, HiveGrowthStarted started) {
        HiveGrowthJob job = started.job(); ExactItemStack input = state.inventory().items().get(job.consumedItemId());
        if (!subject.equals(job.hiveId()) || input == null || !BIOMASS.equals(input.itemKind()) || input.count() != 64) throw new IllegalArgumentException("hive growth start lacks exact biomass");
        return state.startHiveGrowth(job);
    }

    static FrontierWorldState reduceCompleted(FrontierWorldState state, SubjectId subject, HiveGrowthCompleted completed) {
        HiveGrowthJob job = state.hiveColony().growthJobs().get(completed.jobId());
        if (job == null || !subject.equals(job.hiveId())) throw new IllegalArgumentException("hive growth completion lacks its owning hive");
        return state.completeHiveGrowth(completed.jobId());
    }

    static FrontierWorldState reduceBlocked(FrontierWorldState state, SubjectId subject, HiveGrowthBlocked blocked) {
        if (!subject.equals(blocked.hiveId()) || !blocked.hiveId().equals(state.bootstrap().hive().id())
                || state.bootstrap().hive().seedNests().stream().noneMatch(nest -> nest.id().equals(blocked.nestId()))) throw new IllegalArgumentException("hive growth block lacks its owning colony");
        boolean biomassPresent = state.inventory().items().values().stream().anyMatch(item -> BIOMASS.equals(item.itemKind())
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && state.isHiveStore(slot.containerId()));
        if (blocked.reason() == HiveGrowthBlockReason.BIOMASS_UNAVAILABLE && biomassPresent) throw new IllegalArgumentException("hive biomass block precondition does not hold");
        if (blocked.reason() == HiveGrowthBlockReason.GROWTH_CAPACITY_UNAVAILABLE && state.hiveColony().growthJobs().isEmpty()
                && state.hiveColony().addedOrgans().size() < HiveColony.MAX_ADDED_ORGANS && state.hiveColony().spawnedBioforms().size() < HiveColony.MAX_SPAWNED_BIOFORMS) {
            throw new IllegalArgumentException("hive capacity block precondition does not hold");
        }
        return state;
    }

    static ScheduledAction start(int ordinal, long due) {
        return new ScheduledAction(new ScheduleId("schedule:hive-growth-start-" + ordinal), new SimInstant(due), 0,
                new SubjectId("hive:frontier"), "frontier.hive.growth.start", 1);
    }

    private static ScheduledAction complete(HiveGrowthJob job, long due) {
        return new ScheduledAction(new ScheduleId("schedule:hive-growth-complete-" + job.id().value().substring("job:".length())), new SimInstant(due), 0,
                job.id(), "frontier.hive.growth.complete", 1);
    }

    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }

    private static SubjectId work(int ordinal) { return new SubjectId("work:hive-growth-" + ordinal); }

    private static HiveGrowthJob growthJob(SubjectId hive, HiveNest nest, ExactItemStack input, int ordinal) {
        int column = (ordinal - 1) % 8; int row = (ordinal - 1) / 8; int x = nest.anchor().x() + 12 + column * 8; int z = nest.anchor().z() + 12 + row * 8;
        return new HiveGrowthJob(new SubjectId("job:hive-growth-" + ordinal), hive, nest.id(), input.id(),
                new HiveOrgan(new SubjectId("organ:west-grown-heart-" + ordinal), hive, nest.id(), HiveOrganKind.HEART, new BlockPosition(x, nest.anchor().y(), z), Optional.empty()),
                new Bioform(new SubjectId("bioform:west-grown-" + ordinal), hive, nest.id(), BioformRole.GUARD, new BlockPosition(x + 4, nest.anchor().y(), z)));
    }

    private static int ordinal(String id) {
        int separator = id.lastIndexOf('-');
        if (separator < 0 || separator == id.length() - 1) throw new IllegalArgumentException("hive growth identity lacks ordinal: " + id);
        try { int value = Integer.parseInt(id.substring(separator + 1)); if (value <= 0) throw new IllegalArgumentException("hive growth ordinal must be positive: " + id); return value; }
        catch (NumberFormatException error) { throw new IllegalArgumentException("hive growth identity has malformed ordinal: " + id, error); }
    }
}
