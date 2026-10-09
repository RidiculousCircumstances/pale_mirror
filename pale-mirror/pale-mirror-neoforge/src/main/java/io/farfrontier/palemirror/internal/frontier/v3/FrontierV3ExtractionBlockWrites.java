package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Durable physical capture before external writes; source policy alone acknowledges their meaning. */
final class FrontierV3ExtractionBlockWrites implements FrontierV3ManagedBlockWrites.Owner {
    @Override public CellMutationKey.OwnerFamily family() { return CellMutationKey.OwnerFamily.EXTRACTIVE_SITE; }
    @Override public void observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            BlockPos position, BlockState replacement, boolean committed) {
        var state = runtime.passiveOwnershipState().orElse(null);
        if (state == null || !state.bootstrap().ruleset().extraction().enabled() || !level.hasChunkAt(position)) return;
        var indexed = FrontierV3WorksiteRegistry.point(state, position).orElse(null);
        if (indexed == null || FrontierV3WorksiteWrites.owns(indexed.key())) return;
        var ledger = FrontierV3GrayboxLedger.get(level); var prior = ledger.worksite(position);
        // The container owner owns this socket after creation, not the block projector.
        if (indexed.key().role() == WorksiteBlock.Role.CONTAINER_SOCKET && ledger.claim(position) != null) return;
        if (prior != null && prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED) {
            if (committed) {
                ledger.worksite(new FrontierV3WorksiteBlockWitness(prior.declaration(), prior.before(), prior.phase(),
                        prior.effectOperation(), Optional.of(FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position)))));
                ledger.persist(level);
            }
            return; // Original production receipt closes before this ordinary external successor.
        }
        var declaration = FrontierV3WorksiteRegistry.owner(indexed.key().family()).current(state, indexed);
        var actual = FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position));
        if (!committed && actual.equals(FrontierV3MinecraftBlockExtraction.describe(replacement))) return;
        if (declaration.key().role() == WorksiteBlock.Role.RESOURCE) {
            var region = new ExtractionRegion(declaration.key().owner(), position.getX() >> 4, position.getZ() >> 4);
            var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
            if (lease == null || !lease.live()) {
                var replica = state.replicaCustody().replicas().get(region.objectId());
                if (!FrontierV3ExtractionWorksiteOwner.submit(runtime, new ExtractionSourceBoundary(region,
                        ExtractionSourceBoundary.Operation.PREPARE, lease == null ? 0 : lease.authorityEpoch(),
                        replica == null ? 0 : replica.replicaRevision(), ExtractionSourceCustody.fingerprint(state.extractionSites(), region))))
                    throw new IllegalStateException("external extraction write lacks its canonical before-write fence");
            }
        }
        var preimage = prior != null && (prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING
                || prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL) ? prior.before() : actual;
        ledger.worksite(new FrontierV3WorksiteBlockWitness(declaration, preimage, committed
                ? FrontierV3WorksiteBlockWitness.Phase.EXTERNAL : FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING));
        ledger.persist(level);
    }
    static void reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElseThrow(); var ledger = FrontierV3GrayboxLedger.get(level);
        for (var entry : FrontierV3WorksiteRegistry.chunks(state).entrySet()) {
            var chunk = new net.minecraft.world.level.ChunkPos(entry.getKey());
            if (!level.getChunkSource().hasChunk(chunk.x, chunk.z)) continue;
            for (var indexed : entry.getValue()) {
                var position = FrontierV3WorksiteProjection.position(indexed); var witness = ledger.worksite(position);
                if (witness == null || witness.phase() != FrontierV3WorksiteBlockWitness.Phase.EXTERNAL
                        && witness.phase() != FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING) continue;
                var current = FrontierV3WorksiteRegistry.owner(indexed.key().family()).current(state, indexed);
                var actual = FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position));
                if (witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING && actual.equals(witness.before())) {
                    // Native setBlock may refuse. No physical fact changed and no production was awarded.
                    ledger.worksite(new FrontierV3WorksiteBlockWitness(current, witness.before(), actual.equals(current.block())
                            ? FrontierV3WorksiteBlockWitness.Phase.SETTLED : FrontierV3WorksiteBlockWitness.Phase.EXTERNAL));
                    ledger.persist(level); continue;
                }
                if (current.revision() > witness.declaration().revision() && current.block().equals(actual)) {
                    // Canonical acknowledgement was durable before the native capture retired.
                    ledger.worksite(new FrontierV3WorksiteBlockWitness(current, actual, FrontierV3WorksiteBlockWitness.Phase.SETTLED));
                    ledger.persist(level); continue;
                }
                if (current.key().role() != WorksiteBlock.Role.RESOURCE) {
                    var event = new ExtractionGeometryChanged(current, actual);
                    if (FrontierV3CommandSubmission.submit(runtime, "extraction-geometry-changed", current.key().owner().value(), event)
                            instanceof CommandResult.Accepted) {
                        var successor = FrontierV3WorksiteRegistry.owner(current.key().family()).current(runtime.decodedState().orElseThrow(), indexed);
                        ledger.worksite(new FrontierV3WorksiteBlockWitness(successor, actual, FrontierV3WorksiteBlockWitness.Phase.SETTLED));
                        ledger.persist(level);
                    }
                    return;
                }
                var region = new ExtractionRegion(current.key().owner(), position.getX() >> 4, position.getZ() >> 4);
                var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
                if (lease == null || !lease.live()) {
                    var replica = state.replicaCustody().replicas().get(region.objectId());
                    FrontierV3ExtractionWorksiteOwner.submit(runtime, new ExtractionSourceBoundary(region,
                            ExtractionSourceBoundary.Operation.PREPARE, lease == null ? 0 : lease.authorityEpoch(),
                            replica == null ? 0 : replica.replicaRevision(), ExtractionSourceCustody.fingerprint(state.extractionSites(), region)));
                    return;
                }
                var abort = Optional.<ExtractionSourceChanged.UnappliedEffect>empty();
                if (state.extractionSites().work().values().stream().anyMatch(value -> value.siteId().equals(region.siteId())
                        && value.pending().isPresent() && value.target().filter(target -> region.contains(
                            state.extractionSites().deposits().get(region.siteId()).site().layout().require(target.key().cell()).source())
                                && target.key().cell() != current.key().cell()).isPresent())) continue;
                var job = state.extractionSites().work().values().stream().filter(value -> value.target().filter(target ->
                        target.key().owner().equals(current.key().owner()) && target.key().cell() == current.key().cell()).isPresent())
                        .findFirst().orElse(null);
                if (job != null && job.pending().isPresent()) {
                    if (!(job.pending().orElseThrow() instanceof ExtractionPhysicalStep.BlockWork step)
                            || !witness.before().equals(step.extraction().definition().before())) continue;
                    var actor = level.getEntity(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(
                            state.bootstrap().worldId(), job.execution().actorId()));
                    if (!(actor instanceof net.minecraft.world.entity.npc.Villager worker)) continue;
                    var hand = worker.getOffhandItem();
                    if (step.carriedBefore() == 0 ? !hand.isEmpty() : hand.getCount() != step.carriedBefore()
                            || !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(hand.getItem()).toString().equals(job.outputKind())) continue;
                    abort = Optional.of(new ExtractionSourceChanged.UnappliedEffect(job.id(), step,
                            step.carriedBefore() == 0 ? List.of() : List.of(ExtractionPhysicalStateSupport.hand(state, job, step.carriedBefore()))));
                }
                var event = new ExtractionSourceChanged(new ExtractionTarget(new CellMutationKey(current.key().family(),
                        current.key().owner(), current.key().cell()), current.revision()), actual,
                        lease.authorityEpoch(), lease.expectedReplicaRevision(), abort);
                if (FrontierV3CommandSubmission.submit(runtime, "extraction-source-changed", event.target().key().owner().value(), event)
                        instanceof CommandResult.Accepted) {
                    var successor = FrontierV3WorksiteRegistry.owner(indexed.key().family()).current(runtime.decodedState().orElseThrow(), indexed);
                    ledger.worksite(new FrontierV3WorksiteBlockWitness(successor, actual, FrontierV3WorksiteBlockWitness.Phase.SETTLED));
                    ledger.persist(level);
                }
                return; // One canonical foreign fact per turn; no unbounded catch-up batch.
            }
        }
    }
}
