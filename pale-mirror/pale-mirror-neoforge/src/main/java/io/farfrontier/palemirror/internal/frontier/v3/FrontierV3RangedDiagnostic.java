package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3DiagnosticJson.*;
/** Bounded read-only explanation of the declared weapon method and actual personal ammunition. */
final class FrontierV3RangedDiagnostic {
    static String actor(FrontierWorldState state,SubjectId subject){
        var guard=state.strategicPlans().guards().values().stream().filter(g->g.execution().actorId().equals(subject)).findFirst();
        return guard.map(g->",\"guardMethod\":\""+g.method()+"\",\"arrowsCarried\":"
                +io.farfrontier.palemirror.frontier.v3.model.combat.Ammunition.available(state,subject,g.home(),"minecraft:arrow")
                +",\"shotPrepared\":"+state.combat().ranged().pending().containsKey(g.id())
                +",\"nextShotAt\":"+state.combat().ranged().nextAttack().getOrDefault(g.id(),0L)).orElse("");
    }
    static String settlement(FrontierWorldState state,SubjectId subject){
        var crossbow=GuardEquipment.in(state,subject).stream().filter(g->g.method()==io.farfrontier.palemirror.frontier.v3.model.combat.CombatMethod.CROSSBOW)
                .filter(g->state.actorLocations().get(g.execution().actorId()).condition().status()==ActorLifeStatus.ALIVE && GuardEquipment.held(state,g)).findFirst();
        return ""
                + ",\"hasArmedCrossbow\":"+crossbow.isPresent()
                + ",\"crossbowActor\":\""+crossbow.map(g->g.execution().actorId().value()).orElse("")+"\""
                + ",\"crossbowName\":\""+quote(crossbow.map(g->state.humanPopulation().resident(g.execution().actorId()).name()).orElse(""))+"\""
                + ",\"crossbowHasAmmunition\":"+crossbow.filter(g->Ammunition.available(state,g.execution().actorId(),g.home(),"minecraft:arrow")>0).isPresent()
                + ",\"crossbowHasFired\":"+crossbow.filter(g->state.inventory().items().get(g.weapon()).condition().damage()>0).isPresent()
                + ",\"crossbowPosition\":"+crossbow.map(g->position(state.actorLocations().get(g.execution().actorId()).body())).orElse("null");
    }
    static String render(String id,io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView checkpoint,FrontierWorldState state){
        var home=subject(id).orElse(null);
        if(home==null || state.bootstrap().settlements().stream().noneMatch(h->h.id().equals(home)))return unavailable("settlement_security",id,checkpoint,"not_found");
        var guards=GuardEquipment.in(state,home);
        return base("settlement_security",id,checkpoint)+",\"status\":\"ok\",\"guardsAssigned\":"+guards.size()
                +",\"guardsReady\":"+guards.stream().filter(g->state.actorLocations().get(g.execution().actorId()).condition().status()==ActorLifeStatus.ALIVE && GuardEquipment.held(state,g)).count()+settlement(state,home)
                +",\"guards\":"+guards.stream().map(g->"{\"actor\":\""+quote(g.execution().actorId().value())+"\",\"name\":\""+quote(state.humanPopulation().resident(g.execution().actorId()).name())
                    +"\",\"method\":\""+g.method()+"\",\"phase\":\""+g.phase()+"\",\"arrows\":"+Ammunition.available(state,g.execution().actorId(),home,"minecraft:arrow")+"}")
                    .collect(java.util.stream.Collectors.joining(",","[","]"))+"}";
    }
    private FrontierV3RangedDiagnostic(){}
}
