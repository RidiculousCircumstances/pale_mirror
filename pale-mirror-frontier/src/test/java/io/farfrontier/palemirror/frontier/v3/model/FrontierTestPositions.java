package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Explicit coordinate conversions used by legacy-oriented fixtures.
 *
 * <p>Test data historically described an actor by the supporting block below
 * its feet.  Production code deliberately no longer accepts that ambiguous
 * value, so fixtures must state the conversion at their boundary.</p>
 */
final class FrontierTestPositions {
    private FrontierTestPositions() {
    }

    static BodyPosition bodyAboveSupport(BlockPosition support) {
        return BodyPosition.above(new SurfaceAnchor(support));
    }

    static BlockPosition supportOf(ActorLocation location) {
        return location.supportingSurface().support();
    }

    static BodyPosition bodyCellOf(ActorLocation location) {
        return location.body();
    }

    /** Explicit test-only external mobilisation; production must use its lifecycle event flow. */
    static FrontierWorldState deployBioform(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId bioformId,
                                            BodyPosition body) {
        BioformLifecycle lifecycle = state.hiveColony().bioformLifecycles().get(bioformId);
        if (lifecycle == null) throw new IllegalArgumentException("test deployment requires canonical bioform: " + bioformId.value());
        if (lifecycle.phase().occupiesCocoon()) {
            java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, BioformLifecycle> next =
                    new java.util.LinkedHashMap<>(state.hiveColony().bioformLifecycles());
            next.put(bioformId, lifecycle.waking().active());
            state = state.withChanges(FrontierWorldStateUpdate.begin().hiveColony(state.hiveColony().withBioformLifecycles(next)));
        }
        return state.withActorBody(bioformId, body);
    }
}
