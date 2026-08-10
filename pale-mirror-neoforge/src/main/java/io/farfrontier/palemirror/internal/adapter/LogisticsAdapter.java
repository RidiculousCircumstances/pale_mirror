package io.farfrontier.palemirror.internal.adapter;

import io.farfrontier.palemirror.api.IntegrationAdapter;
import net.minecraft.server.level.ServerLevel;

/** Read-only bridge for a player-built supply route. It never creates or drives the foreign machinery. */
public interface LogisticsAdapter extends IntegrationAdapter {
    LogisticsRouteObservation observe(ServerLevel level, LogisticsRouteContract contract);
}
