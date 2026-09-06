package io.farfrontier.palemirror.internal.adapter;

import java.util.List;

import io.farfrontier.palemirror.api.IntegrationAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Read-only discovery SPI; a settlement adapter cannot create or alter a village. */
public interface SettlementAdapter extends IntegrationAdapter {
    List<SettlementObservation> observeNearby(ServerLevel level, BlockPos focus);
}
