package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.Set;

/** Exact reducer owner for company, market and production facts. */
final class FrontierEconomyProcessModule implements FrontierWorldProcessModule {
    @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() {
        return List.of(new FunctionalPhysicalIntentLifecycleCapability(
                PhysicalIntentLifecycleDeclaration.physical(PhysicalIntentLifecycleOwner.PRODUCTION_WORK,
                        Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.PRODUCTION_TRANSFORMATION),
                        Set.of(PhysicalIntentRoleSchema.PRODUCTION, PhysicalIntentRoleSchema.PRODUCTION_RESOURCES)),
                (state, command, prepared) -> FrontierWorldCommandPlanner.rejected("physical executor cannot prepare production work"),
                (state, command, intent, transition) -> {
                    ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
                    if (job == null) return FrontierWorldCommandPlanner.rejected("production transformation has no active job");
                    try {
                        ProductionTransformationStateSupport.validateIntent(state, intent);
                        return new CommandPlan.Accepted(List.of(new ProposedEvent(job.settlementId(), transition)));
                    } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
                },
                (state, subject, intent) -> {
                    ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
                    if (job == null || !subject.equals(job.settlementId())) {
                        throw new IllegalArgumentException("production transformation must be prepared by its settlement");
                    }
                    ProductionTransformationStateSupport.validateIntent(state, intent);
                    return state.preparePhysicalIntent(intent);
                },
                FrontierEconomyProcessModule::reduceProductionTransition, PhysicalIntentLifecycleRetirementPolicy.of(
                        (state, command, intent, transition) -> {
                            ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
                            if (job == null) return FrontierWorldCommandPlanner.rejected("production transformation has no active job");
                            try {
                                ProductionTransformationStateSupport.validateIntent(state, intent);
                                return new CommandPlan.Accepted(List.of(new ProposedEvent(job.settlementId(), transition)));
                            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
                        },
                        FrontierEconomyProcessModule::reduceProductionTransition), intent -> FencedRecoveryAsset.EFFECT,
                retirementAccount(PhysicalIntentLifecycleOwner.PRODUCTION_WORK), PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery(), PhysicalIntentRecoveryDiagnosticProducer.PRODUCTION_WORK));
    }

    private static PhysicalIntentRetirementAccount retirementAccount(PhysicalIntentLifecycleOwner owner) {
        return PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                FrontierEconomyProcessModule::bindRetirement,
                FrontierEconomyProcessModule::verifyRetirementBinding,
                (before, after, intent, transition, binding) -> {
                    if (intent.lifecycleOwner() != owner || !before.productionJobs().containsKey(intent.causeSubjectId())) {
                        throw new IllegalArgumentException("production retirement account lacks its exact pre-state job");
                    }
                    ProductionJob job = before.productionJobs().get(intent.causeSubjectId());
                    if (!(binding.relations() instanceof PhysicalIntentRetirementAccount.Exact<java.util.List<FrontierDomainRelationships.Edge>> exact)
                            || exact.value().size() != job.inputQuantities().size() + 2
                            || !(binding.leaseOrCarrier() instanceof PhysicalIntentRetirementAccount.Exact<SubjectId> carrier)
                            || !carrier.value().equals(job.workerId()) || !(binding.commitment() instanceof PhysicalIntentRetirementAccount.Exact<SubjectId> commitment)
                            || !commitment.value().equals(job.consumedItemId())) {
                        throw new IllegalArgumentException("production retirement account does not retain its exact declared relations, worker and input commitment");
                    }
                    if (job.inputHold() instanceof ProductionInputHold.FungibleBound) {
                        FungibleProductionStateSupport.verifyRetirement(before, after, job, transition.status());
                        return;
                    }
                    if (after != before && transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED
                            && (after.productionJobs().containsKey(job.id()) || after.inventory().items().containsKey(job.consumedItemId())
                            || !after.inventory().items().containsKey(job.outputItemId()))) {
                        throw new IllegalArgumentException("production retirement account did not prove the committed input-to-output disposition");
                    }
                    if (after != before && transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                            && (!after.productionJobs().containsKey(job.id()) || !after.inventory().items().containsKey(job.consumedItemId()))) {
                        throw new IllegalArgumentException("production retirement account lost its ambiguous worker/input commitment");
                    }
                });
    }

    private static PhysicalIntentRetirementAccount.Binding bindRetirement(FrontierWorldState before, FrontierCommand command,
                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                            PhysicalIntentTransition transition) {
        var continuation = (command == null ? java.util.Optional.<io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding>empty() :
                command.scheduleBinding()).<PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>>map(value -> new PhysicalIntentRetirementAccount.Exact<>(value.action().id()))
                .orElseGet(() -> new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION));
        return retirementFacts(before, intent, transition, continuation);
    }

    private static void verifyRetirementBinding(FrontierWorldState before, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                PhysicalIntentTransition transition, PhysicalIntentRetirementAccount.Binding binding) {
        PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding, retirementFacts(before, intent, transition, binding.continuation()));
    }

    private static PhysicalIntentRetirementAccount.Binding retirementFacts(FrontierWorldState before,
                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                            PhysicalIntentTransition transition,
                                                                            PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId> continuation) {
        ProductionJob job = before.productionJobs().get(intent.causeSubjectId());
        if (job == null) throw new IllegalArgumentException("production retirement account has no exact job");
        FrontierDomainRelationships.SubjectEndpoint owner = new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.PRODUCTION_JOB, job.id());
        FrontierDomainRelationships.EntityKind resourceKind = job.inputHold().resourceEntityKind();
        java.util.List<FrontierDomainRelationships.Edge> relations = new java.util.ArrayList<>();
        relations.add(FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.JOB_WORKER, owner, owner,
                new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESIDENT, job.workerId()), FrontierDomainRelationships.Lifecycle.ACTIVE, job.id().value()));
        job.inputQuantities().keySet().stream().sorted().forEach(inputId -> relations.add(
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.JOB_INPUT, owner, owner,
                        new FrontierDomainRelationships.SubjectEndpoint(resourceKind, inputId), FrontierDomainRelationships.Lifecycle.ACTIVE, job.id().value())));
        relations.add(FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.JOB_OUTPUT, owner, owner,
                new FrontierDomainRelationships.SubjectEndpoint(resourceKind, job.outputItemId()), FrontierDomainRelationships.Lifecycle.ACTIVE, job.id().value()));
        return new PhysicalIntentRetirementAccount.Binding(intent.lifecycleOwner(), intent.id(), new PhysicalIntentRetirementAccount.Exact<>(relations), continuation,
                new PhysicalIntentRetirementAccount.Exact<>(job.workerId()), new PhysicalIntentRetirementAccount.Exact<>(job.consumedItemId()),
                transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                        ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE);
    }

    private static FrontierWorldState reduceProductionTransition(FrontierWorldState state, SubjectId subject,
                                                                   io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                   PhysicalIntentTransition transition) {
        ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
        if (job == null || !subject.equals(job.settlementId())) {
            throw new IllegalArgumentException("production transformation transition lacks settlement ownership");
        }
        return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                (currentState, current, evidence, intents) -> {
                    if (current.roles().schema() == PhysicalIntentRoleSchema.PRODUCTION_RESOURCES) {
                        if (!(evidence instanceof FungibleProductionObservation production)) {
                            throw new IllegalArgumentException("resource production requires its nominal lot receipt");
                        }
                        return FungibleProductionStateSupport.complete(currentState, current, production, intents);
                    }
                    if (!(evidence instanceof ProductionTransformationObservation production)) {
                        throw new IllegalArgumentException("production transformation requires exact physical receipt");
                    }
                    return ProductionTransformationStateSupport.complete(currentState, current, production, intents);
                },
                (currentState, current, intents) -> ProductionTransformationStateSupport.unknown(currentState, current, intents));
    }
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
        if (command.payload() instanceof ProductionWorkProgressed progressed) return planHotProgress(state, command, progressed);
        if (command.payload() instanceof ProductionWorkTraversalAdvanced advanced) return planHotTraversal(state, advanced);
        if (command.payload() instanceof ProductionWorkTraversalBlocked blocked) return planHotTraversalBlocked(state, command, blocked);
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
            case MarketRelationshipIncidentRecorded recorded -> MarketClearingProcess.reduceRelationshipIncident(state, event.subject(), recorded);
            case MarketDemandExpired expired -> MarketClearingProcess.reduceExpired(state, event.subject(), event.instant().ticks(), expired);
            case MarketDemandCancelled cancelled -> MarketClearingProcess.reduceCancelled(state, event.subject(), cancelled);
            case ProductionStarted started -> ProductionProcess.reduceStarted(state, event.subject(), started);
            case ProductionCompleted completed -> ProductionProcess.reduceCompleted(state, event.subject(), completed);
            case FungibleProductionCompleted completed -> ProductionProcess.reduceFungibleCompleted(state, event.subject(), completed);
            case ProductionWorkProgressed progressed -> ProductionProcess.reduceWorkProgressed(state, event.subject(), progressed);
            case ProductionWorkTraversalAdvanced advanced -> ProductionProcess.reduceWorkTraversalAdvanced(state, event.subject(), advanced);
            case ProductionColdWorkAdvanced advanced -> ProductionProcess.reduceColdWorkAdvanced(state, event.subject(), advanced);
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

    private static CommandPlan planHotProgress(FrontierWorldState state, FrontierCommand command, ProductionWorkProgressed progressed) {
        try {
            ProductionJob job = FrontierProductionWorkSceneSupport.require(state, new ProductionWorkSceneCause(progressed.jobId()));
            if (!hot(state, job.id())) return FrontierWorldCommandPlanner.rejected("production work progress requires its HOT scene");
            ProductionProcess.reduceWorkProgressed(state, job.settlementId(), progressed);
            if (job.workProgress().stage() == ProductionWorkProgress.Stage.INPUT_READY
                    || job.workProgress().stage() == ProductionWorkProgress.Stage.PROCESSING) {
                var binding = command.scheduleBinding().orElseThrow(() -> new IllegalArgumentException("production labor requires its retained schedule")).action();
                if (!binding.equals(ProductionProcess.complete(job, binding.dueAt().ticks())))
                    throw new IllegalArgumentException("production labor has a foreign schedule");
                long nextDue = job.workProgress().nextWorkDue(binding.dueAt().ticks(), command.submittedAt().ticks());
                var replacement = ProductionProcess.complete(job, nextDue);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(job.settlementId(), progressed),
                        new ProposedEvent(job.id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled(binding.id(), replacement))));
            }
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
    private static CommandPlan planHotTraversalBlocked(FrontierWorldState state, FrontierCommand command, ProductionWorkTraversalBlocked blocked) {
        try {
            SceneLease lease = state.sceneLeases().get(blocked.leaseId());
            if (lease != null && FrontierSceneBehaviors.isProductionWork(lease)) {
                SubjectId expected = FrontierSceneBehaviors.productionWork(lease).jobId();
                if (!expected.equals(blocked.jobId())) return new CommandPlan.Accepted(ProductionProcess.planRelationshipConflict(state, command, lease, blocked));
            }
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
