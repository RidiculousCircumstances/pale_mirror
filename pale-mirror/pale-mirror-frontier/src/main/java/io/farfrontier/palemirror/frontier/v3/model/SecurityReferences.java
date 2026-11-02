package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
/** Combat relation closure, not health, body or equipment ownership. */
final class SecurityReferences {
    private static final ImmutableInvariantCheck<Void> REFERENCES=new ImmutableInvariantCheck<>();
    static void validate(FrontierBootstrap bootstrap,SecurityState security,StrategicPlanState plans) {
        REFERENCES.validate(()->{
            for(var relation:security.hostility().keySet()) FrontierWorldStateSupport.settlement(bootstrap,relation.home());
            for(var contact:security.contacts().values()) {
                FrontierWorldStateSupport.settlement(bootstrap,contact.home());
                if(contact.target().kind()==CombatTarget.Kind.PLAYER && !security.hostile(contact.home(),contact.target().entity()))
                    throw new IllegalArgumentException("player threat contact has no declared hostile relation");
            }
            for(var sight:security.awareness().sights().values()) {
                FrontierWorldStateSupport.settlement(bootstrap,sight.key().scope());
                if(sight.key().target().kind()==CombatTarget.Kind.PLAYER && !security.hostile(sight.key().scope(),sight.key().target().entity()))
                    throw new IllegalArgumentException("personal player sight lost hostile relation");
            }
            for(var harm:security.awareness().harms().values())FrontierWorldStateSupport.settlement(bootstrap,harm.key().scope());
            for(var response:security.responses().values()) {
                var duty=plans.guards().get(response.duty());
                if(duty==null || !duty.home().equals(response.contact().home()))
                    throw new IllegalArgumentException("response lost its declared guard/home relationship");
            }
            for(var strike:security.pending().values()) {
                var duty=plans.guards().get(strike.duty());
                if(duty==null || !duty.execution().equals(strike.attacker().execution()) || duty.revision()!=strike.dutyRevision()
                        || !duty.weapon().equals(strike.weapon()) || !strike.attacker().body().actorId().equals(duty.execution().actorId()))
                    throw new IllegalArgumentException("pending combat effect lost its exact guard execution/weapon");
            }
            for(var duty:security.nextAttack().keySet()) if(!plans.guards().containsKey(duty))
                throw new IllegalArgumentException("attack readiness retained a retired owner");
        },bootstrap,security,plans.guards());
    }
    private SecurityReferences(){}
}
