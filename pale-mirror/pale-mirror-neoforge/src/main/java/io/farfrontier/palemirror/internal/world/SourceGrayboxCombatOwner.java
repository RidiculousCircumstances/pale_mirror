package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.internal.effect.EffectLeaseStore;
import java.util.Optional;

/** Narrow durable boundary required by local source-graybox combat execution. */
interface SourceGrayboxCombatOwner extends EffectLeaseStore {
    ReferenceGrayboxSnapshot snapshot();

    ReferenceGrayboxActorExecutionState actorExecution();

    long actorExecutionGameTime(long observedGameTime);

    Optional<ReferenceGrayboxActorExecutionState.CombatAction> reserveActorCombat(String id, String leaseId,
                                                                                    String holder, long gameTick,
                                                                                    long cooldownTicks);
}
