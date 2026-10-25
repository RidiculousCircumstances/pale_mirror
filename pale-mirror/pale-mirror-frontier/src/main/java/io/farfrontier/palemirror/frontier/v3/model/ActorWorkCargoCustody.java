package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;

/** Shared actual-hand binding/departure protocol. Family callers validate their retained account relation. */
public final class ActorWorkCargoCustody {
    public enum Boundary { MATERIALIZED, SAVED_DEPARTURE }
    private ActorWorkCargoCustody() { }
    public static FrontierWorldState apply(FrontierWorldState state, ActorExecutionId execution, SubjectId account,
            ActorActuationId identity, Boundary boundary, FungiblePhysicalObservation.Stack stack) {
        if (!execution.equals(identity.execution())) throw new IllegalArgumentException("work cargo has foreign actuation");
        state.actorExecutions().requireRetained(execution);
        var body = identity.body(); var fence = ActorBodyAuthority.require(state, body);
        var ledger = state.inventory().fungibleResources();
        var bindings = ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(account)).toList();
        switch (boundary) {
            case MATERIALIZED -> {
                if (!bindings.isEmpty()) throw new IllegalArgumentException("work cargo materialization already owns a binding");
                ActorBodyAuthority.requireActuation(state, identity);
                ledger = ledger.rebind(account, body.physicalEpoch(), FungiblePhysicalObservation.bind(ledger, account, body.physicalEpoch(), List.of(stack)));
            }
            case SAVED_DEPARTURE -> {
                if (!body.equals(ActorBodyAuthority.current(state, body.actorId())) || bindings.size() != 1
                        || bindings.getFirst().authorityEpoch() != body.physicalEpoch() || !bindings.getFirst().address().equals(stack.address())
                        || !bindings.getFirst().lotQuantities().equals(ledger.accounts().get(account).lotQuantities())
                        || !bindings.getFirst().claimQuantities().isEmpty()
                        || fence.phase() != FencedRecoveryPhase.RUNNING && fence.phase() != FencedRecoveryPhase.AMBIGUOUS)
                    throw new IllegalArgumentException("work cargo departure lacks its exact saved hand binding");
                ledger = ledger.releaseBindings(account, body.physicalEpoch());
            }
        }
        return state.withInventory(state.inventory().withFungibleResources(ledger));
    }
}
