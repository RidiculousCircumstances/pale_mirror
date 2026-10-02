package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
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
                retirementAccount(PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION), PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery(), PhysicalIntentRecoveryDiagnosticProducer.RESOURCE_SITE_PREPARATION),
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
                retirementAccount(PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST), PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery(), PhysicalIntentRecoveryDiagnosticProducer.RESOURCE_SITE_HARVEST));
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
                        if (after != before && transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED
                                && deferredReceipt(before, intent) == null) {
                            ResourceSiteHarvestJob job = harvestJob(before, intent);
                            ResourceSite site = before.resourceSite(job.siteId());
                            ResourceFieldYield yield = ResourceFieldYield.fromCompletedCycle(job.siteId(), site.settlementId(),
                                    before.resourceSites().cycle(job.siteId()));
                            CustodyAccount depot = after.inventory().fungibleResources().accounts().get(job.depotAccountId());
                            if (yield.lots().stream().anyMatch(lot -> depot == null
                                    || !depot.custody().equals(new ResourceCustody.Container(job.outputSlot().containerId()))
                                    || !Integer.valueOf(lot.quantity()).equals(depot.lotQuantities().get(lot.id())))) {
                                throw new IllegalArgumentException("harvest retirement account did not retain its exact fungible yield in the declared depot");
                            }
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
        ResourceSiteHarvestLineage lineage = deferredReceipt(state, intent);
        if (lineage != null) {
            // COLD has already completed the job and released its declared relationship edges,
            // but the lifecycle retains this one exact physical receipt authority.  It is not a
            // new inferred relationship: the lineage names both authoritative endpoints.
            return new PhysicalIntentRetirementAccount.Binding(owner, intent.id(),
                    new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_APPLICABLE_RELATION), continuation,
                    new PhysicalIntentRetirementAccount.Exact<>(lineage.workerId()),
                    new PhysicalIntentRetirementAccount.Exact<>(lineage.depotAccountId()), lateDisposition(transition));
        }
        ResourceSiteHarvestJob job = harvestJob(state, intent);
        // A HOT executor confirmation is not itself an engine-scheduled command, but it closes
        // the active harvest's one durable COLD continuation in the same transaction.  The
        // retirement proof must therefore bind that exact stable schedule identity rather than
        // falsely declaring that no engine continuation exists.
        if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED) {
            continuation = new PhysicalIntentRetirementAccount.Exact<>(ResourceSiteHarvestProcess.coldProgress(job, 0L).id());
        }
        FrontierDomainRelationships.SubjectEndpoint site = new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESOURCE_SITE, job.siteId());
        FrontierDomainRelationships.SubjectEndpoint harvest = new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESOURCE_HARVEST_JOB, job.id());
        List<FrontierDomainRelationships.Edge> edges = List.of(
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.HARVEST_SITE, site, site, harvest,
                        FrontierDomainRelationships.Lifecycle.ACTIVE, job.id().value()),
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.HARVEST_TASK, harvest, harvest,
                        new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.TASK, job.taskId()), FrontierDomainRelationships.Lifecycle.ACTIVE, job.id().value()),
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.HARVEST_WORKER, harvest, harvest,
                        new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESIDENT, job.workerId()), FrontierDomainRelationships.Lifecycle.ACTIVE, job.id().value()),
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.HARVEST_ACTOR_ACCOUNT, harvest, harvest,
                        new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESOURCE_ACCOUNT, job.actorAccountId()), FrontierDomainRelationships.Lifecycle.ACTIVE, job.id().value()),
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.HARVEST_DEPOT_ACCOUNT, harvest, harvest,
                        new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESOURCE_ACCOUNT, job.depotAccountId()), FrontierDomainRelationships.Lifecycle.ACTIVE, job.id().value()));
        return new PhysicalIntentRetirementAccount.Binding(owner, intent.id(), new PhysicalIntentRetirementAccount.Exact<>(edges), continuation,
                new PhysicalIntentRetirementAccount.Exact<>(job.workerId()), new PhysicalIntentRetirementAccount.Exact<>(job.depotAccountId()), lateDisposition(transition));
    }

    private static ResourceSiteHarvestJob harvestJob(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().get(intent.causeSubjectId());
        ResourceSiteHarvestJob job = lifecycle == null ? null : lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElse(null);
        if (job == null || !job.intentId().equals(intent.id())) throw new IllegalArgumentException("harvest retirement account has no exact retained harvest job");
        return job;
    }

    private static ResourceSiteHarvestLineage deferredReceipt(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().get(intent.causeSubjectId());
        return lifecycle == null ? null : lifecycle.harvestLineage()
                .filter(ResourceSiteHarvestLineage::receiptPending)
                .filter(value -> value.predecessorIntentId().equals(intent.id()))
                .orElse(null);
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
                    if (evidence instanceof ResourceSiteHarvestDeferredObservation deferred) {
                        return ResourceSitePhysicalIntentStateSupport.completeDeferredHarvest(currentState, current, deferred,
                                new java.util.LinkedHashMap<>(intents));
                    }
                    if (!(evidence instanceof ResourceSiteHarvestDeliveryObservation harvest)) {
                        throw new IllegalArgumentException("resource-site harvest requires observed farmer-hand and depot evidence");
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
        if (command.payload() instanceof ResourceSiteHarvestScenePreparationAborted aborted) {
            ResourceSite site = state.resourceSite(aborted.siteId());
            if (site == null) return FrontierWorldCommandPlanner.rejected("field scene preparation abort has no declared site");
            try {
                reduceHarvestScenePreparationAborted(state, site.settlementId(), aborted);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(site.settlementId(), aborted)));
            } catch (IllegalArgumentException invalid) {
                return FrontierWorldCommandPlanner.rejected("invalid field scene preparation abort: " + invalid.getMessage());
            }
        }
        if (command.payload() instanceof ResourceSiteHarvestHandRelease released) {
            SceneLease lease = state.sceneLeases().get(released.sceneRelease().leaseId());
            if (lease == null) return FrontierWorldCommandPlanner.rejected("harvest hand release has no scene lease");
            try {
                return new CommandPlan.Accepted(FrontierSceneContinuationPlanner.harvestHandReleaseEvents(state,
                        lease, command.submittedAt().ticks(), released,
                        command.scheduleBinding().map(io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestSceneReconciled reconciled) {
            try {
                ResourceSiteHarvestSceneReconciliation.reduce(state, reconciled.siteId(), reconciled);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(reconciled.siteId(), reconciled)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestHandProjected projected) {
            try {
                ResourceSiteHarvestProcess.reduceHandProjected(state, projected.siteId(), projected);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(projected.siteId(), projected)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
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
                ScheduledAction binding = command.scheduleBinding().map(io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("resource-site harvest progress has no engine schedule binding"));
                if (!binding.kind().equals(ResourceSiteHarvestProcess.COLD_PROGRESS_KIND))
                    throw new IllegalArgumentException("resource-site harvest progress has a foreign owner declaration kind");
                ResourceSiteHarvestJob job = FrontierResourceSiteHarvestSceneSupport.require(state,
                        new ResourceSiteHarvestSceneCause(binding.subject(), progressed.jobId()));
                boolean hot = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                        .anyMatch(lease -> lease.status() == SceneLeaseStatus.HOT
                                && FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(job.siteId())
                                && FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id()));
                if (!hot) return FrontierWorldCommandPlanner.rejected("resource-site harvest progress requires its HOT scene");
                ResourceSiteHarvestProcess.requireDueContinuationBinding(job, binding, command.submittedAt().ticks());
                if (!progressed.coldScheduleId().equals(binding.id())
                        || progressed.coldDueAt() != binding.dueAt().ticks())
                    throw new IllegalArgumentException("resource-site harvest progress has foreign or missing continuation evidence");
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
                ScheduledAction binding = command.scheduleBinding().map(io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("resource-site crop preparation has no engine schedule binding"));
                if (!binding.kind().equals(ResourceSiteHarvestProcess.COLD_PROGRESS_KIND))
                    throw new IllegalArgumentException("resource-site crop preparation has a foreign owner declaration kind");
                ResourceSiteHarvestJob job = FrontierResourceSiteHarvestSceneSupport.require(state,
                        new ResourceSiteHarvestSceneCause(binding.subject(), prepared.jobId()));
                boolean hot = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                        .anyMatch(lease -> lease.status() == SceneLeaseStatus.HOT
                                && FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(job.siteId())
                                && FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id()));
                if (!hot) return FrontierWorldCommandPlanner.rejected("resource-site crop preparation requires its HOT scene");
                if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, job))
                    return FrontierWorldCommandPlanner.rejected("resource-site crop preparation requires its actual farmer at the current CellId station");
                ResourceSiteHarvestProcess.requireDueContinuationBinding(job, binding, command.submittedAt().ticks());
                return new CommandPlan.Accepted(java.util.List.of(new ProposedEvent(job.siteId(), prepared)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestHotTraversalAdvanced advanced) {
            // Keep the registered reducer/codec for historical WAL replay only.
            // New farmer movement has exactly one goal-owned command path;
            // accepting this cursor advance would reintroduce a second authority.
            return FrontierWorldCommandPlanner.rejected("field per-block HOT traversal is retired; submit observed goal movement");
        }
        if (command.payload() instanceof ResourceSiteHarvestHotGoalArrived arrived) {
            try {
                ScheduledAction binding = command.scheduleBinding().map(
                        io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("field HOT goal arrival has no engine schedule binding"));
                if (!binding.kind().equals(ResourceSiteHarvestProcess.COLD_PROGRESS_KIND))
                    throw new IllegalArgumentException("field HOT goal arrival has a foreign continuation kind");
                ResourceSiteHarvestJob job = FrontierResourceSiteHarvestSceneSupport.require(state,
                        new ResourceSiteHarvestSceneCause(binding.subject(), arrived.jobId()));
                ResourceSiteHarvestProcess.requireContinuationBinding(job, binding);
                ResourceSiteHarvestProcess.reduceHotGoalArrived(state, job.siteId(), arrived);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(job.siteId(), arrived)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestHotTransitObserved observed) {
            try {
                ScheduledAction binding = command.scheduleBinding().map(
                        io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("interrupted HOT field goal has no engine schedule binding"));
                if (!binding.kind().equals(ResourceSiteHarvestProcess.COLD_PROGRESS_KIND))
                    throw new IllegalArgumentException("interrupted HOT field goal has a foreign continuation kind");
                ResourceSiteHarvestJob job = FrontierResourceSiteHarvestSceneSupport.require(state,
                        new ResourceSiteHarvestSceneCause(binding.subject(), observed.jobId()));
                ResourceSiteHarvestProcess.requireContinuationBinding(job, binding);
                ResourceSiteHarvestProcess.reduceHotTransitObserved(state, job.siteId(), observed);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(job.siteId(), observed)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestSegmentRenewed renewed) {
            try {
                ScheduledAction binding = command.scheduleBinding().map(
                        io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("HOT field segment renewal has no engine schedule binding"));
                if (!binding.kind().equals(ResourceSiteHarvestProcess.COLD_PROGRESS_KIND)
                        || renewed.hotLeaseId().isEmpty() || !binding.subject().equals(renewed.siteId())
                        || !binding.id().equals(renewed.coldScheduleId())
                        || binding.dueAt().ticks() != renewed.coldDueAt())
                    throw new IllegalArgumentException("HOT field segment renewal lacks its exact declared owner");
                ResourceSiteHarvestProcess.reduceSegmentRenewed(state, renewed.siteId(), renewed);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(renewed.siteId(), renewed)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestWorkChanged changed) {
            try {
                var binding = command.scheduleBinding().orElseThrow(
                        () -> new IllegalArgumentException("labour command needs an exact continuation binding")).action();
                if (changed.hotLeaseId().isEmpty() || changed.atTick() != command.submittedAt().ticks()
                        || !binding.id().equals(changed.scheduleId()) || binding.dueAt().ticks() != changed.dueAt())
                    throw new IllegalArgumentException("HOT labour has no exact current binding");
                return new CommandPlan.Accepted(ResourceSiteHarvestWorkProcess.events(state, changed, binding));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestCellSkip skipped) {
            try {
                ScheduledAction binding = command.scheduleBinding().map(
                        io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("HOT blocked field cell has no engine schedule binding"));
                if (!binding.kind().equals(ResourceSiteHarvestProcess.COLD_PROGRESS_KIND)
                        || skipped.hotLeaseId().isEmpty() || !binding.subject().equals(skipped.siteId())
                        || !binding.id().equals(skipped.coldScheduleId())
                        || binding.dueAt().ticks() != skipped.coldDueAt())
                    throw new IllegalArgumentException("HOT blocked field cell lacks its exact declared owner");
                ResourceSiteHarvestProcess.reduceCellSkipped(state, skipped.siteId(), skipped);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(skipped.siteId(), skipped)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestTargetRetargeted retargeted) {
            try {
                ScheduledAction binding = command.scheduleBinding().map(
                        io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("HOT area work retarget has no continuation binding"));
                if (retargeted.hotLeaseId().isEmpty()
                        || !binding.kind().equals(ResourceSiteHarvestProcess.COLD_PROGRESS_KIND)
                        || !binding.subject().equals(retargeted.siteId())
                        || !binding.id().equals(retargeted.coldScheduleId())
                        || binding.dueAt().ticks() != retargeted.coldDueAt())
                    throw new IllegalArgumentException("HOT area work retarget has a foreign owner");
                ResourceSiteHarvestRetargeting.reduceTargetRetargeted(state, retargeted.siteId(), retargeted);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(retargeted.siteId(), retargeted)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestRouteBlocked blocked) {
            try {
                ScheduledAction binding = command.scheduleBinding().map(
                        io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("HOT farmer route block has no continuation binding"));
                if (!binding.kind().equals(ResourceSiteHarvestProcess.COLD_PROGRESS_KIND)
                        || !binding.subject().equals(blocked.siteId()) || !binding.id().equals(blocked.coldScheduleId())
                        || binding.dueAt().ticks() != blocked.coldDueAt())
                    throw new IllegalArgumentException("HOT farmer route block has a foreign continuation");
                ResourceSiteHarvestProcess.reduceRouteBlocked(state, blocked.siteId(), blocked);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(blocked.siteId(), blocked)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestRouteCleared cleared) {
            try {
                // Historical route-clear events remain replayable, but a new semantic
                // movement block may no longer be cleared by a path-plan assertion.
                // Only HOT goal arrival retains the physical body and lifts that block.
                if (cleared.expected().reason()
                            != ResourceSiteHarvestNavigationBlock.Reason.CONTINUATION_UNAVAILABLE)
                    throw new IllegalArgumentException("field movement goal clears only on observed HOT arrival");
                ScheduledAction binding = command.scheduleBinding().map(
                        io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action).orElseThrow(
                        () -> new IllegalArgumentException("HOT farmer route clearance has no continuation binding"));
                if (!binding.kind().equals(ResourceSiteHarvestProcess.COLD_PROGRESS_KIND)
                        || !binding.subject().equals(cleared.siteId()) || !binding.id().equals(cleared.coldScheduleId())
                        || binding.dueAt().ticks() != cleared.coldDueAt())
                    throw new IllegalArgumentException("HOT farmer route clearance has a foreign continuation");
                ResourceSiteHarvestProcess.reduceRouteCleared(state, cleared.siteId(), cleared);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(cleared.siteId(), cleared)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestBatchDelivered delivered) {
            try {
                ResourceSitePhysicalIntentStateSupport.deliverHarvestBatch(state, delivered);
                SubjectId settlementId = state.resourceSite(delivered.receipt().siteId()).settlementId();
                return new CommandPlan.Accepted(List.of(new ProposedEvent(delivered.receipt().siteId(), delivered),
                        new ProposedEvent(settlementId, new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(
                                StrategicObjectiveProcess.stockReconsideration(settlementId, delivered.receipt().jobId(),
                                        delivered.deliveredYieldBefore(), Math.addExact(command.submittedAt().ticks(), 1L))))));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteHarvestBatchPrepared prepared) {
            try {
                ResourceSiteHarvestProcess.reduceBatchPrepared(state, prepared.siteId(), prepared);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(prepared.siteId(), prepared)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceSiteConflictObserved conflict) {
            try { return new CommandPlan.Accepted(ResourceSiteProcess.planConflict(state, conflict)); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceFieldCellObserved observed) {
            try {
                var after = ResourceSiteProcess.reduceCellObserved(state, observed.siteId(), observed);
                var events = new java.util.ArrayList<ProposedEvent>();
                events.add(new ProposedEvent(observed.siteId(), observed));
                events.addAll(ResourceFieldGrowthProcess.afterObservedGrowth(state, after, observed, command.submittedAt().ticks()));
                return new CommandPlan.Accepted(List.copyOf(events));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceFieldWorkAccessObserved observed) {
            try {
                ResourceSiteProcess.reduceWorkAccessObserved(state, observed.siteId(), observed);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(observed.siteId(), observed)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceFieldWorldChangeHeld held) {
            try {
                ResourceSiteProcess.reduceWorldChangeHeld(state, held.observation().siteId(), held);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(held.observation().siteId(), held)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceFieldWorldChangeAcknowledged acknowledged) {
            try {
                ResourceSiteProcess.reduceWorldChangeAcknowledged(state, acknowledged.observation().siteId(), acknowledged);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(acknowledged.observation().siteId(), acknowledged)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceFieldForeignChangeHeld held) {
            try {
                ResourceSiteProcess.reduceForeignChangeHeld(state, held.siteId(), held);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(held.siteId(), held)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceFieldForeignCellObserved observed) {
            try {
                ResourceSiteProcess.reduceForeignCellObserved(state, observed.hold().siteId(), observed);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(observed.hold().siteId(), observed)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceFieldForeignChangeAcknowledged acknowledged) {
            try {
                ResourceSiteProcess.reduceForeignChangeAcknowledged(state, acknowledged.hold().siteId(), acknowledged);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(acknowledged.hold().siteId(), acknowledged)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResourceFieldPlayerBreakPrepared prepared) {
            try {
                ResourceSiteProcess.reducePlayerBreakPrepared(state, prepared.siteId(), prepared);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(prepared.siteId(), prepared)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
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
            case ResourceSiteHarvestColdGoalAdvanced advanced -> ResourceSiteHarvestProcess.reduceColdGoalAdvanced(state, event.subject(), advanced);
            case ResourceSiteHarvestColdGoalHeld held -> ResourceSiteHarvestProcess.reduceColdGoalHeld(state, event.subject(), held);
            case ResourceSiteHarvestHotTransitObserved observed -> ResourceSiteHarvestProcess.reduceHotTransitObserved(state, event.subject(), observed);
            case ResourceSiteHarvestReturned returned -> ResourceSiteHarvestProcess.reduceReturned(state, event.subject(), returned);
            case ResourceSiteHarvestSegmentRenewed renewed -> ResourceSiteHarvestProcess.reduceSegmentRenewed(state, event.subject(), renewed);
            case ResourceSiteHarvestBlockedCellSkipped skipped -> ResourceSiteHarvestProcess.reduceBlockedCellSkipped(state, event.subject(), skipped);
            case ResourceSiteHarvestWorkChanged changed -> ResourceSiteHarvestWorkProcess.reduce(state, event.subject(), changed);
            case ResourceSiteHarvestImmatureCellSkipped skipped -> ResourceSiteHarvestProcess.reduceCellSkipped(state, event.subject(), skipped);
            case ResourceSiteHarvestTargetRetargeted retargeted -> ResourceSiteHarvestRetargeting.reduceTargetRetargeted(state, event.subject(), retargeted);
            case ResourceSiteHarvestRouteBlocked blocked -> ResourceSiteHarvestProcess.reduceRouteBlocked(state, event.subject(), blocked);
            case ResourceSiteHarvestRouteCleared cleared -> ResourceSiteHarvestProcess.reduceRouteCleared(state, event.subject(), cleared);
            case ResourceSiteHarvestBatchDelivered delivered -> {
                if (!event.subject().equals(delivered.receipt().siteId()))
                    throw new IllegalArgumentException("field batch receipt has a foreign event owner");
                yield ResourceSitePhysicalIntentStateSupport.deliverHarvestBatch(state, delivered);
            }
            case ResourceSiteHarvestBatchPrepared prepared -> ResourceSiteHarvestProcess.reduceBatchPrepared(state, event.subject(), prepared);
            case ResourceSiteHarvestHotTraversalAdvanced advanced -> ResourceSiteHarvestProcess.reduceHotTraversalAdvanced(state, event.subject(), advanced);
            case ResourceSiteHarvestHotGoalArrived arrived -> ResourceSiteHarvestProcess.reduceHotGoalArrived(state, event.subject(), arrived);
            case ResourceSiteHarvestProgressed progressed -> ResourceSiteHarvestProcess.reduceProgressed(state, event.subject(), progressed);
            case ResourceSiteHarvestSceneReconciled reconciled -> ResourceSiteHarvestSceneReconciliation.reduce(state, event.subject(), reconciled);
            case ResourceSiteHarvestHandProjected projected -> ResourceSiteHarvestProcess.reduceHandProjected(state, event.subject(), projected);
            case ResourceSiteHarvestHandRelease released -> ResourceSiteHarvestProcess.reduceHandRelease(state, event.subject(), released);
            case ResourceSiteHarvestSceneLeasePrepared prepared -> reduceHarvestScenePrepared(state, event.subject(), event, prepared);
            case ResourceSiteHarvestSceneLeaseHandoff handoff -> reduceHarvestSceneHandoff(state, event.subject(), event, handoff);
            case ResourceSiteHarvestScenePreparationAborted aborted -> reduceHarvestScenePreparationAborted(state, event.subject(), aborted);
            case ResourceSiteConflictObserved conflict -> ResourceSiteProcess.reduceConflict(state, event.subject(), conflict);
            case ResourceFieldCellObserved observed -> ResourceSiteProcess.reduceCellObserved(state, event.subject(), observed);
            case ResourceFieldWorkAccessObserved observed -> ResourceSiteProcess.reduceWorkAccessObserved(state, event.subject(), observed);
            case ResourceFieldWorldChangeHeld held -> ResourceSiteProcess.reduceWorldChangeHeld(state, event.subject(), held);
            case ResourceFieldWorldChangeAcknowledged acknowledged -> ResourceSiteProcess.reduceWorldChangeAcknowledged(state, event.subject(), acknowledged);
            case ResourceFieldForeignChangeHeld held -> ResourceSiteProcess.reduceForeignChangeHeld(state, event.subject(), held);
            case ResourceFieldForeignCellObserved observed -> ResourceSiteProcess.reduceForeignCellObserved(state, event.subject(), observed);
            case ResourceFieldForeignChangeAcknowledged acknowledged -> ResourceSiteProcess.reduceForeignChangeAcknowledged(state, event.subject(), acknowledged);
            case ResourceFieldPlayerBreakPrepared prepared -> ResourceSiteProcess.reducePlayerBreakPrepared(state, event.subject(), prepared);
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
        var job = (ResourceSiteHarvestJob) state.resourceSites().site(
                FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId()).activeWork().orElseThrow();
        return ResourceSiteHarvestLabour.pauseJob(state, job, event.instant().ticks()).prepareSceneLease(lease);
    }

    private static FrontierWorldState reduceHarvestSceneHandoff(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                                FrontierEvent event, ResourceSiteHarvestSceneLeaseHandoff handoff) {
        SceneLease lease = handoff.lease();
        if (!subject.equals(FrontierResourceSiteHarvestSceneSupport.owner(state, FrontierSceneBehaviors.resourceSiteHarvest(lease)))
                || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("resource-site harvest scene hand-off does not match its retained field work");
        }
        var job = (ResourceSiteHarvestJob) state.resourceSites().site(
                FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId()).activeWork().orElseThrow();
        FrontierWorldState paused = ResourceSiteHarvestLabour.pauseJob(state, job, event.instant().ticks());
        FrontierWorldState rebased = ResourceSiteHarvestProcess.rebaseForAmbientHandoff(paused, subject, handoff);
        return rebased.handoffAmbientScene(new SceneLeaseHandoff(lease, handoff.ambientMembers()));
    }

    private static FrontierWorldState reduceHarvestScenePreparationAborted(FrontierWorldState state,
            SubjectId subject, ResourceSiteHarvestScenePreparationAborted aborted) {
        SceneLease lease = state.sceneLeases().get(aborted.leaseId());
        ResourceSite site = state.resourceSite(aborted.siteId());
        if (lease == null || lease.status() != SceneLeaseStatus.PREPARED
                && (lease.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART || lease.recoveryEvidence().isPresent())
                || !FrontierSceneBehaviors.isResourceSiteHarvest(lease)
                || site == null || !subject.equals(site.settlementId())
                || !FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(aborted.siteId())
                || !FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(aborted.jobId())
                || lease.ambientHandoffActorIds().size() != 0
                || lease.members().size() != 1
                || state.resourceSites().site(aborted.siteId()).activeWork()
                        .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                        .filter(job -> job.id().equals(aborted.jobId())
                                && lease.members().getFirst().actorId().equals(job.workerId())).isEmpty()) {
            throw new IllegalArgumentException("field preparation abort lacks one exact body-free job scene");
        }
        return FrontierSceneLeaseStateSupport.abortPrepared(state, aborted.leaseId());
    }
}
