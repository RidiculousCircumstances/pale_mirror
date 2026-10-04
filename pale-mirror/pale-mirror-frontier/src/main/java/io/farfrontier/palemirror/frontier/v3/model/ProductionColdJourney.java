package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Optional;

/** Same unfinished station in COLD; rejoining never credits work performed elsewhere. */
public final class ProductionColdJourney {
    public record Step(int cursor, ProductionWorkProgress progress, StationApproachState spatial, BodyPosition body) { }
    private ProductionColdJourney() { }
    public static Optional<Step> next(FrontierWorldState state, ProductionJob job) {
        if (job.bakeryWork().isPresent()) throw new IllegalArgumentException("bakery does not use production station cursors");
        var route = job.workTraversal().linearCorridorSurfaces();
        var actor = state.actorLocations().get(job.workerId());
        var spatial = job.spatial();
        if (actor == null || !actor.supportingSurface().equals(spatial.current(route.get(job.traversalCursor()))))
            throw new IllegalArgumentException("production COLD body has no matching departure checkpoint");
        if (spatial.waitingOrigin().isPresent()) {
            spatial = ProductionJourneyKnowledge.checkpoint(state, job, spatial.waitingOrigin().orElseThrow());
            if (spatial.waitingOrigin().isPresent()) return Optional.empty();
        }
        var knowledge = ProductionJourneyKnowledge.view(state, job);
        if (spatial.approach().isPresent()) {
            var approach = spatial.approach().orElseThrow();
            var advanced = approach.arrived() ? approach : approach.advance(approach.nextCursor(1), 1);
            if (!knowledge.traversable(approach.path().subList(approach.cursor(), advanced.cursor() + 1)))
                return Optional.empty();
            if (!advanced.arrived()) return Optional.of(new Step(job.traversalCursor(), job.workProgress(),
                    new StationApproachState(Math.incrementExact(spatial.revision()), Optional.of(advanced), Optional.empty()),
                    advanced.current().standingBody()));
            spatial = spatial.cleared();
            // Returning to the current work station is travel only; no processing credit yet.
            if (ProductionJourneyKnowledge.target(job).equals(route.get(job.traversalCursor())))
                return Optional.of(new Step(job.traversalCursor(), job.workProgress(), spatial, advanced.target().standingBody()));
        }
        int last = route.size() - 1;
        boolean input = job.workProgress().stage() == ProductionWorkProgress.Stage.APPROACH && job.traversalCursor() == last - 1;
        int cursor = input ? job.traversalCursor() : Math.min(job.traversalCursor() + 1, last);
        if (!knowledge.traversable(List.of(actor.supportingSurface(), route.get(cursor)))) return Optional.empty();
        var progress = input ? ProductionWorkProgress.inputReady() : cursor > job.traversalCursor()
                ? job.workProgress() : nextProgress(job.workProgress());
        return Optional.of(new Step(cursor, progress, spatial.cleared(), route.get(cursor).standingBody()));
    }
    private static ProductionWorkProgress nextProgress(ProductionWorkProgress progress) {
        return switch (progress.stage()) {
            case APPROACH -> ProductionWorkProgress.inputReady();
            case INPUT_READY -> ProductionWorkProgress.processing(0);
            case PROCESSING -> progress.completedTicks() + 1 == ProductionWorkProgress.REQUIRED_PROCESSING_TICKS
                    ? ProductionWorkProgress.outputReady() : ProductionWorkProgress.processing(progress.completedTicks() + 1);
            case OUTPUT_READY -> throw new IllegalArgumentException("production COLD work is already terminal");
        };
    }
}
