package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.world.entity.Mob;
import java.util.*;

/** Short-lived physical latitude under one retained order. Never awards semantic arrival. */
final class FrontierV3LocalGoalCorrection {
    record Correction(ActorActuationId actor,MovementOrder parent,long token,SurfaceAnchor station,
            LocalNavigationEnvelope envelope,long expiresAt) {
        Correction {
            Objects.requireNonNull(actor);Objects.requireNonNull(parent);Objects.requireNonNull(station);Objects.requireNonNull(envelope);
            if(!actor.execution().actorId().equals(parent.actorId()) || !actor.execution().activityOwnerId().equals(parent.ownerId())
                    || token<1 || expiresAt<0 || !envelope.contains(station.support()))throw new IllegalArgumentException("foreign local goal correction");
        }
        boolean current(ActorActuationId expected,MovementOrder order,long tick){return actor.equals(expected) && parent.equals(order) && tick<=expiresAt;}
    }
    private static final Map<Mob,Correction> CORRECTIONS=new WeakHashMap<>();
    static void offer(Mob body,FrontierV3ActorActuation actuation,MovementOrder parent,long token,SurfaceAnchor station,
            LocalNavigationEnvelope envelope,long expiresAt){
        if(!actuation.current(body) || !actuation.currentMovement(parent))throw new IllegalArgumentException("local correction lost its retained movement authority");
        long now=body.level().getGameTime();
        if(expiresAt<now || expiresAt-now>40)throw new IllegalArgumentException("unbounded local correction lifetime");
        CORRECTIONS.put(body,new Correction(actuation.id(),parent,token,station,envelope,expiresAt));
    }
    static Optional<Correction> current(Mob body,FrontierV3ActorActuation actuation,MovementOrder parent){
        var value=CORRECTIONS.get(body);if(value==null)return Optional.empty();
        if(!value.actor().equals(actuation.id()) || !value.parent().equals(parent))return Optional.empty();
        if(!actuation.current(body) || !actuation.currentMovement(parent) || !value.current(actuation.id(),parent,body.level().getGameTime())){
            CORRECTIONS.remove(body);return Optional.empty();
        }
        return Optional.of(value);
    }
    static void withdraw(Mob body,ActorActuationId actor,long token){
        var value=CORRECTIONS.get(body);
        if(value!=null && value.actor().equals(actor) && value.token()==token)CORRECTIONS.remove(body);
    }
    private FrontierV3LocalGoalCorrection(){}
}
