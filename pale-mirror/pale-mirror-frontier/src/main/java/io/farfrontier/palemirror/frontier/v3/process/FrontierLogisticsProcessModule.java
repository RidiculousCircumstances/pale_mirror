package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Exact owner for contracts, cargo, route operations and their HOT/COLD scenes. */
final class FrontierLogisticsProcessModule implements FrontierWorldProcessModule {
    @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() {
        return List.of(new FunctionalPhysicalIntentLifecycleCapability(
                PhysicalIntentLifecycleOwner.ROUTE_OPERATION,
                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_LOADING,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_HANDOFF),
                (state, command, prepared) -> FrontierWorldCommandPlanner.rejected("physical executor cannot prepare route operation work"),
                FrontierLogisticsProcessModule::planPhysicalTransition,
                FrontierLogisticsProcessModule::reducePhysicalPrepared,
                FrontierLogisticsProcessModule::reducePhysicalTransition,
                PhysicalIntentLifecycleRetirementPolicy.of(
                        FrontierLogisticsProcessModule::planPhysicalTransition,
                        FrontierLogisticsProcessModule::reducePhysicalTransition),
                intent -> FencedRecoveryAsset.CARGO,
                retirementAccount(PhysicalIntentLifecycleOwner.ROUTE_OPERATION)));
    }

    private static PhysicalIntentRetirementAccount retirementAccount(PhysicalIntentLifecycleOwner owner) {
        return PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                FrontierLogisticsProcessModule::bindRetirement,
                FrontierLogisticsProcessModule::verifyRetirementBinding,
                (before, after, intent, transition, binding) -> {
                    if (intent.lifecycleOwner() != owner) throw new IllegalArgumentException("route retirement account owner mismatch");
                    RouteOperation operation = before.operations().get(intent.causeSubjectId());
                    if (operation == null || FrontierDomainRelationships.view(before).edges().stream().noneMatch(edge -> edge.kind()
                            == FrontierDomainRelationships.Kind.CONTRACT_ROUTE_OPERATION && edge.target().equals(
                            new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.ROUTE_OPERATION, operation.id())))) {
                        throw new IllegalArgumentException("route retirement account lacks exact contract relation");
                    }
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
        RouteOperation operation = before == null ? null : before.operations().get(intent.causeSubjectId());
        if (operation == null) throw new IllegalArgumentException("route retirement account has no exact operation");
        List<FrontierDomainRelationships.Edge> relations = retirementRelations(before, operation);
        if (relations.size() != 3) throw new IllegalArgumentException("route retirement account does not bind its exact contract/cargo/carrier relations");
        var continuation = command == null ? new PhysicalIntentRetirementAccount.CheckedNone<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>(io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)
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
        RouteOperation operation = before.operations().get(intent.causeSubjectId());
        if (operation == null) throw new IllegalArgumentException("route retirement account has no exact operation");
        List<FrontierDomainRelationships.Edge> relations = retirementRelations(before, operation);
        PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding, new PhysicalIntentRetirementAccount.Binding(
                intent.lifecycleOwner(), intent.id(), new PhysicalIntentRetirementAccount.Exact<>(relations), binding.continuation(),
                new PhysicalIntentRetirementAccount.Exact<>(operation.cargoCarrierId()), new PhysicalIntentRetirementAccount.Exact<>(operation.cargoId()),
                transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                        ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE));
    }

    /** Exact authoritative owners, never correlation-string discovery, bind route retirement. */
    private static List<FrontierDomainRelationships.Edge> retirementRelations(FrontierWorldState state, RouteOperation operation) {
        FrontierDomainRelationships.SubjectEndpoint contract = new FrontierDomainRelationships.SubjectEndpoint(
                FrontierDomainRelationships.EntityKind.SUPPLY_CONTRACT, operation.contractId());
        FrontierDomainRelationships.SubjectEndpoint route = new FrontierDomainRelationships.SubjectEndpoint(
                FrontierDomainRelationships.EntityKind.ROUTE_OPERATION, operation.id());
        List<FrontierDomainRelationships.Edge> relations = FrontierDomainRelationships.view(state).edges().stream()
                .filter(edge -> ((edge.kind() == FrontierDomainRelationships.Kind.CONTRACT_ROUTE_OPERATION
                        || edge.kind() == FrontierDomainRelationships.Kind.CONTRACT_CARGO) && edge.owner().equals(contract))
                        || (edge.kind() == FrontierDomainRelationships.Kind.ROUTE_CARRIER && edge.owner().equals(route)))
                .toList();
        if (relations.size() != 3) {
            throw new IllegalArgumentException("route retirement account does not bind its exact contract/cargo/carrier relations");
        }
        return relations;
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
                validateHotAssemblyObservation(state, operation, advanced.assembly());
                state.advanceOperationAssembly(advanced.operationId(), advanced.assembly());
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            List<ProposedEvent> events = new ArrayList<>(List.of(new ProposedEvent(operation.settlementId(), advanced)));
            if (advanced.assembly().complete()) {
                events.add(new ProposedEvent(operation.settlementId(), new OperationTravelStarted(operation.id(),
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
                state.deferOperationAssembly(deferred.operationId(), deferred.deferral());
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), deferred)));
        }
        if (command.payload() instanceof OperationTravelSegmentCompleted completed) {
            RouteOperation operation = state.operations().get(completed.operationId());
            if (operation == null) return FrontierWorldCommandPlanner.rejected("operation travel completion has no active operation");
            try { state.completeOperationTravelSegment(completed.operationId()); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), completed)));
        }
        if (command.payload() instanceof OperationTravelAdvanced advanced) {
            RouteOperation operation = state.operations().get(advanced.operationId());
            if (operation == null) return FrontierWorldCommandPlanner.rejected("operation travel observation has no active operation");
            try { state.advanceOperationTravel(advanced.operationId(), advanced.travel()); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), advanced)));
        }
        if (command.payload() instanceof OperationTravelStarted started) {
            RouteOperation operation = state.operations().get(started.operationId());
            if (operation == null) return FrontierWorldCommandPlanner.rejected("operation travel start has no active operation");
            try { state.startOperationTravel(started.operationId(), started.travel()); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), started)));
        }
        if (command.payload() instanceof SceneLeasePrepared prepared) {
            RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(prepared.lease()).operationId());
            if (operation == null) return FrontierWorldCommandPlanner.rejected("scene lease has no owning operation");
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
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSceneOwnerSupport.owner(state, lease), transition))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SceneLeaseReleased released) return planSceneReleased(state, command, released);
        if (command.payload() instanceof SceneLeaseRecoveryUnresolved unresolved) return planRecoveryUnresolved(state, command, unresolved);
        if (command.payload() instanceof SceneLeaseRecoveryRevoked revoked) return planRecoveryRevoked(state, command, revoked);
        if (command.payload() instanceof ActorDied death) return planActorDied(state, death);
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
            case SceneLeaseTransition transition -> reduceSceneTransition(state, event.subject(), transition);
            case SceneLeaseReleased released -> reduceSceneReleased(state, event.subject(), released);
            case SceneLeaseRecoveryUnresolved unresolved -> reduceRecoveryUnresolved(state, event.subject(), unresolved);
            case SceneLeaseRecoveryRevoked revoked -> reduceRecoveryRevoked(state, event.subject(), revoked);
            case ActorDied death -> reduceActorDied(state, event.subject(), event.instant().ticks(), death);
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

    private static CommandPlan planRecoveryRevoked(FrontierWorldState state, FrontierCommand command, SceneLeaseRecoveryRevoked revoked) {
        SceneLease lease = state.sceneLeases().get(revoked.leaseId());
        if (lease == null || lease.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART || !FrontierSceneBehaviors.isRoutePatrol(lease)) {
            return FrontierWorldCommandPlanner.rejected("scene recovery revoke does not bind one unknown patrol lease");
        }
        try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSceneOwnerSupport.owner(state, lease), revoked))); }
        catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }

    private static CommandPlan planActorDied(FrontierWorldState state, ActorDied death) {
        SceneLease lease = state.sceneLeases().get(death.leaseId());
        if (lease == null || (lease.status() != SceneLeaseStatus.HOT && lease.status() != SceneLeaseStatus.DRAINING)
                || lease.members().stream().noneMatch(member -> member.actorId().equals(death.actorId()))) {
            return FrontierWorldCommandPlanner.rejected("actor death is not evidence for an active scene member");
        }
        SubjectId owner;
        try { owner = FrontierSceneOwnerSupport.owner(state, lease); }
        catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        List<ProposedEvent> events = new ArrayList<>();
        events.add(new ProposedEvent(owner, death));
        CompanyFoundationProcess.terminationForDeath(state, death.actorId()).ifPresent(events::add);
        events.addAll(ProductionProcess.failPreEffectWorkForDeath(state, death.actorId()));
        if (lease.status() == SceneLeaseStatus.HOT) events.add(new ProposedEvent(owner, new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING)));
        return new CommandPlan.Accepted(List.copyOf(events));
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
        return state.createOperation(operation);
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
        return state.advanceOperationAssembly(advanced.operationId(), advanced.assembly());
    }
    private static FrontierWorldState reduceAssemblyDeferred(FrontierWorldState state, SubjectId subject, OperationAssemblyDeferred deferred) {
        requireOperation(state, subject, deferred.operationId(), "operation assembly deferral");
        return state.deferOperationAssembly(deferred.operationId(), deferred.deferral());
    }
    private static FrontierWorldState reduceTravelStarted(FrontierWorldState state, SubjectId subject, OperationTravelStarted started) {
        requireOperation(state, subject, started.operationId(), "operation travel");
        return state.startOperationTravel(started.operationId(), started.travel());
    }
    private static FrontierWorldState reduceTravelAdvanced(FrontierWorldState state, SubjectId subject, OperationTravelAdvanced advanced) {
        requireOperation(state, subject, advanced.operationId(), "operation travel");
        return state.advanceOperationTravel(advanced.operationId(), advanced.travel());
    }
    private static FrontierWorldState reduceTravelSegmentCompleted(FrontierWorldState state, SubjectId subject, OperationTravelSegmentCompleted completed) {
        requireOperation(state, subject, completed.operationId(), "operation travel completion");
        return state.completeOperationTravelSegment(completed.operationId());
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
        return state.prepareSceneLease(lease);
    }

    private static FrontierWorldState reduceSceneHandoff(FrontierWorldState state, SubjectId subject, FrontierEvent event, SceneLeaseHandoff handoff) {
        SceneLease lease = handoff.lease();
        RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
        if (operation == null || !subject.equals(operation.settlementId()) || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("scene hand-off does not match its current operation hand-off");
        }
        return state.handoffAmbientScene(handoff);
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

    private static FrontierWorldState reduceSceneTransition(FrontierWorldState state, SubjectId subject, SceneLeaseTransition transition) {
        SceneLease lease = state.sceneLeases().get(transition.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("scene lease transition lacks its owning scene");
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

    private static FrontierWorldState reduceActorDied(FrontierWorldState state, SubjectId subject, long atTick, ActorDied death) {
        SceneLease lease = state.sceneLeases().get(death.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("actor death lacks its owning scene");
        return state.recordActorDeath(death, atTick);
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

    private static void validateHotAssemblyObservation(FrontierWorldState state, RouteOperation operation, OperationAssembly next) {
        OperationAssembly current = operation.activeAssembly().orElseThrow(() -> new IllegalArgumentException("operation has no active assembly"));
        if (!current.members().keySet().equals(next.members().keySet())) throw new IllegalArgumentException("HOT assembly observation changes formation");
        SubjectId observed = null;
        for (SubjectId actor : current.members().keySet()) {
            OperationAssembly.Member before = current.members().get(actor), after = next.members().get(actor);
            if (!before.topology().equals(after.topology()) || after.cursor() < before.cursor() || after.cursor() > before.cursor() + 1) {
                throw new IllegalArgumentException("HOT assembly observation may advance only one adjacent cursor");
            }
            if (after.cursor() > before.cursor()) {
                if (observed != null) throw new IllegalArgumentException("HOT assembly observation may acknowledge only one actor");
                observed = actor;
            }
        }
        if (observed == null) throw new IllegalArgumentException("HOT assembly observation did not advance an actor");
        if (current.deferral().isPresent()) {
            OperationAssemblyDeferral blocked = current.deferral().orElseThrow();
            if (!blocked.actorId().equals(observed) || !next.members().get(observed).currentSurface().equals(blocked.target())) {
                throw new IllegalArgumentException("HOT assembly observation may not bypass a loaded-world assembly deferral");
            }
        }
        AmbientActorLease lease = state.ambientLeases().get(observed);
        OperationAssembly.Member arrived = next.members().get(observed);
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.OPERATION_ASSEMBLY
                || !lease.goalBody().equals(arrived.currentSurface().standingBody())) {
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
