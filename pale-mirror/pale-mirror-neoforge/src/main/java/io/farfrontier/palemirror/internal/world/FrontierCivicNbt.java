package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.FrontierCivicState;
import io.farfrontier.palemirror.frontier.FrontierResource;
import io.farfrontier.palemirror.frontier.FrontierSettlement;
import io.farfrontier.palemirror.frontier.FrontierStateHydration;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** NBT adapter for the complete civic ledger embedded in a stable settlement identity. */
final class FrontierCivicNbt {
    private FrontierCivicNbt() { }

    static void write(CompoundTag tag, FrontierSettlement settlement) {
        tag.putLong("netCredit", settlement.netCredit());
        tag.putString("civicState", settlement.civicState().name());
        tag.putInt("threatPermille", settlement.threatPermille());
        tag.putInt("foodReserveDaysMilli", settlement.foodReserveDaysMilli());
        tag.putInt("rationPermille", settlement.rationPermille());
        tag.putLong("civicRevision", settlement.civicRevision());
    }

    static FrontierStateHydration.SettlementState read(CompoundTag tag, Map<FrontierResource, Long> stocks) {
        try {
            if (!tag.contains("civicState", Tag.TAG_STRING) || !tag.contains("threatPermille", Tag.TAG_INT)
                    || !tag.contains("foodReserveDaysMilli", Tag.TAG_INT) || !tag.contains("rationPermille", Tag.TAG_INT)
                    || !tag.contains("civicRevision", Tag.TAG_LONG)) {
                throw new IllegalArgumentException("incomplete civic ledger");
            }
            return new FrontierStateHydration.SettlementState(tag.getString("id"), stocks, tag.getLong("netCredit"),
                    FrontierCivicState.valueOf(tag.getString("civicState")), tag.getInt("threatPermille"),
                    tag.getInt("foodReserveDaysMilli"), tag.getInt("rationPermille"), tag.getLong("civicRevision"));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("invalid Frontier settlement civic ledger", invalid);
        }
    }
}
