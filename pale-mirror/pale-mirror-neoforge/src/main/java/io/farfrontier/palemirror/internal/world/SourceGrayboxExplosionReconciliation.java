package io.farfrontier.palemirror.internal.world;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Reconciles durable external-explosion candidates only after Minecraft has changed the blocks. */
final class SourceGrayboxExplosionReconciliation {
    private SourceGrayboxExplosionReconciliation() { }

    static boolean reconcile(SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer, ServerLevel level) {
        List<SourceGrayboxExplosionObservationLedger.Pending> pending = data.pendingExplosions().drainThrough(level.getGameTime());
        if (pending.isEmpty()) return false;
        boolean handled = false;
        for (SourceGrayboxExplosionObservationLedger.Pending explosion : pending) {
            for (long encoded : explosion.positions()) {
                BlockPos position = BlockPos.of(encoded);
                if (!SourceGrayboxPalette.managed(level.getBlockState(position).getBlock())) {
                    handled |= SourceGrayboxBlockObservation.observe(data, materializer, level, position, explosion.id());
                }
            }
        }
        data.markPendingExplosionsDirty();
        return handled;
    }
}
