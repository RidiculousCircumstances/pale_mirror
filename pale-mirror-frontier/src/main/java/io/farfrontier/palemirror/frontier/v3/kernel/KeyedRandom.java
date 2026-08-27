package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;

/** Stateless counter/keyed deterministic random source. */
public final class KeyedRandom {
    private KeyedRandom() {
    }

    public static long nextLong(DecisionKey key) {
        long state = mix(key.worldSeed());
        state = mix(state ^ hash(key.subsystem()));
        state = mix(state ^ hash(key.subject().value()));
        state = mix(state ^ hash(key.decisionKind()));
        return mix(state ^ key.ordinal());
    }

    public static int nextInt(DecisionKey key, int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        return (int) Long.remainderUnsigned(nextLong(key), Integer.toUnsignedLong(bound));
    }

    public static FixedRatio nextRatio(DecisionKey key) {
        long raw = Long.remainderUnsigned(nextLong(key), FixedScalar.SCALE + 1L);
        return new FixedRatio(new FixedScalar(raw));
    }

    private static long hash(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix(long value) {
        long mixed = value + 0x9e3779b97f4a7c15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
