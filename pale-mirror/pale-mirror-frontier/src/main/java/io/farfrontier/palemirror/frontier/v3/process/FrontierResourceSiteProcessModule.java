package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Exact reducer and physical-observation admission owner for resource sites. */
final class FrontierResourceSiteProcessModule implements FrontierWorldProcessModule {
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof ResourceSiteHarvestSceneLeasePrepared prepared) {
            try {
                ResourceSiteHarvestJob job = FrontierResourceSiteHarvestSceneSupport.require(state,
                        FrontierSceneBehaviors.resourceSiteHarvest(prepared.lease()));
                ResourceSiteHarvestProcess.requireContinuationBinding(job, command.scheduleBinding().map(io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("resource-site HOT admission has no engine schedule binding")));
                return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(
                        FrontierResourceSiteHarvestSceneSupport.owner(state, FrontierSceneBehaviors.resourceSiteHarvest(prepared.lease())), prepared)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestSceneLeaseHandoff handoff) {
            try {
                ResourceSiteHarvestJob job = FrontierResourceSiteHarvestSceneSupport.require(state,
                        FrontierSceneBehaviors.resourceSiteHarvest(handoff.lease()));
                ResourceSiteHarvestProcess.requireContinuationBinding(job, command.scheduleBinding().map(io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("resource-site HOT handoff has no engine schedule binding")));
                return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(
                        FrontierResourceSiteHarvestSceneSupport.owner(state, FrontierSceneBehaviors.resourceSiteHarvest(handoff.lease())), handoff)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestProgressed progressed) {
            if (!ResourceSiteHarvestProcess.irreversibleCropEffectsAdmitted()) {
                return FrontierWorldCommandPlanner.rejected("resource-site irreversible harvest progress is deferred to F0.2");
            }
            try {
                ResourceSiteHarvestJob job = FrontierResourceSiteHarvestSceneSupport.require(state,
                        new ResourceSiteHarvestSceneCause(progressed.jobId()));
                boolean hot = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                        .anyMatch(lease -> lease.status() == SceneLeaseStatus.HOT
                                && FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id()));
                if (!hot) return FrontierWorldCommandPlanner.rejected("resource-site harvest progress requires its HOT scene");
                return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(job.siteId(), progressed)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestCropPrepared prepared) {
            if (!ResourceSiteHarvestProcess.irreversibleCropEffectsAdmitted()) {
                return FrontierWorldCommandPlanner.rejected("resource-site irreversible crop preparation is deferred to F0.2");
            }
            try {
                ResourceSiteHarvestJob job = FrontierResourceSiteHarvestSceneSupport.require(state,
                        new ResourceSiteHarvestSceneCause(prepared.jobId()));
                boolean hot = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                        .anyMatch(lease -> lease.status() == SceneLeaseStatus.HOT
                                && FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id()));
                if (!hot) return FrontierWorldCommandPlanner.rejected("resource-site crop preparation requires its HOT scene");
                if (!job.atCurrentCropStation()) return FrontierWorldCommandPlanner.rejected("resource-site crop preparation requires its retained workstation");
                return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(job.siteId(), prepared)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestHotTraversalAdvanced advanced) {
            try {
                ResourceSiteHarvestJob job = FrontierResourceSiteHarvestSceneSupport.require(state, new ResourceSiteHarvestSceneCause(advanced.jobId()));
                ScheduledAction binding = command.scheduleBinding().map(io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("resource-site HOT checkpoint has no engine schedule binding"));
                ResourceSiteHarvestProcess.requireContinuationBinding(job, binding);
                if (!advanced.workerId().equals(job.workerId())) return FrontierWorldCommandPlanner.rejected("resource-site HOT checkpoint has a foreign farmer");
                // The reducer is also the complete causal validator: exact job, exact HOT
                // lease, current retained recovery body, observed next body and one cursor.
                // Validate it before admitting an event so an executor cannot persist a
                // partial checkpoint merely because another harvest lease happens to be HOT.
                ResourceSiteHarvestProcess.reduceHotTraversalAdvanced(state, job.siteId(), advanced);
                return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(job.siteId(), advanced),
                        ResourceSiteHarvestProcess.advanceBoundContinuation(state, job, binding)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteConflictObserved conflict) {
            try { return new CommandPlan.Accepted(ResourceSiteProcess.planConflict(state, conflict)); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        return FrontierWorldCommandPlanner.rejected("resource-site process does not admit command: " + command.payload().type());
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case ResourceSiteGrowthAdvanced advanced -> ResourceSiteProcess.reduceGrowth(state, event.subject(), advanced);
            case ResourceSitePreparationStarted started -> ResourceSiteProcess.reducePreparationStarted(state, event.subject(), started);
            case ResourceSitePrepared prepared -> ResourceSiteProcess.reducePrepared(state, event.subject(), prepared);
            case ResourceSiteHarvestStarted started -> ResourceSiteHarvestProcess.reduceStarted(state, event.subject(), started);
            case ResourceSiteHarvestCropPrepared prepared -> ResourceSiteHarvestProcess.reduceCropPrepared(state, event.subject(), prepared);
            case ResourceSiteHarvestColdTraversalAdvanced advanced -> ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, event.subject(), advanced);
            case ResourceSiteHarvestHotTraversalAdvanced advanced -> ResourceSiteHarvestProcess.reduceHotTraversalAdvanced(state, event.subject(), advanced);
            case ResourceSiteHarvestProgressed progressed -> ResourceSiteHarvestProcess.reduceProgressed(state, event.subject(), progressed);
            case ResourceSiteHarvestSceneLeasePrepared prepared -> reduceHarvestScenePrepared(state, event.subject(), event, prepared);
            case ResourceSiteHarvestSceneLeaseHandoff handoff -> reduceHarvestSceneHandoff(state, event.subject(), event, handoff);
            case ResourceSiteConflictObserved conflict -> ResourceSiteProcess.reduceConflict(state, event.subject(), conflict);
            default -> throw new IllegalArgumentException("resource-site process does not own event: " + event.payload().type());
        };
    }

    private static FrontierWorldState reduceHarvestScenePrepared(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                                 FrontierEvent event, ResourceSiteHarvestSceneLeasePrepared prepared) {
        SceneLease lease = prepared.lease();
        if (!subject.equals(FrontierResourceSiteHarvestSceneSupport.owner(state, FrontierSceneBehaviors.resourceSiteHarvest(lease)))
                || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("resource-site harvest scene lease does not match its retained field-work hand-off");
        }
        return state.prepareSceneLease(lease);
    }

    private static FrontierWorldState reduceHarvestSceneHandoff(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                                FrontierEvent event, ResourceSiteHarvestSceneLeaseHandoff handoff) {
        SceneLease lease = handoff.lease();
        if (!subject.equals(FrontierResourceSiteHarvestSceneSupport.owner(state, FrontierSceneBehaviors.resourceSiteHarvest(lease)))
                || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("resource-site harvest scene hand-off does not match its retained field work");
        }
        FrontierWorldState rebased = ResourceSiteHarvestProcess.rebaseForAmbientHandoff(state, subject, handoff);
        return rebased.handoffAmbientScene(new SceneLeaseHandoff(lease, handoff.ambientMembers()));
    }
}
