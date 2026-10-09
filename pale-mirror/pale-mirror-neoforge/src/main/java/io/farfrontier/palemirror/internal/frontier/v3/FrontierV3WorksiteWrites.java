package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.WorksiteBlock;
import java.util.function.BooleanSupplier;

/** Exact ephemeral attribution for an owner's write, not a bypass for any neighbouring cell. */
final class FrontierV3WorksiteWrites {
    private static final ThreadLocal<WorksiteBlock.Key> CURRENT = new ThreadLocal<>();
    private FrontierV3WorksiteWrites() { }
    static boolean owns(WorksiteBlock.Key key) { return key.equals(CURRENT.get()); }
    static boolean apply(WorksiteBlock.Key key, BooleanSupplier effect) {
        if (CURRENT.get() != null) throw new IllegalStateException("nested worksite physical actuator");
        CURRENT.set(key);
        try { return effect.getAsBoolean(); } finally { CURRENT.remove(); }
    }
}
