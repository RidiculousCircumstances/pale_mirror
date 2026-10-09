package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3HotHandoff.Status.*;

/** Exact loaded geometry gates body insertion; a declared plan is not already a physical quarry. */
final class FrontierV3WorksiteHandoff {
    private FrontierV3WorksiteHandoff() { }
    static List<FrontierV3HotHandoff.Check> inspect(ServerLevel level, FrontierWorldState state, ChunkPos chunk) {
        var checks = new ArrayList<FrontierV3HotHandoff.Check>(); var ledger = FrontierV3GrayboxLedger.get(level);
        var byOwner = new LinkedHashMap<io.farfrontier.palemirror.frontier.v3.api.SubjectId, List<WorksiteBlock>>();
        for (var declared : FrontierV3WorksiteRegistry.chunks(state).getOrDefault(chunk.toLong(), List.of()))
            byOwner.computeIfAbsent(declared.key().owner(), ignored -> new ArrayList<>()).add(
                    FrontierV3WorksiteRegistry.owner(declared.key().family()).current(state, declared));
        for (var entry : byOwner.entrySet()) {
            boolean found = false;
            var status = READY; String reason = "current_worksite_cells";
            for (var cell : entry.getValue()) {
                if (Math.floorDiv(cell.position().x(), 16) != chunk.x || Math.floorDiv(cell.position().z(), 16) != chunk.z
                        || cell.key().role() == WorksiteBlock.Role.CONTAINER_SOCKET) continue;
                found = true; var position = new BlockPos(cell.position().x(), cell.position().y(), cell.position().z());
                var witness = ledger.worksite(position);
                if (witness == null || !witness.declaration().key().equals(cell.key())
                        || witness.phase() == FrontierV3WorksiteBlockWitness.Phase.PREPARED) {
                    status = WAITING; reason = "worksite_initial_projection"; break;
                }
                if (witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL
                        || witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING) {
                    status = CONFLICT; reason = "worksite_external_change"; continue;
                }
                var actual = FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position));
                if (FrontierV3WorksiteRegistry.owner(cell.key().family()).committedEffect(state, witness).isPresent()
                        && actual.equals(cell.block())) continue;
                if (witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED) {
                    var exact = FrontierV3WorksiteRegistry.owner(cell.key().family()).effectResumption(state, cell, witness, actual);
                    if (exact) continue; // Its retained owner can rejoin to settle the proved split.
                }
                if (!witness.declaration().equals(cell) || !actual.equals(cell.block())) {
                    status = WAITING; reason = "worksite_canonical_physical_boundary"; break;
                }
            }
            if (found) checks.add(new FrontierV3HotHandoff.Check(entry.getKey(), status, reason));
        }
        return List.copyOf(checks);
    }
}
