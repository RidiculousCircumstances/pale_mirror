package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3DiagnosticJson.*;
/** Read-only family explanation. No role, route, contact or timing mutation. */
final class FrontierV3SecurityDiagnostic {
    static String read(FrontierWorldState state, SubjectId home, long tick) {
        var contacts=state.security().contactsIn(home);
        var contact=contacts.size()==1?contacts.getFirst():null;
        var responses=state.security().responses().values().stream().filter(r -> r.contact().home().equals(home))
                .sorted(java.util.Comparator.comparing(io.farfrontier.palemirror.frontier.v3.model.security.SecurityState.Response::duty)).toList();
        long available=io.farfrontier.palemirror.frontier.v3.model.GuardEquipment.in(state,home).stream()
                .filter(duty -> io.farfrontier.palemirror.frontier.v3.model.security.SecurityDispatch.eligible(state,duty)).count();
        long posted=io.farfrontier.palemirror.frontier.v3.model.GuardEquipment.in(state,home).stream()
                .filter(duty -> io.farfrontier.palemirror.frontier.v3.model.security.SecurityDispatch.eligible(state,duty)
                        && !state.security().responses().containsKey(duty.id())).count();
        String threats=contacts.stream().map(c -> "{\"target\":\""+c.target().entity()+"\",\"kind\":\""+c.target().kind()
                +"\",\"atTick\":"+c.atTick()+",\"position\":"+position(c.position())+",\"responders\":"
                +strings(responses.stream().filter(r -> r.contact().target().equals(c.target())).map(r -> r.duty().value()).toList())+"}")
                .collect(java.util.stream.Collectors.joining(",","[","]"));
        String missions=io.farfrontier.palemirror.frontier.v3.model.GuardEquipment.in(state,home).stream().map(duty->
                "{\"actor\":\""+quote(duty.execution().actorId().value())+"\",\"phase\":\""+duty.phase()+"\",\"mission\":"
                +duty.mission().map(mission->"{\"kind\":\""+mission.stage()
                        +"\",\"targetKind\":\""+mission.targetKind()+"\",\"target\":\""+quote(mission.target().value())
                        +"\",\"station\":"+position(mission.station().support())+",\"watchUntil\":"+mission.watchUntil()
                        +",\"returnStation\":"+position(mission.post().station().support())+"}").orElse("null")
                +",\"strikePending\":"+state.security().pending().containsKey(duty.id())+"}").collect(java.util.stream.Collectors.joining(",","[","]"));
        return "{\"respondingGuards\":"+responses.size()+",\"postedGuards\":"+posted
                +",\"responseBudget\":"+io.farfrontier.palemirror.frontier.v3.model.security.SecurityDispatch.budget(state,(int)available)
                +",\"contactCount\":"+contacts.size()+",\"threats\":"+threats
                +",\"missions\":"+missions+",\"hostilePlayers\":"+state.security().hostility().keySet().stream().filter(relation->relation.home().equals(home)).count()
                +",\"contactAt\":"+(contact==null?"null":Long.toString(contact.atTick()))
                +",\"contactKind\":"+(contact==null?"null":"\""+contact.target().kind()+"\"")
                +",\"contactTarget\":"+(contact==null?"null":"\""+quote(contact.target().entity().toString())+"\"")
                +",\"heldSites\":"+strings(state.siteUses().held(home).stream().map(area->area.id().value()).toList())+"}";
    }

    private FrontierV3SecurityDiagnostic(){}
}
