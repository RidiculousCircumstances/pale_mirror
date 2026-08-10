package io.farfrontier.palemirror.internal.adapter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record FreightServiceRequest(String serviceId, String connectionId, BlockPos assemblyTrack,
                                    Direction assemblyDirection, String originStation, String destinationStation) {
    public FreightServiceRequest {
        if (serviceId == null || serviceId.isBlank() || connectionId == null || connectionId.isBlank()
                || assemblyTrack == null || assemblyDirection == null || originStation == null || originStation.isBlank()
                || destinationStation == null || destinationStation.isBlank()) {
            throw new IllegalArgumentException("Invalid freight service request");
        }
        assemblyTrack = assemblyTrack.immutable();
    }
}
