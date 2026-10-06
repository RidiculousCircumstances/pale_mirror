package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute;
import java.util.stream.Collectors;

/** Bounded read-only roster/movement view. Diagnostics never request a path or advance a mission. */
final class FrontierV3GroupDiagnosticJson {
    private FrontierV3GroupDiagnosticJson() { }
    static String render(CheckpointImage checkpoint, FrontierWorldState state, UnitGroup group) {
        var mission = state.shipments().missions().get(group.mission().id());
        var readiness = io.farfrontier.palemirror.frontier.v3.process.UnitGroupProcess.navigationReadiness(state, group);
        String members = group.members().stream().map(member -> {
            var actor = member.actorId(); var movement = state.actorMovements().get(actor);
            var body = ActorMovementProcess.bodyAt(state, actor, checkpoint.instant().ticks());
            var target = group.journey().map(journey -> journey.stations().get(actor));
            String wait = state.actorLocations().get(actor).condition().status() != ActorLifeStatus.ALIVE ? "PARTICIPANT_CASUALTY"
                    : state.humanPopulation().meals().containsKey(actor) ? "SELF_CARE"
                    : movement != null ? (movement.coldTravel().isPresent() ? "TIMED_JOURNEY" : "NATIVE_OR_KNOWN_ROUTE_PENDING")
                    : target.filter(body.supportingSurface()::equals).isPresent() ? "AT_FORMATION_STATION"
                    : target.isPresent() ? "FORMATION_MOVE_PENDING" : "MISSION_BOUNDARY";
            return "{\"actor\":" + string(actor.value()) + ",\"role\":" + string(member.role().name())
                    + ",\"originalPurpose\":" + string(member.activityKind().name()) + ",\"originalOwner\":" + string(member.activityOwnerId().value())
                    + ",\"body\":" + FrontierV3DiagnosticJson.position(body) + ",\"target\":"
                    + target.map(surface -> FrontierV3DiagnosticJson.position(surface.standingBody())).orElse("null")
                    + ",\"disposition\":" + string(wait) + ",\"coldArrivalTick\":"
                    + (movement == null ? -1 : movement.coldTravel().map(TimedKnownRoute::arrivalTick).orElse(-1L)) + "}";
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
                + ",\"members\":[" + members + "],\"schedule\":[" + schedules + "]}";
    }
    private static String string(String value) { return "\"" + FrontierV3DiagnosticJson.quote(value) + "\""; }
}
