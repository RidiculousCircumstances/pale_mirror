package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.Set;

/** Sole pure-domain reducer owner for resident-owned settlement service work. */
final class FrontierSettlementServiceWorkProcessModule implements FrontierWorldProcessModule {
    @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() {
        return List.of(new FunctionalPhysicalIntentLifecycleCapability(
                PhysicalIntentLifecycleDeclaration.physical(PhysicalIntentLifecycleOwner.SETTLEMENT_SERVICE_WORK,
                        Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE),
                        Set.of(PhysicalIntentRoleSchema.SERVICE_INPUT_ISSUE)),
                (state, command, prepared) -> FrontierWorldCommandPlanner.rejected("physical executor cannot prepare settlement service work"),
                FrontierSettlementServiceWorkProcessModule::planPhysicalTransition,
                FrontierSettlementServiceWorkProcessModule::reducePhysicalPrepared,
                FrontierSettlementServiceWorkProcessModule::reducePhysicalTransition,
                PhysicalIntentLifecycleRetirementPolicy.of(
                        FrontierSettlementServiceWorkProcessModule::planPhysicalTransition,
                        FrontierSettlementServiceWorkProcessModule::reducePhysicalTransition), intent -> FencedRecoveryAsset.EFFECT,
                retirementAccount(PhysicalIntentLifecycleOwner.SETTLEMENT_SERVICE_WORK), PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery(), PhysicalIntentRecoveryDiagnosticProducer.SETTLEMENT_SERVICE_WORK),
                new FunctionalPhysicalIntentLifecycleCapability(
                        PhysicalIntentLifecycleDeclaration.physical(PhysicalIntentLifecycleOwner.SETTLEMENT_SERVICE_DECONTAMINATION,
                                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.DECONTAMINATION),
                                Set.of(PhysicalIntentRoleSchema.SERVICE_DECONTAMINATION)),
                        (state, command, prepared) -> FrontierWorldCommandPlanner.rejected("physical executor cannot prepare settlement service decontamination"),
                        FrontierSettlementServiceWorkProcessModule::planServiceDecontaminationTransition,
                        FrontierSettlementServiceWorkProcessModule::reduceServiceDecontaminationPreparation,
                        FrontierSettlementServiceWorkProcessModule::reduceServiceDecontaminationTransition,
                        PhysicalIntentLifecycleRetirementPolicy.of(
                                FrontierSettlementServiceWorkProcessModule::planServiceDecontaminationTransition,
                                FrontierSettlementServiceWorkProcessModule::reduceServiceDecontaminationTransition), intent -> FencedRecoveryAsset.EFFECT,
                        retirementAccount(PhysicalIntentLifecycleOwner.SETTLEMENT_SERVICE_DECONTAMINATION), PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery(),
                                PhysicalIntentRecoveryDiagnosticProducer.SETTLEMENT_SERVICE_DECONTAMINATION));
    }

    private static PhysicalIntentRetirementAccount retirementAccount(PhysicalIntentLifecycleOwner owner) {
        return PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                FrontierSettlementServiceWorkProcessModule::bindRetirement,
                FrontierSettlementServiceWorkProcessModule::verifyRetirementBinding,
                (before, after, intent, transition, binding) -> {
                    if (intent.lifecycleOwner() != owner) throw new IllegalArgumentException("settlement-service retirement account owner mismatch");
                    SettlementServiceWork work = before.serviceWorks().get(intent.causeSubjectId());
                    if (work == null) throw new IllegalArgumentException("service retirement account lacks exact retained work");
                    if (after != before && transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED) {
                        SettlementServiceWork retained = after.serviceWorks().get(work.id());
                        if (retained == null) throw new IllegalArgumentException("service retirement account lost its exact work outcome");
                        ExactItemStack retainedItem = after.inventory().items().get(work.inputItemId());
                        if (owner == PhysicalIntentLifecycleOwner.SETTLEMENT_SERVICE_WORK
                                && (retained.phase() == SettlementServiceWorkPhase.INPUT_ISSUE_PENDING
                                || retainedItem == null || !(retainedItem.custody() instanceof InventoryCustody.Actor actor)
                                || !actor.actorId().equals(work.workerId()))) {
                            throw new IllegalArgumentException("service retirement account did not prove its exact input hand-off outcome");
                        }
                        if (owner == PhysicalIntentLifecycleOwner.SETTLEMENT_SERVICE_DECONTAMINATION
                                && (retained.phase() != SettlementServiceWorkPhase.COMPLETED
                                    && !(retained.phase() == SettlementServiceWorkPhase.BLOCKED
                                        && after.actorLocations().get(work.workerId()).condition().status() == ActorLifeStatus.DEAD)
                                || after.inventory().items().containsKey(work.inputItemId()))) {
                            throw new IllegalArgumentException("service retirement account did not prove its exact endpoint outcome");
                        }
                    }
                });
    }

    private static PhysicalIntentRetirementAccount.Binding bindRetirement(FrontierWorldState before, FrontierCommand command,
                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                            PhysicalIntentTransition transition) {
        SettlementServiceWork work = before == null ? null : before.serviceWorks().get(intent.causeSubjectId());
        if (work == null) throw new IllegalArgumentException("service retirement account has no exact work");
        List<FrontierDomainRelationships.Edge> relations = retirementRelations(work);
        var continuation = command == null
                ? new PhysicalIntentRetirementAccount.CheckedNone<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>(
                        io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)
                : command.scheduleBinding().<PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>>map(binding -> new PhysicalIntentRetirementAccount.Exact<>(binding.action().id()))
                .orElseGet(() -> new PhysicalIntentRetirementAccount.CheckedNone<>(io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION));
        return new PhysicalIntentRetirementAccount.Binding(intent.lifecycleOwner(), intent.id(), new PhysicalIntentRetirementAccount.Exact<>(relations), continuation,
                new PhysicalIntentRetirementAccount.Exact<>(work.workerId()), new PhysicalIntentRetirementAccount.Exact<>(work.inputItemId()),
                transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                        ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY
                        : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE);
    }

    private static void verifyRetirementBinding(FrontierWorldState before, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                PhysicalIntentTransition transition, PhysicalIntentRetirementAccount.Binding binding) {
        SettlementServiceWork work = before.serviceWorks().get(intent.causeSubjectId());
        if (work == null) throw new IllegalArgumentException("service retirement account has no exact work");
        List<FrontierDomainRelationships.Edge> relations = retirementRelations(work);
        PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding, new PhysicalIntentRetirementAccount.Binding(
                intent.lifecycleOwner(), intent.id(), new PhysicalIntentRetirementAccount.Exact<>(relations), binding.continuation(),
                new PhysicalIntentRetirementAccount.Exact<>(work.workerId()), new PhysicalIntentRetirementAccount.Exact<>(work.inputItemId()),
                transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                        ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE));
    }

    private static List<FrontierDomainRelationships.Edge> retirementRelations(SettlementServiceWork work) {
        FrontierDomainRelationships.SubjectEndpoint owner = new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.SERVICE_WORK, work.id());
        return List.of(
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.SERVICE_TASK, owner, owner,
                        new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.TASK, work.taskId()), FrontierDomainRelationships.Lifecycle.ACTIVE, work.id().value()),
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.SERVICE_WORKER, owner, owner,
                        new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESIDENT, work.workerId()), FrontierDomainRelationships.Lifecycle.ACTIVE, work.id().value()),
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.SERVICE_FACILITY, owner, owner,
                        new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.STRUCTURE, work.facilityId()), FrontierDomainRelationships.Lifecycle.ACTIVE, work.id().value()),
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.SERVICE_INPUT, owner, owner,
                        new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.EXACT_ITEM, work.inputItemId()), FrontierDomainRelationships.Lifecycle.ACTIVE, work.id().value()));
    }

    private static CommandPlan planServiceDecontaminationTransition(FrontierWorldState state, FrontierCommand command,
                                                                     io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                     PhysicalIntentTransition transition) {
        SettlementServiceWork work = state.serviceWorks().get(intent.causeSubjectId());
        if (work == null) return FrontierWorldCommandPlanner.rejected("service decontamination has no exact retained work");
        if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFLICTED)
            SettlementServiceDecontaminationStateSupport.validateFatalityAbandonment(state, intent);
        if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING
                || transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED) {
            SettlementServiceDecontaminationStateSupport.validateIntent(state, intent);
        }
        ProposedEvent physical = new ProposedEvent(work.settlementId(), transition);
        if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED) {
            return new CommandPlan.Accepted(List.of(physical, new ProposedEvent(work.settlementId(),
                    new StrategicTaskTransition(work.taskId(), StrategicTaskStatus.COMPLETED))));
        }
        if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            return new CommandPlan.Accepted(List.of(physical, new ProposedEvent(work.settlementId(),
                    new StrategicTaskTransition(work.taskId(), StrategicTaskStatus.BLOCKED))));
        }
        return new CommandPlan.Accepted(List.of(physical));
    }

    private static FrontierWorldState reduceServiceDecontaminationPreparation(FrontierWorldState state,
                                                                               io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                                               io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        SettlementServiceWork work = state.serviceWorks().get(intent.causeSubjectId());
        if (work == null || !subject.equals(work.settlementId())) throw new IllegalArgumentException("service decontamination preparation lacks its retained settlement owner");
        return state.preparePhysicalIntent(intent);
    }

    private static FrontierWorldState reduceServiceDecontaminationTransition(FrontierWorldState state,
                                                                              io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                                              io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                              PhysicalIntentTransition transition) {
        SettlementServiceWork work = state.serviceWorks().get(intent.causeSubjectId());
        if (work == null || !subject.equals(work.settlementId())) throw new IllegalArgumentException("service decontamination transition lacks its retained settlement owner");
        if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFLICTED)
            SettlementServiceDecontaminationStateSupport.validateFatalityAbandonment(state, intent);
        if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING
                || transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED) {
            SettlementServiceDecontaminationStateSupport.validateIntent(state, intent);
        }
        return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                (currentState, current, evidence, intents) -> SettlementServiceDecontaminationStateSupport.complete(currentState, current, evidence, new java.util.LinkedHashMap<>(intents)),
                (currentState, current, intents) -> SettlementServiceDecontaminationStateSupport.unknown(currentState, current, new java.util.LinkedHashMap<>(intents)),
                transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFLICTED
                        ? PhysicalIntentTransitionStorage.RecoveryEdges.INSPECT_AND_ABANDON
                        : PhysicalIntentTransitionStorage.RecoveryEdges.CONFIRM_ONLY);
    }

    private static CommandPlan planPhysicalTransition(FrontierWorldState state, FrontierCommand command,
                                                       io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                       PhysicalIntentTransition transition) {
        SettlementServiceWork work = state.serviceWorks().get(intent.causeSubjectId());
        if (work == null) return FrontierWorldCommandPlanner.rejected("service physical intent has no exact retained work");
        if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING)
            SettlementServiceInputIssueStateSupport.validateIntent(state, intent);
        else if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFLICTED)
            SettlementServiceInputIssueStateSupport.validateFatalityAbandonment(state, intent);
        else SettlementServiceInputIssueStateSupport.validateRetainedInput(state, intent);
        return new CommandPlan.Accepted(List.of(new ProposedEvent(work.settlementId(), transition)));
    }

    private static FrontierWorldState reducePhysicalPrepared(FrontierWorldState state,
                                                              io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                              io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        SettlementServiceWork work = state.serviceWorks().get(intent.causeSubjectId());
        if (work == null || !subject.equals(work.settlementId())) throw new IllegalArgumentException("service work preparation lacks its retained settlement owner");
        SettlementServiceInputIssueStateSupport.validateIntent(state, intent);
        return state.preparePhysicalIntent(intent);
    }

    private static FrontierWorldState reducePhysicalTransition(FrontierWorldState state,
                                                               io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                               io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                               PhysicalIntentTransition transition) {
        SettlementServiceWork work = state.serviceWorks().get(intent.causeSubjectId());
        if (work == null || !subject.equals(work.settlementId())) throw new IllegalArgumentException("service work transition lacks its retained settlement owner");
        if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING)
            SettlementServiceInputIssueStateSupport.validateIntent(state, intent);
        else if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFLICTED)
            SettlementServiceInputIssueStateSupport.validateFatalityAbandonment(state, intent);
        else SettlementServiceInputIssueStateSupport.validateRetainedInput(state, intent);
        return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                (currentState, current, evidence, intents) -> SettlementServiceInputIssueStateSupport.complete(currentState, current,
                        SettlementServiceInputIssueStateSupport.requireReceipt(evidence), new java.util.LinkedHashMap<>(intents)),
                SettlementServiceInputIssueStateSupport::unknown,
                transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFLICTED
                        ? PhysicalIntentTransitionStorage.RecoveryEdges.INSPECT_AND_ABANDON
                        : PhysicalIntentTransitionStorage.RecoveryEdges.CONFIRM_ONLY);
    }
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
            var work = FrontierSettlementServiceWorkSceneSupport.require(state, FrontierSceneBehaviors.serviceWork(prepared.lease()));
            return SettlementServiceJourneyKnowledge.atScopeAdmission(state, work).prepareSceneLease(prepared.lease());
        }
        if (event.payload() instanceof SettlementServiceWorkSceneLeaseHandoff handoff) {
            if (!event.subject().equals(FrontierSettlementServiceWorkSceneSupport.owner(state, FrontierSceneBehaviors.serviceWork(handoff.lease())))
                    || !handoff.lease().handoffInstant().equals(event.instant())) throw new IllegalArgumentException("service-work hand-off has foreign owner or instant");
            var work = FrontierSettlementServiceWorkSceneSupport.require(state, FrontierSceneBehaviors.serviceWork(handoff.lease()));
            var admitted = SettlementServiceJourneyKnowledge.atScopeAdmission(state, work);
            FrontierSettlementServiceWorkSceneSupport.validatePrepared(admitted, handoff.lease());
            return admitted.handoffAmbientScene(new SceneLeaseHandoff(handoff.lease(), handoff.ambientMembers()));
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
