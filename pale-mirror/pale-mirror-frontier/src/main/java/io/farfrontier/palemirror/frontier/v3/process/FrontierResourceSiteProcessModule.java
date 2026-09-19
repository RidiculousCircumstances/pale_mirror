package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.Set;

/** Exact reducer and physical-observation admission owner for resource sites. */
final class FrontierResourceSiteProcessModule implements FrontierWorldProcessModule {
    @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() {
        return List.of(new FunctionalPhysicalIntentLifecycleCapability(
                PhysicalIntentLifecycleDeclaration.physical(PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION,
                        Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.RESOURCE_SITE_PREPARATION),
                        Set.of(PhysicalIntentRoleSchema.RESOURCE_SITE_PREPARATION)),
                (state, command, prepared) -> new CommandPlan.Accepted(List.of(new ProposedEvent(prepared.intent().causeSubjectId(), prepared))),
                (state, command, intent, transition) -> new CommandPlan.Accepted(
                        ResourceSiteProcess.planPreparationTransition(state, intent, transition, command.submittedAt().ticks())),
                ResourceSiteProcess::reducePrepared,
                FrontierResourceSiteProcessModule::reducePreparationTransition, PhysicalIntentLifecycleRetirementPolicy.of(
                        (state, command, intent, transition) -> new CommandPlan.Accepted(
                                ResourceSiteProcess.planPreparationTransition(state, intent, transition, command.submittedAt().ticks())),
                        FrontierResourceSiteProcessModule::reducePreparationTransition), intent -> FencedRecoveryAsset.EFFECT,
                retirementAccount(PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION), PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery()),
                new FunctionalPhysicalIntentLifecycleCapability(
                PhysicalIntentLifecycleDeclaration.physical(PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST,
                        Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.RESOURCE_SITE_HARVEST),
                        Set.of(PhysicalIntentRoleSchema.RESOURCE_SITE_HARVEST)),
                (state, command, prepared) -> FrontierWorldCommandPlanner.rejected("physical executor cannot prepare resource-site harvest"),
                (state, command, intent, transition) -> new CommandPlan.Accepted(
                        ResourceSiteHarvestProcess.planTransition(state, intent, transition, command.submittedAt().ticks())),
                ResourceSiteHarvestProcess::reducePrepared,
                FrontierResourceSiteProcessModule::reduceHarvestTransition, PhysicalIntentLifecycleRetirementPolicy.of(
                        FrontierResourceSiteProcessModule::planHarvestRetirement,
                        FrontierResourceSiteProcessModule::reduceHarvestTransition), intent -> FencedRecoveryAsset.EFFECT,
                retirementAccount(PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST), PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery()));
    }

    private static PhysicalIntentRetirementAccount retirementAccount(PhysicalIntentLifecycleOwner owner) {
        return PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                (before, command, intent, transition) -> retirementFacts(before, command, intent, transition, owner),
                (before, intent, transition, binding) -> PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding,
                        retirementFacts(before, binding.continuation(), intent, transition, owner)),
                (before, after, intent, transition, binding) -> {
                    if (intent.lifecycleOwner() != owner) throw new IllegalArgumentException("resource-site retirement account owner mismatch");
                    if (owner == PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST) {
                        ResourceSiteHarvestJob job = harvestJob(before, intent);
                        if (after != before && transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED
                                && after.inventory().items().get(job.outputItemId()) == null) {
                            throw new IllegalArgumentException("harvest retirement account did not retain its exact output outcome");
                        }
                    } else {
                        preparation(before, intent);
                    }
                });
    }

    private static PhysicalIntentRetirementAccount.Binding retirementFacts(FrontierWorldState state, FrontierCommand command,
                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                            PhysicalIntentTransition transition, PhysicalIntentLifecycleOwner owner) {
        var continuation = command == null ? new PhysicalIntentRetirementAccount.CheckedNone<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)
                : command.scheduleBinding().<PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>>map(bound -> new PhysicalIntentRetirementAccount.Exact<>(bound.action().id()))
                .orElseGet(() -> new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION));
        return retirementFacts(state, continuation, intent, transition, owner);
    }

    private static PhysicalIntentRetirementAccount.Binding retirementFacts(FrontierWorldState state,
                                                                            PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId> continuation,
                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                            PhysicalIntentTransition transition, PhysicalIntentLifecycleOwner owner) {
        if (owner == PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION) {
            ResourceSitePreparationJob job = preparation(state, intent);
            return new PhysicalIntentRetirementAccount.Binding(owner, intent.id(),
                    new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_APPLICABLE_RELATION), continuation,
                    new PhysicalIntentRetirementAccount.Exact<>(job.id()),
                    new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_RESOURCE_COMMITMENT), lateDisposition(transition));
        }
        ResourceSiteHarvestJob job = harvestJob(state, intent);
        FrontierDomainRelationships.SubjectEndpoint site = new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESOURCE_SITE, job.siteId());
        FrontierDomainRelationships.SubjectEndpoint harvest = new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESOURCE_HARVEST_JOB, job.id());
        List<FrontierDomainRelationships.Edge> edges = List.of(
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.HARVEST_SITE, site, site, harvest,
                        FrontierDomainRelationships.Lifecycle.ACTIVE, job.id().value()),
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.HARVEST_TASK, harvest, harvest,
                        new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.TASK, job.taskId()), FrontierDomainRelationships.Lifecycle.ACTIVE, job.id().value()),
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.HARVEST_WORKER, harvest, harvest,
                        new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESIDENT, job.workerId()), FrontierDomainRelationships.Lifecycle.ACTIVE, job.id().value()),
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.HARVEST_OUTPUT, harvest, harvest,
                        new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.EXACT_ITEM, job.outputItemId()), FrontierDomainRelationships.Lifecycle.ACTIVE, job.id().value()));
        return new PhysicalIntentRetirementAccount.Binding(owner, intent.id(), new PhysicalIntentRetirementAccount.Exact<>(edges), continuation,
                new PhysicalIntentRetirementAccount.Exact<>(job.workerId()), new PhysicalIntentRetirementAccount.Exact<>(job.outputItemId()), lateDisposition(transition));
    }

    private static ResourceSiteHarvestJob harvestJob(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().get(intent.causeSubjectId());
        ResourceSiteHarvestJob job = lifecycle == null ? null : lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElse(null);
        if (job == null || !job.intentId().equals(intent.id())) throw new IllegalArgumentException("harvest retirement account has no exact retained harvest job");
        return job;
    }

    private static ResourceSitePreparationJob preparation(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().get(intent.causeSubjectId());
        ResourceSitePreparationJob job = lifecycle == null ? null : lifecycle.activeWork().filter(ResourceSitePreparationJob.class::isInstance)
                .map(ResourceSitePreparationJob.class::cast).filter(value -> value.intentId().equals(intent.id())).orElse(null);
        if (job == null) {
            throw new IllegalArgumentException("resource-site preparation retirement has no exact retained site job");
        }
        return job;
    }

    private static PhysicalIntentRetirementAccount.LateDisposition lateDisposition(PhysicalIntentTransition transition) {
        return transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE;
    }

    private static FrontierWorldState reducePreparationTransition(FrontierWorldState state,
                                                                    io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                                    io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                    PhysicalIntentTransition transition) {
        if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("resource-site preparation transition lacks site ownership");
        return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                (currentState, current, evidence, intents) -> {
                    if (!(evidence instanceof ResourceSitePreparationObservation preparation)) {
                        throw new IllegalArgumentException("resource-site preparation requires exact field evidence");
                    }
                    return ResourceSitePhysicalIntentStateSupport.complete(currentState, current, preparation, new java.util.LinkedHashMap<>(intents));
                },
                (currentState, current, intents) -> ResourceSitePhysicalIntentStateSupport.conflict(currentState, current, new java.util.LinkedHashMap<>(intents)));
    }

    private static FrontierWorldState reduceHarvestTransition(FrontierWorldState state,
                                                               io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                               io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                               PhysicalIntentTransition transition) {
        if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("resource-site harvest transition lacks site ownership");
        if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING) {
            ResourceSiteHarvestProcess.validateRunningTransition(state, intent);
        }
        return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                (currentState, current, evidence, intents) -> {
                    if (!(evidence instanceof ResourceSiteHarvestObservation harvest)) {
                        throw new IllegalArgumentException("resource-site harvest requires exact field and output evidence");
                    }
                    return ResourceSitePhysicalIntentStateSupport.completeHarvest(currentState, current, harvest, new java.util.LinkedHashMap<>(intents));
                },
                (currentState, current, intents) -> ResourceSitePhysicalIntentStateSupport.conflict(currentState, current, new java.util.LinkedHashMap<>(intents)));
    }

    private static CommandPlan planHarvestRetirement(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command,
                                                       io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                       PhysicalIntentTransition transition) {
        try {
            return new CommandPlan.Accepted(ResourceSiteHarvestProcess.planTransition(state, intent, transition,
                    command.submittedAt().ticks()));
        } catch (IllegalArgumentException invalid) {
            return FrontierWorldCommandPlanner.rejected(invalid.getMessage());
        }
    }
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof ResourceSiteHarvestSceneLeasePrepared prepared) {
            try {
                ResourceSiteHarvestJob job = FrontierResourceSiteHarvestSceneSupport.require(state,
                        FrontierSceneBehaviors.resourceSiteHarvest(prepared.lease()));
                ResourceSiteHarvestProcess.requireContinuationBinding(job, command.scheduleBinding().map(io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("resource-site HOT admission has no engine schedule binding")));
                return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(
                        FrontierResourceSiteHarvestSceneSupport.owner(state, FrontierSceneBehaviors.resourceSiteHarvest(prepared.lease())), prepared)));
            } catch (IllegalArgumentException | IllegalStateException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
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
                ScheduledAction binding = command.scheduleBinding().map(io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("resource-site harvest progress has no engine schedule binding"));
                ResourceSiteHarvestProcess.requireDueContinuationBinding(job, binding, command.submittedAt().ticks());
                ResourceSiteHarvestProcess.reduceProgressed(state, job.siteId(), progressed);
                return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(job.siteId(), progressed),
                        ResourceSiteHarvestProcess.advanceBoundContinuation(state, job, binding)));
            } catch (IllegalArgumentException | IllegalStateException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
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
                ScheduledAction binding = command.scheduleBinding().map(io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("resource-site crop preparation has no engine schedule binding"));
                ResourceSiteHarvestProcess.requireDueContinuationBinding(job, binding, command.submittedAt().ticks());
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
                return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(job.siteId(), advanced)));
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
