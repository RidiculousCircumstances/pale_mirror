package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.WorksiteBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Read-only overlay: exact mutation journal wins over immutable generation history.
 * Generation supplies a block fact; the declared family alone supplies its worksite meaning. */
final class FrontierV3WorksiteEvidence {
    private FrontierV3WorksiteEvidence() { }
    static FrontierV3WorksiteBlockWitness at(ServerLevel level, FrontierV3GrayboxLedger ledger,
            FrontierWorldState state, BlockPos position) {
        var retained = ledger.worksite(position);
        if (retained != null) return retained;
        return FrontierV3WorksiteRegistry.point(state, position)
                .map(current -> initial(level, state, current)).orElse(null);
    }
    /** Acquisition must retain its actual positive observation, even when the birth
     * declaration came from a virtual generation receipt. The caller fences the
     * journal before confirming custody; later unload/recovery needs no loaded chunk. */
    static boolean retainCurrent(FrontierV3GrayboxLedger ledger, WorksiteBlock current,
            io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Block actual,
            FrontierV3WorksiteBlockWitness witness) {
        if (witness == null || !witness.settledCurrent(current, actual)) return false;
        ledger.worksite(witness); return true;
    }
    static FrontierV3WorksiteBlockWitness initial(ServerLevel level, FrontierWorldState state, WorksiteBlock current) {
        var position = FrontierV3WorksiteProjection.position(current);
        var infrastructure = FrontierV3GeneratedWorksites.loaded(level, position).orElse(null);
        if (infrastructure != null && infrastructure.cells().containsKey(position)) {
            FrontierV3GenerationConfiguration.require(state.bootstrap(), infrastructure.seed(), infrastructure.configuration());
            var initial = infrastructure.cells().get(position);
            if (!initial.key().equals(current.key()))
                throw new IllegalStateException("generated worksite infrastructure changed owner at " + position);
            return new FrontierV3WorksiteBlockWitness(initial, initial.block(), FrontierV3WorksiteBlockWitness.Phase.SETTLED);
        }
        return FrontierV3WorksiteRegistry.owner(current.key().family()).initialSource(level, state, current)
                .map(initial -> new FrontierV3WorksiteBlockWitness(initial, initial.block(), FrontierV3WorksiteBlockWitness.Phase.SETTLED))
                .orElse(null);
    }
}
