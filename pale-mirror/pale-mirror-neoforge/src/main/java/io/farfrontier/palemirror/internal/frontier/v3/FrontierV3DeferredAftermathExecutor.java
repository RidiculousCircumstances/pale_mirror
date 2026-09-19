package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * Realizes one naturally loaded deferred aftermath cell.  It is deliberately an aftermath
 * writer, not an explosion executor: no entity, projectile, blast, force-load, or terrain scan
 * is recreated here.  RUNNING is durable before its single write; the graybox tombstone lets a
 * restart recognize that write as ours rather than foreign drift.
 */
final class FrontierV3DeferredAftermathExecutor {
    private FrontierV3DeferredAftermathExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        tick(FrontierV3AftermathPhysicalWorld.minecraft(level), runtime);
    }

    static void tick(FrontierV3AftermathPhysicalWorld world, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        // Availability is a local chunk fact.  A lexically older COLD record in an unloaded
        // chunk must never starve a naturally loaded one elsewhere.
        state.deferredAftermath().entries().values().stream().filter(value -> !value.terminal())
                .flatMap(value -> candidates(value).stream()).filter(candidate -> world.naturallyLoaded(candidate.cell().position()))
                .sorted(Comparator.comparing((Candidate value) -> DeferredAftermath.chunkKey(value.cell().position()))
                        .thenComparing(value -> value.aftermath().id()).thenComparingInt(Candidate::cursor))
                .findFirst().ifPresent(value -> execute(world, runtime, value.aftermath(), value.cursor(), value.cell()));
    }

    private static java.util.List<Candidate> candidates(DeferredAftermath aftermath) {
        int running = aftermath.runningCursor();
        if (running >= 0) return java.util.List.of(new Candidate(aftermath, running, aftermath.cellAt(running)));
        return IntStream.range(0, aftermath.cells().size()).filter(index -> aftermath.cellAt(index).status() == DeferredAftermathCellStatus.PENDING)
                .mapToObj(index -> new Candidate(aftermath, index, aftermath.cellAt(index))).toList();
    }
    private static BlockPos block(DeferredAftermathCell cell) { return new BlockPos(cell.position().x(), cell.position().y(), cell.position().z()); }

    private static void execute(FrontierV3AftermathPhysicalWorld world, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, DeferredAftermath aftermath, int cursor, DeferredAftermathCell cell) {
        if (cell == null) return;
        if (!world.naturallyLoaded(cell.position())) return;
        // Shielding was not observed at COLD commit.  A later naturally loaded inspection may
        // abandon exactly this cell, but may not turn absence of knowledge into write authority.
        if (aftermath.knowledge() == DeferredAftermathKnowledge.UNKNOWN_SHIELDING) {
            resolve(runtime, aftermath, cursor, cell.authorityRevision(), DeferredAftermathCellStatus.CONFLICTED);
            return;
        }
        FrontierV3GrayboxLedger ledger = world.ledger();
        if (cell.status() == DeferredAftermathCellStatus.RUNNING) {
            FrontierV3GrayboxLedger.Claim claim = ledger.claim(block(cell));
            // A COLD loss can be projected as an exact tombstone before this executor visits
            // the naturally loaded cell.  That tombstone/AIR pair is already the requested
            // physical postcondition.  A materialized RUNNING effect instead retains the one
            // preclaim -> damage revision transition: the durable boundary stores the expected
            // post-damage revision, never an unretained pre-write sentinel.
            if (claim != null && claim.deferred() && exactClaim(claim, cell)
                    && claim.revision() == cell.authorityRevision() && world.isAir(cell.position())) {
                resolve(runtime, aftermath, cursor, cell.authorityRevision(), DeferredAftermathCellStatus.REALIZED);
            } else if (claim != null && !claim.deferred() && exactClaim(claim, cell)
                    && claim.revision() + 1L == cell.authorityRevision() && world.hasMaterial(cell.position(), cell.expectedMaterial())) {
                applyDamage(world, runtime, aftermath, cursor, cell, ledger, claim);
            } else conflicted(runtime, aftermath, cursor, ledger, claim, cell);
            return;
        }
        FrontierV3GrayboxLedger.Claim claim = ledger.claim(block(cell));
        // Projection is the declared first-observation owner for a naturally loaded structural
        // cell.  AIR without its claim is therefore not foreign evidence: the bounded graybox
        // cursor may simply not have reached this exact masked baseline candidate yet.  Leave
        // the consequence pending until that owner records either the exact tombstone or the
        // real world evidence.  A non-air unclaimed block is already positive foreign evidence
        // and remains an isolated conflict below.
        if (claim == null && world.isAir(cell.position())) return;
        if (claim != null && claim.deferred() && exactClaim(claim, cell) && world.isAir(cell.position())) {
            // Cause-time projection has already retained this exact loss; make that observed
            // completion durable without writing the world or reclassifying it as a conflict.
            resolve(runtime, aftermath, cursor, claim.revision(), DeferredAftermathCellStatus.RUNNING);
            return;
        }
        if (claim == null || claim.deferred() || !exactClaim(claim, cell) || !world.hasMaterial(cell.position(), cell.expectedMaterial())) {
            conflicted(runtime, aftermath, cursor, ledger, claim, cell);
            return;
        }
        long damageRevision;
        try { damageRevision = Math.addExact(claim.revision(), 1L); }
        catch (ArithmeticException exhausted) { conflicted(runtime, aftermath, cursor, ledger, claim, cell); return; }
        if (!resolve(runtime, aftermath, cursor, damageRevision, DeferredAftermathCellStatus.RUNNING)) return;
        // Refresh after the durable-before-effect command.  A different canonical state never
        // grants the old observation precondition permission to write.
        FrontierWorldState current = runtime.decodedState().orElse(null);
        DeferredAftermath running = current == null ? null : current.deferredAftermath().entries().get(aftermath.id());
        if (running == null || running.terminal() || running.cellAt(cursor) == null || running.cellAt(cursor).status() != DeferredAftermathCellStatus.RUNNING) return;
        applyDamage(world, runtime, running, cursor, running.cellAt(cursor), ledger, claim);
    }

    private static void applyDamage(FrontierV3AftermathPhysicalWorld world, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, DeferredAftermath aftermath,
                                    int cursor, DeferredAftermathCell cell, FrontierV3GrayboxLedger ledger, FrontierV3GrayboxLedger.Claim claim) {
        if (!world.clear(cell.position())) {
            conflicted(runtime, aftermath, cursor, ledger, claim, cell); return;
        }
        ledger.damaged(block(cell), claim.owner(), claim.targetTag(), claim.material(), claim.semanticPart());
        FrontierV3GrayboxLedger.Claim damaged = ledger.claim(block(cell));
        if (damaged == null || !damaged.deferred() || !exactClaim(damaged, cell) || damaged.revision() != cell.authorityRevision()) {
            conflicted(runtime, aftermath, cursor, ledger, damaged, cell); return;
        }
        resolve(runtime, aftermath, cursor, cell.authorityRevision(), DeferredAftermathCellStatus.REALIZED);
    }

    private static boolean exactClaim(FrontierV3GrayboxLedger.Claim claim, DeferredAftermathCell cell) {
        return claim.owner().equals(cell.expectedOwner().value()) && claim.targetTag() == cell.semanticTarget().kind().wireTag() && claim.material().equals(cell.expectedMaterial().name())
                && claim.semanticPart().equals(cell.expectedPart().name());
    }
    private static boolean conflicted(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, DeferredAftermath aftermath, int cursor,
                                      FrontierV3GrayboxLedger ledger, FrontierV3GrayboxLedger.Claim claim, DeferredAftermathCell cell) {
        if (claim != null && exactClaim(claim, cell)) ledger.defer(block(cell));
        return resolve(runtime, aftermath, cursor, cell.authorityRevision(), DeferredAftermathCellStatus.CONFLICTED);
    }
    private static boolean resolve(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, DeferredAftermath aftermath, int cursor, long authorityRevision,
                                   DeferredAftermathCellStatus result) {
        var checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId id = new CommandId("executor:deferred-aftermath-" + result.name().toLowerCase() + "-"
                + aftermath.id().value().replace(':', '-') + "-" + cursor);
        CommandResult command = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id),
                new DeferredAftermathResolved(aftermath.id(), aftermath.expectedEpoch(), checkpoint.instant().ticks(), cursor, authorityRevision, result)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (!(command instanceof CommandResult.Accepted)) throw new IllegalStateException("deferred aftermath resolution was rejected: " + command);
        return true;
    }
    private record Candidate(DeferredAftermath aftermath, int cursor, DeferredAftermathCell cell) { }
}
