package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import java.util.stream.Collectors;

/** Bounded read-only roster/movement view. Diagnostics never request a path or advance a mission. */
final class FrontierV3GroupDiagnosticJson {
    private FrontierV3GroupDiagnosticJson() { }
    static String render(CheckpointImage checkpoint, FrontierWorldState state, UnitGroup group) {
        return render(checkpoint, state, group,
                io.farfrontier.palemirror.frontier.v3.model.navigation.ActorPositionView.canonical(state, checkpoint.instant().ticks()), "CANONICAL",
                movement -> java.util.Optional.empty());
    }
    static String render(CheckpointImage checkpoint, FrontierWorldState state, UnitGroup group,
                         io.farfrontier.palemirror.frontier.v3.model.navigation.ActorPositionView positions, String positionSource,
                         java.util.function.Function<io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement, java.util.Optional<String>> nativeWait) {
        var mission = state.shipments().missions().get(group.mission().id());
        var readiness = io.farfrontier.palemirror.frontier.v3.process.UnitGroupProcess.navigationReadiness(state, group);
        String members = group.members().stream().map(member -> {
            var actor = member.actorId(); var movement = state.actorMovements().get(actor);
            var body = positions.bodyAt(actor);
            var target = group.journey().map(journey -> journey.stations().get(actor));
            String wait = state.actorLocations().get(actor).condition().status() != ActorLifeStatus.ALIVE ? "PARTICIPANT_CASUALTY"
                    : state.humanPopulation().meals().containsKey(actor) ? "SELF_CARE"
                    : movement != null ? (movement.coldTravel().isPresent() ? "TIMED_JOURNEY" : "NATIVE_OR_KNOWN_ROUTE_PENDING")
                    : target.filter(body.supportingSurface()::equals).isPresent() ? "AT_FORMATION_STATION"
                    : target.isPresent() ? "FORMATION_MOVE_PENDING" : "MISSION_BOUNDARY";
            var permission = movement == null ? null : io.farfrontier.palemirror.frontier.v3.process.ActorMovementProviders.require(movement)
                    .movementPermission(state, movement, body.supportingSurface(), checkpoint.instant().ticks(), positions,
                            io.farfrontier.palemirror.frontier.v3.model.navigation.MovementPermission.allow());
            var calculation = movement == null ? java.util.Optional.<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult>empty()
                    : movement.context() instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.GroupLeg
                        ? routeEvidence(state, group, body.supportingSurface(), movement.order().legalStations().getFirst())
                        : java.util.Optional.<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult>empty();
            if (permission != null && !permission.allowed()) wait = permission.reason().name();
            else if (movement != null && movement.coldTravel().isEmpty() && calculation.isPresent())
                wait = "ROUTE_" + calculation.orElseThrow().status();
            else if (movement == null && state.humanPopulation().meals().containsKey(actor))
                wait = "MEAL_" + state.humanPopulation().meals().get(actor).phase();
            else if (mission != null && mission.stage() == TransportMission.Stage.LOADING) wait = "LOADING";
            var physicalWait = movement == null ? java.util.Optional.<String>empty() : nativeWait.apply(movement);
            return "{\"actor\":" + string(actor.value()) + ",\"role\":" + string(member.role().name())
                    + ",\"originalPurpose\":" + string(member.activityKind().name()) + ",\"originalOwner\":" + string(member.activityOwnerId().value())
                    + ",\"body\":" + FrontierV3DiagnosticJson.position(body) + ",\"target\":"
                    + target.map(surface -> FrontierV3DiagnosticJson.position(surface.standingBody())).orElse("null")
                    + ",\"disposition\":" + string(wait) + ",\"coldArrivalTick\":"
                    + (movement == null ? -1 : movement.coldTravel().map(TimedKnownRoute::arrivalTick).orElse(-1L))
                    + ",\"waitingFor\":" + (permission == null ? "null" : permission.waitingFor().map(peer -> string(peer.value())).orElse("null"))
                    + ",\"requestedPaceBlocksPerTick\":" + (permission == null ? "null"
                        : permission.pace().map(pace -> Double.toString(pace.blocksPerTick())).orElse("null"))
                    + ",\"nativeWait\":" + physicalWait.map(FrontierV3GroupDiagnosticJson::string).orElse("null")
                    + ",\"routeReason\":" + calculation.map(result -> string(result.reason())).orElse("null") + "}";
        }).collect(Collectors.joining(","));
        String schedules = checkpoint.schedules().stream().filter(action -> action.subject().equals(group.id())
                || action.subject().equals(group.mission().id()) || group.members().stream().anyMatch(member -> member.actorId().equals(action.subject())))
                .sorted().limit(UnitGroup.MAX_MEMBERS).map(action -> "{\"kind\":" + string(action.kind()) + ",\"dueAt\":" + action.dueAt().ticks() + "}")
                .collect(Collectors.joining(","));
        return FrontierV3DiagnosticJson.base("process", group.id().value(), checkpoint)
                + ",\"status\":\"ok\",\"family\":\"frontier.unit_group\",\"group\":" + string(group.id().value())
                + ",\"mission\":" + string(group.mission().id().value()) + ",\"missionKind\":" + string(group.mission().kind().name())
                + ",\"groupRevision\":" + group.revision() + ",\"goalOrdinal\":" + group.goalOrdinal()
                + ",\"formation\":" + string(group.formation().name()) + ",\"phase\":" + string(group.phase().name())
                + ",\"missionStage\":" + (mission == null ? "null" : string(mission.stage().name()))
                + ",\"routeCursor\":" + group.journey().map(UnitGroup.Journey::cursor).orElse(-1)
                + ",\"routeSize\":" + group.journey().map(journey -> journey.route().size()).orElse(0)
                + ",\"navigationStatus\":" + string(readiness.status()) + ",\"navigationReason\":" + string(readiness.reason())
                + ",\"positionSource\":" + string(positionSource)
                + ",\"provisioningWait\":" + (mission == null ? "null" : supplyWait(state, mission))
                + ",\"members\":[" + members + "],\"schedule\":[" + schedules + "]}";
    }
    private static java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult> routeEvidence(
            FrontierWorldState state, UnitGroup group, SurfaceAnchor start, SurfaceAnchor target) {
        try {
            return io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupMissionPorts.require(group).knowledge(state, group)
                    .planningEvidence(start, target);
        } catch (KnownPedestrianNavigation.RouteUnavailable unsupportedDeparture) {
            return java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult(
                    io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.UNKNOWN_GEOMETRY,
                    java.util.List.of(), 0, 0, unsupportedDeparture.getMessage()));
        }
    }
    private static String supplyWait(FrontierWorldState state, TransportMission mission) {
        if (mission.replenishment().isPresent()) {
            var transfer = mission.replenishment().orElseThrow();
            return string(transfer.pending().isPresent() ? "REFILL_PHYSICAL_RECEIPT"
                    : ServiceAccessCoordinator.available(state, ExpeditionSupplyServiceAccess.identity(mission, transfer))
                        ? "REFILL_APPROACH_OR_TAKE" : "REFILL_SERVICE_ACCESS");
        }
        var next = mission.supplies().flatMap(load -> load.next());
        if (mission.stage() != TransportMission.Stage.LOADING || next.isEmpty()) return "null";
        var allocation = next.orElseThrow();
        return string(allocation.pending().isPresent() ? "LOAD_PHYSICAL_RECEIPT"
                : ServiceAccessCoordinator.available(state, ExpeditionSupplyServiceAccess.identity(mission, allocation))
                    ? "LOAD_APPROACH_OR_TAKE" : "LOAD_SERVICE_ACCESS");
    }
    private static String string(String value) { return "\"" + FrontierV3DiagnosticJson.quote(value) + "\""; }
}
