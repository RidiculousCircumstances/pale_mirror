package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.WorksiteBlock;
import io.farfrontier.palemirror.internal.world.GeneratedGeology;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Read-only overlay: exact mutation journal wins over immutable generation history.
 * Generation supplies a block fact; the declared family alone supplies its worksite meaning. */
final class FrontierV3WorksiteEvidence {
    // One immutable content digest, not a full ruleset serialization per block.
    private static io.farfrontier.palemirror.frontier.v3.model.FrontierRuleset fingerprintedRules;
    private static String fingerprint;
    private FrontierV3WorksiteEvidence() { }
    private static synchronized String fingerprint(FrontierWorldState state) {
        var rules = state.bootstrap().ruleset();
        if (rules != fingerprintedRules) { fingerprint = rules.contentSha256(); fingerprintedRules = rules; }
        return fingerprint;
    }
    static FrontierV3WorksiteBlockWitness at(ServerLevel level, FrontierV3GrayboxLedger ledger,
            FrontierWorldState state, BlockPos position) {
        var retained = ledger.worksite(position);
        if (retained != null) return retained;
        return FrontierV3WorksiteRegistry.point(state, position)
                .map(current -> initial(level, state, current)).orElse(null);
    }
    static FrontierV3WorksiteBlockWitness initial(ServerLevel level, FrontierWorldState state, WorksiteBlock current) {
        var position = FrontierV3WorksiteProjection.position(current);
        var baseline = GeneratedGeology.loaded(level, position).orElse(null);
        if (baseline == null || !baseline.blocks().containsKey(position)) return null;
        if (baseline.seed() != state.bootstrap().seed()
                || !baseline.configuration().equals(fingerprint(state)))
            throw new IllegalStateException("worksite generation baseline belongs to a different configuration at " + position);
        var generated = FrontierV3MinecraftBlockExtraction.describe(baseline.blocks().get(position));
        return FrontierV3WorksiteRegistry.owner(current.key().family()).initialGeology(state, current, generated)
                .map(initial -> new FrontierV3WorksiteBlockWitness(initial, generated, FrontierV3WorksiteBlockWitness.Phase.SETTLED))
                .orElse(null);
    }
}
