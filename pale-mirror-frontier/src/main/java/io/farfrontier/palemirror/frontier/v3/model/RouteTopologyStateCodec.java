package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded snapshot encoding for canonical route replacements. */
final class RouteTopologyStateCodec {
    private RouteTopologyStateCodec() { }
    static void write(DataOutputStream output, RouteTopology topology) throws IOException {
        List<Map.Entry<SubjectId, List<BlockPosition>>> routes = topology.replacementSupplyRoutes().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        output.writeByte(routes.size());
        for (Map.Entry<SubjectId, List<BlockPosition>> route : routes) {
            FrontierWorldStateCodec.writeString(output, route.getKey().value()); output.writeByte(route.getValue().size());
            for (BlockPosition point : route.getValue()) FrontierWorldStateCodec.writePosition(output, point);
        }
    }
    static RouteTopology read(DataInputStream input, FrontierBootstrap bootstrap) throws IOException {
        Map<SubjectId, List<BlockPosition>> routes = new LinkedHashMap<>(); int count = input.readUnsignedByte();
        if (count > RouteTopology.MAX_REPLACEMENTS) throw new IllegalArgumentException("route replacement count is out of bounds");
        for (int index = 0; index < count; index++) {
            SubjectId settlement = new SubjectId(FrontierWorldStateCodec.readString(input)); int length = input.readUnsignedByte();
            if (length < RouteTopology.MIN_WAYPOINTS || length > RouteTopology.MAX_WAYPOINTS) {
                throw new IllegalArgumentException("route replacement path size is out of bounds");
            }
            java.util.ArrayList<BlockPosition> route = new java.util.ArrayList<>();
            for (int point = 0; point < length; point++) route.add(FrontierWorldStateCodec.readPosition(input));
            FrontierRouteNetwork.validateSupplyWaypoints(bootstrap, settlement, route);
            if (routes.put(settlement, List.copyOf(route)) != null) throw new IllegalArgumentException("duplicate replacement route settlement");
        }
        return new RouteTopology(routes);
    }
}
