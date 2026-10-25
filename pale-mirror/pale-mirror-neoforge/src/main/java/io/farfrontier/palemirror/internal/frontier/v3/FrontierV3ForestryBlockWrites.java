package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Forest external-edit adapter. The common block journal retains attempted/applied evidence;
 * this family withdraws only its source entitlement, never credits player-produced wood. */
final class FrontierV3ForestryBlockWrites implements FrontierV3ManagedBlockWrites.Owner {
    @Override public CellMutationKey.OwnerFamily family() { return CellMutationKey.OwnerFamily.FOREST_TREE; }
    @Override public boolean preparePlannedWrites(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            List<FrontierV3ManagedBlockWrites.PlannedWrite> writes, Runnable resume) {
        return true; // Per-block ingress below fences the exact source before native mutation.
    }
    @Override public boolean observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            BlockPos position, BlockState replacement, boolean committed) {
        var state = runtime.passiveOwnershipState().orElse(null);
        if (state == null || !state.bootstrap().ruleset().forestry().enabled() || !FrontierV3LoadedWorld.ready(level, position)) return true;
        var declared = ForestryWorksiteBlocks.at(state, new BlockPosition(position.getX(), position.getY(), position.getZ())).orElse(null);
        if (declared == null || FrontierV3WorksiteWrites.owns(declared.key())) return true;
        var ledger = FrontierV3GrayboxLedger.get(level); var prior = FrontierV3WorksiteEvidence.at(level, ledger, state, position);
        if (prior != null && prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED) {
            if (committed) {
                ledger.worksite(new FrontierV3WorksiteBlockWitness(prior.declaration(), prior.before(), prior.phase(), prior.effectOperation(),
                        Optional.of(FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position))))); ledger.persist(level);
            }
            return true; // Original felling receipt must settle before its external successor.
        }
        var actual = FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position));
        if (!committed && actual.equals(FrontierV3MinecraftBlockExtraction.describe(replacement))) return true;
        var tree = ForestryWorksiteBlocks.tree(state, declared);
        if (!ForestrySourceCustody.blocksCold(state, tree.reference()) && !FrontierV3ForestryWorksiteOwner.prepare(runtime, state, tree)) return false;
        var before = prior != null && (prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING
                || prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL) ? prior.before() : actual;
        ledger.worksite(new FrontierV3WorksiteBlockWitness(declared, before, committed
                ? FrontierV3WorksiteBlockWitness.Phase.EXTERNAL : FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING));
        ledger.persist(level); return true;
    }
    static void reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElseThrow(); var ledger = FrontierV3GrayboxLedger.get(level);
        for (var position : ledger.externalWorksites(CellMutationKey.OwnerFamily.FOREST_TREE)) {
            var witness = ledger.worksite(position);
            if (witness == null || witness.declaration().key().family() != CellMutationKey.OwnerFamily.FOREST_TREE
                    || !FrontierV3LoadedWorld.ready(level, position)) continue;
            var tree = ForestryWorksiteBlocks.tree(state, witness.declaration()); int log = Math.toIntExact(witness.declaration().key().cell()-1);
            if (!tree.present(log) && !tree.extracted(log)) {
                if (witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL) { ledger.retireExternalWorksite(witness); ledger.persistAsync(level); }
                continue;
            }
            var current = ForestryWorksiteBlocks.cell(tree, log);
            var actual = FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position));
            if (witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING && actual.equals(witness.before())) {
                ledger.worksite(new FrontierV3WorksiteBlockWitness(current, actual, actual.equals(current.block())
                        ? FrontierV3WorksiteBlockWitness.Phase.SETTLED : FrontierV3WorksiteBlockWitness.Phase.EXTERNAL));
                ledger.persistAsync(level); continue;
            }
            if (state.forest().work().values().stream().anyMatch(job -> job.pending().isPresent()
                    && job.target().filter(target -> target.tree().equals(tree.reference())).isPresent())) continue;
            var lease = state.replicaCustody().custodyByScope().get(ForestrySourceCustody.scope(tree.reference()));
            if (lease == null || !lease.live()) { FrontierV3ForestryWorksiteOwner.prepare(runtime, state, tree); return; }
            var event = new ForestObservation.Withdrawn(List.of(new ForestObservation.Withdrawal(tree.declaration(), tree.revision(),
                    lease.authorityEpoch(), lease.expectedReplicaRevision(), List.of(log))));
            if (FrontierV3CommandSubmission.submit(runtime, "forest-external-log", current.key().owner().value(), event) instanceof CommandResult.Accepted) {
                ledger.retireExternalWorksite(witness); ledger.persistAsync(level);
            }
            return; // Bounded semantic capture; no whole-forest reconciliation scan.
        }
    }
}
