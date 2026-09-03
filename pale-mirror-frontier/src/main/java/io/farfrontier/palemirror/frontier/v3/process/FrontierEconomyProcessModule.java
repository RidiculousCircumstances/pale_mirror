package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Exact reducer owner for company, market and production facts. */
final class FrontierEconomyProcessModule implements FrontierWorldProcessModule {
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof ProductionWorkSceneLeasePrepared prepared) {
            try { return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(FrontierProductionWorkSceneSupport.owner(state,
                    FrontierSceneBehaviors.productionWork(prepared.lease())), prepared))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ProductionWorkSceneLeaseHandoff handoff) {
            try { return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(FrontierProductionWorkSceneSupport.owner(state,
                    FrontierSceneBehaviors.productionWork(handoff.lease())), handoff))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ProductionWorkProgressed progressed) return planHotProgress(state, progressed);
        if (command.payload() instanceof ProductionWorkTraversalAdvanced advanced) return planHotTraversal(state, advanced);
        if (command.payload() instanceof ProductionWorkTraversalBlocked blocked) return planHotTraversalBlocked(state, blocked);
        return FrontierWorldCommandPlanner.rejected("economy process does not admit command: " + command.payload().type());
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case CompanyRegistered registered -> CompanyFoundationProcess.reduce(state, event.subject(), registered);
            case EmploymentContractOpened opened -> CompanyFoundationProcess.reduceEmployment(state, event.subject(), opened);
            case EmploymentContractTerminated terminated -> CompanyFoundationProcess.reduceEmploymentTermination(state, event.subject(), terminated);
            case MarketDemandOpened opened -> MarketClearingProcess.reduceOpened(state, event.subject(), opened);
            case MarketQuotePublished published -> MarketClearingProcess.reduceQuote(state, event.subject(), event.instant().ticks(), published);
            case MarketWorkOrderAccepted accepted -> MarketClearingProcess.reduceAccepted(state, event.subject(), event.instant().ticks(), accepted);
            case MarketWorkOrderCancelled cancelled -> MarketClearingProcess.reduceWorkOrderCancelled(state, event.subject(), cancelled);
            case MarketDemandExpired expired -> MarketClearingProcess.reduceExpired(state, event.subject(), event.instant().ticks(), expired);
            case MarketDemandCancelled cancelled -> MarketClearingProcess.reduceCancelled(state, event.subject(), cancelled);
            case ProductionStarted started -> ProductionProcess.reduceStarted(state, event.subject(), started);
            case ProductionCompleted completed -> ProductionProcess.reduceCompleted(state, event.subject(), completed);
            case ProductionWorkProgressed progressed -> ProductionProcess.reduceWorkProgressed(state, event.subject(), progressed);
            case ProductionWorkTraversalAdvanced advanced -> ProductionProcess.reduceWorkTraversalAdvanced(state, event.subject(), advanced);
            case ProductionWorkTraversalBlocked blocked -> ProductionProcess.reduceWorkTraversalBlocked(state, event.subject(), blocked);
            case ProductionWorkSceneLeasePrepared prepared -> reduceWorkScenePrepared(state, event.subject(), event, prepared);
            case ProductionWorkSceneLeaseHandoff handoff -> reduceWorkSceneHandoff(state, event.subject(), event, handoff);
            case ProductionWorkScenePreparationAborted aborted -> ProductionProcess.reduceWorkScenePreparationAborted(state, event.subject(), aborted);
            case ProductionWorkSceneFinalized finalized -> ProductionProcess.reduceWorkSceneFinalized(state, event.subject(), finalized);
            case ProductionBlocked blocked -> ProductionProcess.reduceBlocked(state, event.subject(), blocked);
            case ProductionInterrupted interrupted -> ProductionProcess.reduceInterrupted(state, event.subject(), event.instant().ticks(), interrupted);
            default -> throw new IllegalArgumentException("economy process does not own event: " + event.payload().type());
        };
    }

    private static CommandPlan planHotProgress(FrontierWorldState state, ProductionWorkProgressed progressed) {
        try {
            ProductionJob job = FrontierProductionWorkSceneSupport.require(state, new ProductionWorkSceneCause(progressed.jobId()));
            if (!hot(state, job.id())) return FrontierWorldCommandPlanner.rejected("production work progress requires its HOT scene");
            ProductionProcess.reduceWorkProgressed(state, job.settlementId(), progressed);
            return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(job.settlementId(), progressed)));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }
    private static CommandPlan planHotTraversal(FrontierWorldState state, ProductionWorkTraversalAdvanced advanced) {
        try {
            ProductionJob job = FrontierProductionWorkSceneSupport.require(state, new ProductionWorkSceneCause(advanced.jobId()));
            if (!hot(state, job.id())) return FrontierWorldCommandPlanner.rejected("production work traversal requires its HOT scene");
            ProductionProcess.reduceWorkTraversalAdvanced(state, job.settlementId(), advanced);
            return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(job.settlementId(), advanced)));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }
    private static CommandPlan planHotTraversalBlocked(FrontierWorldState state, ProductionWorkTraversalBlocked blocked) {
        try {
            ProductionJob job = FrontierProductionWorkSceneSupport.require(state, new ProductionWorkSceneCause(blocked.jobId()));
            if (!hot(state, job.id())) return FrontierWorldCommandPlanner.rejected("production work traversal block requires its HOT scene");
            return new CommandPlan.Accepted(ProductionProcess.planWorkTraversalBlocked(state, job.settlementId(), blocked));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }
    private static boolean hot(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId jobId) {
        return state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isProductionWork)
                .anyMatch(lease -> lease.status() == SceneLeaseStatus.HOT && FrontierSceneBehaviors.productionWork(lease).jobId().equals(jobId));
    }
    private static FrontierWorldState reduceWorkScenePrepared(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                               FrontierEvent event, ProductionWorkSceneLeasePrepared prepared) {
        if (!subject.equals(FrontierProductionWorkSceneSupport.owner(state, FrontierSceneBehaviors.productionWork(prepared.lease()))) || !prepared.lease().handoffInstant().equals(event.instant()))
            throw new IllegalArgumentException("production-work scene preparation does not match its retained hand-off");
        return state.prepareSceneLease(prepared.lease());
    }
    private static FrontierWorldState reduceWorkSceneHandoff(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                              FrontierEvent event, ProductionWorkSceneLeaseHandoff handoff) {
        if (!subject.equals(FrontierProductionWorkSceneSupport.owner(state, FrontierSceneBehaviors.productionWork(handoff.lease()))) || !handoff.lease().handoffInstant().equals(event.instant()))
            throw new IllegalArgumentException("production-work scene hand-off does not match its retained worker");
        FrontierWorldState rebased = ProductionProcess.rebaseForAmbientHandoff(state, subject, handoff);
        return rebased.handoffAmbientScene(new SceneLeaseHandoff(handoff.lease(), handoff.ambientMembers()));
    }
}
