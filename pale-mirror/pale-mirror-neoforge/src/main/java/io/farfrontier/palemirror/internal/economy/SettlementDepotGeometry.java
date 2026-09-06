package io.farfrontier.palemirror.internal.economy;

import io.farfrontier.palemirror.internal.materialization.ParcelRecord;
import io.farfrontier.palemirror.internal.materialization.SemanticCellRecord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** One reusable spatial contract for the depot's mutable functional core. */
public final class SettlementDepotGeometry {
    private SettlementDepotGeometry() { }

    public static String parcelId(String regionId) { return regionId + ":parcel:supply_depot"; }

    public static BlockPos parcelMin(BlockPos anchor) { return anchor.offset(-3, -1, -3); }

    public static BlockPos parcelMax(BlockPos anchor) { return anchor.offset(3, 3, 3); }

    public static List<BlockPos> positions(BlockPos anchor) {
        List<BlockPos> result = new ArrayList<>(27);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) result.add(anchor.offset(x, 0, z));
        }
        result.add(anchor.above());
        result.add(anchor.offset(2, 1, 2));
        return List.copyOf(result);
    }

    public static boolean fits(ParcelRecord parcel, Collection<BlockPos> positions) {
        return positions.stream().allMatch(parcel::contains);
    }

    public static List<SemanticCellRecord> capture(ServerLevel level, BlockPos anchor) {
        return positions(anchor).stream().map(position -> {
            var state = level.getBlockState(position);
            return new SemanticCellRecord(position, state, state);
        }).toList();
    }
}
