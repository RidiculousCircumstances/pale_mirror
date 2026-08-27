package io.farfrontier.palemirror.internal.world;

/** Keeps source-owned blasts out of the external-explosion observation path. */
final class SourceGrayboxExplosionObservation {
    private static final ThreadLocal<Integer> SOURCE_EFFECT_DEPTH = ThreadLocal.withInitial(() -> 0);

    private SourceGrayboxExplosionObservation() { }

    static void runSourceEffect(Runnable effect) {
        SOURCE_EFFECT_DEPTH.set(SOURCE_EFFECT_DEPTH.get() + 1);
        try {
            effect.run();
        } finally {
            int depth = SOURCE_EFFECT_DEPTH.get() - 1;
            if (depth == 0) SOURCE_EFFECT_DEPTH.remove();
            else SOURCE_EFFECT_DEPTH.set(depth);
        }
    }

    static boolean isSourceEffect() {
        return SOURCE_EFFECT_DEPTH.get() > 0;
    }
}
