package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Sole pure-domain reducer owner for resident-owned settlement service work. */
final class FrontierSettlementServiceWorkProcessModule implements FrontierWorldProcessModule {
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof SettlementServiceWorkSceneLeasePrepared prepared) {
            return ownLease(state, prepared.lease(), prepared);
        }
        if (command.payload() instanceof SettlementServiceWorkSceneLeaseHandoff handoff) {
            return ownLease(state, handoff.lease(), handoff);
        }
        if (command.payload() instanceof SettlementServiceWorkTraversalAdvanced advanced) {
            try {
                SettlementServiceWork work = state.serviceWorks().get(advanced.workId());
                if (work == null) throw new IllegalArgumentException("service-work traversal has no retained work");
                SettlementServiceWorkProcess.reduceHotTraversalAdvanced(state, work.settlementId(), advanced);
                return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(work.settlementId(), advanced)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SettlementServiceWorkProgressed progressed) {
            try {
                SettlementServiceWork work = state.serviceWorks().get(progressed.workId());
                if (work == null) throw new IllegalArgumentException("service-work progress has no retained work");
                SettlementServiceWorkProcess.reduceHotProgressed(state, work.settlementId(), progressed);
                return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(work.settlementId(), progressed)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SettlementServiceWorkTraversalBlocked blocked) {
            try {
                SettlementServiceWork work = state.serviceWorks().get(blocked.workId());
                if (work == null) throw new IllegalArgumentException("service-work block has no retained work");
                return new CommandPlan.Accepted(SettlementServiceWorkProcess.planHotTraversalBlocked(state, work.settlementId(), blocked));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        return FrontierWorldCommandPlanner.rejected("settlement-service-work process does not admit command: " + command.payload().type());
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        if (event.payload() instanceof SettlementServiceWorkStarted started) {
            return SettlementServiceWorkProcess.reduceStarted(state, event.subject(), started);
        }
        if (event.payload() instanceof SettlementServiceWorkSceneLeasePrepared prepared) {
            if (!event.subject().equals(FrontierSettlementServiceWorkSceneSupport.owner(state, FrontierSceneBehaviors.serviceWork(prepared.lease())))
                    || !prepared.lease().handoffInstant().equals(event.instant())) throw new IllegalArgumentException("service-work prepared lease has foreign owner or instant");
            return state.prepareSceneLease(prepared.lease());
        }
        if (event.payload() instanceof SettlementServiceWorkSceneLeaseHandoff handoff) {
            if (!event.subject().equals(FrontierSettlementServiceWorkSceneSupport.owner(state, FrontierSceneBehaviors.serviceWork(handoff.lease())))
                    || !handoff.lease().handoffInstant().equals(event.instant())) throw new IllegalArgumentException("service-work hand-off has foreign owner or instant");
            FrontierSettlementServiceWorkSceneSupport.validatePrepared(state, handoff.lease());
            return state.handoffAmbientScene(new SceneLeaseHandoff(handoff.lease(), handoff.ambientMembers()));
        }
        if (event.payload() instanceof SettlementServiceWorkTraversalAdvanced advanced) {
            return SettlementServiceWorkProcess.reduceHotTraversalAdvanced(state, event.subject(), advanced);
        }
        if (event.payload() instanceof SettlementServiceWorkProgressed progressed) {
            return SettlementServiceWorkProcess.reduceHotProgressed(state, event.subject(), progressed);
        }
        if (event.payload() instanceof SettlementServiceWorkTraversalBlocked blocked) {
            return SettlementServiceWorkProcess.reduceHotTraversalBlocked(state, event.subject(), blocked);
        }
        throw new IllegalArgumentException("settlement-service-work process does not own event: " + event.payload().type());
    }

    private static CommandPlan ownLease(FrontierWorldState state, SceneLease lease, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        try {
            return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(FrontierSettlementServiceWorkSceneSupport.owner(state,
                    FrontierSceneBehaviors.serviceWork(lease)), payload)));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }
}
