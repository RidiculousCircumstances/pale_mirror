package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Structural owner of confirmed native changes. Uses the shared write bridge, journal and geometry observer. */
final class FrontierV3StructureBlockWrites implements FrontierV3ManagedBlockWrites.Owner {
    private static final Map<FrontierV3ServerRuntime<?, ?>, Iterator<BlockPosition>> LOSSES = new WeakHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<Long, Long>> LIVE_PREPARATIONS = new WeakHashMap<>();
    private static final int MAX_UNCONFIRMED_NATIVE_TICKS = 200; // Bounded unused permission retention, never delayed automatic replay.
    @Override public FrontierV3ManagedBlockWrites.OwnerKind family() { return FrontierV3ManagedBlockWrites.OwnerKind.STRUCTURE; }
    @Override public boolean defersPlayerInput(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            List<FrontierV3ManagedBlockWrites.PlannedWrite> writes) {
        var ledger=FrontierV3GrayboxLedger.get(level);
        return writes.stream().map(write -> ledger.claim(write.position())).filter(Objects::nonNull)
                .anyMatch(claim -> claim.targetTag()==PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE.wireTag());
    }
    @Override public boolean preparePlannedWrites(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            List<FrontierV3ManagedBlockWrites.PlannedWrite> writes, Runnable resume) {
        boolean ready=true;
        for (var write:writes) ready &= observe(level,runtime,write.position(),write.replacement(),false);
        if (!ready) {
            var ledger=FrontierV3GrayboxLedger.get(level);
            ledger.afterDurable(ledger.persistAsync(level),resume);
        }
        return ready;
    }
    @Override public void finishPlannedWrites(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            List<FrontierV3ManagedBlockWrites.PlannedWrite> writes) {
        var ledger=FrontierV3GrayboxLedger.get(level);
        for (var write:writes) {
            var capture=ledger.structuralChange(write.position());
            if (capture!=null && !capture.hasUUID("receipt")
                    && capture.getCompound("before").equals(NbtUtils.writeBlockState(level.getBlockState(write.position())))) {
                ledger.finishStructuralChange(write.position());
                var live=LIVE_PREPARATIONS.get(runtime);if(live!=null)live.remove(write.position().asLong());
            }
        }
        ledger.persistAsync(level);
    }
    @Override public boolean observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            BlockPos position, BlockState replacement, boolean committed) {
        var ledger = FrontierV3GrayboxLedger.get(level); var claim = ledger.claim(position);
        if (claim == null || claim.targetTag() != PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE.wireTag()) return true;
        // Initial projection writes an unclaimed cell; a changed owned cell always has explicit provenance.
        var actual = level.getBlockState(position);
        if (committed) return true; // Bounded owner reconciliation publishes only the final observed outcome.
        var state = runtime.passiveOwnershipState().orElseThrow();
        var raw = new BlockPosition(position.getX(), position.getY(), position.getZ());
        if (!claim.deferred() && !state.physicalDeltas().containsKey(raw)
                && FrontierV3GrayboxPalette.sameStructure(FrontierV3GrayboxExecutor.material(StructureMaterial.require(claim.material())), replacement)) return true;
        if (actual.equals(replacement) || FrontierV3GrayboxPalette.sameStructure(FrontierV3GrayboxExecutor.material(StructureMaterial.require(claim.material())), replacement)
                && FrontierV3GrayboxPalette.sameStructure(actual, replacement)) return true; // Door/furnace operating properties are not physical damage.
        var capture = ledger.structuralChange(position);
        if (capture != null) return capture.getCompound("after").equals(NbtUtils.writeBlockState(replacement))
                && ledger.prepareStructuralChange(position, capture);
        LIVE_PREPARATIONS.computeIfAbsent(runtime, ignored -> new HashMap<>()).putIfAbsent(position.asLong(), level.getGameTime());
        capture = capture(position, claim, actual, replacement);
        return ledger.prepareStructuralChange(position, capture);
    }
    private static CompoundTag capture(BlockPos position, FrontierV3GrayboxLedger.Claim claim, BlockState before, BlockState after) {
        var capture = new CompoundTag(); capture.putLong("pos", position.asLong());
        capture.putString("owner", claim.owner()); capture.putInt("targetTag", claim.targetTag());
        capture.putString("part", claim.semanticPart()); capture.putString("material", claim.material());
        capture.put("before", NbtUtils.writeBlockState(before)); capture.put("after", NbtUtils.writeBlockState(after));
        return capture;
    }
    static void reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var ledger = FrontierV3GrayboxLedger.get(level);
        for (var position : ledger.pendingStructuralChanges()) {
            if (!FrontierV3LoadedWorld.ready(level, position)) continue;
            var capture = ledger.structuralChange(position);
            var live = LIVE_PREPARATIONS.getOrDefault(runtime, Map.of()).get(position.asLong());
            if (live != null && level.getGameTime() - live < MAX_UNCONFIRMED_NATIVE_TICKS
                    && capture.getCompound("before").equals(NbtUtils.writeBlockState(level.getBlockState(position)))) continue;
            if (publish(level, runtime, ledger, position)) {
                ledger.finishStructuralChange(position); ledger.persistAsync(level);
                var preparations = LIVE_PREPARATIONS.get(runtime); if (preparations != null) preparations.remove(position.asLong());
            }
            return; // Exact bounded progress; no global building scan on ordinary ticks.
        }
        var state = runtime.decodedState().orElseThrow();
        var losses = LOSSES.get(runtime);
        if (losses == null || !losses.hasNext()) {
            losses = state.physicalDeltas().keySet().iterator(); LOSSES.put(runtime, losses);
        }
        for (int i = 0; i < 8 && losses.hasNext(); i++) {
            var p = losses.next(); var delta = state.physicalDeltas().get(p);
            if (delta == null || delta.semanticTarget().filter(t -> t.kind() == PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE).isEmpty()) continue;
            var position = new BlockPos(p.x(), p.y(), p.z());
            if (!FrontierV3LoadedWorld.ready(level, position)) continue;
            var claim = ledger.claim(position);
            if (claim != null && FrontierV3GrayboxPalette.sameStructure(FrontierV3GrayboxExecutor.material(StructureMaterial.require(claim.material())), level.getBlockState(position))) {
                publish(level, runtime, ledger, position); return;
            }
        }
    }
    private static boolean publish(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierV3GrayboxLedger ledger, BlockPos position) {
        var claim = ledger.claim(position); var capture = ledger.structuralChange(position);
        if (claim == null || claim.targetTag() != PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE.wireTag()
                || capture != null && (!claim.owner().equals(capture.getString("owner"))
                    || claim.targetTag() != capture.getInt("targetTag") || !claim.material().equals(capture.getString("material"))
                    || !claim.semanticPart().equals(capture.getString("part"))))
            throw new IllegalStateException("structural capture lost its exact declared owner at " + position);
        var observation = FrontierV3NavigationShapes.capture(level, new BlockPosition(position.getX(), position.getY(), position.getZ()));
        if (observation.cell().isEmpty()) return false;
        var material = StructureMaterial.require(claim.material());
        boolean original = FrontierV3GrayboxPalette.sameStructure(FrontierV3GrayboxExecutor.material(material), level.getBlockState(position))
                && (!(material instanceof StructureMaterial.Authored authored) || authored.data().isEmpty()
                    || FrontierV3AuthoredBlockData.matches(level, position, authored.data().orElseThrow()));
        var current = runtime.decodedState().orElseThrow();
        var raw = new BlockPosition(position.getX(), position.getY(), position.getZ());
        boolean acknowledged = original ? !current.physicalDeltas().containsKey(raw)
                : current.structureDamage().getOrDefault(new SubjectId(claim.owner()), StructureDamage.empty(new SubjectId(claim.owner())))
                    .cells().containsKey(raw) && observation.equals(current.navigationGeometry().cells().get(raw));
        if (!acknowledged) {
            if (capture == null) {
                capture = capture(position, claim, level.getBlockState(position), level.getBlockState(position));
                ledger.prepareStructuralChange(position, capture); // Observed restoration gets the same retained delivery identity.
            }
            var operations = FrontierV3OperationLedger.get(level);
            UUID ticket;
            if (capture != null && capture.hasUUID("receipt")) ticket = capture.getUUID("receipt");
            else {
                var fact = new StructureCellObserved(new SubjectId(claim.owner()), raw,
                        GrayboxSemanticPart.valueOf(claim.semanticPart()), observation, original, "native:block-change");
                ticket = FrontierV3ConfirmedNativeEvent.capture(level, fact.structureId(), fact);
                if (capture != null) ledger.bindStructuralReceipt(position, ticket);
            }
            var command = operations.ownerCommand(ticket).orElseThrow(() -> new IllegalStateException("structural outcome lost its retained consequence"));
            if (!FrontierV3OperationConfirmation.submitRequired(level, runtime, ticket, command.consequence())) return false;
        }
        if (original) {
            if (claim.deferred()) ledger.repaired(position, claim.owner(), claim.targetTag(), claim.material(), claim.semanticPart());
        } else ledger.defer(position);
        ledger.persistAsync(level); return true;
    }
}
