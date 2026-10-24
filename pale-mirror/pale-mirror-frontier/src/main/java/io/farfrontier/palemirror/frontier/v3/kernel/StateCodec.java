package io.farfrontier.palemirror.frontier.v3.kernel;

/** Pure canonical-state encoder used for deterministic checkpoints and test hashes. */
@FunctionalInterface
public interface StateCodec<S> {
    byte[] encode(S state);

    /**
     * Stateful profiles opt into recovery by overriding this operation. Wave 1 encoder-only
     * fixtures remain valid, but a lifecycle must fail closed instead of inventing state when a
     * checkpoint asks an encoder-only codec to recover it.
     */
    default S decode(byte[] encoded) {
        throw new IllegalStateException("this Frontier state codec does not support checkpoint recovery");
    }
}
