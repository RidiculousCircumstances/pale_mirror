package io.farfrontier.palemirror.frontier.v3.kernel;

/** Pure canonical-state encoder used for deterministic checkpoints and test hashes. */
@FunctionalInterface
public interface StateCodec<S> {
    byte[] encode(S state);
}
