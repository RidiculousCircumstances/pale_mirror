package io.farfrontier.palemirror.internal.effect;

import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;

/**
 * Transaction boundary for short, non-replayable physical effects such as a
 * direct hit or status application. Persistent projectiles will later attach
 * their native UUID to the same lease before becoming active.
 */
public final class ControlledEffectExecutor {
    private ControlledEffectExecutor() { }

    public static boolean executeOnce(PaleMirrorSavedData data, EffectLease proposal, long gameTick, Runnable action) {
        EffectLease lease = data.effectLeases().plan(proposal);
        data.setDirty(); // Persist authorization before touching Minecraft state.
        if (!data.effectLeases().begin(lease.id())) return false;
        data.setDirty(); // A crash from this point must not replay the action.
        try {
            action.run();
            data.effectLeases().complete(lease.id(), gameTick);
            data.setDirty();
            return true;
        } catch (RuntimeException failure) {
            data.effectLeases().fail(lease.id(), gameTick, failure.getClass().getSimpleName());
            data.setDirty();
            throw failure;
        }
    }
}
