package io.farfrontier.palemirror.frontier.v3.model;

/** Follow the actual retained work deadline in isolated fixtures, never award fixture labour. */
final class BakeryTestClock {
    private BakeryTestClock() { }
    static long due(FrontierWorldState state, ProductionJob job, long proposed) {
        var retained = state.productionJobs().get(job.id());
        return retained == null ? proposed : BakeryLabour.progress(state, retained)
                .filter(WorkProgress::running).map(work -> Math.max(proposed, work.activeUntilTick()))
                .orElse(proposed);
    }
}
