package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import java.util.function.Function;

/** Closed provenance ports; the generic surface owner neither knows nor recompiles quarry geometry. */
final class FrontierV3ContainerSocketProvenance {
    interface Provider {
        FrontierV3ContainerSurfaceExecutor.SocketReadiness inspect(ServerLevel level, FrontierV3GrayboxLedger ledger,
                BlockPos position, ContainerSocketSupport declaration);
    }
    private static final Map<ContainerSocketSupport.Kind, Provider> PROVIDERS = Map.of(
            ContainerSocketSupport.Kind.GRAYBOX, FrontierV3ContainerSocketProvenance::graybox,
            ContainerSocketSupport.Kind.WORKSITE, FrontierV3ContainerSocketProvenance::worksite);
    private FrontierV3ContainerSocketProvenance() { }
    private static final Map<ContainerSocketSupport.Kind, java.util.function.BiFunction<ContainerSocketSupport, java.util.function.BooleanSupplier, Boolean>> WRITERS = Map.of(
            ContainerSocketSupport.Kind.GRAYBOX, (declaration, effect) -> {
                if (!(declaration instanceof ContainerSocketSupport.Graybox)) throw new IllegalArgumentException("forged socket schema");
                return effect.getAsBoolean();
            }, ContainerSocketSupport.Kind.WORKSITE, (declaration, effect) -> {
                if (!(declaration instanceof ContainerSocketSupport.Worksite worksite)) throw new IllegalArgumentException("forged socket schema");
                return FrontierV3WorksiteWrites.apply(worksite.socket().key(), effect);
            });
    static boolean openingWrite(ContainerSocketSupport declaration, java.util.function.BooleanSupplier effect) {
        return WRITERS.get(java.util.Objects.requireNonNull(declaration).kind()).apply(declaration, effect);
    }
    static FrontierV3ContainerSurfaceExecutor.SocketReadiness inspect(ServerLevel level, FrontierV3GrayboxLedger ledger,
            BlockPos target, ContainerSocketSupport support) {
        if (support == null) return FrontierV3ContainerSurfaceExecutor.SocketReadiness.DEFERRED;
        if (!support.position().equals(new BlockPosition(target.getX(), target.getY() - 1, target.getZ())))
            throw new IllegalArgumentException("container support does not name its exact socket");
        return PROVIDERS.get(support.kind()).inspect(level, ledger, target.below(), support);
    }
    private static FrontierV3ContainerSurfaceExecutor.SocketReadiness graybox(ServerLevel level, FrontierV3GrayboxLedger ledger,
            BlockPos position, ContainerSocketSupport support) {
        if (!(support instanceof ContainerSocketSupport.Graybox declared)) throw new IllegalArgumentException("forged graybox support schema");
        return FrontierV3ContainerSurfaceExecutor.supportReadiness(level, ledger, position.above(), declared.cell());
    }
    private static FrontierV3ContainerSurfaceExecutor.SocketReadiness worksite(ServerLevel level, FrontierV3GrayboxLedger ledger,
            BlockPos position, ContainerSocketSupport support) {
        if (!(support instanceof ContainerSocketSupport.Worksite declared)) throw new IllegalArgumentException("forged worksite support schema");
        var opening = ledger.worksite(position.above());
        if (opening == null || opening.phase() == FrontierV3WorksiteBlockWitness.Phase.PREPARED)
            return FrontierV3ContainerSurfaceExecutor.SocketReadiness.DEFERRED;
        if (opening.phase() != FrontierV3WorksiteBlockWitness.Phase.SETTLED || !opening.declaration().equals(declared.socket()))
            return FrontierV3ContainerSurfaceExecutor.SocketReadiness.CONFLICT;
        // Once the opening has been prepared, the common container owner may hold a chest
        // there. Its own provenance check validates that chest; do not require AIR again.
        var witness = ledger.worksite(position);
        if (witness == null || witness.phase() == FrontierV3WorksiteBlockWitness.Phase.PREPARED)
            return FrontierV3ContainerSurfaceExecutor.SocketReadiness.DEFERRED;
        return witness.phase() == FrontierV3WorksiteBlockWitness.Phase.SETTLED && witness.declaration().equals(declared.cell())
                && FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position)).equals(declared.cell().block())
                ? FrontierV3ContainerSurfaceExecutor.SocketReadiness.READY : FrontierV3ContainerSurfaceExecutor.SocketReadiness.CONFLICT;
    }
}
