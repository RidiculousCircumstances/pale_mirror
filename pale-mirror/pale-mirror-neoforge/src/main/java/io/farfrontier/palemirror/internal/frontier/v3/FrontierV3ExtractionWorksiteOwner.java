package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;

/** Finite-source policy behind the common worksite projection SPI. */
final class FrontierV3ExtractionWorksiteOwner implements FrontierV3WorksiteProjectionOwner {
    private final ExtractionWorksiteView currentView = new ExtractionWorksiteView();
    @Override public Map<Region, List<WorksiteBlock>> currentRegions(FrontierWorldState state) {
        return currentView.current(state);
    }
    @Override public Optional<WorksiteBlock> point(FrontierWorldState state, BlockPosition position) {
        WorksiteBlock found = null;
        var index = geometry(state);
        for (var owner : index.worksiteOwners(new ExtractionGeometryIndex.Chunk(position.x() >> 4, position.z() >> 4))) {
            var deposit = state.extractionSites().deposits().get(owner);
            var declared = ExtractionWorksiteBlocks.declaredAt(deposit, position);
            if (declared.isEmpty()) continue;
            if (found != null) throw new IllegalStateException("overlapping worksite declarations");
            found = ExtractionWorksiteBlocks.current(deposit, declared.orElseThrow());
        }
        return Optional.ofNullable(found);
    }
    private FrontierBootstrap bootstrap;
    private List<WorksiteBlock> declarations = List.of();
    private ExtractionGeometryIndex geometry;
    private ExtractionGeometryIndex declarationGeometry;
    private Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ExtractionDeposit> geometryDeposits;
    private ExtractionGeometryIndex geometry(FrontierWorldState state) {
        var deposits = state.extractionSites().deposits();
        if (geometry != null && geometryDeposits == deposits) return geometry;
        var declared = ExtractionGeometryIndex.declarations(state.extractionSites());
        if (geometry == null || !geometry.matches(declared)) geometry = new ExtractionGeometryIndex(declared);
        geometryDeposits = deposits;
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
    @Override public Optional<WorksiteBlock> initialGeology(FrontierWorldState state, WorksiteBlock current,
            BlockExtraction.Block generated) {
        var layout = state.extractionSites().deposits().get(current.key().owner()).site().layout();
        BlockExtraction.Block initial = switch (current.key().role()) {
            case RESOURCE -> layout.require(current.key().cell()).definition().before();
            case NATURAL_TERRAIN -> layout.terrainSupports().get(current.position());
            case INFRASTRUCTURE, CONTAINER_SOCKET -> null;
        };
        return generated.equals(initial) ? Optional.of(new WorksiteBlock(current.key(), current.position(), 1, initial)) : Optional.empty();
    }
    @Override public Optional<WorksiteBlockTransition> reclassification(FrontierWorldState state,
            WorksiteBlock predecessor, WorksiteBlock successor) {
        return ExtractionWorksiteBlocks.reclassification(
                state.extractionSites().deposits().get(successor.key().owner()), predecessor, successor);
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
        var witness = FrontierV3WorksiteEvidence.at(level, FrontierV3GrayboxLedger.get(level), state, nativePosition);
        // View demand may expose a stale cached source without making it tick.
        // Bring that source current under temporary custody, then release from
        // positive whole-region proof; an already-current view needs no lease.
        if (!level.shouldTickBlocksAt(nativePosition) && witness != null && witness.settledCurrent(declaration, actual)) return true;
        var region = new ExtractionRegion(declaration.key().owner(), Math.floorDiv(position.x(), 16), Math.floorDiv(position.z(), 16));
        var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
        if (lease != null && lease.live()) return lease.status() == PhysicalCustodyLeaseStatus.PREPARING
                || lease.status() == PhysicalCustodyLeaseStatus.ACQUIRED
                    && (FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(FrontierV3WorksiteProjection.position(declaration))).equals(declaration.block())
                        || Optional.ofNullable(FrontierV3WorksiteEvidence.at(level, FrontierV3GrayboxLedger.get(level), state,
                                FrontierV3WorksiteProjection.position(declaration)))
                            .flatMap(retained -> committedEffect(state, retained)).isPresent());
        var replica = state.replicaCustody().replicas().get(region.objectId());
        submit(runtime, new ExtractionSourceBoundary(region, ExtractionSourceBoundary.Operation.PREPARE,
                lease == null ? 0 : lease.authorityEpoch(), replica == null ? 0 : replica.replicaRevision(),
                ExtractionSourceCustody.fingerprint(state.extractionSites(), region)));
        return false; // Refresh the exact canonical generation on the next bounded adapter turn.
    }
    @Override public void afterProjection(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElseThrow(); var ledger = FrontierV3GrayboxLedger.get(level);
        if (!ledger.prepareDurable(level)) return; // Never publish custody from a submitted-but-unforced image.
        var index = geometry(state);
        for (var region : index.regions()) {
            var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
            if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.PREPARING) continue;
            boolean complete = index.cells(region).stream().allMatch(cell -> {
                var position = new BlockPos(cell.source().x(), cell.source().y(), cell.source().z());
                if (!FrontierV3LoadedWorld.ready(level, position)) return false;
                var witness = FrontierV3WorksiteEvidence.at(level, ledger, state, position); var current = state.extractionSites().deposits().get(region.siteId()).cells().get(cell.id());
                return witness != null && witness.phase() == FrontierV3WorksiteBlockWitness.Phase.SETTLED
                        && witness.declaration().key().equals(new WorksiteBlock.Key(family(), region.siteId(), WorksiteBlock.Role.RESOURCE, cell.id()))
                        && witness.declaration().revision() == current.revision()
                        && FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position)).equals(current.knownBlock());
            });
            if (complete) complete = currentRegions(state).getOrDefault(new Region(
                    region.chunkX(), region.chunkZ()), List.of()).stream().filter(cell -> cell.key().owner().equals(region.siteId())
                    && cell.key().role() == WorksiteBlock.Role.NATURAL_TERRAIN && region.contains(cell.position()))
                    .allMatch(cell -> {
                        var position = FrontierV3WorksiteProjection.position(cell);
                        if (!FrontierV3LoadedWorld.ready(level, position)) return false;
                        var witness = FrontierV3WorksiteEvidence.at(level, ledger, state, position); var current = current(state, cell);
                        return witness != null && witness.phase() == FrontierV3WorksiteBlockWitness.Phase.SETTLED
                                && witness.declaration().equals(current)
                                && FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position)).equals(current.block());
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
        if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.PREPARING
                && lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED) return;
        if (state.extractionSites().work().values().stream().anyMatch(job -> job.siteId().equals(region.siteId())
                && job.pending().isPresent() && job.target().filter(target -> region.contains(
                    state.extractionSites().deposits().get(region.siteId()).site().layout().require(target.key().cell()).source())).isPresent())) return;
        var ledger = FrontierV3GrayboxLedger.get(level);
        var chunk = level.getChunkSource().getChunkNow(region.chunkX(), region.chunkZ());
        if (lease.status() == PhysicalCustodyLeaseStatus.ACQUIRED) {
            // A retained exact whole-region departure image, not missing chunks,
            // discharges custody after its last pending operation is cancelled.
            for (var declaration : regionBlocks(state, region)) {
                var witness = ledger.worksite(FrontierV3WorksiteProjection.position(declaration));
                if (witness == null || !witness.settledCurrent(declaration, declaration.block())) return;
            }
            if (!ledger.prepareDurable(level)) return;
            submit(runtime, new ExtractionSourceBoundary(region, ExtractionSourceBoundary.Operation.RELEASE,
                    lease.authorityEpoch(), lease.expectedReplicaRevision(), ExtractionSourceCustody.fingerprint(state.extractionSites(), region)));
            return;
        }
        for (var cell : geometry(state).cells(region)) {
            var declaration = ExtractionWorksiteBlocks.current(state.extractionSites().deposits().get(region.siteId()),
                    new WorksiteBlock(new WorksiteBlock.Key(family(), region.siteId(), WorksiteBlock.Role.RESOURCE, cell.id()),
                            cell.source(), 1, cell.definition().before()));
            var position = FrontierV3WorksiteProjection.position(declaration);
            var witness = FrontierV3WorksiteProjection.alignDeclaration(ledger, state, declaration);
            if (!FrontierV3WorksiteBlockWitness.permitsProjectionWithdrawal(witness, declaration)) return;
            if (chunk != null && witness != null && !FrontierV3MinecraftBlockExtraction.describe(chunk.getBlockState(position))
                    .equals(witness.declaration().block())) return;
        }
        if (!ledger.prepareDurable(level)) return;
        submit(runtime, new ExtractionSourceBoundary(region, ExtractionSourceBoundary.Operation.WITHDRAW_PROJECTION,
                lease.authorityEpoch(), lease.expectedReplicaRevision(), ExtractionSourceCustody.fingerprint(state.extractionSites(), region)));
    }
    @Override public void departure(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            net.minecraft.world.level.chunk.LevelChunk chunk) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var regions = geometry(state).regions(new ExtractionGeometryIndex.Chunk(chunk.getPos().x, chunk.getPos().z));
        if (regions.isEmpty()) return;
        var ledger = FrontierV3GrayboxLedger.get(level);
        for (var region : regions) {
            var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
            if (lease != null && lease.status() == PhysicalCustodyLeaseStatus.ACQUIRED)
                captureDeparture(level, state, chunk, region, ledger);
        }
        // Native unload has the last physical image, but is not a transaction
        // drain. beforeTurn withdraws the exact retained lease only after these
        // witnesses are durable, including after restart or a pending operation.
        ledger.persistAsync(level);
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
            // Preserve generated block facts before the chunk disappears. Pending
            // work may delay canonical release, but may not erase departure evidence.
            var ledger = FrontierV3GrayboxLedger.get(level);
            captureDeparture(level, state, chunk, region, ledger);
            withdrawProjection(level, runtime, region);
            state = runtime.decodedState().orElseThrow();
        }
    }
    private void captureDeparture(ServerLevel level, FrontierWorldState state, net.minecraft.world.level.chunk.LevelChunk chunk,
            ExtractionRegion region, FrontierV3GrayboxLedger ledger) {
        for (var declaration : regionBlocks(state, region)) {
            var position = FrontierV3WorksiteProjection.position(declaration);
            var witness = FrontierV3WorksiteEvidence.at(level, ledger, state, position);
            if (witness != null) witness.departure(declaration,
                    FrontierV3MinecraftBlockExtraction.describe(chunk.getBlockState(position))).ifPresent(ledger::worksite);
        }
    }
    private List<WorksiteBlock> regionBlocks(FrontierWorldState state, ExtractionRegion region) {
        return currentRegions(state).getOrDefault(new Region(
                region.chunkX(), region.chunkZ()), List.of()).stream().filter(cell -> cell.key().owner().equals(region.siteId())
                && (cell.key().role() == WorksiteBlock.Role.RESOURCE || cell.key().role() == WorksiteBlock.Role.NATURAL_TERRAIN))
                .toList();
    }
    @Override public void shutdown(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        for (var region : geometry(state).regions()) {
            afterProjection(level, runtime); // Complete a fully observed successor before releasing its saved region.
            var chunk = level.getChunkSource().getChunkNow(region.chunkX(), region.chunkZ());
            if (chunk != null) departure(level, runtime, chunk, true);
        }
    }
}
