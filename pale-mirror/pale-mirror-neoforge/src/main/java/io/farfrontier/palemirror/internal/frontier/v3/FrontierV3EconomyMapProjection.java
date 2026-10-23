package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementProjection;
import java.util.*;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3EconomyProjection.row;

/** Bounded, off-thread map view of retained state. Never loads terrain or requests a route. */
final class FrontierV3EconomyMapProjection {
    static final int MAX_MISSIONS = 256;
    static final int MAX_ROUTE_POINTS = 128;
    private FrontierV3EconomyMapProjection() { }
    static Map<String, Object> snapshot(FrontierWorldState state, long tick) {
        var bounds = state.bootstrap().bounds();
        var active = state.shipments().missions().values().stream()
                .filter(mission -> mission.stage() != TransportMission.Stage.COMPLETE)
                .sorted(Comparator.comparing(TransportMission::id)).toList();
        var missions = new ArrayList<Map<String, Object>>();
        for (var mission : active.stream().limit(MAX_MISSIONS).toList()) {
            var group = state.unitGroups().groups().get(mission.groupId());
            var members = new ArrayList<Map<String, Object>>();
            if (group != null) for (var member : group.members()) {
                if (!state.actorLocations().containsKey(member.actorId())) continue;
                var position = ActorMovementProjection.bodyAt(state, member.actorId(), tick);
                members.add(row("actor", member.actorId(), "role", member.role().name(),
                        "x", position.x(), "y", position.y(), "z", position.z()));
            }
            var journey = group == null ? Optional.<io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Journey>empty() : group.journey();
            var route = journey.map(value -> route(value.route())).orElse(List.of());
            var cargo = mission.shipmentIds().stream().map(id -> state.shipments().shipments().get(id))
                    .filter(Objects::nonNull).map(shipment -> row("item", shipment.itemKind(),
                            "quantity", shipment.quantity(), "status", shipment.status().name())).toList();
            missions.add(row("id", mission.id(), "members", members, "route", route,
                    "routeSampled", journey.map(value -> value.route().size() > MAX_ROUTE_POINTS).orElse(false),
                    "cargo", cargo));
        }
        return row("schema", 1, "bounds", row("minX", bounds.minX(), "minZ", bounds.minZ(),
                "width", bounds.width(), "depth", bounds.depth()), "missions", missions,
                "activeCount", active.size(), "shownCount", missions.size());
    }
    /** Display sample only, never an executable path; preserve both endpoints and bound work. */
    static List<Map<String, Object>> route(List<SurfaceAnchor> route) {
        if (route.isEmpty()) return List.of();
        int count = Math.min(route.size(), MAX_ROUTE_POINTS);
        var points = new ArrayList<Map<String, Object>>(count);
        for (int i = 0; i < count; i++) {
            int index = count == 1 ? 0 : (int) ((long) i * (route.size() - 1) / (count - 1));
            var point = route.get(index);
            points.add(row("x", point.x(), "y", point.y(), "z", point.z()));
        }
        return List.copyOf(points);
    }
}
