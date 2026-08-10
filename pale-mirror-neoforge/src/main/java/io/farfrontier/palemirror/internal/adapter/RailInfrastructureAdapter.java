package io.farfrontier.palemirror.internal.adapter;

import java.util.Optional;

import io.farfrontier.palemirror.api.IntegrationAdapter;
import net.minecraft.server.level.ServerLevel;

/** Generic managed railway boundary. No provider types may cross it. */
public interface RailInfrastructureAdapter extends IntegrationAdapter {
    void installPlacementAuthority(RailPlacementAuthority authority);
    RailConnectionObservation plan(ServerLevel level, RailConnectionRequest request);
    RailConnectionObservation start(ServerLevel level, String connectionId);
    Optional<RailConnectionObservation> connection(ServerLevel level, String connectionId);
    RailConnectionObservation suspend(ServerLevel level, String connectionId, String reason);
    RailConnectionObservation resume(ServerLevel level, String connectionId);
    FreightServiceObservation ensureFreightService(ServerLevel level, FreightServiceRequest request);
    Optional<FreightServiceObservation> freightService(ServerLevel level, String serviceId);
    FreightServiceObservation parkFreightService(ServerLevel level, String serviceId);
    FreightServiceObservation resumeFreightService(ServerLevel level, String serviceId);
}
