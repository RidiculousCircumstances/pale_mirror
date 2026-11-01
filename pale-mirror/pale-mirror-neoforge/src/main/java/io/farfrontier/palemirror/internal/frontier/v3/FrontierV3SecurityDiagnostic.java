package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3DiagnosticJson.*;
/** Read-only family explanation. No role, route, contact or timing mutation. */
final class FrontierV3SecurityDiagnostic {
    static String read(FrontierWorldState state, SubjectId home, long tick) {
        var contact=state.security().contacts().get(home);
        String missions=io.farfrontier.palemirror.frontier.v3.model.GuardEquipment.in(state,home).stream().map(duty->
                "{\"actor\":\""+quote(duty.execution().actorId().value())+"\",\"phase\":\""+duty.phase()+"\",\"mission\":"
                +duty.mission().map(mission->"{\"kind\":\""+(mission.response().isEmpty()?"PATROL":io.farfrontier.palemirror.frontier.v3.model.security.GuardResponse.contact(state,duty,tick).isPresent()?"RESPOND":"INVESTIGATE")
                        +"\",\"targetKind\":\""+mission.targetKind()+"\",\"target\":\""+quote(mission.target().value())
                        +"\",\"station\":"+position(mission.station().support())+",\"watchUntil\":"+mission.watchUntil()
                        +",\"returnStation\":"+position(mission.post().station().support())+"}").orElse("null")
                +",\"strikePending\":"+state.security().pending().containsKey(duty.id())+"}").collect(java.util.stream.Collectors.joining(",","[","]"));
        return "{\"respondingGuards\":"+io.farfrontier.palemirror.frontier.v3.model.GuardEquipment.in(state,home).stream()
                .filter(duty->duty.mission().flatMap(io.farfrontier.palemirror.frontier.v3.model.security.GuardMission::response).isPresent()).count()
                +",\"missions\":"+missions+",\"hostilePlayers\":"+state.security().hostility().keySet().stream().filter(relation->relation.home().equals(home)).count()
                +",\"contactAt\":"+(contact==null?"null":Long.toString(contact.atTick()))
                +",\"contactTarget\":"+(contact==null?"null":"\""+quote(contact.target().entity().toString())+"\"")
                +",\"heldSites\":"+strings(state.siteUses().held(home).stream().map(area->area.id().value()).toList())+"}";
    }

    private FrontierV3SecurityDiagnostic(){}
}
