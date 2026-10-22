package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.WorksiteBlock;
import io.farfrontier.palemirror.internal.world.GeneratedGeology;
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
        var baseline = GeneratedGeology.loaded(level, position).orElse(null);
        if (baseline == null || !baseline.blocks().containsKey(position)) return null;
        FrontierV3GenerationConfiguration.require(state.bootstrap(), baseline.seed(), baseline.configuration());
        var generated = FrontierV3MinecraftBlockExtraction.describe(baseline.blocks().get(position));
        return FrontierV3WorksiteRegistry.owner(current.key().family()).initialGeology(state, current, generated)
                .map(initial -> new FrontierV3WorksiteBlockWitness(initial, generated, FrontierV3WorksiteBlockWitness.Phase.SETTLED))
                .orElse(null);
    }
}
