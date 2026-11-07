package io.farfrontier.palemirror.frontier.v3.model.clinical;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.Objects;

/** Prepared native recovery: current vitality must still equal its declared preimage. */
public record ClinicalHealing(ActorActuationId actor,long before,long amount,long throughTick,long harmRevision,long attempt){
    public ClinicalHealing {
        Objects.requireNonNull(actor);
        if(before<1||amount<1||throughTick<0||harmRevision<0||attempt<1)throw new IllegalArgumentException("invalid prepared recovery");
    }
}
