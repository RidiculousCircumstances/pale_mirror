package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldWorkAccessObserved;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/** Bounded, naturally loaded observation of one area-work target's headroom. */
final class FrontierV3ResourceFieldWorkAccessExecutor {
    private FrontierV3ResourceFieldWorkAccessExecutor() { }

    record Reading(boolean blocked, String blockId) { }

    /** Read only the physical standing headroom; crop and soil have separate owners. */
    static Optional<Reading> read(ServerLevel level, ResourceFieldLayout.Cell cell) {
        var headroom = cell.workstation().support().offset(0, 2, 0);
        var physical = new BlockPos(headroom.x(), headroom.y(), headroom.z());
        if (!level.hasChunkAt(physical)) return Optional.empty();
        var block = level.getBlockState(physical);
        return Optional.of(new Reading(!block.getCollisionShape(level, physical).isEmpty(),
                BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString()));
    }

    static boolean observeOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                              SubjectId siteId, ResourceFieldLayout.CellId cellId) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null || state.resourceSites().hasPendingCellMutation(siteId, cellId)) return false;
        var cycle = state.resourceSites().cycle(siteId);
        if (cycle.pendingPlayerBreaks().containsKey(cellId)) return false;
        var claim = FrontierV3ResourceSiteLedger.get(level).siteClaim(siteId);
        if (!(claim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim claimed)
                || !(claimed.claim() instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || !owner.witness().matchesCycle(cycle)
                || owner.witness().cell(cellId).pending().isPresent()) return false;
        var cell = cycle.layout().requireCell(cellId);
        var headroom = cell.workstation().support().offset(0, 2, 0);
        var reading = read(level, cell);
        if (reading.isEmpty()) return false;
        boolean blocked = reading.orElseThrow().blocked();
        if (blocked == cycle.cell(cellId).workAccessBlocked()) return false;
        var observed = new ResourceFieldWorkAccessObserved(siteId, cycle.epoch(), cycle.layout().revision(),
                cellId, headroom, blocked, reading.orElseThrow().blockId(), Optional.empty());
        var checkpoint = runtime.canonicalState().orElseThrow();
        CommandId id = new CommandId("executor:field-work-access-r" + checkpoint.revision().value());
        var result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(),
                checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), observed))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        FrontierV3DiagnosticTrace.record(level.getServer(), "field-work-access:" + siteId.value(),
                "resource_field_work_access_observed", siteId, result);
        return result instanceof CommandResult.Accepted;
    }
}
