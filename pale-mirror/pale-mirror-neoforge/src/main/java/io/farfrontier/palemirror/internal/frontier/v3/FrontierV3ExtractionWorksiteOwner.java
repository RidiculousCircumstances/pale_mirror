package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Finite-source policy behind the common worksite projection SPI. */
final class FrontierV3ExtractionWorksiteOwner implements FrontierV3WorksiteProjectionOwner {
    private FrontierBootstrap bootstrap;
    private List<WorksiteBlock> declarations = List.of();
    private ExtractionGeometryIndex geometry;
    private ExtractionGeometryIndex declarationGeometry;
    private ExtractionGeometryIndex geometry(FrontierWorldState state) {
        var declared = ExtractionGeometryIndex.declarations(state.extractionSites());
        if (geometry == null || !geometry.matches(declared)) geometry = new ExtractionGeometryIndex(declared);
        return geometry;
    }
    @Override public CellMutationKey.OwnerFamily family() { return CellMutationKey.OwnerFamily.EXTRACTIVE_SITE; }
    @Override public void beforeTurn(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierV3ExtractionBlockWrites.reconcile(level, runtime);
        afterProjection(level, runtime);
        // A cached loaded chunk is not an active physical custodian. Reuse the same
        // positive current-block departure proof when its natural ticking demand ends.
        var state = runtime.decodedState().orElseThrow();
        for (var region : geometry(state).chunks()) {
            var chunk = level.getChunkSource().getChunkNow(region.x(), region.z());
            if (chunk != null && !level.shouldTickBlocksAt(new BlockPos(region.x() * 16, level.getMinBuildHeight(), region.z() * 16)))
                departure(level, runtime, chunk, false);
        }
        // Includes already-unloaded regions after recovery. Withdrawal is based on
        // durable write/effect journals, never on elapsed time or invented observation.
        for (var region : geometry(runtime.decodedState().orElseThrow()).regions()) {
            if (level.getChunkSource().getChunkNow(region.chunkX(), region.chunkZ()) == null)
                withdrawProjection(level, runtime, region);
        }
    }
    @Override public List<WorksiteBlock> declarations(FrontierWorldState state) {
        var currentGeometry = geometry(state);
        if (bootstrap != state.bootstrap() || declarationGeometry != currentGeometry) {
            bootstrap = state.bootstrap();
            declarationGeometry = currentGeometry;
            declarations = state.extractionSites().deposits().values().stream().sorted(Comparator.comparing(value -> value.site().id()))
                    .flatMap(deposit -> ExtractionWorksiteBlocks.declared(deposit).stream()).toList();
        }
        return declarations;
    }
    @Override public WorksiteBlock current(FrontierWorldState state, WorksiteBlock declaration) {
        return ExtractionWorksiteBlocks.current(state.extractionSites().deposits().get(declaration.key().owner()), declaration);
    }
    @Override public Optional<FrontierV3WorksiteBlockWitness> committedEffect(FrontierWorldState state, FrontierV3WorksiteBlockWitness witness) {
        if (witness.phase() != FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED
                && witness.phase() != FrontierV3WorksiteBlockWitness.Phase.EFFECT_COMMITTED
                || witness.declaration().key().role() != WorksiteBlock.Role.RESOURCE) return Optional.empty();
        var key = witness.declaration().key(); var cell = state.extractionSites().deposits().get(key.owner()).cells().get(key.cell());
        if (!cell.extractionOperation().equals(witness.effectOperation())) return Optional.empty();
        return Optional.of(new FrontierV3WorksiteBlockWitness(current(state, witness.declaration()), witness.before(),
                FrontierV3WorksiteBlockWitness.Phase.EFFECT_COMMITTED, witness.effectOperation(), witness.externalSuccessor()));
    }
    @Override public boolean effectResumption(FrontierWorldState state, WorksiteBlock cell, FrontierV3WorksiteBlockWitness witness,
            BlockExtraction.Block actual) {
        if (witness.phase() != FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED) return false;
        return state.extractionSites().work().values().stream().flatMap(job -> job.pending().stream())
                .filter(ExtractionPhysicalStep.BlockWork.class::isInstance).map(ExtractionPhysicalStep.BlockWork.class::cast)
                .anyMatch(step -> witness.effectOperation().equals(Optional.of(step.extraction().operationId()))
                        && step.extraction().target().equals(cell.position())
                        && actual.equals(witness.externalSuccessor().orElse(step.extraction().definition().after())));
    }
    @Override public boolean beforeProjection(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, WorksiteBlock declaration) {
        if (declaration.key().role() != WorksiteBlock.Role.RESOURCE) return true;
        var position = declaration.position();
        var nativePosition = FrontierV3WorksiteProjection.position(declaration);
        var actual = FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(nativePosition));
        var witness = FrontierV3GrayboxLedger.get(level).worksite(nativePosition);
        // View demand may expose a stale cached source without making it tick.
        // Bring that source current under temporary custody, then release from
        // positive whole-region proof; an already-current view needs no lease.
        if (!level.shouldTickBlocksAt(nativePosition) && witness != null && witness.settledCurrent(declaration, actual)) return true;
        var region = new ExtractionRegion(declaration.key().owner(), Math.floorDiv(position.x(), 16), Math.floorDiv(position.z(), 16));
        var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
        if (lease != null && lease.live()) return lease.status() == PhysicalCustodyLeaseStatus.PREPARING
                || lease.status() == PhysicalCustodyLeaseStatus.ACQUIRED
                    && (FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(FrontierV3WorksiteProjection.position(declaration))).equals(declaration.block())
                        || Optional.ofNullable(FrontierV3GrayboxLedger.get(level).worksite(FrontierV3WorksiteProjection.position(declaration)))
                            .flatMap(retained -> committedEffect(state, retained)).isPresent());
        var replica = state.replicaCustody().replicas().get(region.objectId());
        submit(runtime, new ExtractionSourceBoundary(region, ExtractionSourceBoundary.Operation.PREPARE,
                lease == null ? 0 : lease.authorityEpoch(), replica == null ? 0 : replica.replicaRevision(),
                ExtractionSourceCustody.fingerprint(state.extractionSites(), region)));
        return false; // Refresh the exact canonical generation on the next bounded adapter turn.
    }
    @Override public void afterProjection(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElseThrow(); var ledger = FrontierV3GrayboxLedger.get(level);
        var index = geometry(state);
        for (var region : index.regions()) {
            var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
            if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.PREPARING) continue;
            boolean complete = index.cells(region).stream().allMatch(cell -> {
                var position = new BlockPos(cell.source().x(), cell.source().y(), cell.source().z());
                if (!level.hasChunkAt(position)) return false;
                var witness = ledger.worksite(position); var current = state.extractionSites().deposits().get(region.siteId()).cells().get(cell.id());
                return witness != null && witness.phase() == FrontierV3WorksiteBlockWitness.Phase.SETTLED
                        && witness.declaration().key().equals(new WorksiteBlock.Key(family(), region.siteId(), WorksiteBlock.Role.RESOURCE, cell.id()))
                        && witness.declaration().revision() == current.revision()
                        && FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position)).equals(current.knownBlock());
            });
            if (complete) {
                submit(runtime, new ExtractionSourceBoundary(region, ExtractionSourceBoundary.Operation.CONFIRM,
                        lease.authorityEpoch(), lease.expectedReplicaRevision(), ExtractionSourceCustody.fingerprint(state.extractionSites(), region)));
                return;
            }
        }
    }
    static boolean submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ExtractionSourceBoundary event) {
        return FrontierV3CommandSubmission.submit(runtime, "extraction-source-" + event.operation().name().toLowerCase(Locale.ROOT),
                event.region().objectId().value(), event) instanceof CommandResult.Accepted;
    }
    private void withdrawProjection(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ExtractionRegion region) {
        var state = runtime.decodedState().orElseThrow();
        var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
        if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.PREPARING) return;
        if (state.extractionSites().work().values().stream().anyMatch(job -> job.siteId().equals(region.siteId())
                && job.pending().isPresent() && job.target().filter(target -> region.contains(
                    state.extractionSites().deposits().get(region.siteId()).site().layout().require(target.key().cell()).source())).isPresent())) return;
        var ledger = FrontierV3GrayboxLedger.get(level);
        var chunk = level.getChunkSource().getChunkNow(region.chunkX(), region.chunkZ());
        for (var cell : geometry(state).cells(region)) {
            var declaration = ExtractionWorksiteBlocks.current(state.extractionSites().deposits().get(region.siteId()),
                    new WorksiteBlock(new WorksiteBlock.Key(family(), region.siteId(), WorksiteBlock.Role.RESOURCE, cell.id()),
                            cell.source(), 1, cell.definition().before()));
            var position = FrontierV3WorksiteProjection.position(declaration);
            var witness = ledger.worksite(position);
            if (!FrontierV3WorksiteBlockWitness.permitsProjectionWithdrawal(witness, declaration)) return;
            if (chunk != null && witness != null && !FrontierV3MinecraftBlockExtraction.describe(chunk.getBlockState(position))
                    .equals(witness.declaration().block())) return;
        }
        if (ledger.isDirty()) ledger.persist(level);
        submit(runtime, new ExtractionSourceBoundary(region, ExtractionSourceBoundary.Operation.WITHDRAW_PROJECTION,
                lease.authorityEpoch(), lease.expectedReplicaRevision(), ExtractionSourceCustody.fingerprint(state.extractionSites(), region)));
    }
    @Override public void departure(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            net.minecraft.world.level.chunk.LevelChunk chunk) {
        departure(level, runtime, chunk, true);
    }
    private void departure(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            net.minecraft.world.level.chunk.LevelChunk chunk, boolean withdrawPreparing) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var index = geometry(state);
        for (var region : index.regions(new ExtractionGeometryIndex.Chunk(chunk.getPos().x, chunk.getPos().z))) {
            var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
            if (withdrawPreparing && lease != null && lease.status() == PhysicalCustodyLeaseStatus.PREPARING) {
                withdrawProjection(level, runtime, region);
                state = runtime.decodedState().orElseThrow();
                continue;
            }
            if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED) continue;
            var currentState = state;
            if (currentState.extractionSites().work().values().stream().anyMatch(job -> job.siteId().equals(region.siteId())
                    && job.pending().isPresent() && job.target().filter(target ->
                    region.contains(currentState.extractionSites().deposits().get(region.siteId()).site().layout().require(target.key().cell()).source())).isPresent())) continue;
            boolean matches = index.cells(region).stream().allMatch(cell ->
                    FrontierV3MinecraftBlockExtraction.describe(chunk.getBlockState(new BlockPos(cell.source().x(), cell.source().y(), cell.source().z())))
                            .equals(currentState.extractionSites().deposits().get(region.siteId()).cells().get(cell.id()).knownBlock()));
            if (matches) submit(runtime, new ExtractionSourceBoundary(region, ExtractionSourceBoundary.Operation.RELEASE,
                    lease.authorityEpoch(), lease.expectedReplicaRevision(), ExtractionSourceCustody.fingerprint(state.extractionSites(), region)));
            state = runtime.decodedState().orElseThrow();
        }
    }
    @Override public void shutdown(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        for (var region : geometry(state).regions()) {
            afterProjection(level, runtime); // Complete a fully observed successor before releasing its saved region.
            var chunk = level.getChunkSource().getChunkNow(region.chunkX(), region.chunkZ());
            if (chunk != null) departure(level, runtime, chunk);
        }
    }
}
