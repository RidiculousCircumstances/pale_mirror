package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Read-only progress of the exact retained meal; observing it cannot plan, move or release a visitor. */
public record ResidentMealProgressObligation(long generation, String fingerprint, long budgetTicks,
        String disposition, String next) {
    public static ResidentMealProgressObligation describe(FrontierWorldState state, SubjectId actor) {
        var meal=state.humanPopulation().meals().get(actor);
        var execution=state.actorExecutions().actors().get(actor);
        long generation=execution==null?1:Math.max(1,execution.generation());
        if(meal==null)return new ResidentMealProgressObligation(generation,"CLOSED",ActorMovementProcess.maximumJourneyTicks(),"TERMINAL","CLOSED");
        var body=state.actorLocations().get(actor).body();
        String disposition=meal.waitReason().isPresent()?"HIGHER_PRIORITY_ACTIVITY"
                : !meal.portable()&&ReferenceContainerCustody.blocksCanonicalUse(state,meal.depotId())?"RECOVERY_UNKNOWN"
                : !meal.portable()&&meal.phase()==ResidentMeal.Phase.MOVE
                    && !ResidentMealServiceAccess.available(state,meal.depotId(),actor)?"SERVICE_ACCESS":"ELIGIBLE";
        String next=meal.pendingPhysicalStep().isPresent()?"PHYSICAL_RECEIPT":meal.phase().name();
        return new ResidentMealProgressObligation(generation,meal.claimId().value()+":"+meal.phase()+":"+body+":"+meal.pendingPhysicalStep(),
                ActorMovementProcess.maximumJourneyTicks(),disposition,next);
    }
}
