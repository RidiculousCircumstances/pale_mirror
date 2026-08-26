package io.farfrontier.palemirror.internal.effect;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Transaction boundary for short, non-replayable physical effects such as a
 * direct hit or status application. Persistent projectiles will later attach
 * their native UUID to the same lease before becoming active.
 */
public final class ControlledEffectExecutor {
    private ControlledEffectExecutor() { }

    public static boolean executeOnce(EffectLeaseStore data, EffectLease proposal, long gameTick, Runnable action) {
        return executeOnceWithReceipt(data, proposal, gameTick, () -> {
            action.run();
            return "";
        });
    }

    /** Executes once and records the caller's exact, bounded post-impact receipt. */
    public static boolean executeOnceWithReceipt(EffectLeaseStore data, EffectLease proposal, long gameTick,
                                                 Supplier<String> action) {
        return executeOnceWithReceipt(data, proposal, gameTick, null, action);
    }

    /** Binds the inspected native target before the non-replayable action starts. */
    public static boolean executeOnceWithReceipt(EffectLeaseStore data, EffectLease proposal, long gameTick,
                                                 UUID nativeReference, Supplier<String> action) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(action, "action");
        EffectLease lease = data.effectLeases().plan(proposal);
        if (nativeReference != null && !data.effectLeases().attachNativeReference(lease.id(), nativeReference)) return false;
        data.markEffectLeaseDirty(); // Persist authorization before touching Minecraft state.
        if (!data.effectLeases().begin(lease.id())) return false;
        data.markEffectLeaseDirty(); // A crash from this point must not replay the action.
        try {
            data.effectLeases().complete(lease.id(), gameTick, action.get());
            data.markEffectLeaseDirty();
            return true;
        } catch (RuntimeException failure) {
            data.effectLeases().fail(lease.id(), gameTick, failure.getClass().getSimpleName());
            data.markEffectLeaseDirty();
            throw failure;
        }
    }
}
