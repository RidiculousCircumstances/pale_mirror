package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import io.farfrontier.palemirror.internal.forestry.ForestRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.*;

/** Sparse forest adapter to the common native source journal and replica authority. */
final class FrontierV3ForestryWorksiteOwner implements FrontierV3WorksiteProjectionOwner {
    @Override public InitialPlacement initialPlacement() { return InitialPlacement.GENERATION_RECEIPT; }
    private static final Comparator<Region> REGION_ORDER = Comparator.comparingInt(Region::x).thenComparingInt(Region::z);
    private record Part(Map<TreeId, ForestTree> source, Map<Region, List<WorksiteBlock>> blocks) { }
    private Map<ForestRegion, Map<TreeId, ForestTree>> sources = Map.of();
    private Map<ForestRegion, Part> parts = Map.of();
    private Map<Region, List<List<WorksiteBlock>>> inputs = Map.of();
    private Map<Region, List<WorksiteBlock>> regions = Map.of();
    private List<WorksiteBlock> declarations = List.of();
    @Override public CellMutationKey.OwnerFamily family() { return CellMutationKey.OwnerFamily.FOREST_TREE; }
    @Override public List<WorksiteBlock> declarations(FrontierWorldState state) { currentRegions(state); return declarations; }
    @Override public Map<Region, List<WorksiteBlock>> currentRegions(FrontierWorldState state) {
        if (sources == state.forest().regions()) return regions;
        var nextParts = new TreeMap<ForestRegion, Part>(); var nextInputs = new TreeMap<Region, List<List<WorksiteBlock>>>(REGION_ORDER);
        for (var entry : state.forest().regions().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var part = parts.get(entry.getKey());
            if (part == null || part.source() != entry.getValue()) {
                var changed = new TreeMap<Region, List<WorksiteBlock>>(REGION_ORDER);
                for (var tree : entry.getValue().values().stream().sorted(Comparator.comparing(value -> value.reference().id())).toList())
                    for (int log = 0; log < tree.declaration().logs().size(); log++) {
                        if (!tree.present(log) && !tree.extracted(log)) continue;
                        var cell = ForestryWorksiteBlocks.cell(tree, log); var region = new Region(cell.position().x() >> 4, cell.position().z() >> 4);
                        changed.computeIfAbsent(region, ignored -> new ArrayList<>()).add(cell);
                    }
                var frozen = new TreeMap<Region, List<WorksiteBlock>>(REGION_ORDER); changed.forEach((region, values) -> frozen.put(region, List.copyOf(values)));
                part = new Part(entry.getValue(), Collections.unmodifiableMap(frozen));
            }
            nextParts.put(entry.getKey(), part);
            part.blocks().forEach((region, blocks) -> nextInputs.computeIfAbsent(region, ignored -> new ArrayList<>()).add(blocks));
        }
        var next = new TreeMap<Region, List<WorksiteBlock>>(REGION_ORDER); var frozenInputs = new TreeMap<Region, List<List<WorksiteBlock>>>(REGION_ORDER);
        nextInputs.forEach((region, values) -> {
            var priorInputs = inputs.get(region); boolean same = priorInputs != null && priorInputs.size() == values.size();
            for (int i = 0; same && i < values.size(); i++) same = priorInputs.get(i) == values.get(i);
            next.put(region, same ? regions.get(region) : values.stream().flatMap(List::stream).toList());
            frozenInputs.put(region, List.copyOf(values));
        });
        sources = state.forest().regions(); parts = Map.copyOf(nextParts); inputs = Collections.unmodifiableMap(frozenInputs);
        regions = Collections.unmodifiableMap(next); declarations = regions.values().stream().flatMap(List::stream).toList(); return regions;
    }
    @Override public Optional<WorksiteBlock> point(FrontierWorldState state, BlockPosition position) { return ForestryWorksiteBlocks.at(state, position); }
    @Override public WorksiteBlock current(FrontierWorldState state, WorksiteBlock declared) {
        return ForestryWorksiteBlocks.cell(ForestryWorksiteBlocks.tree(state, declared), Math.toIntExact(declared.key().cell()-1));
    }
    @Override public Optional<WorksiteBlock> initialSource(ServerLevel level, FrontierWorldState state, WorksiteBlock current) {
        var position = FrontierV3WorksiteProjection.position(current);
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) return Optional.empty();
        var generated = chunk.getData(ForestRegistration.GENERATED).orElse(null); if (generated == null) return Optional.empty();
        FrontierV3GenerationConfiguration.require(state.bootstrap(), generated.seed(), generated.configuration());
        var tree = ForestryWorksiteBlocks.tree(state, current); var ref = tree.reference();
        boolean captured = generated.trees().stream().anyMatch(tree.declaration()::equals)
                || generated.links().stream().anyMatch(link -> link.tree().equals(ref.id()) && link.generation() == ref.generation() && link.owner().equals(ref.region()));
        if (!captured) return Optional.empty();
        return initialGeology(state, current, tree.declaration().logs().block(Math.toIntExact(current.key().cell()-1)));
    }
    @Override public Optional<WorksiteBlock> initialGeology(FrontierWorldState state, WorksiteBlock current, BlockExtraction.Block generated) {
        var tree = ForestryWorksiteBlocks.tree(state, current); int log = Math.toIntExact(current.key().cell()-1);
        return generated.equals(tree.declaration().logs().block(log)) ? Optional.of(new WorksiteBlock(current.key(), current.position(), 1, generated)) : Optional.empty();
    }
    @Override public Optional<WorksiteBlockTransition> reclassification(FrontierWorldState state, WorksiteBlock before, WorksiteBlock after) { return Optional.empty(); }
    @Override public Optional<FrontierV3WorksiteBlockWitness> committedEffect(FrontierWorldState state, FrontierV3WorksiteBlockWitness witness) {
        var tree = ForestryWorksiteBlocks.tree(state, witness.declaration()); int log = Math.toIntExact(witness.declaration().key().cell()-1);
        if (!tree.extractionOperation(log).equals(witness.effectOperation()) || witness.effectOperation().isEmpty()) return Optional.empty();
        return Optional.of(new FrontierV3WorksiteBlockWitness(ForestryWorksiteBlocks.cell(tree, log), witness.before(),
                FrontierV3WorksiteBlockWitness.Phase.EFFECT_COMMITTED, witness.effectOperation(), witness.externalSuccessor()));
    }
    @Override public boolean effectResumption(FrontierWorldState state, WorksiteBlock cell, FrontierV3WorksiteBlockWitness witness, BlockExtraction.Block actual) {
        return witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED
                && state.forest().work().values().stream().flatMap(job -> job.pending().stream())
                    .filter(ForestryPhysicalStep.Fell.class::isInstance).map(ForestryPhysicalStep.Fell.class::cast)
                    .anyMatch(step -> witness.effectOperation().equals(Optional.of(step.effect().operationId()))
                            && step.effect().target().equals(cell.position()) && actual.equals(witness.externalSuccessor().orElse(step.effect().definition().after())));
    }
    @Override public boolean beforeProjection(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, WorksiteBlock declared) {
        var tree = ForestryWorksiteBlocks.tree(state, declared); var lease = state.replicaCustody().custodyByScope().get(ForestrySourceCustody.scope(tree.reference()));
        if (lease != null && lease.live()) return lease.status() == PhysicalCustodyLeaseStatus.PREPARING
                || lease.status() == PhysicalCustodyLeaseStatus.ACQUIRED && nativeBlock(level, declared).equals(declared.block());
        prepare(runtime, state, tree); return false;
    }
    static boolean prepare(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, ForestTree tree) {
        var address = ForestrySourceCustody.address(tree.reference()); var lease = state.replicaCustody().custodyByScope().get(address.scope());
        var replica = state.replicaCustody().replicas().get(address.object());
        return submit(runtime, new ForestrySourceBoundary(tree.reference(), ForestrySourceBoundary.Operation.PREPARE,
                lease == null ? 0 : lease.authorityEpoch(), replica == null ? 0 : replica.replicaRevision(), ForestrySourceCustody.fingerprint(tree)));
    }
    static boolean submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ForestrySourceBoundary boundary) {
        return FrontierV3CommandSubmission.submit(runtime, "forest-source-" + boundary.operation().name().toLowerCase(Locale.ROOT),
                ForestrySourceCustody.object(boundary.tree()).value(), boundary) instanceof CommandResult.Accepted;
    }
    private static BlockExtraction.Block nativeBlock(ServerLevel level, WorksiteBlock cell) {
        return FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(FrontierV3WorksiteProjection.position(cell)));
    }
    private static List<WorksiteBlock> cells(ForestTree tree) {
        var cells = new ArrayList<WorksiteBlock>();
        for (int log = 0; log < tree.declaration().logs().size(); log++) if (tree.present(log) || tree.extracted(log)) cells.add(ForestryWorksiteBlocks.cell(tree, log));
        return cells;
    }
    @Override public void beforeTurn(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierV3ForestryBlockWrites.reconcile(level, runtime); afterProjection(level, runtime);
        var state = runtime.decodedState().orElseThrow(); var ledger = FrontierV3GrayboxLedger.get(level);
        for (var part : state.forest().regions().values()) for (var tree : part.values()) {
            var lease = state.replicaCustody().custodyByScope().get(ForestrySourceCustody.scope(tree.reference()));
            if (lease == null || !lease.live()) continue;
            boolean hot = !cells(tree).isEmpty() && tree.declaration().footprint().stream().anyMatch(region -> level.shouldTickBlocksAt(new BlockPos(region.x()*16, tree.declaration().origin().y(), region.z()*16)));
            if (hot) continue;
            captureLoaded(level, state, tree, ledger);
            if (state.forest().work().values().stream().anyMatch(job -> job.pending().isPresent()
                    && job.target().filter(target -> target.tree().equals(tree.reference())).isPresent())) continue;
            boolean proof = cells(tree).stream().allMatch(cell -> {
                var witness = ledger.worksite(FrontierV3WorksiteProjection.position(cell));
                return lease.status() == PhysicalCustodyLeaseStatus.ACQUIRED
                        ? witness != null && witness.settledCurrent(cell, cell.block())
                        : FrontierV3WorksiteBlockWitness.permitsProjectionWithdrawal(witness, cell);
            });
            if (!proof || !ledger.prepareDurable(level)) continue;
            submit(runtime, new ForestrySourceBoundary(tree.reference(), lease.status() == PhysicalCustodyLeaseStatus.ACQUIRED
                    ? ForestrySourceBoundary.Operation.RELEASE : ForestrySourceBoundary.Operation.WITHDRAW_PROJECTION,
                    lease.authorityEpoch(), lease.expectedReplicaRevision(), ForestrySourceCustody.fingerprint(tree)));
            return;
        }
    }
    @Override public void afterProjection(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElseThrow(); var ledger = FrontierV3GrayboxLedger.get(level);
        if (!ledger.prepareDurable(level)) return;
        for (var part : state.forest().regions().values()) for (var tree : part.values()) {
            var lease = state.replicaCustody().custodyByScope().get(ForestrySourceCustody.scope(tree.reference()));
            if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.PREPARING) continue;
            boolean complete = cells(tree).stream().allMatch(cell -> {
                var position = FrontierV3WorksiteProjection.position(cell); if (!FrontierV3LoadedWorld.ready(level, position)) return false;
                var witness = FrontierV3WorksiteEvidence.at(level, ledger, state, position);
                return FrontierV3WorksiteEvidence.retainCurrent(ledger, cell, nativeBlock(level, cell), witness);
            });
            if (complete && ledger.prepareDurable(level)) { submit(runtime, new ForestrySourceBoundary(tree.reference(), ForestrySourceBoundary.Operation.CONFIRM,
                    lease.authorityEpoch(), lease.expectedReplicaRevision(), ForestrySourceCustody.fingerprint(tree))); return; }
        }
    }
    private static void captureLoaded(ServerLevel level, FrontierWorldState state, ForestTree tree, FrontierV3GrayboxLedger ledger) {
        for (var cell : cells(tree)) {
            var position = FrontierV3WorksiteProjection.position(cell); if (!FrontierV3LoadedWorld.ready(level, position)) continue;
            var witness = FrontierV3WorksiteEvidence.at(level, ledger, state, position);
            if (witness != null) witness.departure(cell, nativeBlock(level, cell)).ifPresent(ledger::worksite);
        }
    }
    @Override public void departure(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, LevelChunk chunk) {
        var state = runtime.decodedState().orElse(null); if (state == null) return;
        var ledger = FrontierV3GrayboxLedger.get(level);
        for (var tree : state.forest().touching(new ForestRegion(chunk.getPos().x, chunk.getPos().z)))
            for (var cell : cells(tree)) {
                var position = FrontierV3WorksiteProjection.position(cell);
                if ((position.getX() >> 4) != chunk.getPos().x || (position.getZ() >> 4) != chunk.getPos().z) continue;
                var witness = FrontierV3WorksiteEvidence.at(level, ledger, state, position);
                if (witness != null) witness.departure(cell, FrontierV3MinecraftBlockExtraction.describe(chunk.getBlockState(position))).ifPresent(ledger::worksite);
            }
        ledger.persistAsync(level);
    }
    @Override public void shutdown(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierV3ForestryExecutor.shutdown(level, runtime);
        beforeTurn(level, runtime);
        var state = runtime.decodedState().orElseThrow(); var ledger = FrontierV3GrayboxLedger.get(level);
        for (var part : state.forest().regions().values()) for (var tree : part.values()) captureLoaded(level, state, tree, ledger);
        ledger.persistAsync(level);
    }
}
