package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Exact owner for contracts, cargo, route operations and their HOT/COLD scenes. */
final class FrontierLogisticsProcessModule implements FrontierWorldProcessModule {
    @Override public List<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> retiredSchedules(
            FrontierWorldState previous, FrontierWorldState next,
            java.util.function.Supplier<List<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction>> pending) {
        return ResidentLifeScheduleRetirement.afterDeath(previous, next, pending);
    }
    @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() {
        return List.of(new FunctionalPhysicalIntentLifecycleCapability(
                PhysicalIntentLifecycleDeclaration.physical(PhysicalIntentLifecycleOwner.ROUTE_OPERATION,
                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_LOADING,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_HANDOFF),
                        Set.of(PhysicalIntentRoleSchema.CARGO_LOADING, PhysicalIntentRoleSchema.CARGO_HANDOFF)),
                (state, command, prepared) -> FrontierWorldCommandPlanner.rejected("physical executor cannot prepare route operation work"),
                FrontierLogisticsProcessModule::planPhysicalTransition,
                FrontierLogisticsProcessModule::reducePhysicalPrepared,
                FrontierLogisticsProcessModule::reducePhysicalTransition,
                PhysicalIntentLifecycleRetirementPolicy.of(
                        FrontierLogisticsProcessModule::planPhysicalTransition,
                        FrontierLogisticsProcessModule::reducePhysicalTransition),
                intent -> FencedRecoveryAsset.CARGO,
                retirementAccount(PhysicalIntentLifecycleOwner.ROUTE_OPERATION), PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery(), PhysicalIntentRecoveryDiagnosticProducer.ROUTE_OPERATION));
    }

    private static PhysicalIntentRetirementAccount retirementAccount(PhysicalIntentLifecycleOwner owner) {
        return PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                FrontierLogisticsProcessModule::bindRetirement,
                FrontierLogisticsProcessModule::verifyRetirementBinding,
                (before, after, intent, transition, binding) -> {
                    if (intent.lifecycleOwner() != owner) throw new IllegalArgumentException("route retirement account owner mismatch");
                    if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_LOADING) {
                        verifyCargoLoadingRetirement(before, after, intent, transition, binding);
                        return;
                    }
                    RouteOperation operation = before.operations().get(intent.causeSubjectId());
                    if (operation == null || before.contracts().get(operation.contractId()) == null
                            || !before.contracts().get(operation.contractId()).cargoId().equals(operation.cargoId()))
                        throw new IllegalArgumentException("route retirement account lacks exact retained contract/cargo facts");
                    if (!after.operations().containsKey(intent.causeSubjectId()) && transition.status()
                            == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
                        throw new IllegalArgumentException("route retirement account lost ambiguous operation authority");
                    }
                    if (binding.leaseOrCarrier() instanceof PhysicalIntentRetirementAccount.Exact<io.farfrontier.palemirror.frontier.v3.api.SubjectId> carrier
                            && !carrier.value().equals(operation.cargoCarrierId())
                            || binding.commitment() instanceof PhysicalIntentRetirementAccount.Exact<io.farfrontier.palemirror.frontier.v3.api.SubjectId> commitment
                            && !commitment.value().equals(operation.cargoId())) {
                        throw new IllegalArgumentException("route retirement account has a foreign carrier or cargo commitment");
                    }
                    if (after != before && transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED
                            && intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_HANDOFF
                            && before.contracts().values().stream().filter(contract -> contract.id().equals(operation.contractId()))
                            .findFirst().map(contract -> after.contracts().get(contract.id()).status() != ContractStatus.DELIVERED).orElse(true)) {
                        throw new IllegalArgumentException("route retirement account did not prove the committed cargo hand-off");
                    }
                });
    }

    private static PhysicalIntentRetirementAccount.Binding bindRetirement(FrontierWorldState before, FrontierCommand command,
                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                            PhysicalIntentTransition transition) {
        if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_LOADING) {
            return cargoLoadingRetirementBinding(before, command, intent, transition);
        }
        RouteOperation operation = before == null ? null : before.operations().get(intent.causeSubjectId());
        if (operation == null) throw new IllegalArgumentException("route retirement account has no exact operation");
        List<FrontierDomainRelationships.Edge> relations = retirementRelations(before, operation);
        if (relations.size() != 3) throw new IllegalArgumentException("route retirement account does not bind its exact contract/cargo/carrier relations");
        var continuation = command == null
                ? new PhysicalIntentRetirementAccount.CheckedNone<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>(
                        io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)
                : command.scheduleBinding().<PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>>map(binding -> new PhysicalIntentRetirementAccount.Exact<>(binding.action().id()))
                .orElseGet(() -> new PhysicalIntentRetirementAccount.CheckedNone<>(io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION));
        return new PhysicalIntentRetirementAccount.Binding(intent.lifecycleOwner(), intent.id(), new PhysicalIntentRetirementAccount.Exact<>(relations), continuation,
                new PhysicalIntentRetirementAccount.Exact<>(operation.cargoCarrierId()), new PhysicalIntentRetirementAccount.Exact<>(operation.cargoId()),
                transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                        ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY
                        : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE);
    }

    private static void verifyRetirementBinding(FrontierWorldState before, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                PhysicalIntentTransition transition, PhysicalIntentRetirementAccount.Binding binding) {
        if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_LOADING) {
            PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding,
                    cargoLoadingRetirementBinding(before, null, intent, transition));
            return;
        }
        RouteOperation operation = before.operations().get(intent.causeSubjectId());
        if (operation == null) throw new IllegalArgumentException("route retirement account has no exact operation");
        List<FrontierDomainRelationships.Edge> relations = retirementRelations(before, operation);
        PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding, new PhysicalIntentRetirementAccount.Binding(
                intent.lifecycleOwner(), intent.id(), new PhysicalIntentRetirementAccount.Exact<>(relations), binding.continuation(),
                new PhysicalIntentRetirementAccount.Exact<>(operation.cargoCarrierId()), new PhysicalIntentRetirementAccount.Exact<>(operation.cargoId()),
                transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                        ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE));
    }

    /** Exact retained contract/operation fields, never a relationship-view query, bind route retirement. */
    private static List<FrontierDomainRelationships.Edge> retirementRelations(FrontierWorldState state, RouteOperation operation) {
        SupplyContract retainedContract = state.contracts().get(operation.contractId());
        if (retainedContract == null || !retainedContract.cargoId().equals(operation.cargoId())) {
            throw new IllegalArgumentException("route retirement account does not retain its exact contract/cargo facts");
        }
        FrontierDomainRelationships.SubjectEndpoint contract = new FrontierDomainRelationships.SubjectEndpoint(
                FrontierDomainRelationships.EntityKind.SUPPLY_CONTRACT, retainedContract.id());
        FrontierDomainRelationships.SubjectEndpoint route = new FrontierDomainRelationships.SubjectEndpoint(
                FrontierDomainRelationships.EntityKind.ROUTE_OPERATION, operation.id());
        return List.of(
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.CONTRACT_ROUTE_OPERATION, contract, contract, route,
                        FrontierDomainRelationships.Lifecycle.ACTIVE, operation.id().value()),
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.CONTRACT_CARGO, contract, contract,
                        new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.CARGO, retainedContract.cargoId()),
                        FrontierDomainRelationships.Lifecycle.ACTIVE, retainedContract.id().value()),
                FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.ROUTE_CARRIER, route, route,
                        new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESIDENT, operation.cargoCarrierId()),
                        FrontierDomainRelationships.Lifecycle.ACTIVE, operation.id().value()));
    }

    /**
     * Depot removal precedes creation of its route operation.  Its terminal account is therefore
     * the exact ordered contract/cargo pair, not a future operation or carrier that does not yet
     * exist.  This is deliberately a distinct owner-local phase of the route-operation lifecycle.
     */
    private static PhysicalIntentRetirementAccount.Binding cargoLoadingRetirementBinding(FrontierWorldState state, FrontierCommand command,
                                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                                            PhysicalIntentTransition transition) {
        SupplyContract contract = cargoLoadingContract(state, intent);
        var continuation = command == null
                ? new PhysicalIntentRetirementAccount.CheckedNone<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)
                : command.scheduleBinding().<PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>>map(value -> new PhysicalIntentRetirementAccount.Exact<>(value.action().id()))
                .orElseGet(() -> new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION));
        return new PhysicalIntentRetirementAccount.Binding(intent.lifecycleOwner(), intent.id(),
                new PhysicalIntentRetirementAccount.Exact<>(cargoLoadingRelations(contract)), continuation,
                new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_LEASE_OR_CARRIER),
                new PhysicalIntentRetirementAccount.Exact<>(contract.cargoId()),
                transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                        ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY
                        : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE);
    }

    private static void verifyCargoLoadingRetirement(FrontierWorldState before, FrontierWorldState after,
                                                      io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                      PhysicalIntentTransition transition, PhysicalIntentRetirementAccount.Binding binding) {
        SupplyContract contract = cargoLoadingContract(before, intent);
        PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding,
                cargoLoadingRetirementBinding(before, null, intent, transition));
        SupplyContract current = after.contracts().get(contract.id());
        if (current == null || !current.cargoId().equals(contract.cargoId())) {
            throw new IllegalArgumentException("cargo loading retirement lost its exact contract/cargo authority");
        }
        // The physical transition is the first event in the one atomic command; CargoLoaded and
        // OperationCreated are reduced immediately afterwards.  At this owner-local boundary the
        // contract must therefore still be ordered, not prematurely required to observe those
        // later command events.
        if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED
                && current.status() != ContractStatus.ORDERED) {
            throw new IllegalArgumentException("cargo loading confirmation changed its contract before exact cargo transfer");
        } else if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                && current.status() != ContractStatus.ORDERED) {
            throw new IllegalArgumentException("ambiguous cargo loading changed its ordered contract");
        }
    }

    private static SupplyContract cargoLoadingContract(FrontierWorldState state,
                                                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        SupplyContract contract = state.contracts().get(intent.causeSubjectId());
        if (contract == null || contract.status() != ContractStatus.ORDERED
                || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.cargoLoading(
                contract.id(), contract.cargoId(), intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.SOURCE_ITEM)))) {
            throw new IllegalArgumentException("cargo loading retirement lacks its exact ordered contract");
        }
        return contract;
    }

    private static List<FrontierDomainRelationships.Edge> cargoLoadingRelations(SupplyContract contract) {
        FrontierDomainRelationships.SubjectEndpoint subject = new FrontierDomainRelationships.SubjectEndpoint(
                FrontierDomainRelationships.EntityKind.SUPPLY_CONTRACT, contract.id());
        return List.of(FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.CONTRACT_CARGO, subject, subject,
                new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.CARGO, contract.cargoId()),
                FrontierDomainRelationships.Lifecycle.ACTIVE, contract.id().value()));
    }

    private static CommandPlan planPhysicalTransition(FrontierWorldState state, FrontierCommand command,
                                                       io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                       PhysicalIntentTransition transition) {
        try {
            if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_LOADING) {
                return new CommandPlan.Accepted(SupplyOperationProcess.planCargoLoadingTransition(state, intent, transition, command.submittedAt().ticks()));
            }
            RouteOperation operation = state.operations().get(intent.causeSubjectId());
            if (operation == null) return FrontierWorldCommandPlanner.rejected("physical intent has no owning operation");
            return new CommandPlan.Accepted(SupplyOperationProcess.planTransition(state, intent, transition, command.submittedAt().ticks()));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }

    private static FrontierWorldState reducePhysicalPrepared(FrontierWorldState state,
                                                              io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                              io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_LOADING) {
            return CargoLoadingStateSupport.reducePrepared(state, subject, intent);
        }
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null || operation.stage() != OperationStage.ARRIVED || !subject.equals(operation.settlementId())
                || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.cargoHandoff(operation.id(), operation.cargoId())) ) {
            throw new IllegalArgumentException("physical intent does not own arrived cargo hand-off");
        }
        return state.preparePhysicalIntent(intent);
    }

    private static FrontierWorldState reducePhysicalTransition(FrontierWorldState state,
                                                               io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                               io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                               PhysicalIntentTransition transition) {
        if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_LOADING) {
            return CargoLoadingStateSupport.reduceTransition(state, subject, intent, transition);
        }
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null || !subject.equals(operation.settlementId())) {
            throw new IllegalArgumentException("physical intent transition subject does not own operation");
        }
        return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                (currentState, current, evidence, intents) -> CargoHandoffConfirmationStateSupport.complete(currentState, current,
                        evidence, new java.util.LinkedHashMap<>(intents), current.id(),
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED),
                PhysicalIntentTransitionStorage::recordUnknown);
    }
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof OperationAssemblyAdvanced advanced) {
            RouteOperation operation = state.operations().get(advanced.operationId());
            if (operation == null) return FrontierWorldCommandPlanner.rejected("operation assembly observation has no active operation");
            try {
                validateHotAssemblyObservation(state, operation, advanced);
                state.advanceOperationAssembly(advanced.operationId(), advanced.assembly(), advanced.executions());
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            List<ProposedEvent> events = new ArrayList<>(List.of(new ProposedEvent(operation.settlementId(), advanced)));
            if (advanced.assembly().complete()) {
                events.add(new ProposedEvent(operation.settlementId(), OperationExecutionAuthority.travelStarted(state, operation,
                        SupplyOperationProcess.travelForCompletedAssembly(state, operation, advanced.assembly()))));
                events.add(new ProposedEvent(operation.id(), new ScheduleEffect.Created(
                        SupplyOperationProcess.operationProgress(operation, command.submittedAt().ticks() + 20L))));
            }
            return new CommandPlan.Accepted(List.copyOf(events));
        }
        if (command.payload() instanceof OperationAssemblyDeferred deferred) {
            RouteOperation operation = state.operations().get(deferred.operationId());
            if (operation == null) return FrontierWorldCommandPlanner.rejected("operation assembly deferral has no active operation");
            try {
                validateHotAssemblyDeferral(state, operation, deferred.deferral());
                state.deferOperationAssembly(deferred.operationId(), deferred.deferral(), deferred.executions());
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), deferred)));
        }
        if (command.payload() instanceof OperationTravelSegmentCompleted completed) {
            RouteOperation operation = state.operations().get(completed.operationId());
            if (operation == null) return FrontierWorldCommandPlanner.rejected("operation travel completion has no active operation");
            try { state.completeOperationTravelSegment(completed.operationId(), completed.executions()); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), completed)));
        }
        if (command.payload() instanceof OperationTravelAdvanced advanced) {
            RouteOperation operation = state.operations().get(advanced.operationId());
            if (operation == null) return FrontierWorldCommandPlanner.rejected("operation travel observation has no active operation");
            try { state.advanceOperationTravel(advanced.operationId(), advanced.travel(), advanced.observation()); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), advanced)));
        }
        if (command.payload() instanceof OperationTravelStarted started) {
            RouteOperation operation = state.operations().get(started.operationId());
            if (operation == null) return FrontierWorldCommandPlanner.rejected("operation travel start has no active operation");
            try { state.startOperationTravel(started.operationId(), started.travel(), started.executions()); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), started)));
        }
        if (command.payload() instanceof SceneLeasePrepared prepared) {
            RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(prepared.lease()).operationId());
            if (operation == null) return FrontierWorldCommandPlanner.rejected("scene lease has no owning operation");
            try { OperationTravelContinuation.atScopeAdmission(state, prepared.lease()).prepareSceneLease(prepared.lease()); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), prepared)));
        }
        if (command.payload() instanceof SettlementAssaultSceneLeasePrepared prepared) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSettlementAssaultSceneSupport.owner(state, prepared.lease()), prepared))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof EngineeringWorkSceneLeasePrepared prepared) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierEngineeringWorkSceneSupport.owner(state,
                    FrontierSceneBehaviors.engineeringWorksite(prepared.lease())), prepared))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SceneLeaseHandoff handoff) {
            RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(handoff.lease()).operationId());
            if (operation == null) return FrontierWorldCommandPlanner.rejected("scene hand-off has no owning operation");
            try { OperationTravelContinuation.atScopeAdmission(state, handoff.lease()).handoffAmbientScene(handoff); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), handoff)));
        }
        if (command.payload() instanceof SettlementAssaultSceneLeaseHandoff handoff) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSettlementAssaultSceneSupport.owner(state, handoff.lease()), handoff))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof EngineeringWorkSceneLeaseHandoff handoff) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierEngineeringWorkSceneSupport.owner(state,
                    FrontierSceneBehaviors.engineeringWorksite(handoff.lease())), handoff))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SceneLeaseTransition transition) {
            SceneLease lease = state.sceneLeases().get(transition.leaseId());
            if (lease == null) return FrontierWorldCommandPlanner.rejected("scene lease is unknown");
            if (!transition.appliesTo(lease)) return FrontierWorldCommandPlanner.rejected("scene lease transition is not allowed from its current status");
            try {
                // Physical participants must already be confirmed by the body owner.
                // Reject an invalid transition before WAL admission, never in its reducer.
                state.transitionSceneLease(transition.leaseId(), transition.status());
                return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSceneOwnerSupport.owner(state, lease), transition)));
            }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SceneLeaseReleased released) return planSceneReleased(state, command, released);
        if (command.payload() instanceof SceneLeaseRecoveryUnresolved unresolved) return planRecoveryUnresolved(state, command, unresolved);
        if (command.payload() instanceof SceneLeaseRecoveryRevoked)
            return FrontierWorldCommandPlanner.rejected("scene recovery revoke has no saved body/health proof");
        return FrontierWorldCommandPlanner.rejected("logistics process does not admit command: " + command.payload().type());
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case SupplyContractCreated created -> reduceContractCreated(state, event.subject(), created);
            case SupplyContractAbandoned abandoned -> reduceContractAbandoned(state, event.subject(), abandoned);
            case CargoLoaded loaded -> reduceCargoLoaded(state, event.subject(), loaded);
            case CargoDelivered delivered -> SupplyOperationProcess.reduceDelivered(state, event.subject(), delivered);
            case OperationCreated created -> reduceOperationCreated(state, event.subject(), created);
            case OperationAdvanced advanced -> reduceOperationAdvanced(state, event.subject(), advanced);
            case OperationAssemblyAdvanced advanced -> reduceAssemblyAdvanced(state, event.subject(), advanced);
            case OperationAssemblyDeferred deferred -> reduceAssemblyDeferred(state, event.subject(), deferred);
            case OperationTravelStarted started -> reduceTravelStarted(state, event.subject(), started);
            case OperationTravelAdvanced advanced -> reduceTravelAdvanced(state, event.subject(), advanced);
            case OperationTravelSegmentCompleted completed -> reduceTravelSegmentCompleted(state, event.subject(), completed);
            case OperationColdSuspended suspended -> reduceColdSuspended(state, event.subject(), suspended);
            case SceneLeasePrepared prepared -> reduceScenePrepared(state, event.subject(), event, prepared);
            case SceneLeaseHandoff handoff -> reduceSceneHandoff(state, event.subject(), event, handoff);
            case SettlementAssaultSceneLeasePrepared prepared -> reduceAssaultPrepared(state, event.subject(), event, prepared);
            case SettlementAssaultSceneLeaseHandoff handoff -> reduceAssaultHandoff(state, event.subject(), event, handoff);
            case EngineeringWorkSceneLeasePrepared prepared -> reduceEngineeringPrepared(state, event.subject(), event, prepared);
            case EngineeringWorkSceneLeaseHandoff handoff -> reduceEngineeringHandoff(state, event.subject(), event, handoff);
            case SceneLeaseTransition transition -> reduceSceneTransition(state, event.subject(), transition, event.instant().ticks());
            case SceneLeaseReleased released -> reduceSceneReleased(state, event.subject(), released);
            case SceneLeaseRecoveryUnresolved unresolved -> reduceRecoveryUnresolved(state, event.subject(), unresolved);
            case SceneLeaseRecoveryRevoked revoked -> reduceRecoveryRevoked(state, event.subject(), revoked);
            case OperationFailed failed -> reduceOperationFailed(state, event.subject(), failed);
            case TerminalLogisticsCompacted compacted -> reduceCompacted(state, event.subject(), event.instant().ticks(), compacted);
            default -> throw new IllegalArgumentException("logistics process does not own event: " + event.payload().type());
        };
    }

    private static CommandPlan planSceneReleased(FrontierWorldState state, FrontierCommand command, SceneLeaseReleased released) {
        SceneLease lease = state.sceneLeases().get(released.leaseId());
        if (lease == null) return FrontierWorldCommandPlanner.rejected("scene lease is unknown");
        try { return new CommandPlan.Accepted(FrontierSceneContinuationPlanner.releaseEvents(state, lease, command.submittedAt().ticks(), released,
                command.scheduleBinding().map(io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action))); }
        catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }

    private static CommandPlan planRecoveryUnresolved(FrontierWorldState state, FrontierCommand command, SceneLeaseRecoveryUnresolved unresolved) {
        SceneLease lease = state.sceneLeases().get(unresolved.leaseId());
        if (lease == null || lease.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART || lease.recoveryEvidence().isPresent()) {
            return FrontierWorldCommandPlanner.rejected("scene recovery evidence does not bind one unresolved restart lease");
        }
        try { return new CommandPlan.Accepted(FrontierSceneContinuationPlanner.recoveryUnresolvedEvents(state, lease, command.submittedAt().ticks(), unresolved)); }
        catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }


    private static FrontierWorldState reduceContractCreated(FrontierWorldState state, SubjectId subject, SupplyContractCreated created) {
        SupplyContract contract = created.contract();
        if (!subject.equals(contract.settlementId())) throw new IllegalArgumentException("contract subject does not own settlement");
        SubjectId depot = FrontierWorldState.depotId(contract.settlementId());
        boolean backed = state.inventory().items().values().stream().anyMatch(item -> item.itemKind().equals(contract.itemKind())
                && item.count() == contract.itemCount() && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot));
        if (!backed) backed = FungibleResourceCustodySupport.firstAtContainer(state, depot, contract.itemKind(), contract.itemCount()).isPresent();
        if (!backed) throw new IllegalArgumentException("supply contract has no depot-backed resource");
        return state.createSupplyContract(contract);
    }

    private static FrontierWorldState reduceContractAbandoned(FrontierWorldState state, SubjectId subject, SupplyContractAbandoned abandoned) {
        SupplyContract contract = state.contracts().get(abandoned.contractId());
        if (contract == null || !subject.equals(contract.settlementId())) throw new IllegalArgumentException("abandoned contract has a foreign settlement owner");
        return state.abandonOrderedSupplyContract(abandoned.contractId());
    }

    private static FrontierWorldState reduceCargoLoaded(FrontierWorldState state, SubjectId subject, CargoLoaded loaded) {
        SupplyContract contract = state.contracts().get(loaded.contractId());
        if (contract == null || !subject.equals(contract.settlementId()) || !loaded.cargo().id().equals(contract.cargoId())) {
            throw new IllegalArgumentException("cargo load does not match its contract");
        }
        if (loaded.cargo().fungibleContents()) {
            FungibleResourceCustodySupport.LotAtContainer lot = FungibleResourceCustodySupport.firstAtContainer(state,
                    FrontierWorldState.depotId(contract.settlementId()), contract.itemKind(), contract.itemCount())
                    .orElseThrow(() -> new IllegalArgumentException("fungible cargo has no contract-backed lot"));
            return state.loadContractFungibleCargo(loaded.contractId(), loaded.cargo(), lot.accountId(), lot.lot().id());
        }
        if (loaded.cargo().itemIds().size() != 1) throw new IllegalArgumentException("exact cargo must name one contract item");
        ExactItemStack item = state.inventory().items().get(loaded.cargo().itemIds().getFirst());
        if (item == null || !item.itemKind().equals(contract.itemKind()) || item.count() != contract.itemCount()) throw new IllegalArgumentException("cargo item does not match contract demand");
        return state.loadContractCargo(loaded.contractId(), loaded.cargo());
    }

    private static FrontierWorldState reduceOperationCreated(FrontierWorldState state, SubjectId subject, OperationCreated created) {
        RouteOperation operation = created.operation();
        if (!subject.equals(operation.settlementId()) || operation.stage() != OperationStage.ASSEMBLING || operation.routeIndex() != 0) {
            throw new IllegalArgumentException("route operation must begin assembling at its owning settlement");
        }
        SupplyContract contract = state.contracts().values().stream().filter(value -> value.cargoId().equals(operation.cargoId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("route operation cargo has no supply contract"));
        if (contract.status() != ContractStatus.LOADED || !contract.settlementId().equals(operation.settlementId()) || !contract.recipientId().equals(operation.destinationId())) {
            throw new IllegalArgumentException("route operation does not match its loaded supply contract");
        }
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), operation.settlementId());
        if (!operation.route().equals(state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id()))) {
            throw new IllegalArgumentException("route operation must use the deterministic settlement-to-nest route");
        }
        return state.createOperation(operation, created.executions());
    }

    private static FrontierWorldState requireOperation(FrontierWorldState state, SubjectId subject, SubjectId operationId, String action) {
        RouteOperation operation = state.operations().get(operationId);
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException(action + " subject does not own operation");
        return state;
    }

    private static FrontierWorldState reduceOperationAdvanced(FrontierWorldState state, SubjectId subject, OperationAdvanced advanced) {
        requireOperation(state, subject, advanced.operationId(), "route advancement");
        return state.advanceOperation(advanced.operationId(), advanced.routeIndex(), advanced.stage());
    }
    private static FrontierWorldState reduceAssemblyAdvanced(FrontierWorldState state, SubjectId subject, OperationAssemblyAdvanced advanced) {
        requireOperation(state, subject, advanced.operationId(), "operation assembly");
        if (advanced.hotArrival().isPresent()) validateHotAssemblyObservation(state, state.operations().get(advanced.operationId()), advanced);
        else if (advanced.assembly().members().entrySet().stream().anyMatch(entry ->
                !entry.getValue().equals(state.operations().get(advanced.operationId()).activeAssembly().orElseThrow().members().get(entry.getKey()))
                        && !ActorExecutionCoordinator.coldAvailable(state, entry.getKey())))
            throw new IllegalArgumentException("COLD assembly transition cannot advance a physically held actor");
        return state.advanceOperationAssembly(advanced.operationId(), advanced.assembly(), advanced.executions());
    }
    private static FrontierWorldState reduceAssemblyDeferred(FrontierWorldState state, SubjectId subject, OperationAssemblyDeferred deferred) {
        requireOperation(state, subject, deferred.operationId(), "operation assembly deferral");
        return state.deferOperationAssembly(deferred.operationId(), deferred.deferral(), deferred.executions());
    }
    private static FrontierWorldState reduceTravelStarted(FrontierWorldState state, SubjectId subject, OperationTravelStarted started) {
        requireOperation(state, subject, started.operationId(), "operation travel");
        return state.startOperationTravel(started.operationId(), started.travel(), started.executions());
    }
    private static FrontierWorldState reduceTravelAdvanced(FrontierWorldState state, SubjectId subject, OperationTravelAdvanced advanced) {
        requireOperation(state, subject, advanced.operationId(), "operation travel");
        return state.advanceOperationTravel(advanced.operationId(), advanced.travel(), advanced.observation());
    }
    private static FrontierWorldState reduceTravelSegmentCompleted(FrontierWorldState state, SubjectId subject, OperationTravelSegmentCompleted completed) {
        requireOperation(state, subject, completed.operationId(), "operation travel completion");
        return state.completeOperationTravelSegment(completed.operationId(), completed.executions());
    }

    private static FrontierWorldState reduceColdSuspended(FrontierWorldState state, SubjectId subject, OperationColdSuspended suspended) {
        RouteOperation operation = state.operations().get(suspended.operationId());
        SceneLease lease = state.sceneLeases().get(suspended.leaseId());
        if (operation == null || !subject.equals(operation.settlementId()) || lease == null || !FrontierSceneBehaviors.isLogistics(lease)
                || !FrontierSceneBehaviors.logistics(lease).operationId().equals(operation.id())
                || lease.status() == SceneLeaseStatus.CLOSED || lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART) {
            throw new IllegalArgumentException("cold operation suspension lacks an active matching scene lease");
        }
        return state;
    }

    private static FrontierWorldState reduceScenePrepared(FrontierWorldState state, SubjectId subject, FrontierEvent event, SceneLeasePrepared prepared) {
        SceneLease lease = prepared.lease();
        RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
        if (operation == null || !subject.equals(operation.settlementId()) || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("scene lease does not match its current operation hand-off");
        }
        return OperationTravelContinuation.atScopeAdmission(state, lease).prepareSceneLease(lease);
    }

    private static FrontierWorldState reduceSceneHandoff(FrontierWorldState state, SubjectId subject, FrontierEvent event, SceneLeaseHandoff handoff) {
        SceneLease lease = handoff.lease();
        RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
        if (operation == null || !subject.equals(operation.settlementId()) || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("scene hand-off does not match its current operation hand-off");
        }
        return OperationTravelContinuation.atScopeAdmission(state, lease).handoffAmbientScene(handoff);
    }

    private static FrontierWorldState reduceAssaultPrepared(FrontierWorldState state, SubjectId subject, FrontierEvent event, SettlementAssaultSceneLeasePrepared prepared) {
        SceneLease lease = prepared.lease();
        if (!subject.equals(FrontierSettlementAssaultSceneSupport.owner(state, lease)) || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("assault scene lease does not match its retained battle hand-off");
        }
        return state.prepareSceneLease(lease);
    }

    private static FrontierWorldState reduceAssaultHandoff(FrontierWorldState state, SubjectId subject, FrontierEvent event, SettlementAssaultSceneLeaseHandoff handoff) {
        SceneLease lease = handoff.lease();
        if (!subject.equals(FrontierSettlementAssaultSceneSupport.owner(state, lease)) || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("assault scene hand-off does not match its retained battle");
        }
        return state.handoffAmbientScene(new SceneLeaseHandoff(lease, handoff.ambientMembers()));
    }

    private static FrontierWorldState reduceEngineeringPrepared(FrontierWorldState state, SubjectId subject, FrontierEvent event,
                                                                 EngineeringWorkSceneLeasePrepared prepared) {
        SceneLease lease = prepared.lease();
        if (!subject.equals(FrontierEngineeringWorkSceneSupport.owner(state, FrontierSceneBehaviors.engineeringWorksite(lease)))
                || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("engineering scene lease does not match its retained work-site hand-off");
        }
        return state.prepareSceneLease(lease);
    }

    private static FrontierWorldState reduceEngineeringHandoff(FrontierWorldState state, SubjectId subject, FrontierEvent event,
                                                                EngineeringWorkSceneLeaseHandoff handoff) {
        SceneLease lease = handoff.lease();
        if (!subject.equals(FrontierEngineeringWorkSceneSupport.owner(state, FrontierSceneBehaviors.engineeringWorksite(lease)))
                || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("engineering scene hand-off does not match its retained work-site");
        }
        return state.handoffAmbientScene(new SceneLeaseHandoff(lease, handoff.ambientMembers()));
    }

    private static FrontierWorldState reduceSceneTransition(FrontierWorldState state, SubjectId subject,
                                                          SceneLeaseTransition transition, long tick) {
        SceneLease lease = state.sceneLeases().get(transition.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("scene lease transition lacks its owning scene");
        if (transition.status() != SceneLeaseStatus.HOT) {
            for (SceneMember member : lease.members()) {
                if (state.humanPopulation().resident(member.actorId()) != null)
                    state = ActivityExecutionCapabilities.pauseLabour(state,
                            HumanAssignmentProjection.compile(state).assignment(member.actorId()), tick);
            }
        }
        return state.transitionSceneLease(transition.leaseId(), transition.status());
    }

    private static FrontierWorldState reduceSceneReleased(FrontierWorldState state, SubjectId subject, SceneLeaseReleased released) {
        SceneLease lease = state.sceneLeases().get(released.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("scene release lacks its owning scene");
        return state.releaseSceneLease(released.leaseId(), released.members());
    }

    private static FrontierWorldState reduceRecoveryUnresolved(FrontierWorldState state, SubjectId subject, SceneLeaseRecoveryUnresolved unresolved) {
        SceneLease lease = state.sceneLeases().get(unresolved.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("scene recovery evidence lacks its owning scene");
        return FrontierSceneLeaseStateSupport.recoveryUnresolved(state, unresolved);
    }

    private static FrontierWorldState reduceRecoveryRevoked(FrontierWorldState state, SubjectId subject, SceneLeaseRecoveryRevoked revoked) {
        SceneLease lease = state.sceneLeases().get(revoked.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) {
            throw new IllegalArgumentException("scene recovery revoke lacks its owning scene");
        }
        return FrontierSceneLeaseStateSupport.revokeUnknownPatrolToCold(state, revoked);
    }


    private static FrontierWorldState reduceOperationFailed(FrontierWorldState state, SubjectId subject, OperationFailed failed) {
        RouteOperation operation = state.operations().get(failed.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("operation failure lacks its owning settlement");
        boolean death = operation.participantIds().stream().anyMatch(actor -> state.actorLocations().get(actor).condition().status() == ActorLifeStatus.DEAD);
        boolean obstruction = "route-obstructed".equals(failed.reason())
                && (!state.routeTopology().supplyPassable(state.bootstrap(), operation.settlementId())
                || operation.activeTravel().map(travel -> !travel.canAdvanceNextEdge()).orElse(false));
        boolean recoveryUnresolved = "scene-recovery-unresolved".equals(failed.reason()) && state.sceneLeases().values().stream()
                .filter(FrontierSceneBehaviors::isLogistics).anyMatch(lease -> FrontierSceneBehaviors.logistics(lease).operationId().equals(operation.id())
                        && lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART && lease.recoveryEvidence().isPresent());
        if (!death && !obstruction && !recoveryUnresolved) throw new IllegalArgumentException("operation failure lacks a dead participant or observed route obstruction");
        return state.failOperation(failed.operationId());
    }

    private static FrontierWorldState reduceCompacted(FrontierWorldState state, SubjectId subject, long atTick, TerminalLogisticsCompacted compacted) {
        RouteOperation operation = state.operations().get(compacted.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("terminal logistics receipt has a foreign operation owner");
        return state.compactTerminalLogistics(compacted.operationId(), atTick);
    }

    private static void validateHotAssemblyObservation(FrontierWorldState state, RouteOperation operation, OperationAssemblyAdvanced evidence) {
        var physical = evidence.hotArrival().orElseThrow(() -> new IllegalArgumentException("HOT assembly requires captured body and scope evidence"));
        OperationAssembly next = evidence.assembly();
        OperationAssembly current = operation.activeAssembly().orElseThrow(() -> new IllegalArgumentException("operation has no active assembly"));
        if (!current.members().keySet().equals(next.members().keySet())) throw new IllegalArgumentException("HOT assembly observation changes formation");
        SubjectId observed = null;
        for (SubjectId actor : current.members().keySet()) {
            OperationAssembly.Member before = current.members().get(actor), after = next.members().get(actor);
            if (!before.equals(after) && !after.equals(before.advanceOne())) {
                throw new IllegalArgumentException("HOT assembly observation may advance only one adjacent cursor");
            }
            if (!before.equals(after)) {
                if (observed != null) throw new IllegalArgumentException("HOT assembly observation may acknowledge only one actor");
                observed = actor;
            }
        }
        if (observed == null) throw new IllegalArgumentException("HOT assembly observation did not advance an actor");
        if (!physical.body().actorId().equals(observed)
                || ActorBodyAuthority.require(state, physical.body()).phase() != FencedRecoveryPhase.RUNNING)
            throw new IllegalArgumentException("assembly arrival has stale or foreign physical custody");
        if (current.deferral().isPresent()) {
            OperationAssemblyDeferral blocked = current.deferral().orElseThrow();
            if (!blocked.actorId().equals(observed) || !next.members().get(observed).currentSurface().equals(blocked.target())) {
                throw new IllegalArgumentException("HOT assembly observation may not bypass a loaded-world assembly deferral");
            }
        }
        AmbientActorLease lease = state.ambientLeases().get(observed);
        OperationAssembly.Member arrived = next.members().get(observed);
        if (lease == null || lease.revision() != physical.leaseRevision() || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.OPERATION_ASSEMBLY
                || !lease.goalBody().equals(arrived.currentSurface().standingBody())
                || !state.actorLocations().get(observed).body().equals(arrived.currentSurface().standingBody())) {
            throw new IllegalArgumentException("HOT assembly observation lacks its exact active actor lease");
        }
    }

    private static void validateHotAssemblyDeferral(FrontierWorldState state, RouteOperation operation, OperationAssemblyDeferral deferral) {
        OperationAssembly assembly = operation.activeAssembly().orElseThrow(() -> new IllegalArgumentException("operation has no active assembly"));
        OperationAssembly.Member member = assembly.members().get(deferral.actorId());
        AmbientActorLease lease = state.ambientLeases().get(deferral.actorId());
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), operation.settlementId());
        SettlementStructure hall = settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("assembly settlement has no Hall access port"));
        SettlementAccessPort access = SettlementAccessPort.forHall(hall);
        if (member == null || member.arrived() || !member.nextSurface().equals(deferral.target())
                || lease == null || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.OPERATION_ASSEMBLY
                || !lease.goalBody().equals(deferral.target().standingBody())
                || (!deferral.obstructionSurface().equals(deferral.target()) && !deferral.obstructionSurface().equals(access.throatSurface()))) {
            throw new IllegalArgumentException("HOT assembly deferral lacks its exact active actor lease");
        }
    }
}
