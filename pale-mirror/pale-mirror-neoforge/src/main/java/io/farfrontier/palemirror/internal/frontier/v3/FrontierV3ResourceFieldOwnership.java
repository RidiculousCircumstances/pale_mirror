package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Read-only membership of current cell-owned fields; no old blast/stage protocol. */
final class FrontierV3ResourceFieldOwnership {
    private FrontierV3ResourceFieldOwnership() { }
    static Set<Long> activeOwnedCells(ServerLevel level, FrontierWorldState state) {
        var claims = FrontierV3ResourceSiteLedger.get(level);
        return state.resourceSiteDescriptors().values().stream()
                .filter(site -> claims.fieldClaim(site.id()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner
                        && owner.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE)
                .flatMap(site -> Stream.concat(site.soilSlots().stream(), site.cropSlots().stream()))
                .map(position -> new BlockPos(position.x(), position.y(), position.z()).asLong())
                .collect(Collectors.toUnmodifiableSet());
    }
}
