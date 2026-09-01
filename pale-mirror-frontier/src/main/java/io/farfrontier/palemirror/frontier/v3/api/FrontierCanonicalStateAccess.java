package io.farfrontier.palemirror.frontier.v3.api;

/**
 * Narrow owning-thread view of the engine's current immutable canonical state.
 *
 * <p>It exists for a physical adapter that must inspect current state and create a command at
 * the same revision. It never grants mutation outside {@link FrontierEngine#submit(FrontierCommand)}
 * and is not a persistence/export boundary; callers needing durable bytes must use
 * {@link FrontierEngine#checkpoint()} explicitly.</p>
 */
public interface FrontierCanonicalStateAccess<S, P extends FrontierProjection> extends FrontierEngine<P> {
    FrontierCanonicalState<S> canonicalState();
}
