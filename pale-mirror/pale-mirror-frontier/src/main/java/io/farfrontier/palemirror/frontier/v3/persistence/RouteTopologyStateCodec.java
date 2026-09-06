package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded snapshot encoding for canonical route replacements. */
public final class RouteTopologyStateCodec {
    private RouteTopologyStateCodec() { }
    public static void write(DataOutputStream output, RouteTopology topology) throws IOException {
        List<Map.Entry<SubjectId, List<BlockPosition>>> routes = topology.replacementSupplyRoutes().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        output.writeByte(routes.size());
        for (Map.Entry<SubjectId, List<BlockPosition>> route : routes) {
            FrontierWorldStateCodec.writeString(output, route.getKey().value()); output.writeByte(route.getValue().size());
            for (BlockPosition point : route.getValue()) FrontierWorldStateCodec.writePosition(output, point);
        }
        List<Map.Entry<SubjectId, Map<TraversalEdgeId, TraversalAvailability>>> availability = topology.supplyAvailability().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList();
        output.writeByte(availability.size());
        for (Map.Entry<SubjectId, Map<TraversalEdgeId, TraversalAvailability>> entry : availability) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value()); output.writeShort(entry.getValue().size());
            for (Map.Entry<TraversalEdgeId, TraversalAvailability> edge : entry.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(TraversalEdgeId::value))).toList()) {
                FrontierWorldStateCodec.writeString(output, edge.getKey().value()); output.writeByte(FrontierWireTags.tag(edge.getValue()));
            }
        }
    }
    public static RouteTopology read(DataInputStream input, FrontierBootstrap bootstrap) throws IOException {
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
        Map<SubjectId, Map<TraversalEdgeId, TraversalAvailability>> availability = new LinkedHashMap<>();
        int availabilityOwners = input.readUnsignedByte();
        if (availabilityOwners > RouteTopology.MAX_REPLACEMENTS) throw new IllegalArgumentException("route availability owner count is out of bounds");
        for (int owner = 0; owner < availabilityOwners; owner++) {
            SubjectId settlement = new SubjectId(FrontierWorldStateCodec.readString(input)); int edgeCount = input.readUnsignedShort();
            if (edgeCount > TraversalTopology.MAX_EDGES || availability.containsKey(settlement)) throw new IllegalArgumentException("route availability edge count is invalid");
            Map<TraversalEdgeId, TraversalAvailability> edges = new LinkedHashMap<>();
            for (int edge = 0; edge < edgeCount; edge++) {
                TraversalEdgeId edgeId = new TraversalEdgeId(FrontierWorldStateCodec.readString(input));
                if (edges.put(edgeId, FrontierWireTags.require(TraversalAvailability.class, input.readUnsignedByte())) != null) {
                    throw new IllegalArgumentException("duplicate route availability edge");
                }
            }
            availability.put(settlement, Map.copyOf(edges));
        }
        RouteTopology topology = new RouteTopology(routes, availability);
        for (SubjectId settlement : availability.keySet()) {
            topology.supplyTraversalTopology(bootstrap, settlement);
        }
        return topology;
    }
}
