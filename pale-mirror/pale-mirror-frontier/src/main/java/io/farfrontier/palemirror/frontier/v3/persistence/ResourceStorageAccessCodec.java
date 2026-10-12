package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.ResourceStorageAccess;
import io.farfrontier.palemirror.frontier.v3.model.ServiceAccessBoundary;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Immutable selected access declaration. Recovery never discovers a replacement warehouse. */
final class ResourceStorageAccessCodec {
    private static final int MAX_BOUNDARY_SURFACES = 4_096;
    private ResourceStorageAccessCodec() { }
    static void write(DataOutputStream output, ResourceStorageAccess access) throws IOException {
        ShipmentStateCodec.endpoint(output, access.endpoint());
        output.writeByte(access.stations().size());
        for (var station : access.stations()) FrontierWorldStateCodec.writePosition(output, station.support());
        var boundary = access.ownedAccessSurfaces();
        if (boundary.size() > MAX_BOUNDARY_SURFACES) throw new IllegalArgumentException("storage access boundary exceeds its safety bound");
        output.writeShort(boundary.size());
        for (var surface : boundary) FrontierWorldStateCodec.writePosition(output, surface.support());
    }
    static ResourceStorageAccess read(DataInputStream input) throws IOException {
        var endpoint = ShipmentStateCodec.endpoint(input);
        int count = input.readUnsignedByte();
        if (count < 1 || count > 8) throw new IllegalArgumentException("selected storage stations exceed their bound");
        var stations = new ArrayList<SurfaceAnchor>();
        for (int index = 0; index < count; index++) stations.add(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(input)));
        count = input.readUnsignedShort();
        if (count < 1 || count > MAX_BOUNDARY_SURFACES) throw new IllegalArgumentException("selected storage boundary exceeds its bound");
        var boundary = new LinkedHashSet<SurfaceAnchor>();
        for (int index = 0; index < count; index++) if (!boundary.add(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(input))))
            throw new IllegalArgumentException("selected storage boundary repeats a surface");
        return new ResourceStorageAccess(endpoint, stations, new ServiceAccessBoundary(boundary));
    }
}
